package org.springframework.lab.aftercommit;

import org.springframework.context.ApplicationEvent;

/**
 * 订单创建集成事件 — 携带幂等键（eventId）。
 *
 * <p>设计要点:
 * <ul>
 *   <li>eventId 是全局唯一的幂等键，由生产端在 INSERT 时生成</li>
 *   <li>消费端用 eventId 做去重，保证"至少一次"投递下不会重复处理</li>
 *   <li>payload 精简：只带下游处理必需的最小字段，不序列化整个聚合根</li>
 * </ul>
 */
public class OrderCreatedEvent extends ApplicationEvent {

	private final String eventId;     // 幂等键 — 全局唯一
	private final String orderId;
	private final String item;
	private final int price;
	private final String scenario;    // 实验场景标识

	public OrderCreatedEvent(Object source, String eventId, String orderId,
							 String item, int price, String scenario) {
		super(source);
		this.eventId = eventId;
		this.orderId = orderId;
		this.item = item;
		this.price = price;
		this.scenario = scenario;
	}

	public String getEventId() { return eventId; }
	public String getOrderId() { return orderId; }
	public String getItem() { return item; }
	public int getPrice() { return price; }
	public String getScenario() { return scenario; }

	@Override
	public String toString() {
		return "OrderCreatedEvent{eventId='" + eventId + "', orderId='" + orderId
				+ "', item='" + item + "', scenario='" + scenario + "'}";
	}
}
