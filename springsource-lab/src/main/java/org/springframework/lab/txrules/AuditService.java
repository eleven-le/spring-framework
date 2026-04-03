package org.springframework.lab.txrules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 审计服务 — 演示 NESTED/MANDATORY/SUPPORTS 三种传播行为
 *
 * <p>NESTED 核心认知：在同一物理连接内创建 JDBC savepoint，
 * 失败时 rollbackToHeldSavepoint（而非整个事务回滚），成功时 releaseHeldSavepoint。
 * 外层仍然可以正常提交——审计失败不应该阻塞交易。
 *
 * <p>MANDATORY 核心认知：必须在已有事务中调用，否则直接抛 IllegalTransactionStateException。
 * 适用于"这段代码不应该被事务外调用"的防御性编程。
 *
 * <p>SUPPORTS 核心认知：有事务就加入，没事务就裸跑。
 * 适用于"只读查询"——有事务时享受事务一致性，无事务时节省开销。
 *
 * <p>NESTED 源码路径:
 * handleExistingTransaction → NESTED
 * → isNestedTransactionAllowed()? useSavepointForNestedTransaction()?
 * → status.createAndHoldSavepoint() → con.setSavepoint()
 * → 提交: releaseHeldSavepoint() → con.releaseSavepoint()
 * → 回滚: rollbackToHeldSavepoint() → con.rollback(savepoint)
 *
 * <p>断点:
 * {@code JdbcTransactionObjectSupport#createSavepoint} — 观察 JDBC savepoint 创建
 */
@Service
public class AuditService {

	private final JdbcTemplate jdbc;

	public AuditService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * NESTED — 正常审计，savepoint 保护
	 */
	@Transactional(propagation = Propagation.NESTED)
	public void log(String orderId, String action) {
		jdbc.update("INSERT INTO audit_log(order_id, action) VALUES(?, ?)", orderId, action);
		System.out.println("  [AuditService] NESTED 审计: " + action +
				" | txName=" + TransactionSynchronizationManager.getCurrentTransactionName());
	}

	/**
	 * NESTED — 审计后故意失败，演示 savepoint 回滚
	 */
	@Transactional(propagation = Propagation.NESTED)
	public void logAndFail(String orderId, String action) {
		jdbc.update("INSERT INTO audit_log(order_id, action) VALUES(?, ?)", orderId, action);
		throw new RuntimeException("审计写入后故意失败 → rollbackToHeldSavepoint，外层不受影响");
	}

	/**
	 * MANDATORY — 必须在已有事务中调用
	 *
	 * <p>源码路径:
	 * getTransaction → isExistingTransaction=false → 传播=MANDATORY
	 * → throw IllegalTransactionStateException("No existing transaction found...")
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void mandatoryLog(String orderId, String action) {
		jdbc.update("INSERT INTO audit_log(order_id, action) VALUES(?, ?)", orderId, action);
		System.out.println("  [AuditService] MANDATORY 审计: " + action +
				" | actualTxActive=" + TransactionSynchronizationManager.isActualTransactionActive());
	}

	/**
	 * SUPPORTS — 有事务就加入，没事务也行
	 *
	 * <p>源码路径:
	 * 1) 有事务: handleExistingTransaction → SUPPORTS → 直接参与
	 * 2) 无事务: getTransaction → SUPPORTS → prepareTransactionStatus(empty transaction)
	 */
	@Transactional(propagation = Propagation.SUPPORTS)
	public void supportsLog(String orderId, String action) {
		jdbc.update("INSERT INTO audit_log(order_id, action) VALUES(?, ?)", orderId, action);
		boolean inTx = TransactionSynchronizationManager.isActualTransactionActive();
		System.out.println("  [AuditService] SUPPORTS 审计: " + action +
				" | 实际在事务中=" + inTx);
	}
}
