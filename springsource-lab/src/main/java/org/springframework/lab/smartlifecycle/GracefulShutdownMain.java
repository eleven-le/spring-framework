package org.springframework.lab.smartlifecycle;

import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultLifecycleProcessor;

/**
 * ===================================================================
 *  W24 · C 端优雅下线实战 Demo（Spring 容器版）
 * ===================================================================
 *
 * 模拟真实 C 端服务的优雅下线流程:
 *   phase=100 → 注册中心摘除（Nacos/Eureka deregister）
 *   phase=200 → 流量排空（等待 in-flight 请求完成）
 *   phase=300 → MQ 消费者停止（RocketMQ/Kafka consumer shutdown）
 *   phase=400 → 连接池关闭（HikariCP/Lettuce/Jedis close）
 *
 * 启动时反过来:
 *   phase=100 → 连接池先就绪
 *   phase=200 → MQ 消费者启动
 *   phase=300 → 流量排空器初始化（无实际动作）
 *   phase=400 → 注册中心注册（对外开放流量）
 *
 * 注意: 这里用小 phase 先启动、大 phase 先关停来模拟
 * 实际场景中 phase 值可以反转，关键是"启动时基础设施先就绪，关停时上层先退出"
 */
@Configuration
public class GracefulShutdownMain {

	// ─── phase 常量 ── 统一管理，严禁裸写数字 ───
	public static final int PHASE_CONN_POOL = 100;
	public static final int PHASE_MQ_CONSUMER = 200;
	public static final int PHASE_TRAFFIC_DRAIN = 300;
	public static final int PHASE_REGISTRY = 400;

	public static void main(String[] args) throws Exception {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println(" W24 · C 端优雅下线实战 Demo");
		System.out.println("═══════════════════════════════════════════════════════════\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(GracefulShutdownMain.class);

		System.out.println("\n────── 服务正常运行中... ──────\n");
		Thread.sleep(500);

		System.out.println("────── 开始优雅关停 (close) ──────");
		ctx.close();

		// 等异步关停线程跑完
		Thread.sleep(1000);
		System.out.println("\n────── 全部关停完毕 ──────");
	}

	// ─── 自定义 LifecycleProcessor（缩短超时用于演示） ───
	@Bean(name = "lifecycleProcessor")
	public DefaultLifecycleProcessor lifecycleProcessor() {
		DefaultLifecycleProcessor processor = new DefaultLifecycleProcessor();
		processor.setTimeoutPerShutdownPhase(5000);  // 5秒超时
		return processor;
	}

	// ─── 连接池 (phase=100): 最先启动，最后关停 ───
	@Bean
	public SmartLifecycle connectionPoolLifecycle() {
		return new AbstractNamedLifecycle("连接池(HikariCP)", PHASE_CONN_POOL) {
			@Override
			protected void doStart() {
				System.out.println("      初始化连接池: minIdle=10, maxPool=50");
			}

			@Override
			protected void doStop(Runnable callback) {
				System.out.println("      关闭连接池: 等待活跃连接归还...");
				// 模拟异步关闭
				new Thread(() -> {
					sleep(200);
					System.out.println("      连接池已关闭: 所有连接已归还");
					callback.run();
				}, "hikari-shutdown").start();
			}
		};
	}

	// ─── MQ 消费者 (phase=200): 连接池之后启动，之前关停 ───
	@Bean
	public SmartLifecycle mqConsumerLifecycle() {
		return new AbstractNamedLifecycle("MQ消费者(RocketMQ)", PHASE_MQ_CONSUMER) {
			@Override
			protected void doStart() {
				System.out.println("      订阅 Topic: ORDER_CREATED, PAYMENT_SUCCESS");
			}

			@Override
			protected void doStop(Runnable callback) {
				System.out.println("      停止消费: 等待当前消息处理完成...");
				new Thread(() -> {
					sleep(300);
					System.out.println("      MQ消费者已停止: 最后一批消息已 ACK");
					callback.run();
				}, "mq-shutdown").start();
			}
		};
	}

	// ─── 流量排空 (phase=300): MQ 之后关停 ───
	@Bean
	public SmartLifecycle trafficDrainLifecycle() {
		return new AbstractNamedLifecycle("流量排空(GracefulDrain)", PHASE_TRAFFIC_DRAIN) {
			@Override
			protected void doStart() {
				System.out.println("      流量排空器就绪");
			}

			@Override
			protected void doStop(Runnable callback) {
				System.out.println("      拒绝新请求 + 等待 in-flight 请求完成...");
				new Thread(() -> {
					sleep(200);
					System.out.println("      流量排空完成: 0 个 in-flight 请求");
					callback.run();
				}, "drain-shutdown").start();
			}
		};
	}

	// ─── 注册中心 (phase=400): 最后启动，最先关停 ───
	@Bean
	public SmartLifecycle registryLifecycle() {
		return new AbstractNamedLifecycle("注册中心(Nacos)", PHASE_REGISTRY) {
			@Override
			protected void doStart() {
				System.out.println("      注册实例: 192.168.1.100:8080, weight=100");
			}

			@Override
			protected void doStop(Runnable callback) {
				System.out.println("      摘除实例: 从 Nacos 注销，等待客户端刷新...");
				new Thread(() -> {
					sleep(100);
					System.out.println("      注册中心摘除完成");
					callback.run();
				}, "nacos-shutdown").start();
			}
		};
	}

	// ─── 抽象基类 ───
	static abstract class AbstractNamedLifecycle implements SmartLifecycle {
		private final String name;
		private final int phase;
		private volatile boolean running = false;

		AbstractNamedLifecycle(String name, int phase) {
			this.name = name;
			this.phase = phase;
		}

		protected abstract void doStart();
		protected abstract void doStop(Runnable callback);

		@Override
		public void start() {
			running = true;
			System.out.println("  ▶ [phase=" + phase + "] " + name + " 启动");
			doStart();
		}

		@Override
		public void stop(Runnable callback) {
			running = false;
			System.out.println("  ■ [phase=" + phase + "] " + name + " 关停");
			doStop(callback);
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

		static void sleep(long ms) {
			try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
	}
}
