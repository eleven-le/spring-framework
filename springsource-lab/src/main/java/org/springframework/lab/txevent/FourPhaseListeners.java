package org.springframework.lab.txevent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 实验 1/2 — 四阶段监听器对照组。
 *
 * <p>源码映射 (TransactionalApplicationListenerSynchronization):
 *
 * BEFORE_COMMIT → beforeCommit(boolean readOnly) 中触发
 *   时机: processCommit 中 triggerBeforeCommit → doCommit 之前
 *   特点: 仍在事务内! 抛异常会导致事务回滚!
 *   适用: 追加审计日志、flush 缓存等必须和主事务绑定的操作
 *
 * AFTER_COMMIT → afterCompletion(STATUS_COMMITTED) 中触发
 *   时机: processCommit 中 doCommit 成功后 → triggerAfterCommit → triggerAfterCompletion(STATUS_COMMITTED)
 *   特点: 事务已提交, 但连接资源仍然活跃; 写 DB 需要 REQUIRES_NEW
 *   适用: 发 MQ / 写 Outbox / 推送通知 / 发短信
 *
 * AFTER_ROLLBACK → afterCompletion(STATUS_ROLLED_BACK) 中触发
 *   时机: processRollback 中 doRollback 后 → triggerAfterCompletion(STATUS_ROLLED_BACK)
 *   特点: 事务已回滚, 做补偿/告警
 *   适用: 解冻库存 / 释放优惠券 / 发告警
 *
 * AFTER_COMPLETION → afterCompletion(任意status) 中触发
 *   时机: 无论提交还是回滚都会执行
 *   适用: 清理临时资源 / 关闭连接 / 移除 ThreadLocal
 *
 * <p>注意: AFTER_COMMIT/AFTER_ROLLBACK/AFTER_COMPLETION 三者都走 afterCompletion(int status) 方法,
 * 只是根据 status 值不同而选择性触发。它们共享同一个时间点，不是 afterCommit() 回调!
 */
@Component
public class FourPhaseListeners {

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	@Order(1)
	public void beforeCommit(OrderPlacedEvent event) {
		System.out.println("    ★ [BEFORE_COMMIT] 事务即将提交, txActive="
				+ TransactionSynchronizationManager.isActualTransactionActive()
				+ " | " + event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Order(2)
	public void afterCommit(OrderPlacedEvent event) {
		System.out.println("    ★ [AFTER_COMMIT] 事务已提交成功, txActive="
				+ TransactionSynchronizationManager.isActualTransactionActive()
				+ " | " + event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
	@Order(3)
	public void afterRollback(OrderPlacedEvent event) {
		System.out.println("    ★ [AFTER_ROLLBACK] 事务已回滚, txActive="
				+ TransactionSynchronizationManager.isActualTransactionActive()
				+ " | " + event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
	@Order(4)
	public void afterCompletion(OrderPlacedEvent event) {
		System.out.println("    ★ [AFTER_COMPLETION] 事务已结束(提交或回滚), txActive="
				+ TransactionSynchronizationManager.isActualTransactionActive()
				+ " | " + event);
	}
}
