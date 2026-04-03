package org.springframework.lab.jdbctemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * W69 — JdbcTemplate 模板方法骨架 + 回调接口设计 + SQLExceptionTranslator 练兵场
 *
 * 8 个实验覆盖：
 * 1. execute(StatementCallback)     — 模板骨架最小闭环: 获取连接→创建Statement→回调→异常翻译→关闭
 * 2. execute(ConnectionCallback)    — 最底层回调: 拿裸Connection做元数据查询
 * 3. query + RowMapper              — 最常用路径: SQL→PreparedStatement→ResultSet→逐行映射
 * 4. query + ResultSetExtractor     — 整批提取: 自己控制rs.next()做聚合/分组
 * 5. query + RowCallbackHandler     — 流式消费: 大数据量逐行处理不聚合
 * 6. update + PreparedStatementCreator + KeyHolder — 插入返回自增ID
 * 7. batchUpdate + BatchPreparedStatementSetter    — 批量更新: 攒一批送一次
 * 8. SQLExceptionTranslator 异常翻译链验证         — DuplicateKey / BadGrammar / 自定义翻译
 *
 * ── 核心断点 ──
 * 1. JdbcTemplate#execute(StatementCallback, boolean)  :375  — 模板骨架入口
 * 2. JdbcTemplate#execute(PSC, PSCallback, boolean)    :633  — PreparedStatement模板骨架
 * 3. JdbcTemplate#translateException(task, sql, ex)    :1574 — 异常翻译统一出口
 * 4. JdbcAccessor#getExceptionTranslator()             :123  — 惰性创建翻译器(volatile+DCL)
 * 5. SQLErrorCodeSQLExceptionTranslator#doTranslate()  :175  — 5级翻译决策链
 */
public class JdbcTemplateMain {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(JdbcTemplateConfig.class);

		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		JdbcTemplate customJdbc = ctx.getBean("customTranslatorJdbcTemplate", JdbcTemplate.class);

		exp1_statementCallback(jdbc);
		exp2_connectionCallback(jdbc);
		exp3_rowMapper(jdbc);
		exp4_resultSetExtractor(jdbc);
		exp5_rowCallbackHandler(jdbc);
		exp6_insertWithKeyHolder(jdbc);
		exp7_batchUpdate(jdbc);
		exp8_exceptionTranslator(jdbc, customJdbc);

