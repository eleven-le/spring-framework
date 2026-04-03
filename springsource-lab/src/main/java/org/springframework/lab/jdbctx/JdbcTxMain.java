package org.springframework.lab.jdbctx;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W45 — JDBC 事务落地：DataSourceTransactionManager / ConnectionHolder / 线程绑定
 *
 * <h2>一句话抽象</h2>
 * DataSourceTransactionManager 解决"如何用最薄的一层把 JDBC 原生 Connection 的 commit/rollback
 * 纳入 Spring 统一事务模型"，ConnectionHolder 解决"一个 Connection 在事务期间如何被安全共享
 * 而不被提前关闭"，TransactionSynchronizationManager.bindResource 解决"业务代码在任意位置
 * 调 getConnection 时如何自动拿到事务绑定的那个连接"——核心矛盾是"Connection 是有状态的
 * (autoCommit/isolation/savepoint)、且线程不安全的，但 Service/DAO 多层调用都要拿到同一个
 * 连接才能保证事务一致性"，解法是 ThreadLocal<DataSource→ConnectionHolder> + 引用计数。
 *
 * <h2>实验列表 (7 个)</h2>
 * <ol>
 *   <li>doBegin 全链路 — Connection 线程绑定观察</li>
 *   <li>ConnectionHolder 引用计数 — 多次 getConnection 返回同一连接</li>
 *   <li>REQUIRES_NEW — suspend 解绑 / 新连接绑定 / resume 恢复</li>
 *   <li>NESTED — savepoint 共享同一 ConnectionHolder 和物理连接</li>
 *   <li>隔离级别落地 — @Transactional(isolation=SERIALIZABLE) → Connection.setTransactionIsolation</li>
 *   <li>事务外 getConnection — 无 ConnectionHolder 绑定</li>
 *   <li>doCleanupAfterCompletion — autoCommit 恢复 / 连接归还</li>
 * </ol>
 *
 * <h2>核心调用链 (10 步)</h2>
 * <pre>
 *  ① DataSourceTransactionManager#doGetTransaction : 从 ThreadLocal 取已有 ConnectionHolder (可能为 null)
 *  ② DSTM#doBegin : DataSource.getConnection → 包装成 ConnectionHolder → con.setAutoCommit(false) → bindResource(DataSource, holder)
 *  ③ ConnectionHolder#getConnection : 惰性从 ConnectionHandle 取真实 Connection
 *  ④ DataSourceUtils#doGetConnection : getResource(DataSource) → 命中 holder → requested() 引用+1 → 返回同一 Connection
 *  ⑤ JdbcTemplate#execute : DataSourceUtils.getConnection → 执行 SQL → DataSourceUtils.releaseConnection (引用-1)
 *  ⑥ DSTM#doCommit : holder.getConnection().commit()
 *  ⑦ DSTM#doSuspend : unbindResource(DataSource) → 返回被解绑的 ConnectionHolder
 *  ⑧ DSTM#doResume : bindResource(DataSource, suspendedHolder) → 恢复外层连接
 *  ⑨ DSTM#doCleanupAfterCompletion : unbindResource → con.setAutoCommit(true) → resetIsolation → releaseConnection
 *  ⑩ DataSourceUtils#releaseConnection : isOpen()==false 时真正 con.close() 归还连接池
 * </pre>
 *
 * <h2>断点抓手 (5 个)</h2>
 * <ol>
 *   <li>DataSourceTransactionManager#doBegin L275 — 观察 getConnection → setAutoCommit(false) → bindResource 全过程</li>
 *   <li>DataSourceUtils#doGetConnection L111 — 观察 getResource 命中 ThreadLocal 后直接复用 Connection</li>
 *   <li>DataSourceTransactionManager#doSuspend L318 — 观察 unbindResource 解绑外层 ConnectionHolder</li>
 *   <li>ConnectionHolder#getConnection L93 — 观察惰性取连接及 ConnectionHandle 间接层</li>
 *   <li>DataSourceTransactionManager#doCleanupAfterCompletion L370 — 观察事务结束后的恢复清理动作</li>
 * </ol>
 */
