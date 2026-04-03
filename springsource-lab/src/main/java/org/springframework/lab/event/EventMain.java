package org.springframework.lab.event;

import java.util.concurrent.Executor;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.ErrorHandler;

/**
 * W20 — 事件驱动模型：ApplicationEventMulticaster 与监听器体系
 *
 * <h2>一句话抽象</h2>
 * <b>ApplicationEventMulticaster</b> 解决"一个事件如何派发给 N 个监听器"——核心矛盾是
 * <i>同步默认保证顺序与异常传播 vs 异步解耦不阻塞主流程</i>，解法是 Executor 策略可插拔。
 * <b>EventListenerMethodProcessor</b> 解决"如何让普通方法变成监听器"——核心矛盾是
 * <i>接口式绑定太重 vs 注解式需要在所有单例初始化后再统一扫描翻译</i>，
 * 解法是 SmartInitializingSingleton 回调时机 + EventListenerFactory 适配器模式。
 *
 * <h2>实验列表</h2>
 * <ol>
 *   <li>实验 1: 接口式 ApplicationListener — 注册路径 + 泛型过滤</li>
 *   <li>实验 2: @EventListener 注解式 — EventListenerMethodProcessor 翻译 + SpEL condition</li>
 *   <li>实验 3: SmartApplicationListener — eventType + sourceType 双维度过滤</li>
 *   <li>实验 4: PayloadApplicationEvent — 发布任意 POJO 事件 + 自动拆包</li>
 *   <li>实验 5: 异步广播器 — setTaskExecutor + ErrorHandler</li>
 *   <li>实验 6: @TransactionalEventListener — 4 个 TransactionPhase + fallbackExecution</li>
 *   <li>实验 7: 容器生命周期事件 + earlyApplicationEvents 早期事件机制</li>
 *   <li>实验 8: 事件链 — @EventListener 返回值自动发布为新事件</li>
 * </ol>
 *
 * <h2>核心调用链 (12 步)</h2>
 * <pre>
 * ① AbstractApplicationContext#initApplicationEventMulticaster : 初始化广播器 — 有自定义用自定义，否则 new SimpleApplicationEventMulticaster
 * ② AbstractApplicationContext#registerListeners : 注册监听器到广播器 — 先 addApplicationListener(编程式)，再 addApplicationListenerBean(Bean名)，最后 multicast earlyEvents
 * ③ EventListenerMethodProcessor#afterSingletonsInstantiated : 所有单例就绪后，扫描每个 Bean 的 @EventListener 方法
 * ④ EventListenerMethodProcessor#processBean : 用 MethodIntrospector 找到 @EventListener 方法，交给 EventListenerFactory 生成 ApplicationListenerMethodAdapter
 * ⑤ ApplicationContext#publishEvent(Object) : 入口 — 非 ApplicationEvent 包装为 PayloadApplicationEvent
 * ⑥ AbstractApplicationContext#publishEvent(Object, ResolvableType) : 判断广播器是否就绪，未就绪存入 earlyApplicationEvents，就绪则 multicastEvent + 向父容器冒泡
 * ⑦ SimpleApplicationEventMulticaster#multicastEvent : 遍历匹配的监听器，有 Executor 走异步，无 Executor 走同步
 * ⑧ AbstractApplicationEventMulticaster#getApplicationListeners(event, type) : 按 eventType+sourceType 缓存键查 retrieverCache，未命中走 retrieveApplicationListeners 过滤
 * ⑨ AbstractApplicationEventMulticaster#supportsEvent : 将 listener 适配为 GenericApplicationListener，调 supportsEventType + supportsSourceType 双重过滤
 * ⑩ SimpleApplicationEventMulticaster#invokeListener : 有 ErrorHandler 包 try-catch，否则直接调
 * ⑪ ApplicationListenerMethodAdapter#processEvent : 解析参数 → SpEL condition 判断 → doInvoke 反射调用 → handleResult 处理返回值(事件链)
 * ⑫ TransactionalApplicationListenerMethodAdapter#onApplicationEvent : 有活跃事务→注册 TransactionSynchronization 延迟到事务阶段；无事务→看 fallbackExecution
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>AbstractApplicationContext#publishEvent(Object, ResolvableType) L410 — 发布总入口，观察 PayloadApplicationEvent 包装 + earlyEvents 暂存</li>
 *   <li>SimpleApplicationEventMulticaster#multicastEvent L137 — 广播核心循环，观察同步/异步分支</li>
 *   <li>AbstractApplicationEventMulticaster#getApplicationListeners(event, type) L187 — 监听器匹配与缓存，观察 retrieverCache + supportsEvent 过滤</li>
 *   <li>EventListenerMethodProcessor#processBean L165 — @EventListener 注解翻译，观察 MethodIntrospector + EventListenerFactory</li>
 *   <li>TransactionalApplicationListenerMethodAdapter#onApplicationEvent L89 — 事务事件注册 TransactionSynchronization</li>
 * </ol>
 */
public class EventMain {

