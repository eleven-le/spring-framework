package org.springframework.lab.jdbctx;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 业务 Service — 演示 DataSourceTransactionManager / ConnectionHolder / 线程绑定
 *
 * 核心关注点：
 * <ul>
 *   <li>doBegin 如何把 Connection 包进 ConnectionHolder 并绑到 ThreadLocal</li>
 *   <li>DataSourceUtils.getConnection() 如何复用线程绑定的同一物理连接</li>
 *   <li>REQUIRES_NEW 时 suspend 解绑 → 拿新连接 → resume 重新绑定</li>
 *   <li>ConnectionHolder 的引用计数 / transactionActive / savepoint 支持</li>
 * </ul>
 */
@Service
public class AccountService {

	private final JdbcTemplate jdbc;
	private final DataSource dataSource;

	public AccountService(JdbcTemplate jdbc, DataSource dataSource) {
		this.jdbc = jdbc;
		this.dataSource = dataSource;
	}

	// ========== 场景 1: doBegin 全链路 — 观察 Connection 的线程绑定 ==========
	@Transactional
	public void transfer(String from, String to, int amount) {
		// 此时 doBegin 已完成: getConnection → setAutoCommit(false) → bindResource
		ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		System.out.println("  [事务内] ConnectionHolder 已绑定: " + (holder != null));
		//System.out.println("  [事务内] transactionActive: " + (holder != null && holder.isTransactionActive()));
		if (holder != null) {
			Connection txConn = holder.getConnection();
			System.out.println("  [事务内] Connection hashCode: " + System.identityHashCode(txConn));
			try {
				System.out.println("  [事务内] autoCommit: " + txConn.getAutoCommit() + " (期望 false)");
			}
			catch (Exception ignored) {}
		}

		jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
		jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
		System.out.println("  [DB] 转账: " + from + " → " + to + ", 金额=" + amount);
	}

	// ========== 场景 2: 同一事务中多次 getConnection 返回同一物理连接 ==========
	@Transactional
	public void verifyConnectionReuse() {
		// 通过 DataSourceUtils 手动获取连接 — 应与 JdbcTemplate 用的是同一个
		Connection c1 = DataSourceUtils.getConnection(dataSource);
		Connection c2 = DataSourceUtils.getConnection(dataSource);
		Connection c3 = DataSourceUtils.getConnection(dataSource);

		System.out.println("  c1 hashCode: " + System.identityHashCode(c1));
		System.out.println("  c2 hashCode: " + System.identityHashCode(c2));
		System.out.println("  c3 hashCode: " + System.identityHashCode(c3));
		System.out.println("  c1 == c2 == c3: " + (c1 == c2 && c2 == c3) + " (期望 true)");

		// 检查 ConnectionHolder 引用计数
		ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		if (holder != null) {
			System.out.println("  ConnectionHolder isOpen: " + holder.isOpen() + " (引用计数 > 0)");
		}

		// 释放引用计数（不是关连接）
		DataSourceUtils.releaseConnection(c3, dataSource);
		DataSourceUtils.releaseConnection(c2, dataSource);
		DataSourceUtils.releaseConnection(c1, dataSource);
	}

