package org.springframework.lab.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 实验 6 — @TransactionalEventListener：事务感知的延迟派发。
 *
 * <p>核心机制:
 * TransactionalApplicationListenerMethodAdapter#onApplicationEvent
 *   → 检测到事务活跃: 注册 TransactionalApplicationListenerSynchronization 到 TransactionSynchronizationManager
 *   → 事务提交/回滚时，同步回调触发 processEvent
 *   → 若无活跃事务且 fallbackExecution=true: 立即执行
 *   → 若无活跃事务且 fallbackExecution=false: 静默丢弃
 *
 * <p>典型业务场景: 订单提交后发短信/推送，必须等事务真正 commit 再执行副作用。
 */
@Component
public class TransactionalListeners {

	/** AFTER_COMMIT (默认): 事务提交后执行 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void afterCommit(OrderRefundedEvent event) {
		System.out.println("  [@TransactionalEventListener AFTER_COMMIT] 事务已提交, 发短信通知: " + event);
	}

	/** AFTER_ROLLBACK: 事务回滚后执行 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
	public void afterRollback(OrderRefundedEvent event) {
		System.out.println("  [@TransactionalEventListener AFTER_ROLLBACK] 事务已回滚, 告警: " + event);
	}

	/** AFTER_COMPLETION: 无论提交还是回滚都执行 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
	public void afterCompletion(OrderRefundedEvent event) {
		System.out.println("  [@TransactionalEventListener AFTER_COMPLETION] 事务已结束(提交或回滚): " + event);
	}

	/** BEFORE_COMMIT: 事务提交前执行（仍在事务内，可以追加 SQL） */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void beforeCommit(OrderRefundedEvent event) {
		System.out.println("  [@TransactionalEventListener BEFORE_COMMIT] 事务即将提交, 可追加操作: " + event);
	}

	/** fallbackExecution: 无事务时也执行 */
	@TransactionalEventListener(fallbackExecution = true)
	public void fallback(OrderPaidEvent event) {
		System.out.println("  [@TransactionalEventListener fallback] 无论有无事务都执行: " + event);
	}
}
