package org.springframework.lab.dynamicregistry;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 主配置: 汇聚四种动态注册方式.
 *
 * 注意执行顺序:
 *   1. PluginScannerBdrpp 作为 @Bean 被 CCPP 解析后, 由 PostProcessorRegistrationDelegate
 *      在 invokeBeanFactoryPostProcessors 中按 PriorityOrdered → Ordered → plain 三段式调用.
 *   2. MiddlewareSelector.selectImports() 在 parser.parse() 阶段立即执行 (非 Deferred).
 *   3. MetricsRegistrar.registerBeanDefinitions() 在 reader.loadBeanDefinitions() 阶段执行.
 *   4. InfraAutoConfigSelector 在 parser.parse() 方法末尾的 deferredImportSelectorHandler.process() 执行.
 */
@Configuration
@EnableMetrics(endpoint = "/actuator/custom-metrics")
@Import({
		MiddlewareSelector.class,
		InfraAutoConfigSelector.class
})
public class DynamicRegistryConfig {

	/**
	 * BDRPP 作为 @Bean 注册: 被 CCPP 在 processConfigBeanDefinitions 中解析为 BD,
	 * 然后在 do-while 循环中被检测为新增的 @Configuration 候选 (BeanDefinitionRegistryPostProcessor
	 * 本身也是一个 Bean), 进而由 PostProcessorRegistrationDelegate 调用.
	 *
	 * 但更常见的做法是通过 @Import 或 @ComponentScan 注册 BDRPP.
	 * 这里用 @Bean 只是为了演示: BDRPP 的生效不依赖于具体的注册方式,
	 * 关键在于它在 invokeBeanFactoryPostProcessors 阶段被容器发现.
	 */
	@org.springframework.context.annotation.Bean
	public PluginScannerBdrpp pluginScannerBdrpp() {
		return new PluginScannerBdrpp();
	}
}
