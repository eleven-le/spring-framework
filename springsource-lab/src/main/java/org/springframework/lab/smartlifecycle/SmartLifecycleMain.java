package org.springframework.lab.smartlifecycle;

import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.DefaultLifecycleProcessor;

import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ===================================================================
 *  W24 · SmartLifecycle 与优雅启停：phased 关停顺序 / DefaultLifecycleProcessor
 * ===================================================================
 *
 * 一句话抽象：
 *   SmartLifecycle 是容器级的"有序启停协议" ——
 *   解决的核心矛盾是：@PreDestroy 只能销毁单个 Bean，而优雅下线需要
 *   "先摘流量→再排空队列→最后关连接池"这种跨 Bean 的分阶段协调。
 *   DefaultLifecycleProcessor 用 phase 分组 + TreeMap 正序启/倒序停 +
 *   CountDownLatch 限时等待异步 stop(Runnable) 回调，实现了这一协议。
 *
 * 本 Demo 共 6 个实验：
 *   实验1: SmartLifecycle 基础 → isAutoStartup + start/stop + isRunning
 *   实验2: phase 分组启停顺序 → 启动 phase 从小到大，关停从大到小
 *   实验3: 异步 stop(Runnable) → 模拟异步关停 + CountDownLatch 超时
 *   实验4: Lifecycle vs SmartLifecycle → 普通 Lifecycle 不自动启动
 *   实验5: phase 与 @PreDestroy/DisposableBean 的执行顺序
 *   实验6: 自定义超时 → setTimeoutPerShutdownPhase + 超时告警
 *
 * 核心调用链（12 步）：
 *   1. AbstractApplicationContext#finishRefresh               : refresh 最后一步
 *   2. AbstractApplicationContext#initLifecycleProcessor      : 注册/获取 DefaultLifecycleProcessor
 *   3. DefaultLifecycleProcessor#onRefresh                    : 触发自动启动
 *   4. DefaultLifecycleProcessor#startBeans(true)             : autoStartupOnly=true，只启动 SmartLifecycle
 *   5. DefaultLifecycleProcessor#getLifecycleBeans            : 发现所有 Lifecycle Bean（SmartLifecycle 忽略 lazy-init）
 *   6. TreeMap&lt;phase, LifecycleGroup&gt;                   : 按 phase 正序分组
 *   7. LifecycleGroup#start → doStart                        : 递归先启动依赖，再启动自身
 *   8. AbstractApplicationContext#doClose                     : 容器关闭入口
 *   9. DefaultLifecycleProcessor#onClose → stopBeans          : 触发分阶段关停
 *  10. keys.sort(Collections.reverseOrder)                    : phase 倒序
 *  11. LifecycleGroup#stop → doStop                           : SmartLifecycle 传 Runnable 回调，普通同步 stop
 *  12. CountDownLatch.await(timeout)                          : 限时等待所有异步 stop 完成
 *
 * 断点抓手（5 个）：
 *   ① DefaultLifecycleProcessor#startBeans              → 看 TreeMap 分组 + phase 顺序
 *   ② DefaultLifecycleProcessor#stopBeans               → 看 reverseOrder 倒序逻辑
 *   ③ DefaultLifecycleProcessor#doStop                  → 看 SmartLifecycle vs Lifecycle 分支
 *   ④ LifecycleGroup#stop                               → 看 CountDownLatch 初始化 + await
 *   ⑤ AbstractApplicationContext#doClose                → 看整体关闭顺序: ContextClosedEvent → onClose → destroyBeans
 */
public class SmartLifecycleMain {

