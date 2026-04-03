package org.springframework.lab.aftercommit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Outbox 投递器 — AFTER_COMMIT 触发后，标记 outbox 记录为已投递。
 *
 * <p>Outbox 模式核心思想:
 * <ol>
 *   <li>业务写 + outbox 写在同一事务 → 原子性保证</li>
 *   <li>AFTER_COMMIT 触发后，尝试投递（发 MQ）并标记 outbox 状态为 SENT</li>
 *   <li>如果 AFTER_COMMIT 回调失败（投递失败），outbox 记录仍为 PENDING</li>
 *   <li>兜底: 定时任务轮询 PENDING 状态的 outbox 记录进行补偿投递</li>
 * </ol>
 *
 * <p>这解决了「事务提交成功但 MQ 发送失败」的经典问题。
 */
@Component
public class OutboxPublisher {

	private final JdbcTemplate jdbc;

	public OutboxPublisher(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, condition = "#event.scenario == 'outbox'")
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void publishFromOutbox(OrderCreatedEvent event) {
		String eventId = event.getEventId();

		// 模拟: 从 outbox 表读取并投递
		String payload = jdbc.queryForObject(
				"SELECT payload FROM outbox WHERE event_id = ? AND status = 'PENDING'",
				String.class, eventId);

		System.out.println("    [OutboxPublisher] 从 outbox 读取待投递记录: " + payload);
		System.out.println("    [OutboxPublisher] 模拟发送 MQ 成功...");

		// 标记为已投递
		int updated = jdbc.update("UPDATE outbox SET status = 'SENT' WHERE event_id = ?", eventId);
		System.out.println("    [OutboxPublisher] outbox 状态更新为 SENT, affected=" + updated);
	}
}