	// ========== 场景 3: REQUIRES_NEW — 不同物理连接 + suspend/resume ==========
	@Transactional
	public void outerTransaction() {
		ConnectionHolder outerHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		Connection outerConn = outerHolder.getConnection();
		System.out.println("  [外层] Connection hashCode: " + System.identityHashCode(outerConn));

		jdbc.update("UPDATE account SET balance = balance - 100 WHERE name = 'Alice'");
		System.out.println("  [外层] Alice -100");

		// 调用内层 REQUIRES_NEW
		innerRequiresNew();

		// 回来后验证外层连接是否恢复
		ConnectionHolder restoredHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		Connection restoredConn = restoredHolder.getConnection();
		System.out.println("  [外层恢复] Connection hashCode: " + System.identityHashCode(restoredConn));
		System.out.println("  [外层恢复] 与挂起前相同: " + (outerConn == restoredConn) + " (期望 true)");
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void innerRequiresNew() {
		// 此时外层 ConnectionHolder 已被 doSuspend unbind，内层拿到新连接
		ConnectionHolder innerHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		Connection innerConn = innerHolder.getConnection();
		System.out.println("  [内层 REQUIRES_NEW] Connection hashCode: " + System.identityHashCode(innerConn));
		//System.out.println("  [内层] transactionActive: " + innerHolder.isTransactionActive());

		jdbc.update("UPDATE account SET balance = balance + 100 WHERE name = 'Bob'");
		System.out.println("  [内层] Bob +100 (独立事务)");
	}

	// ========== 场景 4: NESTED 共享 ConnectionHolder — savepoint ==========
	@Transactional
	public void outerWithNested() {
		ConnectionHolder outerHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		Connection outerConn = outerHolder.getConnection();

		jdbc.update("UPDATE account SET balance = balance - 200 WHERE name = 'Alice'");
		System.out.println("  [外层] Alice -200, Connection hashCode: " + System.identityHashCode(outerConn));

		try {
			nestedTransaction();
		}
		catch (RuntimeException e) {
			System.out.println("  [外层] 捕获嵌套异常: " + e.getMessage() + " (savepoint 已回滚, 外层不受影响)");
		}

		// 验证外层的 ConnectionHolder 没变
		ConnectionHolder afterHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		System.out.println("  [外层] 嵌套后 ConnectionHolder 不变: " + (outerHolder == afterHolder) + " (期望 true)");
		System.out.println("  [外层] 嵌套后 Connection 不变: " +
				(outerConn == afterHolder.getConnection()) + " (期望 true, NESTED 共享同一连接)");
	}

	@Transactional(propagation = Propagation.NESTED)
	public void nestedTransaction() {
		ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		System.out.println("  [NESTED] Connection hashCode: " + System.identityHashCode(holder.getConnection()));
		System.out.println("  [NESTED] 与外层共享同一 Connection (savepoint 而非新连接)");

		jdbc.update("UPDATE account SET balance = balance + 200 WHERE name = 'Bob'");
		System.out.println("  [NESTED] Bob +200 (savepoint 内)");

		// 模拟异常 → rollbackToSavepoint
		throw new RuntimeException("嵌套事务模拟异常 → 回滚到 savepoint");
	}

	// ========== 场景 5: 隔离级别如何落到 Connection ==========
	@Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
	public void withIsolationLevel() {
		ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		Connection conn = holder.getConnection();
		try {
			int level = conn.getTransactionIsolation();
			String name;
			switch (level) {
				case Connection.TRANSACTION_SERIALIZABLE: name = "SERIALIZABLE"; break;
				case Connection.TRANSACTION_REPEATABLE_READ: name = "REPEATABLE_READ"; break;
				case Connection.TRANSACTION_READ_COMMITTED: name = "READ_COMMITTED"; break;
				case Connection.TRANSACTION_READ_UNCOMMITTED: name = "READ_UNCOMMITTED"; break;
				default: name = "UNKNOWN(" + level + ")";
			}
			System.out.println("  Connection 隔离级别: " + name + " (期望 SERIALIZABLE)");
		}
		catch (Exception e) {
			System.out.println("  获取隔离级别失败: " + e.getMessage());
		}

		// ThreadLocal 也记录了
		Integer tsLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
		System.out.println("  ThreadLocal 记录的隔离级别: " + tsLevel + " (8=SERIALIZABLE)");

		jdbc.update("UPDATE account SET balance = balance + 1 WHERE name = 'Alice'");
		System.out.println("  [DB] 在 SERIALIZABLE 下执行更新");
	}

	// ========== 场景 6: 事务外 DataSourceUtils.getConnection 的行为 ==========
	public void nonTransactionalGetConnection() {
		// 没有 @Transactional，ThreadLocal 中没有 ConnectionHolder
		ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		System.out.println("  事务外 ConnectionHolder: " + holder + " (期望 null)");

		Connection c1 = DataSourceUtils.getConnection(dataSource);
		Connection c2 = DataSourceUtils.getConnection(dataSource);
		System.out.println("  c1 hashCode: " + System.identityHashCode(c1));
		System.out.println("  c2 hashCode: " + System.identityHashCode(c2));
		System.out.println("  c1 == c2: " + (c1 == c2) + " (无事务时可能不同, 取决于连接池)");

		try {
			System.out.println("  c1 autoCommit: " + c1.getAutoCommit() + " (期望 true, 没被事务接管)");
		}
		catch (Exception ignored) {}

		DataSourceUtils.releaseConnection(c2, dataSource);
		DataSourceUtils.releaseConnection(c1, dataSource);
	}

	// ========== 场景 7: doCleanupAfterCompletion 的恢复动作 ==========
	@Transactional
	public void observeCleanup() {
		ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
		Connection conn = holder.getConnection();
		System.out.println("  [事务中] Connection hashCode: " + System.identityHashCode(conn));
		try {
			System.out.println("  [事务中] autoCommit: " + conn.getAutoCommit() + " (期望 false)");
		}
		catch (Exception ignored) {}
		//System.out.println("  [事务中] transactionActive: " + holder.isTransactionActive());

		jdbc.update("UPDATE account SET balance = balance + 1 WHERE name = 'Alice'");

		// 注册 Sync 观察 cleanup 时机
		TransactionSynchronizationManager.registerSynchronization(
				new org.springframework.transaction.support.TransactionSynchronization() {
					@Override
					public void afterCompletion(int status) {
						// 此时 doCleanupAfterCompletion 即将执行:
						// 1. unbindResource(DataSource)
						// 2. con.setAutoCommit(true) — 恢复
						// 3. resetConnectionAfterTransaction — 恢复隔离级别/readOnly
						// 4. DataSourceUtils.releaseConnection — 归还连接池
						System.out.println("  [afterCompletion] 即将清理, status=" +
								(status == STATUS_COMMITTED ? "COMMITTED" : "ROLLED_BACK"));
						// 此时连接还在 ThreadLocal，但 transactionActive 已被清掉
						ConnectionHolder h = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
						if (h != null) {
							System.out.println("  [afterCompletion] ConnectionHolder 尚未解绑 (cleanup 在 afterCompletion 之后)");
						}
					}
				});
	}
}
