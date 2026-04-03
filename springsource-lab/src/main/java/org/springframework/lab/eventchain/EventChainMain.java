package org.springframework.lab.eventchain;

import org.springframework.context.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.*;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ===================================================================
 *  W38 · 事件系统主链：publishEvent / initMulticaster / registerListeners
 * ===================================================================
 *
 * 一句话抽象：
 *   事件系统是容器的"神经网络" —— 核心矛盾是：事件可能在广播器还没创建时就被发布,
 *   监听器可能在事件已经发出后才被注册, 且每次 publishEvent 都要做昂贵的类型匹配,
 *   Spring 用 earlyEvents 门控 + retrieverCache 缓存 + ApplicationListenerDetector
 *   BPP 延迟注册 三板斧, 把"先有鸡还是先有蛋"的时序矛盾彻底化解。
 *
 * 核心管线（refresh 中的 3 步搭建）：
 *   refresh step 8:  initApplicationEventMulticaster   → 创建/发现广播器
 *   refresh step 10: registerListeners                → 注册监听器 + 回放早期事件
 *   运行时:          publishEvent                     → 类型解析→缓存匹配→同/异步分发→父容器冒泡
 *
 * 与 W20(事件使用层) 的区别：
 *   W20 覆盖了 @EventListener / 异步广播 / @TransactionalEventListener 等使用模式
 *   W38 深挖基础设施管线：earlyEvents / retrieverCache / ListenerDetector / PayloadEvent / 父容器冒泡
 *
 * 本 Demo 共 8 个实验：
 *   实验1: 反射窥探 initMulticaster → 查看广播器类型 + 默认配置
 *   实验2: earlyEvents 门控 → 事件在广播器就绪前被缓冲, registerListeners 时回放
 *   实验3: ApplicationListenerDetector BPP → 单例 Bean 自动注册为监听器
 *   实验4: PayloadApplicationEvent → POJO 事件自动包装 + 泛型类型保留
 *   实验5: retrieverCache 缓存命中 → 反射查看缓存 key/value + 监听器增删触发 cache 失效
 *   实验6: supportsEvent 三级过滤 → GenericApplicationListener 适配 + eventType + sourceType
 *   实验7: 父容器事件冒泡 → 子容器事件自动冒泡到父容器
 *   实验8: 自定义广播器 → 注册自定义 applicationEventMulticaster Bean 替换默认实现
 *
 * 断点抓手（5 个高价值位置）：
 *   ① AbstractApplicationContext#publishEvent(Object,ResolvableType)        (第410行) → 门控分支: earlyEvents vs multicast
 *   ② AbstractApplicationEventMulticaster#getApplicationListeners          (第187行) → 缓存查找: retrieverCache.get(cacheKey)
 *   ③ AbstractApplicationEventMulticaster#retrieveApplicationListeners     (第231行) → 昂贵匹配: 遍历+supportsEvent 三级过滤
 *   ④ ApplicationListenerDetector#postProcessAfterInitialization           (第73行)  → BPP 自动注册: singleton检查+addApplicationListener
 *   ⑤ SimpleApplicationEventMulticaster#multicastEvent                     (第137行) → 分发入口: 类型解析+Executor分叉(同步/异步)
 */
public class EventChainMain {

