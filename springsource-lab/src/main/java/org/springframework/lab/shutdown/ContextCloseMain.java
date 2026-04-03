package org.springframework.lab.shutdown;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.DefaultLifecycleProcessor;

import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ===================================================================
 *  W52 · 关闭与优雅停机：Context close 全链路 / Drain 释放顺序
 * ===================================================================
 *
 * 一句话抽象：
 *   close() 是容器的"拆弹程序" —— 核心矛盾是：数十个组件之间存在隐式依赖,
 *   必须按 "通知→排空→停组件→销毁Bean→清缓存" 的严格五阶段顺序拆除,
 *   任何一步抛异常都不能中断后续清理(容错降级), 否则就是资源泄漏。
 *
 * doClose() 9 步拆弹序列：
 *   Step 1: publishEvent(ContextClosedEvent)     → 通知所有监听器"我要关了"
 *   Step 2: lifecycleProcessor.onClose()          → 按 phase 倒序停 SmartLifecycle (Drain 在此)
 *   Step 3: destroyBeans()                        → 按创建倒序销毁单例 (@PreDestroy/DisposableBean)
 *   Step 4: closeBeanFactory()                    → 关闭 BeanFactory 状态
 *   Step 5: onClose()                             → 子类钩子 (如 Servlet 容器关停)
 *   Step 6: resetCommonCaches()                   → 清反射缓存
 *   Step 7: 还原监听器集合
 *   Step 8: active.set(false)                     → 标记为非活跃
 *   Step 9: (close方法中) removeShutdownHook      → 摘除 JVM 钩子
 *
 * 本 Demo 共 7 个实验：
 *   实验1: doClose 五阶段顺序可视化          → ContextClosedEvent → Lifecycle.stop → @PreDestroy 严格先后
 *   实验2: Drain 排空模式 (请求计数器)       → 真实 in-flight 请求排空 + 零计数等待
 *   实验3: ShutdownHook vs 显式 close       → 两种触发路径 + close 后自动摘除 Hook
 *   实验4: 依赖感知销毁 (dependentBeans)     → B 依赖 A, 先销毁 B 再销毁 A
 *   实验5: 错误容错 (一个 Bean 炸了其余照常) → destroy() 吃异常不传播
 *   实验6: 多 phase 排空实战                 → 注册中心摘除→流量排空→MQ停消费→连接池关闭 完整链路
 *   实验7: close 幂等性 + CAS 保护           → 多次 close() 只执行一次 doClose
 *
 * 断点抓手（5 个高价值位置）：
 *   ① AbstractApplicationContext#doClose      (第1349行) → CAS 入口, 看 active/closed 状态翻转
 *   ② DefaultLifecycleProcessor#stopBeans     (第195行)  → 看 phase 分组 + reverseOrder 排序
 *   ③ DefaultLifecycleProcessor#doStop        (第224行)  → SmartLifecycle vs Lifecycle 分支 + 依赖递归
 *   ④ DefaultSingletonBeanRegistry#destroyBean(第568行)  → 依赖感知销毁: dependentBeans 递归 + 容错
 *   ⑤ LifecycleGroup#stop                     (第369行)  → CountDownLatch 初始化 + await 超时
 */
public class ContextCloseMain {

