package org.springframework.lab.aop;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W15 - AOP 主链：AutoProxyCreator 何时 wrap Bean
 *
 * <h2>一句话抽象</h2>
 * AOP 自动代理解决的核心问题是"如何在不修改业务代码的前提下, 把横切逻辑织入任意 Bean"；
 * 机制矛盾是"织入时机必须在 Bean 初始化完成后但在交付使用前, 同时要兼容循环依赖的提前暴露"——
 * AnnotationAwareAspectJAutoProxyCreator 作为 BeanPostProcessor 在 postProcessAfterInitialization
 * 阶段判定 wrapIfNecessary, 用 Advisor 匹配 + ProxyFactory 生成 JDK/CGLIB 代理完成织入。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: JDK 动态代理 — 有接口的 OrderService, 观察代理类型和 Advisor 链</li>
 *   <li>实验 2: CGLIB 代理 — 无接口的 PaymentService, 对比代理选择策略</li>
 *   <li>实验 3: 多切面叠加 — @Order 洋葱模型, LogAspect(1) 包 TxAspect(2)</li>
 *   <li>实验 4: proxyTargetClass=true — 强制 CGLIB, 同一 Bean 不同代理类型</li>
 *   <li>实验 5: 自调用失效与 exposeProxy — AopContext.currentProxy() 解法</li>
 * </ul>
 *
 * <h2>核心调用链 (12步)</h2>
 * <pre>
 *  ① @EnableAspectJAutoProxy → @Import(AspectJAutoProxyRegistrar) : 触发注册
 *  ② AspectJAutoProxyRegistrar#registerBeanDefinitions : 委托 AopConfigUtils
 *  ③ AopConfigUtils#registerAspectJAnnotationAutoProxyCreatorIfNecessary : 注册 AAAPPC 为 BPP
 *  ④ AbstractAutoProxyCreator#postProcessAfterInitialization : Bean 初始化完毕后拦截
 *  ⑤ AbstractAutoProxyCreator#wrapIfNecessary : 决策入口, 三级判定(基础设施?跳过?有Advisor?)
 *  ⑥ AbstractAdvisorAutoProxyCreator#getAdvicesAndAdvisorsForBean → findEligibleAdvisors : 找匹配的 Advisor
 *  ⑦ AnnotationAwareAspectJAutoProxyCreator#findCandidateAdvisors : 合并 Spring Advisor + @Aspect 解析
 *  ⑧ BeanFactoryAspectJAdvisorsBuilder#buildAspectJAdvisors : 扫描所有 @Aspect Bean, 缓存 Advisor
 *  ⑨ AopUtils#findAdvisorsThatCanApply → canApply(Pointcut, Class) : Pointcut 二阶匹配(类→方法)
 * ⑩ AbstractAutoProxyCreator#createProxy : 构建 ProxyFactory, 设置 Advisor+TargetSource
 * ⑪ DefaultAopProxyFactory#createAopProxy : 选择 JdkDynamicAopProxy 或 ObjenesisCglibAopProxy
 * ⑫ AopProxy#getProxy : Proxy.newProxyInstance (JDK) 或 Enhancer.create (CGLIB) 生成最终代理
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>AbstractAutoProxyCreator#postProcessAfterInitialization — AOP 入口, 观察哪些 Bean 被拦截</li>
 *   <li>AbstractAutoProxyCreator#wrapIfNecessary — 核心决策: 跳过 or 代理</li>
 *   <li>AbstractAdvisorAutoProxyCreator#findEligibleAdvisors — 看 Advisor 发现与匹配全过程</li>
 *   <li>DefaultAopProxyFactory#createAopProxy — JDK vs CGLIB 分叉点</li>
 *   <li>AbstractAutoProxyCreator#getEarlyBeanReference — 循环依赖场景的提前代理</li>
 * </ol>
 */
public class AopMain {

	public static void main(String[] args) {
		System.out.println("==========================================================");
		System.out.println("  W15 AOP 主链: AutoProxyCreator 何时 wrap Bean");
		System.out.println("==========================================================");

		experiment1_JdkProxy();
		experiment2_CglibProxy();
		experiment3_MultiAspectOrder();
		experiment4_ForceProxyTargetClass();
		experiment5_SelfInvokeAndExposeProxy();

		System.out.println("\n=== 全部实验完毕 ===");
	}

