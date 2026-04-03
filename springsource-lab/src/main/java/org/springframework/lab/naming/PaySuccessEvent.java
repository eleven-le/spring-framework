package org.springframework.lab.naming;

import org.springframework.context.ApplicationEvent;

/**
 * 支付成功事件 —— Listener 模式的事件载体。
 * 对照 Spring：ContextRefreshedEvent / ContextClosedEvent
 */
public class PaySuccessEvent extends ApplicationEvent {

	private final String orderId;
	private final long amountInCents;

	public PaySuccessEvent(Object source, String orderId, long amountInCents) {
		super(source);
		this.orderId = orderId;
		this.amountInCents = amountInCents;
	}

	public String getOrderId() {
		return orderId;
	}

	public long getAmountInCents() {
		return amountInCents;
	}
}
