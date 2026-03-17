package org.springframework.lab.configuration;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W10: @Configuration 处理链精讲
 *
 * 场景覆盖:
 *   1. Full vs Lite 模式 (@Configuration proxyBeanMethods=true/false)
 *   2. @Bean 方法间调用 — CGLIB 代理保证单例语义
 *   3. @Import 三种方式: 普通类 / ImportSelector / ImportBeanDefinitionRegistrar
 *   4. DeferredImportSelector 延迟导入机制
 *   5. 业务落地: @EnablePayChannels 可插拔支付通道
 *
 * 断点推荐:
 *   - ConfigurationClassPostProcessor#processConfigBeanDefinitions  (整体入口)
 *   - ConfigurationClassParser#processImports                       (@Import 三路分发)
 *   - ConfigurationClassEnhancer$BeanMethodInterceptor#intercept    (CGLIB 单例拦截)
 *   - ConfigurationClassParser$DeferredImportSelectorHandler#process(延迟导入触发)
 *   - ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForBeanMethod (@Bean→BD)
 */
public class ConfigurationMain {

	public static void main(String[] args) {
		System.out.println("===== W10: @Configuration 处理链 =====\n");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(FullModeConfig.class);

		// --- 1. Full 模式: @Bean 方法间调用保证单例 ---
		System.out.println("--- Scene 1: Full Mode @Bean inter-call ---");
		OrderService orderService = ctx.getBean(OrderService.class);
		DataSource ds1 = ctx.getBean(DataSource.class);
		System.out.println("orderService.dataSource == ctx.getBean(DataSource): "
				+ (orderService.getDataSource() == ds1));
		// Full 模式下为 true (CGLIB 代理拦截)
		System.out.println("Config class is CGLIB enhanced: "
				+ ctx.getBean(FullModeConfig.class).getClass().getName());

		// --- 2. Lite 模式: @Bean 方法间调用不保证单例 ---
		System.out.println("\n--- Scene 2: Lite Mode @Bean inter-call ---");
		LiteModeConfig liteConfig = ctx.getBean(LiteModeConfig.class);
		System.out.println("Lite config class (no CGLIB): " + liteConfig.getClass().getName());
		// liteService 内部的 dataSource 和容器中的 DataSource 是否同一个?
		// Lite 模式下 @Bean 方法调用是普通 Java 调用, 每次 new

		// --- 3. @Import 普通类 ---
		System.out.println("\n--- Scene 3: @Import plain class ---");
		AuditLogger auditLogger = ctx.getBean(AuditLogger.class);
		System.out.println("AuditLogger imported: " + auditLogger);

		// --- 4. ImportSelector ---
		System.out.println("\n--- Scene 4: ImportSelector ---");
		System.out.println("PayChannel beans: " + ctx.getBeansOfType(PayChannel.class).keySet());

		// --- 5. ImportBeanDefinitionRegistrar ---
		System.out.println("\n--- Scene 5: ImportBeanDefinitionRegistrar ---");
		RpcProxy rpcProxy = ctx.getBean("riskControlRpcProxy", RpcProxy.class);
		System.out.println("RpcProxy registered by Registrar: " + rpcProxy);

		// --- 6. DeferredImportSelector ---
		System.out.println("\n--- Scene 6: DeferredImportSelector ---");
		MonitorReporter reporter = ctx.getBean(MonitorReporter.class);
		System.out.println("MonitorReporter (deferred import): " + reporter);

		System.out.println("\n===== Done =====");
		ctx.close();
	}
}
