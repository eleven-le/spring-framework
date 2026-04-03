package org.springframework.lab.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事件发布者 — 模拟业务场景中通过 ApplicationEventPublisher 发布事件。
 *
 * <p>获取 publisher 有两种方式:
 * 1. 实现 ApplicationEventPublisherAware (本例)
 * 2. 直接 @Autowired ApplicationEventPublisher（因为 ApplicationContext 就是它的实现）
 *
 * <p>核心入口: AbstractApplicationContext#publishEvent
 *   → 非 ApplicationEvent 自动包装为 PayloadApplicationEvent
 *   → multicaster.multicastEvent(event, eventType)
 *   → 父容器也会收到事件（向上冒泡）
 */
@Component
public class OrderEventPublisher implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;
	private final JdbcTemplate jdbcTemplate;

	public OrderEventPublisher(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/** 实验 1/2/3: 发布传统 ApplicationEvent */
	public void createOrder(String orderId, String item, int price) {
		System.out.println("  [Publisher] 创建订单: " + orderId);
		publisher.publishEvent(new OrderCreatedEvent(this, orderId, item, price));
	}

	/** 实验 4: 发布 POJO 事件（PayloadApplicationEvent 自动包装） */
	public void payOrder(String orderId, int amount) {
		System.out.println("  [Publisher] 支付订单: " + orderId);
		publisher.publishEvent(new OrderPaidEvent(orderId, amount));
	}

	/** 实验 6: 在事务中发布事件 — 配合 @TransactionalEventListener */
	@Transactional
	public void refundOrder(String orderId, int amount) {
		jdbcTemplate.update("INSERT INTO refund_log(order_id, amount) VALUES(?, ?)", orderId, amount);
		System.out.println("  [Publisher] 退款记录已写入(事务内), 发布退款事件...");
		publisher.publishEvent(new OrderRefundedEvent(orderId, amount));
		System.out.println("  [Publisher] publishEvent 已返回(事务尚未提交)");
	}

	/** 实验 6b: 事务回滚场景 */
	@Transactional
	public void refundOrderFail(String orderId, int amount) {
		jdbcTemplate.update("INSERT INTO refund_log(order_id, amount) VALUES(?, ?)", orderId, amount);
		System.out.println("  [Publisher] 退款记录已写入(事务内), 发布退款事件...");
		publisher.publishEvent(new OrderRefundedEvent(orderId, amount));
		System.out.println("  [Publisher] 接下来故意抛异常触发回滚...");
		throw new RuntimeException("模拟退款失败");
	}
}
