package org.springframework.lab.extensionmap;

import org.springframework.context.event.EventListener;

/**
 * 运行期 —— @EventListener 事件监听
 * <p>
 * 业务场景: 下单后发通知 / 记审计 / 同步库存 (解耦副作用)
 * 默认同步执行, 在 publishEvent 调用栈内完成
 */
public class OrderEventListener {

	@EventListener
	public void onOrderPlaced(OrderService.OrderPlacedEvent event) {
		TimelineTracker.record("运行",
				"@EventListener.onOrderPlaced",
				"orderId=" + event.getOrderId() + " (同步, 解耦副作用)");
	}
}
