package org.springframework.lab.extensionmap;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * 业务 Service —— 下单流程, 触发运行期扩展点 (AOP 拦截 + 事件发布)
 */
public class OrderService implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public String placeOrder(String orderId) {
		TimelineTracker.record("运行", "OrderService.placeOrder (业务逻辑)",
				"orderId=" + orderId);
		publisher.publishEvent(new OrderPlacedEvent(this, orderId));
		return "OK:" + orderId;
	}

	// ======== 订单事件 ========
	public static class OrderPlacedEvent extends ApplicationEvent {
		private final String orderId;

		public OrderPlacedEvent(Object source, String orderId) {
			super(source);
			this.orderId = orderId;
		}

		public String getOrderId() {
			return orderId;
		}
	}
}
