package org.springframework.lab.dynamicregistry;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W11: 动态注册三件套精讲 — BDRPP / Registrar / ImportSelector
 *
 * 场景覆盖:
 *   1. BDRPP 独立注册 + 链式反应 (BDRPP 注册新 BDRPP, 验证 while(reiterate) 循环)
 *   2. @Enable + ImportBeanDefinitionRegistrar (Starter 底座模式)
 *   3. ImportSelector 条件选择 (根据环境动态选择实现)
 *   4. DeferredImportSelector + Group API (自动配置补位语义)
 *   5. 执行时序对比 (通过打印观察四种机制的触发顺序)
 *
 * 断点推荐:
 *   - PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors:181  (BDRPP while循环)
 *   - ConfigurationClassParser#processImports:570                            (三路分发)
 *   - ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsFromRegistrars:394 (Registrar回调)
 *   - ConfigurationClassParser$DeferredImportSelectorHandler#process:775     (延迟触发)
 *   - ConfigurationClassPostProcessor#processConfigBeanDefinitions:348       (BD增量检测do-while)
 */
public class DynamicRegistryMain {

	public static void main(String[] args) {
		System.out.println("===== W11: 动态注册三件套 =====\n");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(DynamicRegistryConfig.class);

		// --- Scene 1: BDRPP 独立注册 + 链式反应 ---
		System.out.println("\n--- Scene 1: BDRPP chain reaction ---");
		PluginBean plugin = ctx.getBean(PluginBean.class);
		System.out.println("PluginBean (registered by PluginScannerBdrpp): " + plugin.getName());
		AuditPlugin audit = ctx.getBean(AuditPlugin.class);
		System.out.println("AuditPlugin (registered by SecondaryBdrpp, chained!): " + audit);

		// --- Scene 2: @Enable + Registrar ---
		System.out.println("\n--- Scene 2: @EnableMetrics + Registrar ---");
		MetricsCollector collector = ctx.getBean(MetricsCollector.class);
		System.out.println("MetricsCollector (registered by MetricsRegistrar): " + collector);
		System.out.println("MetricsCollector.endpoint = " + collector.getEndpoint());

		// --- Scene 3: ImportSelector 条件选择 ---
		System.out.println("\n--- Scene 3: ImportSelector conditional ---");
		System.out.println("Middleware beans: " + ctx.getBeansOfType(Middleware.class).keySet());

		// --- Scene 4: DeferredImportSelector ---
		System.out.println("\n--- Scene 4: DeferredImportSelector + Group ---");
		HealthEndpoint health = ctx.getBean(HealthEndpoint.class);
		System.out.println("HealthEndpoint (deferred auto-config): " + health);

		System.out.println("\n===== Done =====");
		ctx.close();
	}
}
