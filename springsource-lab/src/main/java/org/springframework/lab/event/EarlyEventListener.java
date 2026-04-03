package org.springframework.lab.event;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 实验 7 — 容器生命周期事件 + 早期事件机制。
 *
 * <p>Spring 内置事件:
 * - ContextRefreshedEvent: refresh() 第 12 步 finishRefresh() 发出
 * - ContextStartedEvent:  context.start() 触发
 * - ContextStoppedEvent:  context.stop() 触发
 * - ContextClosedEvent:   context.close()/registerShutdownHook() 触发
 *
 * <p>早期事件机制 (earlyApplicationEvents):
 * AbstractApplicationContext 在 prepareRefresh() 中初始化 earlyApplicationEvents = new LinkedHashSet()。
 * 在 registerListeners() 执行完之前（广播器 + 监听器尚未就绪），
 * publishEvent 会将事件暂存到 earlyApplicationEvents。
 * registerListeners() 末尾统一 multicast 早期事件，然后置 earlyApplicationEvents = null，
 * 之后的事件直接通过 multicaster 派发。
 */
@Component
public class EarlyEventListener {

	@EventListener
	public void onRefreshed(ContextRefreshedEvent event) {
		System.out.println("  [生命周期] ContextRefreshedEvent — 容器刷新完成!");
	}

	@EventListener
	public void onClosed(ContextClosedEvent event) {
		System.out.println("  [生命周期] ContextClosedEvent — 容器正在关闭!");
	}
}