public class JdbcTxMain {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(JdbcTxConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		DataSource dataSource = ctx.getBean(DataSource.class);
		AccountService accountService = ctx.getBean(AccountService.class);
		TransactionTemplate txTemplate = new TransactionTemplate(ctx.getBean("transactionManager",
				org.springframework.transaction.PlatformTransactionManager.class));

		// 建表
		jdbc.execute("CREATE TABLE IF NOT EXISTS account(" +
				"id INT AUTO_INCREMENT PRIMARY KEY, " +
				"name VARCHAR(50) UNIQUE, " +
				"balance INT DEFAULT 0)");
		jdbc.execute("DELETE FROM account");
		jdbc.update("INSERT INTO account(name, balance) VALUES('Alice', 1000)");
		jdbc.update("INSERT INTO account(name, balance) VALUES('Bob', 1000)");

		// ═══════════════════════════════════════════════════════════════
		// 实验 1: doBegin 全链路 — 观察 Connection 的线程绑定
		// ═══════════════════════════════════════════════════════════════
		System.out.println("═══ 实验1: doBegin 全链路 — Connection 线程绑定 ═══");

		// 事务前: ThreadLocal 中没有绑定
		ConnectionHolder before = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		System.out.println("  事务前 ConnectionHolder: " + before + " (期望 null)");

		accountService.transfer("Alice", "Bob", 100);

		// 事务后: 已 cleanup
		ConnectionHolder after = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		System.out.println("  事务后 ConnectionHolder: " + after + " (期望 null, 已被 doCleanupAfterCompletion 解绑)");
		printBalances(jdbc, "实验1");
		resetBalances(jdbc);

		// ═══════════════════════════════════════════════════════════════
		// 实验 2: ConnectionHolder 引用计数 — 多次 getConnection 同一连接
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验2: ConnectionHolder 引用计数 ═══");
		accountService.verifyConnectionReuse();

		// ═══════════════════════════════════════════════════════════════
		// 实验 3: REQUIRES_NEW — suspend/resume 与不同物理连接
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验3: REQUIRES_NEW — suspend 解绑 / 新连接 / resume 恢复 ═══");
		resetBalances(jdbc);
		accountService.outerTransaction();
		printBalances(jdbc, "实验3");

		// ═══════════════════════════════════════════════════════════════
		// 实验 4: NESTED — savepoint, 共享同一 ConnectionHolder
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验4: NESTED — savepoint 共享同一连接 ═══");
		resetBalances(jdbc);
		accountService.outerWithNested();
		printBalances(jdbc, "实验4 (Alice -200 保留, Bob +200 被 savepoint 回滚)");

		// ═══════════════════════════════════════════════════════════════
		// 实验 5: 隔离级别落地到 Connection
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验5: 隔离级别 → Connection.setTransactionIsolation ═══");
		accountService.withIsolationLevel();

		// ═══════════════════════════════════════════════════════════════
		// 实验 6: 事务外 getConnection 行为
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验6: 事务外 DataSourceUtils.getConnection ═══");
		accountService.nonTransactionalGetConnection();

		// ═══════════════════════════════════════════════════════════════
		// 实验 7: doCleanupAfterCompletion 恢复动作
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验7: doCleanupAfterCompletion 恢复 autoCommit / 释放连接 ═══");
		resetBalances(jdbc);
		accountService.observeCleanup();
		ConnectionHolder postCleanup = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		System.out.println("  事务完成后 ConnectionHolder: " + postCleanup + " (期望 null)");

		ctx.close();
		System.out.println("\n[完成] W45 JDBC 事务落地全部实验执行完毕");
	}

	private static void resetBalances(JdbcTemplate jdbc) {
		jdbc.update("UPDATE account SET balance = 1000 WHERE name IN ('Alice', 'Bob')");
	}

	private static void printBalances(JdbcTemplate jdbc, String label) {
		Integer alice = jdbc.queryForObject("SELECT balance FROM account WHERE name = 'Alice'", Integer.class);
		Integer bob = jdbc.queryForObject("SELECT balance FROM account WHERE name = 'Bob'", Integer.class);
		System.out.println("  [" + label + "] Alice=" + alice + ", Bob=" + bob);
	}
}