	/**
	 * 实验 1: JDK 动态代理
	 * OrderServiceImpl 实现 OrderService 接口 → 默认 JDK 代理
	 *
	 * 断点: DefaultAopProxyFactory#createAopProxy
	 * 观察: hasNoUserSuppliedProxyInterfaces → false → JdkDynamicAopProxy
	 */
	static void experiment1_JdkProxy() {
		System.out.println("\n--- 实验 1: JDK 动态代理 (有接口) ---");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AopConfig.class);

		OrderService orderService = ctx.getBean(OrderService.class);
		ProxyInspector.inspect("OrderService", orderService);

		System.out.println(">> 调用 createOrder:");
		String orderId = orderService.createOrder("iPhone", 1);
		System.out.println(">> 返回: " + orderId);

		ctx.close();
	}

	/**
	 * 实验 2: CGLIB 代理
	 * PaymentService 没有接口 → 强制 CGLIB
	 *
	 * 断点: DefaultAopProxyFactory#createAopProxy
	 * 观察: hasNoUserSuppliedProxyInterfaces → true → ObjenesisCglibAopProxy
	 */
	static void experiment2_CglibProxy() {
		System.out.println("\n--- 实验 2: CGLIB 代理 (无接口) ---");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AopConfig.class);

		PaymentService paymentService = ctx.getBean(PaymentService.class);
		ProxyInspector.inspect("PaymentService", paymentService);

		System.out.println(">> 调用 pay:");
		paymentService.pay("ORD-001", 9999.0);

		ctx.close();
	}

	/**
	 * 实验 3: 多切面叠加 — 洋葱模型
	 * LogAspect(@Order=1) 在外, TxAspect(@Order=2) 在内
	 * createOrder 同时匹配两个 Aspect → 观察嵌套执行顺序
	 */
	static void experiment3_MultiAspectOrder() {
		System.out.println("\n--- 实验 3: 多切面叠加 (洋葱模型) ---");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AopConfig.class);

		OrderService orderService = ctx.getBean(OrderService.class);

		System.out.println(">> createOrder 同时匹配 LogAspect + TxAspect:");
		orderService.createOrder("MacBook", 2);

		System.out.println("\n>> queryOrder 只匹配 LogAspect (TxAspect 不匹配 query*):");
		orderService.queryOrder("ORD-123");

		ctx.close();
	}

	/**
	 * 实验 4: proxyTargetClass=true
	 * 即使 OrderServiceImpl 有接口, 也强制 CGLIB 子类代理
	 * 可以用具体类型注入: ctx.getBean(OrderServiceImpl.class)
	 */
	static void experiment4_ForceProxyTargetClass() {
		System.out.println("\n--- 实验 4: proxyTargetClass=true (全 CGLIB) ---");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AopCglibConfig.class);

		// proxyTargetClass=true 后可以按实现类获取
		OrderServiceImpl orderService = ctx.getBean(OrderServiceImpl.class);
		ProxyInspector.inspect("OrderServiceImpl (强制CGLIB)", orderService);

		PaymentService paymentService = ctx.getBean(PaymentService.class);
		ProxyInspector.inspect("PaymentService (依然CGLIB)", paymentService);

		ctx.close();
	}

	/**
	 * 实验 5: 自调用失效与 exposeProxy 解法
	 * - outer() → this.internal(): AOP 不拦截 internal
	 * - outerViaProxy() → AopContext.currentProxy().internal(): AOP 拦截 internal
	 */
	static void experiment5_SelfInvokeAndExposeProxy() {
		System.out.println("\n--- 实验 5: 自调用失效与 exposeProxy ---");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AopExposeConfig.class);

		SelfInvokeService service = ctx.getBean(SelfInvokeService.class);

		System.out.println(">> outer() — this 直调, internal 的 AOP 不生效:");
		service.outer();

		System.out.println("\n>> outerViaProxy() — AopContext.currentProxy(), internal 的 AOP 生效:");
		service.outerViaProxy();

		ctx.close();
	}
}