		ctx.close();
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 1: StatementCallback — 模板骨架最小闭环
	// ═══════════════════════════════════════════════════════════════
	// 断点: JdbcTemplate:375 execute(StatementCallback, boolean)
	// 观察: getConnection→createStatement→applyStatementSettings→doInStatement→handleWarnings→closeStatement→releaseConnection
	static void exp1_statementCallback(JdbcTemplate jdbc) {
		System.out.println("═══ 实验1: StatementCallback — 模板骨架最小闭环 ═══");

		// 直接使用 StatementCallback 回调, 你只管写"做什么", 模板管"怎么做"
		Integer count = jdbc.execute((StatementCallback<Integer>) stmt -> {
			ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_user");
			rs.next();
			return rs.getInt(1);
		});
		System.out.println("  t_user 行数 = " + count);

		// execute(String sql) 内部也是包装成 StatementCallback
		jdbc.execute("CREATE TABLE IF NOT EXISTS t_temp (id INT)");
		System.out.println("  DDL 执行完毕 (内部走 ExecuteStatementCallback 匿名类)");
		jdbc.execute("DROP TABLE t_temp");
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 2: ConnectionCallback — 拿裸 Connection 做底层操作
	// ═══════════════════════════════════════════════════════════════
	// 断点: JdbcTemplate:329 execute(ConnectionCallback)
	// 观察: createConnectionProxy 生成 close-suppressing 代理, 你调 con.close() 不会真关
	static void exp2_connectionCallback(JdbcTemplate jdbc) {
		System.out.println("\n═══ 实验2: ConnectionCallback — 裸 Connection 操作 ═══");

		String dbInfo = jdbc.execute((ConnectionCallback<String>) con -> {
			// 这里拿到的是 CloseSuppressingProxy, 调 close() 被吞掉
			System.out.println("  Connection 类型: " + con.getClass().getSimpleName());
			return con.getMetaData().getDatabaseProductName() + " "
					+ con.getMetaData().getDatabaseProductVersion();
		});
		System.out.println("  数据库信息: " + dbInfo);
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 3: RowMapper — 最常用的逐行映射路径
	// ═══════════════════════════════════════════════════════════════
	// 断点: JdbcTemplate:706 query(psc, pss, rse) — 核心汇聚点
	// 调用链: query(sql, RowMapper) → query(sql, RowMapperResultSetExtractor)
	//       → query(SimplePSC, null, rse) → execute(psc, PSCallback)
	//       → PSCallback.doInPreparedStatement → pss.setValues → ps.executeQuery → rse.extractData
	//       → RowMapper.mapRow 逐行
	static void exp3_rowMapper(JdbcTemplate jdbc) {
		System.out.println("\n═══ 实验3: RowMapper — 逐行映射 ═══");

		// 方式 A: lambda RowMapper
		List<String> names = jdbc.query(
				"SELECT username, age FROM t_user WHERE age > ?",
				(rs, rowNum) -> rs.getString("username") + "(age=" + rs.getInt("age") + ")",
				25);
		System.out.println("  age>25 的用户: " + names);

		// 方式 B: queryForObject — 单行
		String email = jdbc.queryForObject(
				"SELECT email FROM t_user WHERE username = ?",
				String.class, "alice");
		System.out.println("  alice 的邮箱: " + email);

		// 方式 C: queryForMap — 单行多列
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT * FROM t_user WHERE username = ?", "bob");
		System.out.println("  bob 的完整行: " + row);

		// 方式 D: queryForList — 多行单列
		List<String> allNames = jdbc.queryForList(
				"SELECT username FROM t_user ORDER BY id", String.class);
		System.out.println("  所有用户名: " + allNames);
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 4: ResultSetExtractor — 整批提取(自控 rs.next())
	// ═══════════════════════════════════════════════════════════════
	// 与 RowMapper 的区别: RowMapper 框架帮你迭代, ResultSetExtractor 你自己迭代
	// 适用场景: 聚合统计、分组构建树形结构、一次查询多结果集
	static void exp4_resultSetExtractor(JdbcTemplate jdbc) {
		System.out.println("\n═══ 实验4: ResultSetExtractor — 整批提取 ═══");

		// 用 ResultSetExtractor 做聚合: 按年龄分桶
		Map<String, List<String>> ageGroups = jdbc.query(
				"SELECT username, age FROM t_user ORDER BY age",
				(ResultSetExtractor<Map<String, List<String>>>) rs -> {
					Map<String, List<String>> groups = new java.util.LinkedHashMap<>();
					while (rs.next()) {
						String bucket = rs.getInt("age") <= 27 ? "<=27" : ">27";
						groups.computeIfAbsent(bucket, k -> new ArrayList<>())
								.add(rs.getString("username"));
					}
					return groups;
				});
		System.out.println("  年龄分桶: " + ageGroups);
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 5: RowCallbackHandler — 流式逐行消费(不聚合)
	// ═══════════════════════════════════════════════════════════════
	// 内部实现: RowCallbackHandlerResultSetExtractor 适配器包装
	// 适用场景: 大数据量导出(边读边写文件)、逐行推送消息
	static void exp5_rowCallbackHandler(JdbcTemplate jdbc) {
		System.out.println("\n═══ 实验5: RowCallbackHandler — 流式消费 ═══");

		System.out.print("  逐行打印: ");
		jdbc.query("SELECT username FROM t_user ORDER BY id", (RowCallbackHandler) rs ->
				System.out.print(rs.getString("username") + " "));
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 6: update + PreparedStatementCreator + KeyHolder
	// ═══════════════════════════════════════════════════════════════
	// 断点: JdbcTemplate:985 update(psc, generatedKeyHolder) — 插入后提取自增ID
	// 调用链: update(PSC, KeyHolder) → execute(psc, PSCallback) → ps.executeUpdate → ps.getGeneratedKeys
	static void exp6_insertWithKeyHolder(JdbcTemplate jdbc) {
		System.out.println("\n═══ 实验6: 插入返回自增ID (KeyHolder) ═══");

		KeyHolder keyHolder = new GeneratedKeyHolder();
		int affected = jdbc.update(
				(PreparedStatementCreator) con -> {
					PreparedStatement ps = con.prepareStatement(
							"INSERT INTO t_user (username, email, age) VALUES (?, ?, ?)",
							new String[]{"id"}); // 指定要返回的自增列名
					ps.setString(1, "dave");
					ps.setString(2, "dave@example.com");
					ps.setInt(3, 30);
					return ps;
				},
				keyHolder);
		System.out.println("  插入 " + affected + " 行, 自增ID = " + keyHolder.getKey());

		// 简单 update 方式
		int updated = jdbc.update(
				"UPDATE t_user SET age = ? WHERE username = ?", 29, "dave");
		System.out.println("  更新 dave 年龄, 影响 " + updated + " 行");
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 7: batchUpdate — 批量更新
	// ═══════════════════════════════════════════════════════════════
	// 断点: JdbcTemplate:1029 batchUpdate(sql, BatchPreparedStatementSetter)
	// 观察: 内部用 ps.addBatch() + ps.executeBatch() 一次性送出
	static void exp7_batchUpdate(JdbcTemplate jdbc) {
		System.out.println("\n═══ 实验7: batchUpdate — 批量更新 ═══");

		String[] newUsers = {"eve", "frank", "grace"};
		int[] results = jdbc.batchUpdate(
				"INSERT INTO t_user (username, email, age) VALUES (?, ?, ?)",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						ps.setString(1, newUsers[i]);
						ps.setString(2, newUsers[i] + "@example.com");
						ps.setInt(3, 20 + i);
					}

					@Override
					public int getBatchSize() {
						return newUsers.length;
					}
				});
		System.out.println("  批量插入结果: " + java.util.Arrays.toString(results));

		Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM t_user", Integer.class);
		System.out.println("  当前总行数: " + total);
	}

	// ═══════════════════════════════════════════════════════════════
	// 实验 8: SQLExceptionTranslator 异常翻译链
	// ═══════════════════════════════════════════════════════════════
	// 断点: JdbcTemplate:1574  translateException → getExceptionTranslator().translate()
	// 断点: SQLErrorCodeSQLExceptionTranslator:175  doTranslate 5级决策
	//   1. customTranslate() 子类自定义
	//   2. CustomSQLExceptionTranslator 外挂翻译器
	//   3. CustomSQLErrorCodesTranslation 自定义errorCode映射
	//   4. 分组errorCode匹配(BadGrammar/DuplicateKey/DataIntegrity/...)
	//   5. fallback → SQLExceptionSubclassTranslator → SQLStateSQLExceptionTranslator
	static void exp8_exceptionTranslator(JdbcTemplate jdbc, JdbcTemplate customJdbc) {
		System.out.println("\n═══ 实验8: SQLExceptionTranslator 异常翻译链 ═══");

		// 8a: 默认翻译 — 唯一约束冲突 → DuplicateKeyException
		System.out.println("── 8a: 默认翻译器 ──");
		try {
			jdbc.update("INSERT INTO t_user (username, email, age) VALUES (?, ?, ?)",
					"alice", "duplicate@test.com", 99); // alice 已存在
		}
		catch (DuplicateKeyException e) {
			System.out.println("  捕获 DuplicateKeyException: " + e.getClass().getSimpleName());
			System.out.println("  原始 SQLException: " + e.getCause().getClass().getSimpleName());
		}

		// 8b: 语法错误 → BadSqlGrammarException
		System.out.println("── 8b: SQL语法错误 ──");
		try {
			jdbc.queryForObject("SELECT * FROM non_existent_table", String.class);
		}
		catch (DataAccessException e) {
			System.out.println("  捕获 " + e.getClass().getSimpleName() + ": " + e.getMessage().substring(0, Math.min(80, e.getMessage().length())));
		}

		// 8c: 自定义翻译器 — customTranslate() 覆盖
		System.out.println("── 8c: 自定义翻译器 ──");
		try {
			customJdbc.update("INSERT INTO t_user (username, email, age) VALUES (?, ?, ?)",
					"bob", "duplicate@test.com", 99); // bob 已存在
		}
		catch (DuplicateKeyException e) {
			System.out.println("  自定义翻译: " + e.getMessage());
		}

		// 8d: 翻译器懒加载验证
		System.out.println("── 8d: 翻译器类型 ──");
		System.out.println("  jdbc 翻译器: " + jdbc.getExceptionTranslator().getClass().getSimpleName());
		System.out.println("  customJdbc 翻译器: " + customJdbc.getExceptionTranslator().getClass().getSimpleName());
	}
}
