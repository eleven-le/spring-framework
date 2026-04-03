package org.springframework.lab.processortour;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W33 - Processor 轻量导览：所有注解能力为何都长这套
 *
 * <h2>一句话抽象</h2>
 * 注解驱动能力 = @EnableXxx(@Import) 注册基础设施 Bean + Processor/Advisor 把能力织入容器与代理链。
 * 核心矛盾：无侵入性（业务零感知） vs 可控性（顺序/条件/覆盖都需要精确控制）。
 *
 * <h2>通用套路三变体</h2>
 * <ul>
 *   <li>Advisor 路线 (@Transactional/@Cacheable): AutoProxyRegistrar + Advisor + MethodInterceptor</li>
 *   <li>BPP+Advisor 路线 (@Async): AbstractAdvisingBeanPostProcessor 自己建代理</li>
 *   <li>纯 BPP 路线 (@Scheduled): 不走代理，直接扫方法注册任务</li>
 * </ul>
 *
 * <h2>实验列表</h2>
 * <ol>
 *   <li>Scene-1: 对比 @Transactional/@Cacheable/@Async 三种注解的代理生成路径</li>
 *   <li>Scene-2: 自定义 @EnableAuditLog，完整复刻 Advisor 路线</li>
 *   <li>Scene-3: 验证自定义 AuditLog 拦截器与 Spring 内置拦截器共存于同一代理链</li>
 * </ol>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>AutoProxyRegistrar#registerBeanDefinitions:70     — 观察 InfrastructureAdvisorAutoProxyCreator 注册</li>
 *   <li>AbstractAutoProxyCreator#wrapIfNecessary:328      — 观察哪些 bean 被代理、哪些 Advisor 匹配</li>
 *   <li>ProxyTransactionManagementConfiguration#transactionAdvisor — 观察 TX Advisor 三件套组装</li>
 *   <li>AbstractAdvisingBeanPostProcessor#postProcessAfterInitialization:66 — @Async BPP 自建代理路径</li>
 *   <li>ScheduledAnnotationBeanPostProcessor#postProcessAfterInitialization — @Scheduled 纯扫描路径</li>
 * </ol>
 *
 * <h2>口述调用链（极简版）</h2>
 * <pre>
 * 1. @EnableXxx 注解上 @Import(XxxSelector)
 * 2. XxxSelector#selectImports 返回 [AutoProxyRegistrar, ProxyXxxConfiguration]
 * 3. AutoProxyRegistrar 往容器注册 InfrastructureAdvisorAutoProxyCreator (BPP)
 * 4. ProxyXxxConfiguration 通过 @Bean 注册 Advisor + MethodInterceptor
 * 5. refresh → registerBeanPostProcessors 把 AutoProxyCreator 装进 BPP 链
 * 6. 后续每个 bean 的 initializeBean → postProcessAfterInitialization
 * 7. AutoProxyCreator#wrapIfNecessary 扫容器里所有 Advisor
 * 8. 只要 Pointcut 匹配当前 bean → 用 ProxyFactory 创建代理
 * 9. 运行期调用方法 → 代理拦截 → 走 MethodInterceptor 链
 * 10. 真正干活的就是 TransactionInterceptor / CacheInterceptor / 你自定义的 Interceptor
 * </pre>
 */
public class ProcessorTourMain {

	public static void main(String[] args) {
		System.out.println("========================================================");
		System.out.println("  W33 Processor 轻量导览：所有注解能力为何都长这套");
		System.out.println("========================================================\n");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(ProcessorTourConfig.class);

		// ----- Scene-1: 观察三种注解的代理路径 -----
		System.out.println("=== Scene-1: 三种注解驱动能力的代理路径对比 ===\n");

		OrderService orderService = ctx.getBean(OrderService.class);
		System.out.println("[OrderService 实际类型] " + orderService.getClass().getName());
		System.out.println("  ↑ 被 @Transactional 的 Advisor 路线代理（InfrastructureAdvisorAutoProxyCreator）\n");

		NotificationService notifyService = ctx.getBean(NotificationService.class);
		System.out.println("[NotificationService 实际类型] " + notifyService.getClass().getName());
		System.out.println("  ↑ 被 @Async 的 BPP+Advisor 路线代理（AsyncAnnotationBeanPostProcessor）\n");

		ReportService reportService = ctx.getBean(ReportService.class);
		System.out.println("[ReportService 实际类型] " + reportService.getClass().getName());
		System.out.println("  ↑ @Scheduled 纯 BPP 路线，不会生成代理，原始类型\n");

		// ----- Scene-2: 自定义 @EnableAuditLog -----
		System.out.println("=== Scene-2: 自定义 @EnableAuditLog 复刻 Advisor 路线 ===\n");

		PaymentService paymentService = ctx.getBean(PaymentService.class);
		System.out.println("[PaymentService 实际类型] " + paymentService.getClass().getName());
		System.out.println("  ↑ 被自定义 AuditLogAdvisor 织入的代理\n");

		System.out.println("--- 调用 paymentService.pay() ---");
		paymentService.pay("user-10086", 99_00L);

		// ----- Scene-3: 自定义 Advisor 与 TX Advisor 共存 -----
		System.out.println("\n=== Scene-3: 多 Advisor 共存于同一代理链 ===\n");

		System.out.println("--- 调用 orderService.placeOrder() (同时有 @Transactional + @AuditLog) ---");
		orderService.placeOrder("user-10086", "iPhone-15");
		System.out.println("  ↑ 两个 MethodInterceptor 都在同一代理链里依次执行\n");

		// ----- 打印代理链中的 Advisor 列表（验证多 Advisor 共存） -----
		System.out.println("=== 附：验证 OrderService 代理链中的 Advisor 列表 ===");
		if (orderService instanceof org.springframework.aop.framework.Advised) {
			org.springframework.aop.framework.Advised advised =
					(org.springframework.aop.framework.Advised) orderService;
			org.springframework.aop.Advisor[] advisors = advised.getAdvisors();
			for (int i = 0; i < advisors.length; i++) {
				System.out.println("  [" + i + "] " + advisors[i].getClass().getSimpleName()
						+ " → " + advisors[i].getAdvice().getClass().getSimpleName());
			}
		}

		System.out.println("\n========================================================");
		System.out.println("  所有实验完成");
		System.out.println("========================================================");

		ctx.close();
	}
}
