package org.springframework.lab.txrules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 库存服务 — 演示 REQUIRED 传播的"加入"语义
 *
 * <p>核心认知：REQUIRED 加入已有事务后，内层的任何回滚都会将整个事务标记为 rollback-only。
 * 即使外层 catch 了异常，commit 时仍会检测 {@code isGlobalRollbackOnly()} → 回滚并抛
 * {@code UnexpectedRollbackException}。
 *
 * <p>这就是"REQUIRED 内层异常必定传染外层"的根本原因：
 * {@code AbstractPlatformTransactionManager#processRollback} 中
 * {@code doSetRollbackOnly(status)} 设置了 ConnectionHolder.rollbackOnly = true，
 * 外层 commit 检测到后执行 processRollback(status, true) → 抛 UnexpectedRollbackException。
 *
 * <p>断点:
 * {@code DataSourceTransactionManager#doSetRollbackOnly} — 观察 ConnectionHolder.setRollbackOnly()
 */
@Service
public class InventoryService {

	private final JdbcTemplate jdbc;

	public InventoryService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * REQUIRED — 加入外层事务，库存与订单同一个物理连接
	 *
	 * <p>源码路径:
	 * getTransaction → isExistingTransaction=true → handleExistingTransaction
	 * → 传播=REQUIRED → 直接参与, 返回 newTransaction=false 的 TransactionStatus
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public void deduct(String sku, int qty) {
		jdbc.update("UPDATE inventory SET qty = qty - ? WHERE sku = ?", qty, sku);
		System.out.println("  [InventoryService] 扣库存: " + sku + " × " + qty +
				" | 同一事务=" + TransactionSynchronizationManager.getCurrentTransactionName());
	}

	/**
	 * REQUIRED + 内层失败 → 整体 rollback-only 传染
	 *
	 * <p>processRollback → doSetRollbackOnly → ConnectionHolder.setRollbackOnly(true)
	 * → 外层 commit 时 isGlobalRollbackOnly()=true → UnexpectedRollbackException
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public void deductAndFail(String sku, int qty) {
		jdbc.update("UPDATE inventory SET qty = qty - ? WHERE sku = ?", qty, sku);
		System.out.println("  [InventoryService] 扣库存后抛异常 → rollback-only 传染外层");
		throw new RuntimeException("库存不足，REQUIRED 内层回滚 → 标记整个事务 rollback-only");
	}

	/**
	 * SERIALIZABLE 隔离级别 — 用于演示 validateExistingTransaction 隔离冲突
	 *
	 * <p>当外层是 READ_COMMITTED 而内层声明 SERIALIZABLE + REQUIRED 时：
	 * handleExistingTransaction → validateExistingTransaction=true
	 * → definition.getIsolationLevel() != ISOLATION_DEFAULT
	 *   && definition.getIsolationLevel() != currentIsolationLevel
	 * → 抛 IllegalTransactionStateException
	 */
	@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
	public void deductSerializable(String sku, int qty) {
		jdbc.update("UPDATE inventory SET qty = qty - ? WHERE sku = ?", qty, sku);
	}
}