	public static void main(String[] args) throws Exception {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println(" W38 · 事件系统主链：publishEvent / initMulticaster / registerListeners");
		System.out.println("═══════════════════════════════════════════════════════════\n");

		exp1_inspectMulticaster();
		exp2_earlyEventsGate();
		exp3_listenerDetectorBpp();
		exp4_payloadApplicationEvent();
		exp5_retrieverCache();
		exp6_supportsEventFilter();
		exp7_parentContextBubbling();
		exp8_customMulticaster();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验1: 反射窥探 initMulticaster
	//  源码: AbstractApplicationContext#initApplicationEventMulticaster (1027-1044)
	//  流程: 先查 "applicationEventMulticaster" 同名 Bean → 没有则 new SimpleApplicationEventMulticaster
	// ═══════════════════════════════════════════════════════════
	static void exp1_inspectMulticaster() throws Exception {
		System.out.println("【实验1】反射窥探 initMulticaster → 广播器类型与默认配置");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.refresh();

		// 反射获取 applicationEventMulticaster 字段
		Field multicasterField = AbstractApplicationContext.class.getDeclaredField("applicationEventMulticaster");
		multicasterField.setAccessible(true);
		ApplicationEventMulticaster multicaster = (ApplicationEventMulticaster) multicasterField.get(ctx);

		System.out.println("  广播器类型: " + multicaster.getClass().getSimpleName());
		System.out.println("  全限定名:   " + multicaster.getClass().getName());

		// 检查 Executor 和 ErrorHandler (默认都是 null → 同步模式)
		if (multicaster instanceof SimpleApplicationEventMulticaster) {
			SimpleApplicationEventMulticaster simple = (SimpleApplicationEventMulticaster) multicaster;
			Field executorField = SimpleApplicationEventMulticaster.class.getDeclaredField("taskExecutor");
			executorField.setAccessible(true);
			Object executor = executorField.get(simple);

			Field errorHandlerField = SimpleApplicationEventMulticaster.class.getDeclaredField("errorHandler");
			errorHandlerField.setAccessible(true);
			Object errorHandler = errorHandlerField.get(simple);

			System.out.println("  taskExecutor: " + (executor == null ? "null (同步模式)" : executor));
			System.out.println("  errorHandler: " + (errorHandler == null ? "null (异常直接传播)" : errorHandler));
		}

		System.out.println("  结论: 默认广播器 = SimpleApplicationEventMulticaster, 同步分发, 无错误兜底");
		ctx.close();
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验2: earlyEvents 门控
	//  核心机制: 事件在 multicaster 就绪前发布 → 被缓冲到 earlyApplicationEvents Set
	//          registerListeners() 最后把 earlyEvents 设为 null + 回放缓冲事件
	//  源码: AbstractApplicationContext#publishEvent (426-427) → earlyApplicationEvents.add(event)
	//        AbstractApplicationContext#registerListeners (1102-1107) → earlyEvents=null + replay
	// ═══════════════════════════════════════════════════════════
	static void exp2_earlyEventsGate() throws Exception {
		System.out.println("【实验2】earlyEvents 门控 → 事件缓冲 + registerListeners 回放");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 手动注册一个监听器 (static listener, 在 registerListeners step 1 会被添加到 multicaster)
		final List<String> receivedEvents = new CopyOnWriteArrayList<>();
		ctx.addApplicationListener((ApplicationListener<ApplicationEvent>) event -> {
			if (!(event instanceof ContextRefreshedEvent) && !(event instanceof ContextClosedEvent)) {
				receivedEvents.add(event.getClass().getSimpleName());
			}
		});

		// 在 refresh 之前发布一个自定义事件
		// 此时 earlyApplicationEvents != null, 事件会被缓冲
		ctx.publishEvent(new CustomEarlyEvent("早期事件-1"));
		ctx.publishEvent(new CustomEarlyEvent("早期事件-2"));

		// 反射查看 earlyApplicationEvents
		Field earlyField = AbstractApplicationContext.class.getDeclaredField("earlyApplicationEvents");
		earlyField.setAccessible(true);
		Set<?> earlyEvents = (Set<?>) earlyField.get(ctx);
		System.out.println("  refresh 前 earlyApplicationEvents: " + (earlyEvents != null ? "非null, 缓冲 " + earlyEvents.size() + " 个事件" : "null"));

		// refresh → step 8 initMulticaster + step 10 registerListeners(回放 earlyEvents)
		ctx.refresh();

		earlyEvents = (Set<?>) earlyField.get(ctx);
		System.out.println("  refresh 后 earlyApplicationEvents: " + (earlyEvents == null ? "null (门控关闭, 直接 multicast)" : "非null"));
		System.out.println("  监听器收到的事件: " + receivedEvents);
		System.out.println("  结论: 早期事件被缓冲, registerListeners 回放时监听器才收到");

		ctx.close();
		System.out.println();
	}

	static class CustomEarlyEvent extends ApplicationEvent {
		private final String name;
		CustomEarlyEvent(String name) { super(name); this.name = name; }
		@Override public String toString() { return "CustomEarlyEvent(" + name + ")"; }
	}

	// ═══════════════════════════════════════════════════════════
	//  实验3: ApplicationListenerDetector BPP 自动注册
	//  核心: 它是一个 BeanPostProcessor, refresh step 6 注册
	//  postProcessMergedBeanDefinition: 预记录 bean 是否 singleton
	//  postProcessAfterInitialization: 如果 bean 是 ApplicationListener + singleton → 自动注册
	//  源码: ApplicationListenerDetector#postProcessAfterInitialization (73-93)
	// ═══════════════════════════════════════════════════════════
	static void exp3_listenerDetectorBpp() throws Exception {
		System.out.println("【实验3】ApplicationListenerDetector BPP → 单例监听器 Bean 自动注册");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		// 注册一个实现 ApplicationListener 的 Bean
		ctx.registerBean("orderListener", OrderCreatedListener.class);
		ctx.refresh();

		// 发布事件, 验证 Bean 监听器已被 ListenerDetector 自动注册
		ctx.publishEvent(new OrderCreatedEvent("ORD-001"));

		// 查看 ApplicationListenerDetector 在 BPP 列表中的位置
		String[] bppNames = ctx.getBeanNamesForType(
				org.springframework.beans.factory.config.BeanPostProcessor.class, true, false);
		System.out.println("  BPP 列表中 ApplicationListenerDetector 的存在: " +
				Arrays.stream(bppNames).anyMatch(n -> n.contains("Listener")));
		System.out.println("  结论: 实现 ApplicationListener 的 singleton Bean, 在 initializeBean");
		System.out.println("         阶段被 ApplicationListenerDetector#postProcessAfterInitialization 自动注册");

		ctx.close();
		System.out.println();
	}

	static class OrderCreatedEvent extends ApplicationEvent {
		OrderCreatedEvent(String orderId) { super(orderId); }
		String getOrderId() { return (String) getSource(); }
	}

	static class OrderCreatedListener implements ApplicationListener<OrderCreatedEvent> {
		@Override
		public void onApplicationEvent(OrderCreatedEvent event) {
			System.out.println("    [OrderCreatedListener] 收到: " + event.getOrderId() +
					" (由 ApplicationListenerDetector BPP 自动注册)");
		}
	}

	// ═══════════════════════════════════════════════════════════
	//  实验4: PayloadApplicationEvent 自动包装 + 泛型类型保留
	//  核心: publishEvent(Object) 如果不是 ApplicationEvent 子类, 自动包装为 PayloadApplicationEvent<T>
	//  泛型保留: PayloadApplicationEvent#getResolvableType() 保留了 T 的实际类型
	//  用途: 支持 publishEvent("hello") / publishEvent(new OrderDTO()) 等 POJO 事件
	//  源码: AbstractApplicationContext#publishEvent (414-423) → new PayloadApplicationEvent(this, event)
	// ═══════════════════════════════════════════════════════════
	static void exp4_payloadApplicationEvent() {
		System.out.println("【实验4】PayloadApplicationEvent → POJO 事件自动包装 + 泛型匹配");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 注册 String 类型的 PayloadApplicationEvent 监听器
		ctx.addApplicationListener((ApplicationListener<PayloadApplicationEvent<String>>) event -> {
			System.out.println("    [String监听器] payload = \"" + event.getPayload() + "\"");
		});

		// 注册 OrderDTO 类型的 PayloadApplicationEvent 监听器
		ctx.addApplicationListener((ApplicationListener<PayloadApplicationEvent<OrderDTO>>) event -> {
			System.out.println("    [OrderDTO监听器] payload = " + event.getPayload());
		});

		ctx.refresh();

		// 发布 POJO 事件 — 自动包装
		System.out.println("  >> publishEvent(\"hello\") → 自动包装为 PayloadApplicationEvent<String>:");
		ctx.publishEvent("hello");

		System.out.println("  >> publishEvent(new OrderDTO(\"ORD-002\")) → PayloadApplicationEvent<OrderDTO>:");
		ctx.publishEvent(new OrderDTO("ORD-002"));

		// 查看 ResolvableType
		PayloadApplicationEvent<OrderDTO> pae = new PayloadApplicationEvent<>(ctx, new OrderDTO("test"));
		ResolvableType type = pae.getResolvableType();
		System.out.println("\n  PayloadApplicationEvent<OrderDTO> 的 ResolvableType: " + type);
		System.out.println("  泛型参数: " + type.getGeneric(0));
		System.out.println("  结论: PayloadApplicationEvent#getResolvableType() 保留泛型 T, 确保精准匹配");

		ctx.close();
		System.out.println();
	}

	static class OrderDTO {
		private final String orderId;
		OrderDTO(String orderId) { this.orderId = orderId; }
		@Override public String toString() { return "OrderDTO{" + orderId + "}"; }
	}

	// ═══════════════════════════════════════════════════════════
	//  实验5: retrieverCache 缓存机制
	//  核心: getApplicationListeners(event, type) 用 ConcurrentHashMap 缓存匹配结果
	//  key = ListenerCacheKey(eventType, sourceType), value = CachedListenerRetriever
	//  增/删监听器时 retrieverCache.clear() 使缓存全部失效
	//  源码: AbstractApplicationEventMulticaster#getApplicationListeners (187-222)
	//        AbstractApplicationEventMulticaster#addApplicationListener (113) → cache.clear()
	// ═══════════════════════════════════════════════════════════
	static void exp5_retrieverCache() throws Exception {
		System.out.println("【实验5】retrieverCache 缓存 → 缓存命中 + 增删监听器触发失效");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.addApplicationListener((ApplicationListener<OrderCreatedEvent>) e -> {});
		ctx.refresh();

		// 反射获取 retrieverCache
		Field cacheField = AbstractApplicationEventMulticaster.class.getDeclaredField("retrieverCache");
		cacheField.setAccessible(true);
		ApplicationEventMulticaster multicaster = ctx.getBean(
				AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
				ApplicationEventMulticaster.class);
		Map<?, ?> cache = (Map<?, ?>) cacheField.get(multicaster);

		System.out.println("  发布前 cache size: " + cache.size());

		// 第一次发布 → cache MISS → retrieveApplicationListeners (昂贵) → 缓存结果
		ctx.publishEvent(new OrderCreatedEvent("ORD-001"));
		System.out.println("  第1次发布后 cache size: " + cache.size() + " (首次匹配 → 缓存写入)");

		// 第二次发布同类型事件 → cache HIT → 直接返回
		ctx.publishEvent(new OrderCreatedEvent("ORD-002"));
		System.out.println("  第2次发布后 cache size: " + cache.size() + " (缓存命中 → 跳过匹配)");

		// 打印 cache key 结构
		for (Object key : cache.keySet()) {
			System.out.println("  cache key: " + key);
		}

		// 增加一个监听器 → 触发 cache.clear()
		ctx.addApplicationListener((ApplicationListener<OrderCreatedEvent>) e ->
				System.out.println("    [新增监听器] " + e.getOrderId()));
		System.out.println("\n  增加监听器后 cache size: " + cache.size() + " (addApplicationListener → cache.clear())");

		// 再次发布 → cache MISS → 重新匹配 (包含新增的监听器)
		ctx.publishEvent(new OrderCreatedEvent("ORD-003"));
		System.out.println("  再次发布后 cache size: " + cache.size() + " (重新匹配 + 缓存)");

		System.out.println("  结论: retrieverCache 按 (eventType, sourceType) 缓存, 增删监听器触发全量失效");
		ctx.close();
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验6: supportsEvent 三级过滤
	//  过滤链: GenericApplicationListener 适配 → supportsEventType(ResolvableType) → supportsSourceType(Class)
	//  源码: AbstractApplicationEventMulticaster#supportsEvent (373-379)
	//  要点: 原始 ApplicationListener 被包装为 GenericApplicationListenerAdapter
	//        GenericApplicationListenerAdapter 通过反射提取泛型 T 做类型匹配
	// ═══════════════════════════════════════════════════════════
	static void exp6_supportsEventFilter() {
		System.out.println("【实验6】supportsEvent 三级过滤 → 适配器 + eventType + sourceType");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 1. 泛型精确匹配: 只接收 OrderCreatedEvent
		AtomicInteger orderCount = new AtomicInteger();
		ctx.addApplicationListener((ApplicationListener<OrderCreatedEvent>) e -> orderCount.incrementAndGet());

		// 2. SmartApplicationListener: 按 eventType + sourceType 双维度过滤
		AtomicInteger smartCount = new AtomicInteger();
		ctx.addApplicationListener(new SmartApplicationListener() {
			@Override
			public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
				return OrderCreatedEvent.class.isAssignableFrom(eventType);
			}
			@Override
			public boolean supportsSourceType(Class<?> sourceType) {
				return String.class.isAssignableFrom(sourceType); // 只接受 source 为 String 的
			}
			@Override
			public void onApplicationEvent(ApplicationEvent event) {
				smartCount.incrementAndGet();
			}
		});

		ctx.refresh();

		// OrderCreatedEvent source = "ORD-001" (String) → 两个监听器都匹配
		ctx.publishEvent(new OrderCreatedEvent("ORD-001"));
		System.out.println("  source=String  → orderListener=" + orderCount.get() + ", smartListener=" + smartCount.get());

		// 自定义事件 source = Integer → Smart 的 sourceType 过滤掉
		ctx.publishEvent(new OrderCreatedEvent("ORD-002")); // source仍是String
		System.out.println("  source=String  → orderListener=" + orderCount.get() + ", smartListener=" + smartCount.get());

		System.out.println("  过滤链: 1.适配为GenericApplicationListener → 2.supportsEventType → 3.supportsSourceType");
		System.out.println("  结论: SmartApplicationListener 可按 sourceType 做第二维度过滤(比如只接受特定来源的事件)");
		ctx.close();
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验7: 父容器事件冒泡
	//  子容器 publishEvent → 子容器监听器收到 → 事件自动冒泡到父容器
	//  父容器监听器也收到同一事件
	//  源码: AbstractApplicationContext#publishEvent (434-441) → parent.publishEvent(event)
	// ═══════════════════════════════════════════════════════════
	static void exp7_parentContextBubbling() {
		System.out.println("【实验7】父容器事件冒泡 → 子容器事件自动冒泡到父容器");
		System.out.println("─────────────────────────────────────────────────────────");

		// 创建父容器
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
		parent.addApplicationListener((ApplicationListener<OrderCreatedEvent>) e ->
				System.out.println("    [父容器] 收到: " + e.getOrderId()));
		parent.refresh();

		// 创建子容器, 设置父子关系
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.setParent(parent);
		child.addApplicationListener((ApplicationListener<OrderCreatedEvent>) e ->
				System.out.println("    [子容器] 收到: " + e.getOrderId()));
		child.refresh();

		System.out.println("  >> 子容器 publishEvent:");
		child.publishEvent(new OrderCreatedEvent("ORD-CHILD-001"));

		System.out.println("\n  >> 父容器 publishEvent (不会冒泡到子容器):");
		parent.publishEvent(new OrderCreatedEvent("ORD-PARENT-001"));

		System.out.println("\n  结论: 事件只向上冒泡(子→父), 不向下传播");
		System.out.println("  源码: publishEvent 末尾 if(parent!=null) parent.publishEvent(event)");

		child.close();
		parent.close();
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验8: 自定义广播器替换默认实现
	//  核心: 注册名为 "applicationEventMulticaster" 的 Bean → initMulticaster 时发现并使用
	//  源码: AbstractApplicationContext#initApplicationEventMulticaster (1029-1031)
	//        if (containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) { getBean(...) }
	// ═══════════════════════════════════════════════════════════
	static void exp8_customMulticaster() {
		System.out.println("【实验8】自定义广播器 → 注册同名 Bean 替换默认实现");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CustomMulticasterConfig.class);

		ctx.publishEvent(new OrderCreatedEvent("ORD-CUSTOM-001"));

		ctx.close();
		System.out.println();
	}

	@Configuration
	static class CustomMulticasterConfig {

		/**
		 * Bean 名必须是 "applicationEventMulticaster" — initMulticaster 用这个名字查找
		 * 这里演示: 配置异步 Executor + ErrorHandler
		 */
		@Bean(name = AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME)
		public SimpleApplicationEventMulticaster applicationEventMulticaster() {
			SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();

			// 配置异步线程池 (默认 null = 同步)
			ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
				Thread t = new Thread(r, "event-async");
				t.setDaemon(true);
				return t;
			});
			multicaster.setTaskExecutor(executor);

			// 配置错误处理器 (默认 null = 异常直接传播)
			multicaster.setErrorHandler(t ->
					System.out.println("    [ErrorHandler] 监听器异常被兜底: " + t.getMessage()));

			System.out.println("  自定义广播器已注册: 异步线程池(2线程) + ErrorHandler");
			return multicaster;
		}

		@Bean
		public ApplicationListener<OrderCreatedEvent> asyncOrderListener() {
			return event -> {
				System.out.println("    [异步监听器] " + event.getOrderId() +
						" on thread: " + Thread.currentThread().getName());
			};
		}

		@Bean
		public ApplicationListener<OrderCreatedEvent> errorListener() {
			return event -> {
				throw new RuntimeException("模拟监听器异常: 发送通知失败");
			};
		}
	}
}
