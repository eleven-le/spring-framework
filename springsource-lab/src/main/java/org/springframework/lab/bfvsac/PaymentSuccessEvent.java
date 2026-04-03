package org.springframework.lab.bfvsac;

import org.springframework.context.ApplicationEvent;

/**
 * 支付成功事件 —— 只有 ApplicationContext 才能发布和监听
 *
 * <p>裸 BeanFactory 没有 ApplicationEventMulticaster，
 * 所以无法 publishEvent，也没有监听器注册机制。
 */
public class PaymentSuccessEvent extends ApplicationEvent {

	private final String orderId;

	public PaymentSuccessEvent(Object source, String orderId) {
		super(source);
		this.orderId = orderId;
	}

	public String getOrderId() {
		return orderId;
	}
}
