package org.springframework.lab.event;

import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 实验 1 — 接口式监听器：实现 ApplicationListener。
 *
 * <p>注册路径 1: registerListeners() 扫描 getBeanNamesForType(ApplicationListener.class)。
 * <p>注册路径 2: ApplicationListenerDetector#postProcessAfterInitialization 对 singleton 补充注册。
 *
 * <p>泛型参数决定监听的事件类型，AbstractApplicationEventMulticaster#supportsEvent 通过
 * GenericApplicationListenerAdapter#resolveDeclaredEventType 解析泛型来过滤。
 */
@Component
public class InterfaceBasedListener implements ApplicationListener<OrderCreatedEvent>, Ordered {

	@Override
	public void onApplicationEvent(OrderCreatedEvent event) {
		System.out.println("  [接口式监听器] 收到 " + event
				+ "  thread=" + Thread.currentThread().getName());
	}

	@Override
	public int getOrder() {
		return 1;
	}
}
