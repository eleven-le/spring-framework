package org.springframework.lab.event;

/**
 * 4.2+ 轻量级事件 — 不继承 ApplicationEvent，发布时自动包装为 PayloadApplicationEvent。
 * 监听器方法参数直接声明此类型即可，框架自动拆包。
 */
public class OrderPaidEvent {

	private final String orderId;
	private final int amount;

	public OrderPaidEvent(String orderId, int amount) {
		this.orderId = orderId;
		this.amount = amount;
	}

	public String getOrderId() {
		return orderId;
	}

	public int getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return "OrderPaidEvent{orderId='" + orderId + "', amount=" + amount + "}";
	}
}
