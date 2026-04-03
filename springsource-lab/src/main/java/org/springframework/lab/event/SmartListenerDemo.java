package org.springframework.lab.event;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 实验 3 — SmartApplicationListener：同时按 eventType + sourceType 双维度过滤。
 *
 * <p>普通 ApplicationListener 只能用泛型过滤 eventType，
 * SmartApplicationListener 额外提供 supportsSourceType 来按事件源类型过滤。
 *
 * <p>过滤调用链:
 * AbstractApplicationEventMulticaster#supportsEvent
 *   → smartListener.supportsEventType(eventType)
 *   → smartListener.supportsSourceType(sourceType)
 * 两个方法都返回 true 才会被派发。
 */
@Component
public class SmartListenerDemo implements SmartApplicationListener {

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return OrderCreatedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		// 只接收来自 OrderEventPublisher 发布的事件
		return OrderEventPublisher.class.isAssignableFrom(sourceType);
	}

	@Override
	public int getOrder() {
		return 0; // 最高优先级
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		System.out.println("  [SmartApplicationListener] 双维度过滤命中! " + event);
	}
}
