package org.springframework.lab.txskeleton;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 审计服务 — 不同传播行为，触发事务骨架中不同的分支路径
 *
 * <p>每个方法对应 AbstractPlatformTransactionManager#handleExistingTransaction 的不同 if 分支:
 * <ul>
 *   <li>REQUIRES_NEW → suspend → startTransaction → doBegin 新连接</li>
 *   <li>NOT_SUPPORTED → suspend → prepareTransactionStatus(transaction=null)</li>
 *   <li>NESTED → useSavepointForNestedTransaction → createAndHoldSavepoint</li>
 *   <li>REQUIRED → 直接参与，不 suspend</li>
 * </ul>
 */
@Service
public class AuditService {

	private final JdbcTemplate jdbc;

	public AuditService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * REQUIRES_NEW: 触发完整的 suspend → doBegin(新连接) → doCommit → resume 链路
	 * 断点: AbstractPlatformTransactionManager#handleExistingTransaction L427
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void audit(String msg) {
		// 此时外层事务已被 suspend，ThreadLocal 已清空并重新初始化
		OrderService.printThreadTxState("audit(REQUIRES_NEW) - 内层独立事务中");
		jdbc.update("INSERT INTO audit_log(msg) VALUES(?)", msg);
		System.out.println("  [audit] REQUIRES_NEW 写入: " + msg);
	}

	/**
	 * NOT_SUPPORTED: 触发 suspend → 但不开新事务，以"空事务"运行
	 * 断点: AbstractPlatformTransactionManager#handleExistingTransaction L417
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void auditNotSupported(String msg) {
		// suspend 后没有真实事务，但同步仍可能 active (SYNCHRONIZATION_ALWAYS)
		OrderService.printThreadTxState("auditNotSupported - 无事务状态");
		jdbc.update("INSERT INTO audit_log(msg) VALUES(?)", msg);
		System.out.println("  [audit] NOT_SUPPORTED 写入(自动提交): " + msg);
	}

	/**
	 * NESTED: 不 suspend，在当前连接上创建 savepoint
	 * 断点: AbstractPlatformTransactionManager#handleExistingTransaction L442
	 * 断点: DefaultTransactionStatus#createAndHoldSavepoint
	 */
	@Transactional(propagation = Propagation.NESTED)
	public void auditNested(String msg) {
		jdbc.update("INSERT INTO audit_log(msg) VALUES(?)", msg);
		System.out.println("  [audit] NESTED 写入(savepoint): " + msg);
		// 故意抛异常 → rollbackToHeldSavepoint，只回滚到 savepoint，不影响外层
		throw new RuntimeException("嵌套事务故意失败 — 测试 savepoint 回滚");
	}

	/**
	 * REQUIRED: 直接参与外层事务，TransactionInfo 入栈但事务共享
	 * 走 handleExistingTransaction 最后的 "regular participation" 分支
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public void auditRequired(String msg) {
		OrderService.printThreadTxState("auditRequired(REQUIRED) - 参与外层事务");
		jdbc.update("INSERT INTO audit_log(msg) VALUES(?)", msg);
		System.out.println("  [audit] REQUIRED 参与外层事务写入: " + msg);
	}

	/**
	 * REQUIRES_NEW + 故意失败: 内层回滚，外层不受影响
	 * 完整链路: suspend → doBegin → 业务抛异常 → completeTransactionAfterThrowing
	 * → rollback → processRollback → doRollback → cleanupAfterCompletion → resume
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void auditFailInNew(String msg) {
		jdbc.update("INSERT INTO audit_log(msg) VALUES(?)", msg);
		throw new RuntimeException("REQUIRES_NEW 内层故意失败");
	}
}
