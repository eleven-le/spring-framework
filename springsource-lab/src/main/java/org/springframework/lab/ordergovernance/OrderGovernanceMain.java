//package org.springframework.lab.ordergovernance;
//
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
//import org.springframework.context.ApplicationEvent;
//import org.springframework.context.ApplicationListener;
//import org.springframework.context.annotation.AnnotationConfigApplicationContext;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.event.EventListener;
//import org.springframework.core.Ordered;
//import org.springframework.core.PriorityOrdered;
//import org.springframework.core.annotation.AnnotationAwareOrderComparator;
//import org.springframework.core.annotation.Order;
//import org.springframework.core.annotation.OrderUtils;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.Priority;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
///**
// * ===================================================================
// *  W26 · 顺序与优先级治理：PriorityOrdered/Ordered/@Order
// *        与 AnnotationAwareOrderComparator
// * ===================================================================
// *
// * 一句话抽象：
// *   Spring 的顺序治理解决的核心矛盾是：
// *   "多个同类组件并存时，谁先执行/谁先注入/谁先匹配"不能靠注册顺序碰运气，
// *   必须有一套声明式、分层的、全局一致的排序协议 ——
// *   PriorityOrdered 是"特权层"（不比数值直接赢），Ordered/@Order 是"数值层"（值小先来），
// *   AnnotationAwareOrderComparator 是统一仲裁器，让这套语义在 BPP/BFPP/AOP/MVC/DI 全场景成立。
// *
// * 本 Demo 共 7 个实验：
// *   实验1: OrderComparator vs AnnotationAwareOrderComparator → 谁能读 @Order？
// *   实验2: PriorityOrdered 分层压制 → 特权接口无视数值
// *   实验3: @Order vs Ordered 接口 → 接口值覆盖注解值（接口优先）
// *   实验4: @Priority vs @Order → @Priority 额外支持"唯一选择"语义
// *   实验5: BeanPostProcessor 三梯队注册 → PriorityOrdered → Ordered → 无序
// *   实验6: 集合注入排序 → @Autowired List<Strategy> 按 @Order 排序
// *   实验7: @EventListener 排序 → 同事件多监听器的执行顺序
// *
// * 核心调用链（10 步）：
// *   1. AnnotationAwareOrderComparator#compare          : 入口，委托 doCompare
// *   2. OrderComparator#doCompare                       : 第一关：PriorityOrdered 分层判定
// *   3. OrderComparator#getOrder(obj, sourceProvider)   : 第二关：从 sourceProvider 取 order
// *   4. OrderComparator#findOrder                       : 只识别 Ordered 接口
// *   5. AnnotationAwareOrderComparator#findOrder        : 覆写！先 super.findOrder → 再读 @Order/@Priority
// *   6. OrderUtils#getOrderFromAnnotations              : 缓存 + 注解解析
// *   7. OrderUtils#findOrder                            : @Order 优先 → @Priority 兜底
// *   8. PostProcessorRegistrationDelegate#sortPostProcessors   : BPP/BFPP 排序入口
// *   9. DefaultListableBeanFactory#adaptOrderComparator        : DI 集合排序，附加 OrderSourceProvider
// *  10. AnnotationAwareOrderComparator#getPriority             : @Priority 专属语义，用于"唯一匹配"
// *
// * 断点抓手（5 个）：
// *   ① OrderComparator#doCompare:77                   → 看 PriorityOrdered instanceof 判定分支
// *   ② AnnotationAwareOrderComparator#findOrder:63     → 看 super.findOrder vs 注解查找的优先级
// *   ③ OrderUtils#findOrder:122                        → 看 @Order 优先于 @Priority 的选择逻辑
// *   ④ PostProcessorRegistrationDelegate#sortPostProcessors:389 → 看 dependencyComparator 来源
// *   ⑤ AnnotationAwareOrderComparator#getPriority:90   → 看 @Priority 的独立语义
// */
//public class OrderGovernanceMain {
//
//	public static void main(String[] args) {
//		System.out.println("═══════════════════════════════════════════════════════════");
//		System.out.println(" W26 · 顺序与优先级治理 Demo");
//		System.out.println("═══════════════════════════════════════════════════════════\n");
//
//		exp1_comparatorDifference();
//		exp2_priorityOrderedSuppression();
//		exp3_orderedInterfaceOverridesAnnotation();
//		exp4_priorityVsOrder();
//		exp5_bppThreeTierRegistration();
//		exp6_collectionInjectionOrdering();
//		exp7_eventListenerOrdering();
//	}
//
//	// ─────────────────────────────────────────────────────────────
//	// 实验1: OrderComparator vs AnnotationAwareOrderComparator
//	// 要点: OrderComparator 只识别 Ordered 接口
//	//       AnnotationAwareOrderComparator 额外识别 @Order / @Priority 注解
//	//       框架默认使用 AnnotationAwareOrderComparator（AnnotationConfigUtils 设置）
//	// 源码: AnnotationAwareOrderComparator#findOrder:
//	//       Integer order = super.findOrder(obj);  ← 先查接口
//	//       if (order != null) return order;
//	//       return findOrderFromAnnotation(obj);   ← 再查注解
//	// ─────────────────────────────────────────────────────────────
//	static void exp1_comparatorDifference() {
//		System.out.println("【实验1】OrderComparator vs AnnotationAwareOrderComparator");
//		System.out.println("─────────────────────────────────────────────────────────");
//
//		// 对象A: 只用 @Order 注解声明顺序
//		@Order(1)
//		class AnnotationOnly {}
//
//		// 对象B: 实现 Ordered 接口
//		class InterfaceOnly implements Ordered {
//			@Override
//			public int getOrder() { return 2; }
//		}
//
//		AnnotationOnly a = new AnnotationOnly();
//		InterfaceOnly b = new InterfaceOnly();
//
//		// OrderComparator: 只认接口
//		int resultBasic = org.springframework.core.OrderComparator.INSTANCE.compare(a, b);
//		System.out.println("  OrderComparator.compare(@Order(1), Ordered(2))");
//		System.out.println("    结果 = " + resultBasic + " → " + interpretCompare(resultBasic));
//		System.out.println("    原因: OrderComparator 不识别 @Order → a 的 order 兜底为 LOWEST_PRECEDENCE");
//
//		// AnnotationAwareOrderComparator: 注解+接口都认
//		int resultAware = AnnotationAwareOrderComparator.INSTANCE.compare(a, b);
//		System.out.println("\n  AnnotationAwareOrderComparator.compare(@Order(1), Ordered(2))");
//		System.out.println("    结果 = " + resultAware + " → " + interpretCompare(resultAware));
//		System.out.println("    原因: AnnotationAwareOrderComparator 识别 @Order(1) → a 排在 b 前面");
//
//		// 验证: OrderUtils 读取注解值
//		Integer orderFromAnnotation = OrderUtils.getOrder(a.getClass());
//		Integer orderFromInterface = OrderUtils.getOrder(b.getClass());
//		System.out.println("\n  OrderUtils.getOrder:");
//		System.out.println("    @Order(1) 类 → " + orderFromAnnotation);
//		System.out.println("    Ordered 接口类 → " + orderFromInterface + " (null=OrderUtils 不读接口，只读注解)");
//		System.out.println();
//	}
//
//	// ─────────────────────────────────────────────────────────────
//	// 实验2: PriorityOrdered 分层压制
//	// 要点: PriorityOrdered 是标记接口（空接口），继承 Ordered
//	//       OrderComparator#doCompare 第一步:
//	//         boolean p1 = (o1 instanceof PriorityOrdered);
//	//         boolean p2 = (o2 instanceof PriorityOrdered);
//	//         if (p1 && !p2) return -1;  ← 直接赢，不比 order 值
//	//       语义: "你是框架基础设施，必须在用户代码之前运行"
//	// 对比W23: W23 聚焦 AOP 链排序，这里聚焦排序机制本身
//	// ─────────────────────────────────────────────────────────────
//	static void exp2_priorityOrderedSuppression() {
//		System.out.println("【实验2】PriorityOrdered 分层压制 → 特权接口无视数值");
//		System.out.println("─────────────────────────────────────────────────────────");
//
//		// PriorityOrdered 的 order 值故意设得很大
//		class HighValuePriority implements PriorityOrdered {
//			@Override
//			public int getOrder() { return 999; }
//			@Override
//			public String toString() { return "PriorityOrdered(999)"; }
//		}
//
//		// 普通 Ordered 的 order 值很小
//		class LowValueOrdered implements Ordered {
//			@Override
//			public int getOrder() { return 1; }
//			@Override
//			public String toString() { return "Ordered(1)"; }
//		}
//
//		// 另一个 PriorityOrdered，order 值较小
//		class LowValuePriority implements PriorityOrdered {
//			@Override
//			public int getOrder() { return 10; }
//			@Override
//			public String toString() { return "PriorityOrdered(10)"; }
//		}
//
//		List<Object> items = new ArrayList<>(Arrays.asList(
//				new LowValueOrdered(),     // Ordered(1)
//				new HighValuePriority(),   // PriorityOrdered(999)
//				new LowValuePriority()     // PriorityOrdered(10)
//		));
//
//		System.out.println("  排序前: " + items);
//		AnnotationAwareOrderComparator.sort(items);
//		System.out.println("  排序后: " + items);
//		System.out.println("  解读:");
//		System.out.println("    1. 两个 PriorityOrdered 先排（分层压制 Ordered）");
//		System.out.println("    2. PriorityOrdered 内部按数值排序: 10 < 999");
//		System.out.println("    3. Ordered(1) 虽然数值最小，但只能排在 PriorityOrdered 之后");
//		System.out.println();
//	}
//
//	// ─────────────────────────────────────────────────────────────
//	// 实验3: Ordered 接口值 覆盖 @Order 注解值
//	// 要点: AnnotationAwareOrderComparator#findOrder:
//	//       Integer order = super.findOrder(obj);  ← 先查接口 (Ordered)
//	//       if (order != null) return order;        ← 接口有值就直接返回！
//	//       return findOrderFromAnnotation(obj);    ← 只有接口没实现才查注解
//	//       结论: 同时有 Ordered 接口和 @Order 注解时，接口值生效
//	// 场景: 第三方类加了 @Order，你子类实现 Ordered 改值 → 接口值优先
//	// ─────────────────────────────────────────────────────────────
//	static void exp3_orderedInterfaceOverridesAnnotation() {
//		System.out.println("【实验3】Ordered 接口值覆盖 @Order 注解值");
//		System.out.println("─────────────────────────────────────────────────────────");
//
//		// 既有 @Order(1) 注解，又实现 Ordered 接口返回 100
//		@Order(1)
//		class BothDeclared implements Ordered {
//			@Override
//			public int getOrder() { return 100; }
//		}
//
//		BothDeclared obj = new BothDeclared();
//
//		// 用 AnnotationAwareOrderComparator 获取 order
//		Integer orderFromUtils = OrderUtils.getOrder(obj.getClass());
//		System.out.println("  OrderUtils.getOrder(@Order(1)的类) = " + orderFromUtils + " → 只读注解");
//
//		// findOrder 走的完整链路
//		Integer findOrderResult = AnnotationAwareOrderComparator.INSTANCE.findOrder(obj);
//		System.out.println("  AnnotationAwareOrderComparator.findOrder(同时有接口和注解的实例)");
//		System.out.println("    结果 = " + findOrderResult);
//		System.out.println("    原因: super.findOrder 先检测 Ordered 接口 → 返回 100 → 不再查 @Order(1)");
//		System.out.println("    结论: Ordered 接口值(100) 覆盖了 @Order 注解值(1)");
//
//		// 对比: 纯注解对象
//		@Order(1)
//		class AnnotationOnly {}
//		Integer pureAnnotation = AnnotationAwareOrderComparator.INSTANCE.findOrder(new AnnotationOnly());
//		System.out.println("\n  对比: 纯 @Order(1) 无接口 → findOrder = " + pureAnnotation);
//		System.out.println("    此时注解生效，因为 super.findOrder 返回 null，走到了注解分支");
//		System.out.println();
//	}
//
//	// ─────────────────────────────────────────────────────────────
//	// 实验4: @Priority vs @Order 的语义区别
//	// 要点:
//	//   @Order: 只用于排序（多选场景: List<Strategy> 的顺序）
//	//   @Priority: 除排序外，还支持"唯一匹配"语义
//	//              当需要从多个候选中选一个时（如 @Autowired 无 @Qualifier），
//	//              @Priority 值最小的直接胜出
//	// 源码:
//	//   OrderUtils#findOrder: @Order 优先于 @Priority（排序时等价）
//	//   AnnotationAwareOrderComparator#getPriority: 只读 @Priority，不读 @Order
//	//   DefaultListableBeanFactory#determineHighestPriorityCandidate: 用 getPriority 选唯一
//	// ─────────────────────────────────────────────────────────────
//	static void exp4_priorityVsOrder() {
//		System.out.println("【实验4】@Priority vs @Order → @Priority 额外支持唯一选择语义");
//		System.out.println("─────────────────────────────────────────────────────────");
//
//		@Order(1)
//		class WithOrder {}
//
//		@Priority(2)
//		class WithPriority {}
//
//		// 排序语义: 两者等价
//		Integer orderVal = OrderUtils.getOrder(WithOrder.class);
//		Integer priorityVal = OrderUtils.getOrder(WithPriority.class);
//		System.out.println("  排序维度:");
//		System.out.println("    @Order(1) → OrderUtils.getOrder = " + orderVal);
//		System.out.println("    @Priority(2) → OrderUtils.getOrder = " + priorityVal);
//		System.out.println("    结论: 排序时等价，都能被 OrderUtils 读取");
//
//		// 唯一选择语义: 只有 @Priority 有
//		Integer orderPriority = AnnotationAwareOrderComparator.INSTANCE.getPriority(new WithOrder());
//		Integer priorityPriority = AnnotationAwareOrderComparator.INSTANCE.getPriority(new WithPriority());
//		System.out.println("\n  唯一选择维度 (getPriority):");
//		System.out.println("    @Order(1) → getPriority = " + orderPriority + " (null! 没有唯一选择能力)");
//		System.out.println("    @Priority(2) → getPriority = " + priorityPriority + " (有值! 可用于唯一匹配)");
//		System.out.println("    场景: @Autowired Strategy strategy → 多候选时 @Priority 值最小的胜出");
//
//		// 同时有 @Order 和 @Priority
//		@Order(10)
//		@Priority(20)
//		class BothAnnotations {}
//		Integer bothOrder = OrderUtils.getOrder(BothAnnotations.class);
//		System.out.println("\n  同时有 @Order(10) + @Priority(20):");
//		System.out.println("    OrderUtils.getOrder = " + bothOrder + " → @Order 优先于 @Priority");
//		System.out.println("    源码: OrderUtils#findOrder 先查 @Order → 有值直接返回 → 跳过 @Priority");
//		System.out.println();
//	}
//
//	// ─────────────────────────────────────────────────────────────
//	// 实验5: BeanPostProcessor 三梯队注册顺序
//	// 要点: PostProcessorRegistrationDelegate#registerBeanPostProcessors:
//	//       梯队1: PriorityOrdered → 先 getBean 实例化 → sortPostProcessors → register
//	//       梯队2: Ordered → 先 getBean 实例化 → sortPostProcessors → register
//	//       梯队3: 普通 → getBean → register（无排序保证）
//	//       最后: MergedBeanDefinitionPostProcessor 重新注册到末尾
//	//       排序器: beanFactory.getDependencyComparator() ?? OrderComparator.INSTANCE
//	//              AnnotationConfigUtils 设置为 AnnotationAwareOrderComparator.INSTANCE
//	// 源码: PostProcessorRegistrationDelegate#sortPostProcessors:389
//	// ─────────────────────────────────────────────────────────────
//	static void exp5_bppThreeTierRegistration() {
//		System.out.println("【实验5】BeanPostProcessor 三梯队注册 → 观察注册顺序");
//		System.out.println("─────────────────────────────────────────────────────────");
//
//		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
//		ctx.register(BppConfig.class);
//		System.out.println("  >> refresh() → 观察 BPP 注册日志:");
//		ctx.refresh();
//
//		// 验证最终注册顺序
//		System.out.println("\n  >> BeanPostProcessor 最终注册顺序（从 beanFactory 获取）:");
//		ConfigurableListableBeanFactory bf = ctx.getBeanFactory();
//		// 用反射或直接看日志即可
//		System.out.println("    排序器 = " + bf.getClass().getSimpleName()
//				+ ".getDependencyComparator() = "
//				+ (bf instanceof org.springframework.beans.factory.support.DefaultListableBeanFactory
//				? ((org.springframework.beans.factory.support.DefaultListableBeanFactory) bf)
//				.getDependencyComparator().getClass().getSimpleName()
//				: "unknown"));
//
//		ctx.close();
//		System.out.println();
//	}
//
//	// ─────────────────────────────────────────────────────────────
//	// 实验6: 集合注入排序
//	// 要点: 当 @Autowired List<Strategy> 时，Spring 用 dependencyComparator 排序
//	//       DefaultListableBeanFactory#resolveMultipleBeans → adaptOrderComparator
//	//       → AnnotationAwareOrderComparator.withSourceProvider(...)
//	//       sourceProvider: 从 Bean 的 @Bean 方法或类上读取 @Order
//	// 场景: 策略模式中，不同策略按优先级排序
//	//       支付方式: 余额(1) → 优惠券(2) → 银行卡(3) → 信用卡(4)
//	// ─────────────────────────────────────────────────────────────
//	static void exp6_collectionInjectionOrdering() {
//		System.out.println("【实验6】集合注入排序 → @Autowired List<Strategy> 按 @Order 排序");
//		System.out.println("─────────────────────────────────────────────────────────");
//
//		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
//		ctx.register(StrategyConfig.class);
//		ctx.refresh();
//
//		StrategyConsumer consumer = ctx.getBean(StrategyConsumer.class);
//		consumer.printStrategies();
//
//		ctx.close();
//		System.out.println();
//	}
//
//	// ─────────────────────────────────────────────────────────────
//	// 实验7: @EventListener 排序
//	// 要点: 同一事件的多个 @EventListener 默认按什么顺序执行？
//	//       ApplicationListenerMethodAdapter 在创建时读取方法上的 @Order
//	//       SimpleApplicationEventMulticaster#retrieveApplicationListeners → 排序
//	//       用 AnnotationAwareOrderComparator.sort(listeners)
//	// 场景: 订单支付成功事件 → 先更新库存(1) → 再发通知(2) → 最后记日志(3)
//	// ─────────────────────────────────────────────────────────────
//	static void exp7_eventListenerOrdering() {
//		System.out.println("【实验7】@EventListener 排序 → 同事件多监听器执行顺序");
//		System.out.println("─────────────────────────────────────────────────────────");
//
//		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
//		ctx.register(EventConfig.class);
//		ctx.refresh();
//
//		System.out.println("\n  >> 发布 OrderPaidEvent → 观察监听器执行顺序:");
//		ctx.publishEvent(new OrderPaidEvent("ORD-20260319-001"));
//
//		ctx.close();
//		System.out.println();
//	}
//
//	// ─── 工具方法 ───────────────────────────────────────────────
//
//	private static String interpretCompare(int result) {
//		if (result < 0) return "a 排在 b 前面 (a 优先级更高)";
//		if (result > 0) return "b 排在 a 前面 (b 优先级更高)";
//		return "a 和 b 优先级相同";
//	}
//
//	// ═══════════════════════════════════════════════════════════
//	//  实验5 配置类: BPP 三梯队
//	// ═══════════════════════════════════════════════════════════
//
//	@Configuration
//	static class BppConfig {
//
//		@Bean
//		static TierOneBpp tierOneBpp() {
//			return new TierOneBpp();
//		}
//
//		@Bean
//		static TierTwoBpp tierTwoBpp() {
//			return new TierTwoBpp();
//		}
//
//		@Bean
//		static TierThreeBpp tierThreeBpp() {
//			return new TierThreeBpp();
//		}
//	}
//
//	/** 第一梯队: PriorityOrdered BPP — 最先注册 */
//	static class TierOneBpp implements BeanPostProcessor, PriorityOrdered {
//		public TierOneBpp() {
//			System.out.println("    [梯队1-PriorityOrdered] BPP 实例化 (order=5)");
//		}
//
//		@Override
//		public int getOrder() { return 5; }
//
//		@Override
//		public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//			return bean;
//		}
//	}
//
//	/** 第二梯队: Ordered BPP — PriorityOrdered 之后注册 */
//	static class TierTwoBpp implements BeanPostProcessor, Ordered {
//		public TierTwoBpp() {
//			System.out.println("    [梯队2-Ordered] BPP 实例化 (order=10)");
//		}
//
//		@Override
//		public int getOrder() { return 10; }
//
//		@Override
//		public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//			return bean;
//		}
//	}
//
//	/** 第三梯队: 无排序 BPP — 最后注册 */
//	static class TierThreeBpp implements BeanPostProcessor {
//		public TierThreeBpp() {
//			System.out.println("    [梯队3-无排序] BPP 实例化");
//		}
//
//		@Override
//		public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//			return bean;
//		}
//	}
//
//	// ═══════════════════════════════════════════════════════════
//	//  实验6 配置类: 集合注入排序
//	// ═══════════════════════════════════════════════════════════
//
//	interface PayStrategy {
//		String name();
//	}
//
//	@Order(3)
//	static class BankCardStrategy implements PayStrategy {
//		@Override
//		public String name() { return "银行卡"; }
//	}
//
//	@Order(1)
//	static class BalanceStrategy implements PayStrategy {
//		@Override
//		public String name() { return "余额"; }
//	}
//
//	@Order(4)
//	static class CreditCardStrategy implements PayStrategy {
//		@Override
//		public String name() { return "信用卡"; }
//	}
//
//	@Order(2)
//	static class CouponStrategy implements PayStrategy {
//		@Override
//		public String name() { return "优惠券"; }
//	}
//
//	@Configuration
//	static class StrategyConfig {
//
//		// 故意乱序注册
//		@Bean
//		PayStrategy creditCard() { return new CreditCardStrategy(); }
//		@Bean
//		PayStrategy bankCard() { return new BankCardStrategy(); }
//		@Bean
//		PayStrategy balance() { return new BalanceStrategy(); }
//		@Bean
//		PayStrategy coupon() { return new CouponStrategy(); }
//
//		@Bean
//		StrategyConsumer strategyConsumer(List<PayStrategy> strategies) {
//			return new StrategyConsumer(strategies);
//		}
//	}
//
//	static class StrategyConsumer {
//		private final List<PayStrategy> strategies;
//
//		StrategyConsumer(List<PayStrategy> strategies) {
//			this.strategies = strategies;
//		}
//
//		void printStrategies() {
//			System.out.println("  >> @Autowired List<PayStrategy> 注入顺序:");
//			for (int i = 0; i < strategies.size(); i++) {
//				PayStrategy s = strategies.get(i);
//				Integer order = OrderUtils.getOrder(s.getClass());
//				System.out.println("    [" + i + "] " + s.name() + " (@Order=" + order + ")");
//			}
//			System.out.println("  结论: List 按 @Order 值升序排列，和 Bean 注册顺序无关");
//		}
//	}
//
//	// ═══════════════════════════════════════════════════════════
//	//  实验7 配置类: 事件监听器排序
//	// ═══════════════════════════════════════════════════════════
//
//	/** 自定义事件 */
//	static class OrderPaidEvent extends ApplicationEvent {
//		private final String orderId;
//
//		OrderPaidEvent(String orderId) {
//			super(orderId);
//			this.orderId = orderId;
//		}
//
//		String getOrderId() { return orderId; }
//	}
//
//	@Configuration
//	static class EventConfig {
//		@Bean
//		EventListenerGroup eventListenerGroup() {
//			return new EventListenerGroup();
//		}
//	}
//
//	/** 同一个 Bean 上的多个 @EventListener，用 @Order 控制顺序 */
//	static class EventListenerGroup {
//
//		@EventListener
//		@Order(3)
//		public void logEvent(OrderPaidEvent event) {
//			System.out.println("    [3-日志] 记录支付日志: " + event.getOrderId());
//		}
//
//		@EventListener
//		@Order(1)
//		public void updateInventory(OrderPaidEvent event) {
//			System.out.println("    [1-库存] 扣减库存: " + event.getOrderId());
//		}
//
//		@EventListener
//		@Order(2)
//		public void sendNotification(OrderPaidEvent event) {
//			System.out.println("    [2-通知] 发送支付成功通知: " + event.getOrderId());
//		}
//	}
//}
