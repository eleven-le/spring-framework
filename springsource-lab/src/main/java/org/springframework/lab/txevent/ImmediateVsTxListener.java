package org.springframework.lab.txevent;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 实验 7 — 普通 @EventListener vs @TransactionalEventListener 执行时机对比。
 *
 * <p><b>核心区别</b>:
 * - @EventListener: publishEvent() 内部同步执行，在 publishEvent 返回前就已完成
 * - @TransactionalEventListener: publishEvent() 只注册 Synchronization，
 *   真正执行延迟到事务提交/回滚阶段
 *
 * <p>这意味着:
 * 如果 @EventListener 和 @TransactionalEventListener 监听同一个事件，
 * @EventListener 总是先执行（在 publishEvent 调用栈内），
 * @TransactionalEventListener 后执行（在事务 commit/rollback 调用栈内）。
 *
 * <p>关键差异: @EventListener 执行时事务还没提交 → 它看到的是事务内的「脏数据」。
 * @TransactionalEventListener(AFTER_COMMIT) 执行时事务已提交 → 它看到的是已持久化的数据。
 */
@Component
public class ImmediateVsTxListener {

	/** 普通 @EventListener — 在 publishEvent 内部同步执行 */
	@EventListener
	public void immediate(OrderPlacedEvent event) {
		if ("commit".equals(event.getScenario()) || "rollback".equals(event.getScenario())) {
			System.out.println("    → [@EventListener 立即执行] publishEvent 调用栈内, txActive="
					+ TransactionSynchronizationManager.isActualTransactionActive()
					+ " | " + event.getScenario());
		}
	}
}