	public static void main(String[] args) throws Exception {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println(" W24 · SmartLifecycle 与优雅启停 Demo");
		System.out.println("═══════════════════════════════════════════════════════════\n");

		exp1_basicSmartLifecycle();
		exp2_phasedStartupShutdown();
		exp3_asyncStopCallback();
		exp4_lifecycleVsSmartLifecycle();
		exp5_phaseVsPreDestroy();
		exp6_customTimeout();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验1: SmartLifecycle 基础
	// 要点: 实现 SmartLifecycle 的 Bean 在 finishRefresh() 时自动 start()
	//       isAutoStartup()=true → 自动启动
	//       close() 时自动 stop(Runnable)
	// 源码: DefaultLifecycleProcessor#onRefresh → startBeans(true)
	// ─────────────────────────────────────────────────────────────
	static void exp1_basicSmartLifecycle() {
		System.out.println("【实验1】SmartLifecycle 基础 → 自动启停");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("mqConsumer", MqConsumerLifecycle.class);
		ctx.refresh();  // 触发 finishRefresh → onRefresh → startBeans

		MqConsumerLifecycle consumer = ctx.getBean(MqConsumerLifecycle.class);
		System.out.println("  >> isRunning = " + consumer.isRunning() + " (refresh 后自动启动)");

		ctx.close();  // 触发 doClose → onClose → stopBeans
		System.out.println("  >> isRunning = " + consumer.isRunning() + " (close 后自动停止)");
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验2: phase 分组启停顺序
	// 要点: 启动: phase 从小到大 (MIN_VALUE → 0 → MAX_VALUE)
	//       关停: phase 从大到小 (MAX_VALUE → 0 → MIN_VALUE)
	//       保证依赖方向: 基础设施先启后停
	// 源码: startBeans → new TreeMap (自然升序)
	//       stopBeans → keys.sort(Collections.reverseOrder())
	// C端场景: 注册中心摘除(phase=0) → 流量排空(100) → MQ消费者停止(200) → 连接池关闭(300)
	// ─────────────────────────────────────────────────────────────
	static void exp2_phasedStartupShutdown() {
		System.out.println("【实验2】phase 分组启停顺序 → 启动升序 / 关停降序");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 故意乱序注册，验证 phase 排序
		ctx.registerBean("connPool", PhasedComponent.class, () -> new PhasedComponent("连接池", 300));
		ctx.registerBean("registry", PhasedComponent.class, () -> new PhasedComponent("注册中心", 0));
		ctx.registerBean("mqConsumer", PhasedComponent.class, () -> new PhasedComponent("MQ消费者", 200));
		ctx.registerBean("trafficDrain", PhasedComponent.class, () -> new PhasedComponent("流量排空", 100));
		ctx.refresh();

		System.out.println("\n  >> 关停顺序 (phase 从大到小):");
		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验3: 异步 stop(Runnable) + CountDownLatch 限时等待
	// 要点: SmartLifecycle.stop(Runnable callback)
	//       组件在异步线程中完成关停后调用 callback.run()
	//       DefaultLifecycleProcessor 用 CountDownLatch 等待回调
	//       超过 30s 未回调 → 打印 warn 日志，但不阻塞
	// 源码: LifecycleGroup#stop → new CountDownLatch(smartMemberCount)
	//       → latch.await(timeout, MILLISECONDS)
	// ─────────────────────────────────────────────────────────────
	static void exp3_asyncStopCallback() throws Exception {
		System.out.println("【实验3】异步 stop(Runnable) → 模拟异步关停 + CountDownLatch");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("asyncService", AsyncStopComponent.class,
				() -> new AsyncStopComponent("异步Netty服务", 500, 0));
		ctx.refresh();

		System.out.println("  >> close() — 观察异步 stop + 回调:");
		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验4: Lifecycle vs SmartLifecycle
	// 要点: 普通 Lifecycle 不自动启动（onRefresh 时 autoStartupOnly=true 过滤掉）
	//       需要手动 context.start() 才会启动
	//       SmartLifecycle 默认 isAutoStartup()=true → 自动启动
	// 源码: startBeans(true) → 过滤条件:
	//       bean instanceof SmartLifecycle && ((SmartLifecycle) bean).isAutoStartup()
	// ─────────────────────────────────────────────────────────────
	static void exp4_lifecycleVsSmartLifecycle() {
		System.out.println("【实验4】Lifecycle vs SmartLifecycle → 普通 Lifecycle 不自动启动");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 普通 Lifecycle
		ctx.registerBean("plainLifecycle", PlainLifecycleBean.class);
		// SmartLifecycle
		ctx.registerBean("smartLifecycle", MqConsumerLifecycle.class);

		ctx.refresh();

		PlainLifecycleBean plain = ctx.getBean(PlainLifecycleBean.class);
		MqConsumerLifecycle smart = ctx.getBean(MqConsumerLifecycle.class);

		System.out.println("  >> refresh 后:");
		System.out.println("     PlainLifecycle.isRunning  = " + plain.isRunning() + " (不自动启动)");
		System.out.println("     SmartLifecycle.isRunning  = " + smart.isRunning() + " (自动启动)");

		System.out.println("\n  >> 手动 context.start():");
		ctx.start();
		System.out.println("     PlainLifecycle.isRunning  = " + plain.isRunning() + " (手动启动后运行)");

		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验5: SmartLifecycle.stop 与 @PreDestroy 的执行顺序
	// 要点: doClose() 的关键顺序:
	//       ① ContextClosedEvent 发布
	//       ② lifecycleProcessor.onClose() → SmartLifecycle.stop(Runnable) / Lifecycle.stop()
	//       ③ destroyBeans() → @PreDestroy / DisposableBean.destroy()
	//       结论: SmartLifecycle.stop() 先于 @PreDestroy
	// ─────────────────────────────────────────────────────────────
	static void exp5_phaseVsPreDestroy() {
		System.out.println("【实验5】执行顺序: ContextClosedEvent → SmartLifecycle.stop → @PreDestroy");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("orderedBean", FullShutdownBean.class);
		ctx.refresh();

		System.out.println("\n  >> close() — 观察三阶段关闭顺序:");
		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验6: 自定义超时 + 超时告警
	// 要点: DefaultLifecycleProcessor.setTimeoutPerShutdownPhase(ms)
	//       默认 30000ms (30秒)，可以缩短用于测试
	//       超时后不抛异常，只打 info 日志 + 继续下一个 phase
	// 源码: LifecycleGroup#stop → latch.await(this.timeout, MILLISECONDS)
	//       → if (latch.getCount() > 0) logger.info("Failed to shut down...")
	// ─────────────────────────────────────────────────────────────
	static void exp6_customTimeout() throws Exception {
		System.out.println("【实验6】自定义超时 → 500ms 超时 + 超时告警");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 注册自定义 DefaultLifecycleProcessor，超时 500ms
		ctx.registerBean("lifecycleProcessor", DefaultLifecycleProcessor.class, () -> {
			DefaultLifecycleProcessor processor = new DefaultLifecycleProcessor();
			processor.setTimeoutPerShutdownPhase(500);
			return processor;
		});

		// 注册一个 stop 需要 2000ms 的组件（超过 500ms 超时）
		ctx.registerBean("slowService", AsyncStopComponent.class,
				() -> new AsyncStopComponent("慢服务(2s关停)", 2000, 0));
		// 注册一个正常关停的组件
		ctx.registerBean("fastService", AsyncStopComponent.class,
				() -> new AsyncStopComponent("快服务(100ms关停)", 100, 0));

		ctx.refresh();

		System.out.println("  >> close() — 观察 500ms 超时后的告警日志:");
		System.out.println("     (慢服务 2s > 超时 500ms → 超时告警)");
		ctx.close();
		// 等慢服务的后台线程跑完
		Thread.sleep(2500);
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  内部组件类
	// ═══════════════════════════════════════════════════════════

	/** 基础 SmartLifecycle 实现 — 模拟 MQ 消费者 */
	static class MqConsumerLifecycle implements SmartLifecycle {
		private volatile boolean running = false;

		@Override
		public void start() {
			running = true;
			System.out.println("    [MQ消费者] start() → 开始消费消息");
		}

		@Override
		public void stop() {
			running = false;
			System.out.println("    [MQ消费者] stop() → 停止消费");
		}

		@Override
		public boolean isRunning() {
			return running;
		}

		@Override
		public int getPhase() {
			return 0;  // 默认 phase
		}
	}

	/** 分阶段组件 — 用 phase 控制启停顺序 */
	static class PhasedComponent implements SmartLifecycle {
		private final String name;
		private final int phase;
		private volatile boolean running = false;

		PhasedComponent(String name, int phase) {
			this.name = name;
			this.phase = phase;
		}

		@Override
		public void start() {
			running = true;
			System.out.println("    [phase=" + phase + "] " + name + " → start()");
		}

		@Override
		public void stop(Runnable callback) {
			running = false;
			System.out.println("    [phase=" + phase + "] " + name + " → stop(Runnable)");
			callback.run();  // 同步完成
		}

		@Override
		public void stop() {
			running = false;
		}

		@Override
		public boolean isRunning() {
			return running;
		}

		@Override
		public int getPhase() {
			return phase;
		}
	}

	/** 异步关停组件 — 模拟 Netty/gRPC 的异步 shutdown */
	static class AsyncStopComponent implements SmartLifecycle {
		private final String name;
		private final long shutdownDelayMs;
		private final int phase;
		private volatile boolean running = false;

		AsyncStopComponent(String name, long shutdownDelayMs, int phase) {
			this.name = name;
			this.shutdownDelayMs = shutdownDelayMs;
			this.phase = phase;
		}

		@Override
		public void start() {
			running = true;
			System.out.println("    [" + name + "] start()");
		}

		@Override
		public void stop(Runnable callback) {
			System.out.println("    [" + name + "] stop(Runnable) → 异步关停中...(需要 " + shutdownDelayMs + "ms)");
			// 模拟异步关停: 新线程中执行关停逻辑，完成后调用 callback
			new Thread(() -> {
				try {
					Thread.sleep(shutdownDelayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				running = false;
				System.out.println("    [" + name + "] 异步关停完成 → callback.run()");
				callback.run();  // 关键: 必须调用，否则 CountDownLatch 超时
			}, name + "-shutdown").start();
		}

		@Override
		public void stop() {
			running = false;
		}

		@Override
		public boolean isRunning() {
			return running;
		}

		@Override
		public int getPhase() {
			return phase;
		}
	}

	/** 普通 Lifecycle — 不会自动启动 */
	static class PlainLifecycleBean implements Lifecycle {
		private volatile boolean running = false;

		@Override
		public void start() {
			running = true;
			System.out.println("    [PlainLifecycle] start() → 手动启动");
		}

		@Override
		public void stop() {
			running = false;
			System.out.println("    [PlainLifecycle] stop()");
		}

		@Override
		public boolean isRunning() {
			return running;
		}
	}

	/** 完整关闭顺序演示 Bean — SmartLifecycle + @PreDestroy + ContextClosedEvent */
	static class FullShutdownBean implements SmartLifecycle, ApplicationListener<ContextClosedEvent> {
		private volatile boolean running = false;

		@Override
		public void start() {
			running = true;
			System.out.println("    [FullShutdownBean] start()");
		}

		@Override
		public void stop(Runnable callback) {
			running = false;
			System.out.println("    ② [FullShutdownBean] SmartLifecycle.stop(Runnable) → 优雅关停");
			callback.run();
		}

		@Override
		public void stop() {
			running = false;
		}

		@Override
		public boolean isRunning() {
			return running;
		}

		@Override
		public int getPhase() {
			return 0;
		}

		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
			System.out.println("    ① [FullShutdownBean] ContextClosedEvent → 收到关闭事件（最先执行）");
		}

		@PreDestroy
		public void preDestroy() {
			System.out.println("    ③ [FullShutdownBean] @PreDestroy → 销毁回调（最后执行）");
		}
	}
}
