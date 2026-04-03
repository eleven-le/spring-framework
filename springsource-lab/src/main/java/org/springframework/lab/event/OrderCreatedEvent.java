package org.springframework.lab.event;

import org.springframework.context.ApplicationEvent;

/**
 * 传统风格自定义事件 — 继承 ApplicationEvent。
 * 适合需要携带 source 语义的场景。
 */
public class OrderCreatedEvent extends ApplicationEvent {

	private final String orderId;
	private final String item;
	private final int price;

	public OrderCreatedEvent(Object source, String orderId, String item, int price) {
		super(source);
		this.orderId = orderId;
		this.item = item;
		this.price = price;
	}

	public String getOrderId() {
		return orderId;
	}

	public String getItem() {
		return item;
	}

	public int getPrice() {
		return price;
	}

	@Override
	public String toString() {
		return "OrderCreatedEvent{orderId='" + orderId + "', item='" + item + "', price=" + price + "}";
	}
}
