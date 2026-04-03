package org.springframework.lab.dsproxy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * <h2>W66 — 事务与 DataSource 代理边界: TransactionAware / LazyConnection</h2>
 *
 * 六个实验, 从基础到进阶:
 * <pre>
 * 实验1: TransactionAwareDataSourceProxy — close() 在事务内被拦截, 不关物理连接
 * 实验2: LazyConnectionDataSourceProxy  — 延迟获取, 无SQL不拿物理连接
 * 实验3: 两层代理叠加               — TransactionAware 包 Lazy 的完整代理链
 * 实验4: 原生 JDBC 在事务中透明参与      — 用 txAwareDataSource 让原生代码吃上事务
 * 实验5: AbstractRoutingDataSource  — 读写分离路由 + ThreadLocal
 * 实验6: LazyConnection + 读写路由     — readOnly事务若无SQL, 连物理连接都不拿
 * </pre>
 */
public class DsProxyMain {

	public static void main(String[] args) throws Exception {
		System.out.println("══════════════════════════════════════════════════════════════");
		System.out.println("  W66 — 事务与 DataSource 代理边界: TransactionAware / LazyConnection");
		System.out.println("══════════════════════════════════════════════════════════════\n");

		// 启动 Spring 容器
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(DsProxyConfig.class);

		DataSource realDs = ctx.getBean("realDataSource", DataSource.class);
		LazyConnectionDataSourceProxy lazyDs = ctx.getBean(LazyConnectionDataSourceProxy.class);
		TransactionAwareDataSourceProxy txAwareDs = ctx.getBean(TransactionAwareDataSourceProxy.class);
		PlatformTransactionManager tm = ctx.getBean(PlatformTransactionManager.class);
		DsProxyService service = ctx.getBean(DsProxyService.class);

		exp1_TransactionAwareCloseIntercept(txAwareDs, realDs, tm);
		exp2_LazyConnectionDefer(lazyDs);
		exp3_ProxyChainIdentity(txAwareDs, lazyDs, realDs, tm);
		exp4_RawJdbcInTransaction(txAwareDs, tm);
		exp5_ReadWriteRouting();
		exp6_LazyPlusRouting();

		ctx.close();
		System.out.println("\n══════════════════════════════════════════════════════════════");
		System.out.println("  全部实验完成");
		System.out.println("══════════════════════════════════════════════════════════════");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 1: TransactionAwareDataSourceProxy — close() 拦截
	// ═══════════════════════════════════════════════════════════

	/**
	 * 核心观察点:
	 * - 事务外: conn.close() → 物理关闭
	 * - 事务内: conn.close() → DataSourceUtils.doReleaseConnection() → 仅减引用计数, 不关
	 *
	 * 断点: TransactionAwareInvocationHandler#invoke case "close" (line 197)
	 */
	static void exp1_TransactionAwareCloseIntercept(
			TransactionAwareDataSourceProxy txAwareDs,
			DataSource realDs,
			PlatformTransactionManager tm) throws Exception {

		System.out.println("━━━ 实验1: TransactionAwareDataSourceProxy — close() 拦截 ━━━");

		// 1a. 事务外: close 直接关闭
		Connection conn1 = txAwareDs.getConnection();
		System.out.println("  事务外 conn 类型: " + conn1.getClass().getSimpleName());
		System.out.println("  是 ConnectionProxy? " + (conn1 instanceof ConnectionProxy));
		conn1.close();
		System.out.println("  事务外 close 后 isClosed: " + conn1.isClosed());

		// 1b. 事务内: close 被拦截 — 物理连接不关
		TransactionStatus tx = tm.getTransaction(new DefaultTransactionDefinition());
		// 先通过 DataSourceUtils 绑定事务连接 (TM 内部已做, 这里获取同一个)
		Connection txConn = DataSourceUtils.getConnection(realDs);
		System.out.println("  事务内 DataSourceUtils 获取的连接: " + txConn.hashCode());

		Connection conn2 = txAwareDs.getConnection();
		System.out.println("  事务内 txAwareDs 获取的连接(代理): " + conn2.getClass().getSimpleName());

		// 通过代理拿到的 targetConnection 应该 == txConn (同一物理连接)
		Connection target = ((ConnectionProxy) conn2).getTargetConnection();
		System.out.println("  proxy.getTargetConnection() == txConn ? " + (target == txConn));

		conn2.close();  // 不会真关!
		System.out.println("  事务内 conn2.isClosed(): " + conn2.isClosed() + " (代理标记)");
		System.out.println("  物理连接 txConn.isClosed(): " + txConn.isClosed() + " (仍然存活!)");

		DataSourceUtils.releaseConnection(txConn, realDs);
		tm.commit(tx);
		System.out.println("  事务提交后 txConn.isClosed(): " + txConn.isClosed());
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 2: LazyConnectionDataSourceProxy — 延迟获取
	// ═══════════════════════════════════════════════════════════

	/**
	 * 核心观察点:
	 * - getConnection() 返回的是 JDK 动态代理, 此时没有物理连接
	 * - setAutoCommit/setReadOnly 只记录到代理本地变量, 不拿物理连接
	 * - 直到 prepareStatement / createStatement 才真正 getConnection from targetDS
	 * - 如果只做 commit/rollback/close 但从没创建 Statement → 整个过程 0 物理连接
	 *
	 * 断点: LazyConnectionInvocationHandler#getTargetConnection (line 395-437)
	 */
	static void exp2_LazyConnectionDefer(LazyConnectionDataSourceProxy lazyDs) throws Exception {
		System.out.println("━━━ 实验2: LazyConnectionDataSourceProxy — 延迟获取 ━━━");

		// 2a. 获取 lazy 代理连接 — 此时无物理连接
		Connection lazyConn = lazyDs.getConnection();
		System.out.println("  lazyConn 类型: " + lazyConn.getClass().getSimpleName());
		System.out.println("  是 ConnectionProxy? " + (lazyConn instanceof ConnectionProxy));

		// 2b. 设置事务属性 — 全部缓存在代理本地, 不拿物理连接
		lazyConn.setAutoCommit(false);
		lazyConn.setReadOnly(true);
		lazyConn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		System.out.println("  设置 autoCommit=false, readOnly=true, isolation=READ_COMMITTED → 无物理连接");
		System.out.println("  getAutoCommit(): " + lazyConn.getAutoCommit());
		System.out.println("  isReadOnly(): " + lazyConn.isReadOnly());

		// 2c. commit/rollback/close — 如果从没 createStatement, 全部被忽略
		lazyConn.commit();   // 被忽略 (无 Statement 创建过)
		lazyConn.rollback(); // 被忽略
		System.out.println("  commit + rollback 被忽略 (从没创建过Statement)");

		lazyConn.close();
		System.out.println("  close 被忽略 (从没拿过物理连接)");
		System.out.println("  整个过程: 0 次物理连接获取!\n");

		// 2d. 对比: 如果创建了 Statement → 才真正拿物理连接
		Connection lazyConn2 = lazyDs.getConnection();
		lazyConn2.setAutoCommit(false);
		System.out.println("  lazyConn2: setAutoCommit(false), 仍无物理连接");

		PreparedStatement ps = lazyConn2.prepareStatement("SELECT 1");
		System.out.println("  prepareStatement('SELECT 1') → 此刻才获取物理连接!");

		Connection physicalConn = ((ConnectionProxy) lazyConn2).getTargetConnection();
		System.out.println("  物理连接 class: " + physicalConn.getClass().getName());
		System.out.println("  物理连接 autoCommit: " + physicalConn.getAutoCommit()
				+ " (已从代理缓存同步过去)");
		ps.close();
		lazyConn2.close();
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 3: 两层代理叠加 — 代理链身份验证
	// ═══════════════════════════════════════════════════════════

	/**
	 * 验证 TransactionAware(Lazy(Real)) 的三层结构,
	 * 以及在事务中三层如何协同工作
	 */
	static void exp3_ProxyChainIdentity(
			TransactionAwareDataSourceProxy txAwareDs,
			LazyConnectionDataSourceProxy lazyDs,
			DataSource realDs,
			PlatformTransactionManager tm) throws Exception {

		System.out.println("━━━ 实验3: 两层代理叠加 — TransactionAware(Lazy(Real)) ━━━");

		// 验证 DataSource 嵌套关系
		DataSource txAwareTarget = txAwareDs.getTargetDataSource();
		DataSource lazyTarget = lazyDs.getTargetDataSource();
		System.out.println("  txAwareDs.target == lazyDs ? " + (txAwareTarget == lazyDs));
		System.out.println("  lazyDs.target == realDs ?    " + (lazyTarget == realDs));

		// 在事务中使用 txAwareDs 获取连接
		TransactionStatus tx = tm.getTransaction(new DefaultTransactionDefinition());
		Connection conn = txAwareDs.getConnection();

		System.out.println("  txAwareDs.getConnection() 类型: " + conn.getClass().getSimpleName());
		System.out.println("  conn instanceof ConnectionProxy: " + (conn instanceof ConnectionProxy));

		// 逐层 unwrap
		Connection layer1 = ((ConnectionProxy) conn).getTargetConnection();
		System.out.println("  第1层 unwrap (TransactionAware→Lazy代理/物理): " + layer1.getClass().getName());

		conn.close();
		tm.commit(tx);
		System.out.println("  三层代理链验证完成\n");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 4: 原生 JDBC 在事务中透明参与
	// ═══════════════════════════════════════════════════════════

	/**
	 * 这就是 TransactionAwareDataSourceProxy 的核心价值:
	 * 原生 JDBC 代码 (不用 JdbcTemplate) 也能自动参与 Spring 事务
	 *
	 * 实际场景: 接入第三方库 (如 MyBatis/QueryDSL 直接用 DataSource.getConnection())
	 */
	static void exp4_RawJdbcInTransaction(
			TransactionAwareDataSourceProxy txAwareDs,
			PlatformTransactionManager tm) throws Exception {

		System.out.println("━━━ 实验4: 原生 JDBC 透明参与 Spring 事务 ━━━");

		TransactionStatus tx = tm.getTransaction(new DefaultTransactionDefinition());

		// 原生 JDBC 代码 — 不用 JdbcTemplate, 直接从 txAwareDs 拿连接
		Connection conn = txAwareDs.getConnection();
		PreparedStatement ps = conn.prepareStatement(
				"INSERT INTO t_order(product, amount) VALUES (?, ?)");
		ps.setString(1, "raw-jdbc-product");
		ps.setInt(2, 100);
		ps.executeUpdate();
		ps.close();
		System.out.println("  原生 JDBC INSERT 完成");

		// 验证在同一事务内可以查到
		PreparedStatement query = conn.prepareStatement("SELECT COUNT(*) FROM t_order WHERE product = ?");
		query.setString(1, "raw-jdbc-product");
		ResultSet rs = query.executeQuery();
		rs.next();
		System.out.println("  同事务内查询 count = " + rs.getInt(1));
		rs.close();
		query.close();

		conn.close();  // 被 TransactionAwareInvocationHandler 拦截, 不关物理连接!

		// 回滚 — 原生 JDBC 的写入也会被回滚
		tm.rollback(tx);
		System.out.println("  事务回滚 → 原生 JDBC 的写入被一起回滚");

		// 验证回滚后数据不存在
		Connection verify = txAwareDs.getConnection();
		PreparedStatement verifyPs = verify.prepareStatement(
				"SELECT COUNT(*) FROM t_order WHERE product = ?");
		verifyPs.setString(1, "raw-jdbc-product");
		ResultSet verifyRs = verifyPs.executeQuery();
		verifyRs.next();
		System.out.println("  回滚后查询 count = " + verifyRs.getInt(1) + " (数据已消失)");
		verifyRs.close();
		verifyPs.close();
		verify.close();
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 5: AbstractRoutingDataSource — 读写分离路由
	// ═══════════════════════════════════════════════════════════

	/**
	 * 演示:
	 * - 两个独立的 H2 DataSource 模拟 master / slave
	 * - ReadWriteRoutingDataSource 按 ThreadLocal 路由
	 * - 手动切 ThreadLocal 来模拟 AOP 读写切换
	 */
	static void exp5_ReadWriteRouting() throws Exception {
		System.out.println("━━━ 实验5: AbstractRoutingDataSource — 读写分离路由 ━━━");

		// 两个独立的 H2 数据库模拟 master / slave
		DataSource master = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.setName("master-db")
				.addScript("classpath:lab-dsproxy-schema.sql")
				.build();
		DataSource slave = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.setName("slave-db")
				.addScript("classpath:lab-dsproxy-schema.sql")
				.build();

		ReadWriteRoutingDataSource routingDs = ReadWriteRoutingDataSource.create(master, slave);

		// 先往 master 写数据
		JdbcTemplate masterJdbc = new JdbcTemplate(master);
		masterJdbc.update("INSERT INTO t_order(product, amount) VALUES ('master-item', 1)");

		// 往 slave 写不同数据 (模拟主从数据差异)
		JdbcTemplate slaveJdbc = new JdbcTemplate(slave);
		slaveJdbc.update("INSERT INTO t_order(product, amount) VALUES ('slave-item', 2)");

		JdbcTemplate routingJdbc = new JdbcTemplate(routingDs);

		// 默认路由 → WRITE (master)
		ReadWriteRoutingDataSource.setRoute(ReadWriteRoutingDataSource.RouteType.WRITE);
		Integer masterCount = routingJdbc.queryForObject(
				"SELECT COUNT(*) FROM t_order WHERE product = 'master-item'", Integer.class);
		System.out.println("  路由=WRITE → 查到 master-item: " + masterCount + " 条");

		// 切到 READ (slave)
		ReadWriteRoutingDataSource.setRoute(ReadWriteRoutingDataSource.RouteType.READ);
		Integer slaveCount = routingJdbc.queryForObject(
				"SELECT COUNT(*) FROM t_order WHERE product = 'slave-item'", Integer.class);
		System.out.println("  路由=READ  → 查到 slave-item:  " + slaveCount + " 条");

		// 验证: READ 路由查不到 master 数据
		Integer crossCheck = routingJdbc.queryForObject(
				"SELECT COUNT(*) FROM t_order WHERE product = 'master-item'", Integer.class);
		System.out.println("  路由=READ  → 查 master-item:   " + crossCheck + " 条 (不在slave里!)");

		ReadWriteRoutingDataSource.clearRoute();
		System.out.println("  ✓ 路由切换验证完成\n");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 6: LazyConnection + ReadWrite Routing 组合
	// ═══════════════════════════════════════════════════════════

	/**
	 * 核心价值: readOnly 事务如果走缓存没执行任何SQL → 整个事务周期 0 物理连接
	 * 生产场景: @Transactional(readOnly=true) + Hibernate 二级缓存命中
	 */
	static void exp6_LazyPlusRouting() throws Exception {
		System.out.println("━━━ 实验6: LazyConnection + ReadWrite Routing 组合 ━━━");

		DataSource master = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.setName("combo-master")
				.addScript("classpath:lab-dsproxy-schema.sql")
				.build();

		// 用 LazyConnection 包装 master
		LazyConnectionDataSourceProxy lazyMaster = new LazyConnectionDataSourceProxy(master);

		// 模拟 readOnly 事务 — 只设属性, 不执行SQL
		Connection lazyConn = lazyMaster.getConnection();
		lazyConn.setReadOnly(true);
		lazyConn.setAutoCommit(false);

		System.out.println("  开启 lazy 连接, 设置 readOnly=true, autoCommit=false");
		System.out.println("  此时是否已有物理连接? → 否 (ConnectionProxy target 为 null)");

		// 模拟: 业务代码走了缓存, 没执行任何 SQL
		// 直接 commit + close
		lazyConn.commit();  // 被忽略, 因为没 createStatement
		lazyConn.close();   // 被忽略
		System.out.println("  commit + close → 全部被忽略 (从没建过 Statement)");
		System.out.println("  连接池利用率: 100% 节省 — 未消耗任何物理连接!");

		// 对比: 如果有 SQL 执行
		Connection lazyConn2 = lazyMaster.getConnection();
		lazyConn2.setAutoCommit(false);
		PreparedStatement ps = lazyConn2.prepareStatement(
				"INSERT INTO t_order(product, amount) VALUES ('lazy-item', 1)");
		ps.executeUpdate();
		ps.close();
		System.out.println("\n  对比: prepareStatement → 此刻才拿物理连接, 执行SQL");
		lazyConn2.commit();
		lazyConn2.close();

		// 验证写入成功
		JdbcTemplate jdbc = new JdbcTemplate(master);
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM t_order WHERE product = 'lazy-item'", Integer.class);
		System.out.println("  验证写入: count = " + count);
		System.out.println();
	}
}