	public static void main(String[] args) {
		System.out.println("╔═══════════════════════════════════════════════════════════╗");
		System.out.println("║  W20 — 事件驱动模型: ApplicationEventMulticaster 与监听器体系  ║");
		System.out.println("╚═══════════════════════════════════════════════════════════╝\n");

		// ========== 实验 1/2/3/7/8 共用上下文 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 1: 接口式 ApplicationListener — 注册 + 泛型过滤");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("断点: AbstractApplicationContext#registerListeners L1088");
		System.out.println("断点: ApplicationListenerDetector#postProcessAfterInitialization L73\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EventConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		jdbc.execute("CREATE TABLE IF NOT EXISTS refund_log(id INT AUTO_INCREMENT PRIMARY KEY, order_id VARCHAR(50), amount INT)");

		OrderEventPublisher publisher = ctx.getBean(OrderEventPublisher.class);

		// 实验 1: 接口式 + 实验 2: 注解式 + 实验 3: SmartApplicationListener
		// 发布 OrderCreatedEvent — 三种监听器同时收到
		publisher.createOrder("ORD-001", "iPhone", 9999);

		// ========== 实验 4: PayloadApplicationEvent 自动包装 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 4: PayloadApplicationEvent — POJO 事件自动包装+拆包");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("断点: AbstractApplicationContext#publishEvent L418 — event instanceof ApplicationEvent 为 false，包装为 PayloadApplicationEvent");
		System.out.println("断点: ApplicationListenerMethodAdapter#resolveArguments L246 — 拆 PayloadApplicationEvent 取 payload\n");
		publisher.payOrder("ORD-001", 9999);

		// ========== 实验 5: 异步广播器 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 5: 异步广播器 — setTaskExecutor + ErrorHandler");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("断点: SimpleApplicationEventMulticaster#multicastEvent L141 — executor != null 走异步分支\n");
		demoAsyncMulticaster();

		// ========== 实验 6: @TransactionalEventListener ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 6a: @TransactionalEventListener — 事务提交后执行");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("断点: TransactionalApplicationListenerMethodAdapter#onApplicationEvent L89\n");
		jdbc.execute("DELETE FROM refund_log");
		publisher.refundOrder("ORD-001", 5000);

		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 6b: @TransactionalEventListener — 事务回滚后执行");
		System.out.println("═══════════════════════════════════════════════════════\n");
		jdbc.execute("DELETE FROM refund_log");
		try {
			publisher.refundOrderFail("ORD-002", 3000);
		}
		catch (RuntimeException e) {
			System.out.println("  [Main] 捕获异常: " + e.getMessage());
		}

		// ========== 实验 7: 生命周期事件 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 7: 容器生命周期事件 (ContextClosedEvent)");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("(ContextRefreshedEvent 已在容器启动时触发, 见上方输出)\n");

		// ========== 实验 8: 事件链 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 8: 事件链 — @EventListener 返回值自动发布");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("断点: ApplicationListenerMethodAdapter#handleResult L265\n");
		System.out.println("(事件链已在实验 1 中触发: onOrderCreatedChain 返回 OrderPaidEvent)\n");

		// 关闭容器 — 触发 ContextClosedEvent
		ctx.close();

		System.out.println("\n[完成] W20 事件驱动模型全部实验执行完毕");
	}

	/**
	 * 实验 5: 手动构建异步广播器，展示 Executor + ErrorHandler 策略。
	 *
	 * <p>核心要点:
	 * - 默认 SimpleApplicationEventMulticaster 是同步的 (taskExecutor == null)
	 * - setTaskExecutor(asyncExecutor) → multicastEvent 中 executor.execute(() -> invokeListener)
	 * - setErrorHandler → invokeListener 中 try-catch + errorHandler.handleError
	 * - 要替换默认广播器: 注册 Bean 名为 "applicationEventMulticaster" 即可
	 */
	private static void demoAsyncMulticaster() {
		SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();

		// 设置异步执行器
		multicaster.setTaskExecutor(new SimpleAsyncTaskExecutor("async-event-"));

		// 设置错误处理器
		multicaster.setErrorHandler(t ->
				System.out.println("  [ErrorHandler] 监听器异常被兜底: " + t.getMessage()));

		// 注册一个正常监听器
		multicaster.addApplicationListener((ApplicationListener<OrderCreatedEvent>) event ->
				System.out.println("  [异步监听器1] thread=" + Thread.currentThread().getName() + " 收到: " + event));

		// 注册一个会抛异常的监听器
		multicaster.addApplicationListener((ApplicationListener<OrderCreatedEvent>) event -> {
			throw new RuntimeException("模拟监听器故障");
		});

		// 注册一个正常监听器 — 验证异步模式下一个监听器异常不阻塞其他
		multicaster.addApplicationListener((ApplicationListener<OrderCreatedEvent>) event ->
				System.out.println("  [异步监听器3] thread=" + Thread.currentThread().getName() + " 收到: " + event));

		OrderCreatedEvent event = new OrderCreatedEvent(EventMain.class, "ASYNC-001", "MacBook", 14999);
		multicaster.multicastEvent(event);

		// 等待异步线程输出
		try {
			Thread.sleep(200);
		}
		catch (InterruptedException ignored) {
		}
	}
}
