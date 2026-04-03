package org.springframework.lab.txevent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 实验 4 — fallbackExecution 对照组。
 *
 * <p>源码决策分支 (TransactionalApplicationListenerMethodAdapter#onApplicationEvent):
 * <pre>
 * if (isSynchronizationActive && isActualTransactionActive) {
 *     // 有事务 → 注册 Synchronization，延迟执行
 *     registerSynchronization(new TransactionalApplicationListenerSynchronization<>(event, this, callbacks));
 * } else if (this.fallbackExecution) {
 *     // 无事务 + fallbackExecution=true → 立即执行 processEvent
 *     if (getTransactionPhase() == AFTER_ROLLBACK && logger.isWarnEnabled()) {
 *         logger.warn("Processing " + event + " as a fallback execution on AFTER_ROLLBACK phase");
 *     }
 *     processEvent(event);
 * } else {
 *     // 无事务 + fallbackExecution=false → 静默丢弃!
 *     logger.debug("No transaction is active - skipping " + event);
 * }
 * </pre>
 *
 * <p><b>风险提醒</b>: fallbackExecution=true 意味着「无论有无事务都执行」，
 * 如果监听器依赖事务一致性（比如发 MQ 确认消息），无事务时执行可能导致假一致性!
 * 只有真正不依赖事务结果的副作用才应该用 fallbackExecution=true。
 */
@Component
public class FallbackListener {

	/** fallbackExecution=false (默认) — 无事务时静默丢弃 */
	@TransactionalEventListener
	public void noFallback(OrderPlacedEvent event) {
		if ("no-tx".equals(event.getScenario())) {
			// 这行永远不会打印! 因为无事务时默认丢弃
			System.out.println("    ★ [AFTER_COMMIT, fallback=false] 无事务但收到了事件(不应出现)");
		}
	}

	/** fallbackExecution=true — 无事务时也立即执行 */
	@TransactionalEventListener(fallbackExecution = true)
	public void withFallback(OrderPlacedEvent event) {
		if ("no-tx".equals(event.getScenario())) {
			System.out.println("    ★ [AFTER_COMMIT, fallback=true] 无事务也执行! txActive="
					+ TransactionSynchronizationManager.isActualTransactionActive()
					+ " | " + event);
		}
	}
}
