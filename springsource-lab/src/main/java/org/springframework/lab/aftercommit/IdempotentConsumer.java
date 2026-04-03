package org.springframework.lab.aftercommit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 消费端 — 幂等消费 + 去重表。
 *
 * <p>核心设计:
 * <ol>
 *   <li>consumed_events 去重表: 用 eventId 做 UNIQUE KEY</li>
 *   <li>消费前先 SELECT 检查是否已处理，已处理则跳过</li>
 *   <li>消费成功后 INSERT 去重表 — 必须用 REQUIRES_NEW（因为 AFTER_COMMIT 阶段原事务已提交）</li>
 *   <li>业务逻辑本身也做幂等设计（如 INSERT ... ON CONFLICT DO NOTHING）</li>
 * </ol>
 *
 * <p>关键陷阱:
 * AFTER_COMMIT 回调中，原事务已提交但资源可能还绑定着。
 * 如果不用 REQUIRES_NEW，在 MySQL/PG 中写入不会真正 commit！
 * （H2 内存模式下可能看不出问题，但生产一定会踩坑。）
 *
 * <p>断点: 在 handleOrderCreated 方法入口打断点，观察:
 * - 调用栈经过 TransactionalApplicationListenerSynchronization#afterCompletion
 * - TransactionSynchronizationManager.isActualTransactionActive() == false（原事务已结束）
 */
@Component
public class IdempotentConsumer {

	private final JdbcTemplate jdbc;

	public IdempotentConsumer(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * AFTER_COMMIT 幂等消费 — 核心: 去重 + REQUIRES_NEW。
	 *
	 * <p>调用链:
	 * processCommit → triggerAfterCompletion(STATUS_COMMITTED)
	 *   → TransactionalApplicationListenerSynchronization#afterCompletion
	 *     → processEventWithCallbacks → listener.processEvent（反射调用此方法）
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)  // ← 必须! 原事务已结束
	public void handleOrderCreated(OrderCreatedEvent event) {
		String eventId = event.getEventId();
		String scenario = event.getScenario();

		// ─── 第一步: 去重检查 ───
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM consumed_events WHERE event_id = ?",
				Integer.class, eventId);

		if (count != null && count > 0) {
			System.out.println("    [Consumer] ⚡ 去重拦截! eventId=" + eventId
					+ " 已消费过, 跳过 (scenario=" + scenario + ")");
			return;
		}

		// ─── 第二步: 幂等业务处理 ───
		if ("retry".equals(scenario)) {
			// 实验 4: 模拟第一次消费失败
			RetryState state = RetryState.INSTANCE;
			if (state.getAttempt(eventId) < 2) {
				state.incrementAttempt(eventId);
				System.out.println("    [Consumer] ✗ 消费失败(第 " + state.getAttempt(eventId)
						+ " 次), eventId=" + eventId + " → 将重试");
				throw new RuntimeException("模拟消费端处理失败 → 触发重试");
			}
			System.out.println("    [Consumer] ✓ 第 " + (state.getAttempt(eventId) + 1)
					+ " 次重试成功, eventId=" + eventId);
		}
		else {
			System.out.println("    [Consumer] ✓ 幂等消费成功, eventId=" + eventId
					+ ", orderId=" + event.getOrderId() + ", item=" + event.getItem());
		}

		// ─── 第三步: 记录去重表（原子写入）───
		jdbc.update("INSERT INTO consumed_events(event_id, order_id, status) VALUES(?,?,?)",
				eventId, event.getOrderId(), "CONSUMED");
	}
}
