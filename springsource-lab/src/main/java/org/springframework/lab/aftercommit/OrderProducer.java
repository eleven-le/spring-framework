package org.springframework.lab.aftercommit;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 生产端 — 在事务中写 DB + 发布事件（事件延迟到 AFTER_COMMIT 触发）。
 *
 * <p>核心设计:
 * <ol>
 *   <li>INSERT 时生成 eventId（UUID），同时写入 orders 表和事件本身</li>
 *   <li>publishEvent 是同步调用，但 @TransactionalEventListener(AFTER_COMMIT) 只注册 Synchronization</li>
 *   <li>如果事务回滚，Synchronization 不会执行 AFTER_COMMIT → 事件自然不发送 → 天然一致</li>
 * </ol>
 *
 * <p>业务映射: 这就是"订单创建成功后发 MQ 通知库存/物流"的原型。
 */
@Service
public class OrderProducer {

	private final JdbcTemplate jdbc;
	private final ApplicationEventPublisher publisher;

	public OrderProducer(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
		this.jdbc = jdbc;
		this.publisher = publisher;
	}

	/**
	 * 实验 1: 正常 AFTER_COMMIT 发布 — 事务提交后才触发监听器。
	 */
	@Transactional
	public String createOrder(String orderId, String item, int price) {
		String eventId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO orders(order_id, item, price, event_id) VALUES(?,?,?,?)",
				orderId, item, price, eventId);
		System.out.println("    [Producer] INSERT 完成, eventId=" + eventId
				+ ", txActive=" + TransactionSynchronizationManager.isActualTransactionActive());

		publisher.publishEvent(new OrderCreatedEvent(this, eventId, orderId, item, price, "normal"));
		System.out.println("    [Producer] publishEvent 返回 — AFTER_COMMIT 监听器此时还没执行!");
		return eventId;
	}

	/**
	 * 实验 2: 事务回滚 — AFTER_COMMIT 不触发，事件自然不发送。
	 */
	@Transactional
	public void createOrderThenFail(String orderId, String item, int price) {
		String eventId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO orders(order_id, item, price, event_id) VALUES(?,?,?,?)",
				orderId, item, price, eventId);
		publisher.publishEvent(new OrderCreatedEvent(this, eventId, orderId, item, price, "rollback"));
		System.out.println("    [Producer] 即将抛异常 → 事务回滚 → AFTER_COMMIT 不触发");
		throw new RuntimeException("模拟下单失败 → 回滚");
	}

	/**
	 * 实验 3: 幂等重复发布 — 同一 eventId 发两次，消费端应去重。
	 */
	@Transactional
	public String createOrderDuplicate(String orderId, String item, int price) {
		String eventId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO orders(order_id, item, price, event_id) VALUES(?,?,?,?)",
				orderId, item, price, eventId);

		// 模拟: 同一事件被发布两次（真实场景 = MQ 重复投递）
		publisher.publishEvent(new OrderCreatedEvent(this, eventId, orderId, item, price, "dedup-1"));
		publisher.publishEvent(new OrderCreatedEvent(this, eventId, orderId, item, price, "dedup-2"));
		System.out.println("    [Producer] 同一 eventId 发布了两次, 消费端必须去重!");
		return eventId;
	}

	/**
	 * 实验 4: 模拟消费端失败 → 重试场景。
	 */
	@Transactional
	public String createOrderForRetry(String orderId, String item, int price) {
		String eventId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO orders(order_id, item, price, event_id) VALUES(?,?,?,?)",
				orderId, item, price, eventId);
		publisher.publishEvent(new OrderCreatedEvent(this, eventId, orderId, item, price, "retry"));
		return eventId;
	}

	/**
	 * 实验 5: Outbox 模式 — 事件写入 outbox 表，随主事务原子提交。
	 */
	@Transactional
	public String createOrderWithOutbox(String orderId, String item, int price) {
		String eventId = UUID.randomUUID().toString();
		// 业务写
		jdbc.update("INSERT INTO orders(order_id, item, price, event_id) VALUES(?,?,?,?)",
				orderId, item, price, eventId);
		// Outbox 写 — 与业务在同一事务中
		jdbc.update("INSERT INTO outbox(event_id, event_type, payload, status) VALUES(?,?,?,?)",
				eventId, "ORDER_CREATED",
				"{\"orderId\":\"" + orderId + "\",\"item\":\"" + item + "\",\"price\":" + price + "}",
				"PENDING");
		System.out.println("    [Producer] 业务 + outbox 在同一事务原子写入, eventId=" + eventId);

		// AFTER_COMMIT 触发 outbox 投递（也可以用 CDC/定时轮询，此处演示事件驱动方式）
		publisher.publishEvent(new OrderCreatedEvent(this, eventId, orderId, item, price, "outbox"));
		return eventId;
	}
}