	public static void main(String[] args) throws Exception {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println(" W52 · 关闭与优雅停机：Context close 全链路 / Drain 释放顺序");
		System.out.println("═══════════════════════════════════════════════════════════\n");

		exp1_doClosePhaseOrder();
		exp2_drainPattern();
		exp3_shutdownHookVsExplicitClose();
		exp4_dependencyAwareDestroy();
		exp5_errorResilience();
		exp6_multiPhaseDrain();
		exp7_closeIdempotent();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验1: doClose 五阶段顺序可视化
	//  核心验证: ContextClosedEvent → SmartLifecycle.stop → @PreDestroy/DisposableBean
	//  源码: AbstractApplicationContext#doClose 第1346-1397行
	// ═══════════════════════════════════════════════════════════
	static void exp1_doClosePhaseOrder() {
		System.out.println("【实验1】doClose 五阶段顺序可视化");
		System.out.println("─────────────────────────────────────────────────────────");
		System.out.println("  预期顺序: ① ContextClosedEvent → ② SmartLifecycle.stop → ③ @PreDestroy → ④ DisposableBean.destroy\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("observer", ShutdownOrderObserver.class);
		ctx.refresh();

		ctx.close();
		System.out.println();
	}

	/**
	 * 观察者 Bean: 同时实现 SmartLifecycle + DisposableBean + @PreDestroy + ContextClosedEvent 监听
	 * 通过打印时序来验证 doClose 的阶段顺序
	 */
	static class ShutdownOrderObserver implements SmartLifecycle, DisposableBean,
			ApplicationListener<ContextClosedEvent> {
		private volatile boolean running = false;
		private final long startNano = System.nanoTime();

		private String elapsed() {
			return String.format("+%dms", (System.nanoTime() - startNano) / 1_000_000);
		}

		@Override
		public void start() {
			running = true;
			System.out.println("  [启动] ShutdownOrderObserver.start()");
		}

		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
			System.out.println("  [" + elapsed() + "] ① ContextClosedEvent → publishEvent(new ContextClosedEvent(this))");
			System.out.println("         ↳ doClose 第一步: 通知所有监听器");
		}

		@Override
		public void stop(Runnable callback) {
			running = false;
			System.out.println("  [" + elapsed() + "] ② SmartLifecycle.stop(Runnable) → lifecycleProcessor.onClose()");
			System.out.println("         ↳ doClose 第二步: 按 phase 倒序停所有 Lifecycle Bean");
			callback.run();
		}

		@Override
		public void stop() { running = false; }

		@PreDestroy
		public void preDestroy() {
			System.out.println("  [" + elapsed() + "] ③ @PreDestroy → destroyBeans() 阶段");
			System.out.println("         ↳ doClose 第三步: 按创建倒序销毁单例 (CommonAnnotationBPP 处理)");
		}

		@Override
		public void destroy() {
			System.out.println("  [" + elapsed() + "] ④ DisposableBean.destroy() → destroyBeans() 阶段");
			System.out.println("         ↳ 与 @PreDestroy 同阶段, 但 @PreDestroy 先于 DisposableBean");
		}

		@Override
		public boolean isRunning() { return running; }
		@Override
		public int getPhase() { return 0; }
	}

	// ═══════════════════════════════════════════════════════════
	//  实验2: Drain 排空模式 (请求计数器)
	//  真实场景: HTTP 服务关停时, 需要等 in-flight 请求全部完成
	//  关键: stop(Runnable callback) 中用 AtomicInteger 倒计时,
	//        模拟异步请求完成后再调 callback.run()
	//  源码: DefaultLifecycleProcessor#doStop → SmartLifecycle.stop(callback)
	//        LifecycleGroup#stop → CountDownLatch.await(timeout)
	// ═══════════════════════════════════════════════════════════
	static void exp2_drainPattern() throws Exception {
		System.out.println("【实验2】Drain 排空模式 → 请求计数器 + 零计数等待");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("lifecycleProcessor", DefaultLifecycleProcessor.class, () -> {
			DefaultLifecycleProcessor p = new DefaultLifecycleProcessor();
			p.setTimeoutPerShutdownPhase(5000);
			return p;
		});
		ctx.registerBean("httpServer", RequestDrainLifecycle.class);
		ctx.refresh();

		// 模拟 3 个 in-flight 请求
		RequestDrainLifecycle server = ctx.getBean(RequestDrainLifecycle.class);
		server.simulateInflightRequests(3);

		System.out.println("  >> 当前 in-flight 请求数: " + server.getInflightCount());
		System.out.println("  >> 开始 close() — 观察 Drain 排空过程:\n");
		ctx.close();
		Thread.sleep(500);
		System.out.println();
	}

	/**
	 * 请求排空 Lifecycle — 模拟 HTTP 服务的 Drain 模式
	 * stop(callback) 时: 拒绝新请求 + 等待 inflight 归零 + 调回调
	 */
	static class RequestDrainLifecycle implements SmartLifecycle {
		private volatile boolean running = false;
		private volatile boolean draining = false;
		private final AtomicInteger inflightCount = new AtomicInteger(0);

		void simulateInflightRequests(int count) {
			inflightCount.set(count);
			// 模拟请求在后台逐个完成
			for (int i = 0; i < count; i++) {
				final int reqId = i + 1;
				new Thread(() -> {
					sleep(200 * reqId); // 每个请求间隔 200ms 完成
					int remaining = inflightCount.decrementAndGet();
					System.out.println("    [Drain] 请求#" + reqId + " 完成, 剩余 in-flight: " + remaining);
				}, "req-" + reqId).start();
			}
		}

		int getInflightCount() { return inflightCount.get(); }

		@Override
		public void start() {
			running = true;
			System.out.println("  [HttpServer] start() → 监听 0.0.0.0:8080");
		}

		@Override
		public void stop(Runnable callback) {
			draining = true;
			System.out.println("    [Drain] ■ 进入排空模式: 拒绝新请求, 等待 in-flight 归零...");

			// 异步等待所有请求完成
			new Thread(() -> {
				while (inflightCount.get() > 0) {
					sleep(50); // 轮询间隔
				}
				running = false;
				draining = false;
				System.out.println("    [Drain] ✓ 排空完成: in-flight = 0, 调用 callback.run()");
				callback.run(); // 关键！必须调用，否则 CountDownLatch 超时
			}, "drain-wait").start();
		}

		@Override
		public void stop() { running = false; }
		@Override
		public boolean isRunning() { return running; }
		@Override
		public int getPhase() { return 200; } // 排空 phase: 注册摘除之后, 连接池关闭之前

		static void sleep(long ms) {
			try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
	}

	// ═══════════════════════════════════════════════════════════
	//  实验3: ShutdownHook vs 显式 close
	//  两种触发路径:
	//    路径A: 用户代码显式调用 ctx.close()
	//    路径B: JVM 退出时 ShutdownHook 线程调用 doClose()
	//  关键: close() 内部会 removeShutdownHook，避免重复执行
	//  源码: AbstractApplicationContext#registerShutdownHook (第1285行)
	//        AbstractApplicationContext#close (第1320行) → removeShutdownHook
	// ═══════════════════════════════════════════════════════════
	static void exp3_shutdownHookVsExplicitClose() {
		System.out.println("【实验3】ShutdownHook vs 显式 close → 两条触发路径");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("hookDemo", SimpleLifecycle.class, () -> new SimpleLifecycle("HookDemo"));
		ctx.refresh();

		// 注册 ShutdownHook
		ctx.registerShutdownHook();
		System.out.println("  >> registerShutdownHook() → Runtime.addShutdownHook(SpringContextShutdownHook)");
		System.out.println("  >> 此时有两条关闭路径: Ctrl+C/kill → ShutdownHook, 或显式 close()\n");

		// 显式 close → 内部自动 removeShutdownHook
		ctx.close();
		System.out.println("  >> close() 内部: Runtime.removeShutdownHook(shutdownHook)");
		System.out.println("  >> ShutdownHook 已被摘除, JVM 退出时不会重复执行 doClose()");
		System.out.println();
	}

	static class SimpleLifecycle implements SmartLifecycle {
		private final String name;
		private volatile boolean running = false;

		SimpleLifecycle(String name) { this.name = name; }

		@Override
		public void start() {
			running = true;
			System.out.println("    [" + name + "] start()");
		}
		@Override
		public void stop(Runnable callback) {
			running = false;
			System.out.println("    [" + name + "] stop(Runnable) → 关停");
			callback.run();
		}
		@Override
		public void stop() { running = false; }
		@Override
		public boolean isRunning() { return running; }
		@Override
		public int getPhase() { return 0; }
	}

	// ═══════════════════════════════════════════════════════════
	//  实验4: 依赖感知销毁顺序
	//  要点: B @DependsOn A → destroyBeans 时先销毁 B 再销毁 A
	//  源码: DefaultSingletonBeanRegistry#destroyBean (第568行)
	//        → dependentBeanMap.remove(beanName) → 递归 destroySingleton(dependentBean)
	//  这是 Bean 级别的销毁顺序, 不同于 SmartLifecycle 的 phase 级别顺序
	// ═══════════════════════════════════════════════════════════
	static void exp4_dependencyAwareDestroy() {
		System.out.println("【实验4】依赖感知销毁 → B @DependsOn A, 先销毁 B 再销毁 A");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(DependencyConfig.class);

		System.out.println("  >> close() — 观察依赖感知的销毁顺序:\n");
		ctx.close();
		System.out.println();
	}

	@Configuration
	static class DependencyConfig {
		@Bean(destroyMethod = "shutdown")
		public ResourceA resourceA() { return new ResourceA(); }

		/** serviceB 依赖 resourceA → 销毁时 serviceB 先于 resourceA */
		@Bean(destroyMethod = "shutdown")
		public ServiceB serviceB(ResourceA resourceA) { return new ServiceB(resourceA); }

		/** serviceC 依赖 serviceB → 三层依赖链 */
		@Bean(destroyMethod = "shutdown")
		public ServiceC serviceC(ServiceB serviceB) { return new ServiceC(serviceB); }
	}

	static class ResourceA {
		ResourceA() { System.out.println("    [ResourceA] 构造 → 模拟数据库连接池"); }
		public void shutdown() { System.out.println("    [ResourceA] shutdown() → ③ 连接池关闭 (最后销毁)"); }
	}
	static class ServiceB {
		ServiceB(ResourceA a) { System.out.println("    [ServiceB]  构造 → 依赖 ResourceA"); }
		public void shutdown() { System.out.println("    [ServiceB]  shutdown() → ② Service 关闭 (中间销毁)"); }
	}
	static class ServiceC {
		ServiceC(ServiceB b) { System.out.println("    [ServiceC]  构造 → 依赖 ServiceB → 间接依赖 ResourceA"); }
		public void shutdown() { System.out.println("    [ServiceC]  shutdown() → ① Controller 关闭 (最先销毁)"); }
	}

	// ═══════════════════════════════════════════════════════════
	//  实验5: 错误容错 — 一个 Bean 的 destroy 抛异常, 其余照常销毁
	//  源码: DefaultSingletonBeanRegistry#destroyBean (第587行)
	//        catch (Throwable ex) { logger.warn(...) }  → 吃掉异常, 继续下一个
	//  设计哲学: 关停是"最后的清理", 不能因为一个组件出错就放弃其他清理
	// ═══════════════════════════════════════════════════════════
	static void exp5_errorResilience() {
		System.out.println("【实验5】错误容错 → 一个 Bean 炸了, 其余照常销毁");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		// 注册顺序: good1 → bomb → good2  (销毁倒序: good2 → bomb → good1)
		ctx.registerBean("good1", GoodBean.class, () -> new GoodBean("GoodBean-1"));
		ctx.registerBean("bomb", BombBean.class);
		ctx.registerBean("good2", GoodBean.class, () -> new GoodBean("GoodBean-2"));
		ctx.refresh();

		System.out.println("  >> close() — bomb 会在 destroy 时抛异常:\n");
		ctx.close();
		System.out.println("  >> 结论: BombBean 异常被 warn 日志吞掉, GoodBean-1 照常销毁");
		System.out.println();
	}

	static class GoodBean implements DisposableBean {
		private final String name;
		GoodBean(String name) { this.name = name; }
		@Override
		public void destroy() {
			System.out.println("    [" + name + "] destroy() → 正常销毁");
		}
	}

	static class BombBean implements DisposableBean {
		@Override
		public void destroy() {
			System.out.println("    [BombBean] destroy() → 即将抛出 RuntimeException!");
			throw new RuntimeException("模拟销毁失败: 连接池强制关闭超时");
		}
	}

	// ═══════════════════════════════════════════════════════════
	//  实验6: 多 phase Drain 实战 — C 端下线完整四阶段
	//  phase=400 注册中心摘除 (最先关停)
	//  phase=300 流量排空 (Drain in-flight)
	//  phase=200 MQ 消费者停止
	//  phase=100 连接池关闭 (最后关停)
	//
	//  关键差异 vs 实验5(W24):
	//  这里额外演示 phase 间的时序依赖 + 每个 phase 内的 CountDownLatch 等待
	// ═══════════════════════════════════════════════════════════
	static void exp6_multiPhaseDrain() throws Exception {
		System.out.println("【实验6】多 phase Drain 实战 → C 端下线完整四阶段");
		System.out.println("─────────────────────────────────────────────────────────");
		System.out.println("  关停顺序: 注册摘除(400) → 流量排空(300) → MQ停消费(200) → 连接池关闭(100)\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("lifecycleProcessor", DefaultLifecycleProcessor.class, () -> {
			DefaultLifecycleProcessor p = new DefaultLifecycleProcessor();
			p.setTimeoutPerShutdownPhase(3000); // 每个 phase 3秒超时
			return p;
		});

		// 故意乱序注册, 验证 phase 排序
		ctx.registerBean("connPool", DrainPhaseComponent.class,
				() -> new DrainPhaseComponent("连接池(Hikari)", 100, 100));
		ctx.registerBean("registry", DrainPhaseComponent.class,
				() -> new DrainPhaseComponent("注册中心(Nacos)", 400, 80));
		ctx.registerBean("mqConsumer", DrainPhaseComponent.class,
				() -> new DrainPhaseComponent("MQ消费者(RocketMQ)", 200, 150));
		ctx.registerBean("trafficDrain", DrainPhaseComponent.class,
				() -> new DrainPhaseComponent("流量排空(Servlet)", 300, 200));

		// 同时注册一个 ContextClosedEvent 监听器和一个 @PreDestroy Bean
		ctx.registerBean("eventWatcher", CloseEventWatcher.class);
		ctx.registerBean("destroyWatcher", DestroyWatcher.class);

		ctx.refresh();

		System.out.println("\n  >> 开始 close() — 完整关停时序:\n");
		ctx.close();
		Thread.sleep(300);
		System.out.println();
	}

	/** Drain 阶段组件 — 异步关停, 带耗时模拟 */
	static class DrainPhaseComponent implements SmartLifecycle {
		private final String name;
		private final int phase;
		private final long shutdownMs;
		private volatile boolean running = false;

		DrainPhaseComponent(String name, int phase, long shutdownMs) {
			this.name = name;
			this.phase = phase;
			this.shutdownMs = shutdownMs;
		}

		@Override
		public void start() {
			running = true;
			System.out.println("    ▶ [phase=" + phase + "] " + name + " 启动");
		}

		@Override
		public void stop(Runnable callback) {
			System.out.println("    ■ [phase=" + phase + "] " + name + " 开始关停 (需 " + shutdownMs + "ms)...");
			new Thread(() -> {
				try { Thread.sleep(shutdownMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
				running = false;
				System.out.println("    ✓ [phase=" + phase + "] " + name + " 关停完成 → callback.run()");
				callback.run();
			}, name + "-shutdown").start();
		}

		@Override
		public void stop() { running = false; }
		@Override
		public boolean isRunning() { return running; }
		@Override
		public int getPhase() { return phase; }
	}

	static class CloseEventWatcher implements ApplicationListener<ContextClosedEvent> {
		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
			System.out.println("  ╔═ ContextClosedEvent 发布 (doClose 第一步, 在所有 Lifecycle.stop 之前)");
		}
	}

	static class DestroyWatcher implements DisposableBean {
		@Override
		public void destroy() {
			System.out.println("  ╚═ DisposableBean.destroy() (doClose 第三步, 在所有 Lifecycle.stop 之后)");
		}
	}

	// ═══════════════════════════════════════════════════════════
	//  实验7: close 幂等性 — CAS 保护, 多次 close 只执行一次
	//  源码: doClose 入口:
	//        if (this.active.get() && this.closed.compareAndSet(false, true))
	//        → 只有第一次 CAS 成功的线程能进入 doClose 主体
	//  场景: 显式 close + ShutdownHook 并发触发
	// ═══════════════════════════════════════════════════════════
	static void exp7_closeIdempotent() throws Exception {
		System.out.println("【实验7】close 幂等性 → CAS 保护, 多线程 close 只执行一次 doClose");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("counter", CloseCounter.class);
		ctx.refresh();

		// 5 个线程同时 close
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(5);
		for (int i = 0; i < 5; i++) {
			final int id = i;
			new Thread(() -> {
				try { start.await(); } catch (InterruptedException e) { return; }
				System.out.println("    [Thread-" + id + "] 调用 ctx.close()");
				ctx.close();
				done.countDown();
			}, "closer-" + i).start();
		}

		start.countDown(); // 同时释放 5 个线程
		done.await(3, TimeUnit.SECONDS);

		CloseCounter counter = ctx.getBean(CloseCounter.class);
		System.out.println("\n  >> destroy() 实际执行次数: " + counter.getDestroyCount() + " (预期=1, CAS 保证幂等)");
		System.out.println();
	}

	static class CloseCounter implements DisposableBean {
		private final AtomicInteger destroyCount = new AtomicInteger(0);
		@Override
		public void destroy() {
			int count = destroyCount.incrementAndGet();
			System.out.println("    [CloseCounter] destroy() 执行, 累计次数=" + count);
		}
		int getDestroyCount() { return destroyCount.get(); }
	}
}
