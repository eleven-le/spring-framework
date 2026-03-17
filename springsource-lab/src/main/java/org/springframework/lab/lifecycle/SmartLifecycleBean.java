package org.springframework.lab.lifecycle;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 实验 4: SmartLifecycle — 容器级生命周期管理, 控制启停顺序。
 *
 * 触发点:
 *   start → AbstractApplicationContext#finishRefresh → DefaultLifecycleProcessor#onRefresh
 *   stop  → AbstractApplicationContext#doClose → DefaultLifecycleProcessor#onClose
 *
 * 关键属性:
 *   getPhase()     → 数值越小越先 start, 越后 stop (类似 @Order 但作用域不同)
 *   isAutoStartup()→ true 则容器 refresh 完自动 start; false 需手动
 *   isRunning()    → 容器据此判断是否需要调 start/stop
 *
 * 与 Bean 生命周期的时序关系:
 *   afterPropertiesSet → 在 finishBeanFactoryInitialization 中
 *   SmartLifecycle.start → 在 finishRefresh 中 (refresh 第 12 步, 所有 Bean 已就绪)
 *
 * C端场景: 启动 Netty Server / 开启消息消费者 / 注册服务到注册中心
 */
@Component
public class SmartLifecycleBean implements SmartLifecycle {

	private volatile boolean running = false;

	@Override
	public void start() {
		this.running = true;
		System.out.println("[SmartLifecycle] ▶ start() → phase=" + getPhase()
				+ " | 容器 finishRefresh 后自动启动");
		System.out.println("                  场景: 启动 Netty / 开启 MQ Consumer / 注册到 Nacos");
	}

	@Override
	public void stop() {
		this.running = false;
		System.out.println("[SmartLifecycle] ■ stop()  → phase=" + getPhase()
				+ " | 容器 doClose 时调用");
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public int getPhase() {
		return 0;
	}
}
