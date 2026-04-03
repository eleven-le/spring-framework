package org.springframework.lab.event;

/**
 * 用于实验 6 — @TransactionalEventListener 场景的退款事件。
 */
public class OrderRefundedEvent {

	private final String orderId;
	private final int refundAmount;

	public OrderRefundedEvent(String orderId, int refundAmount) {
		this.orderId = orderId;
		this.refundAmount = refundAmount;
	}

	public String getOrderId() {
		return orderId;
	}

	public int getRefundAmount() {
		return refundAmount;
	}

	@Override
	public String toString() {
		return "OrderRefundedEvent{orderId='" + orderId + "', refundAmount=" + refundAmount + "}";
	}
}
