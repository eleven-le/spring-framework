package org.springframework.lab.txrules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 通知服务 — 演示 NOT_SUPPORTED 和 NEVER 传播行为
 *
 * <p>NOT_SUPPORTED 核心认知：suspend 当前事务，以无事务方式执行。
 * 适用场景：调用外部 HTTP/MQ 等 IO 操作，不应该被包在数据库事务里
 * （长事务持锁、超时回滚导致重复发送等问题）。
 *
 * <p>NEVER 核心认知：如果当前有事务就直接报错。
 * 适用场景：防御性编程——这段代码绝对不允许在事务内执行。
 *
 * <p>NOT_SUPPORTED 源码路径:
 * handleExistingTransaction → NOT_SUPPORTED
 * → suspend(transaction) → SuspendedResourcesHolder
 * → prepareTransactionStatus(definition, null, false, newSynchronization, ...)
 * → "空事务"状态执行业务
 * → cleanupAfterCompletion → resume(transaction, suspendedResources)
 *
 * <p>NEVER 源码路径:
 * handleExistingTransaction → NEVER
 * → throw IllegalTransactionStateException("Existing transaction found...")
 *
 * <p>断点:
 * {@code AbstractPlatformTransactionManager#handleExistingTransaction} L417-425 (NOT_SUPPORTED)
 */
@Service
public class NotifyService {

	/**
	 * NOT_SUPPORTED — 挂起当前事务，无事务方式执行通知
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void sendNotification(String orderId, String message) {
		boolean inTx = TransactionSynchronizationManager.isActualTransactionActive();
		System.out.println("  [NotifyService] NOT_SUPPORTED 发通知: " + message +
				" | 实际在事务中=" + inTx + " (应为false)");
		System.out.println("  [NotifyService] 模拟 HTTP/MQ 调用... 不在事务内，不持锁");
	}

	/**
	 * NEVER — 要求无事务上下文，否则直接抛异常
	 */
	@Transactional(propagation = Propagation.NEVER)
	public void neverInTx(String tag) {
		boolean inTx = TransactionSynchronizationManager.isActualTransactionActive();
		System.out.println("  [NotifyService] NEVER: tag=" + tag + " | 在事务中=" + inTx);
	}
}
