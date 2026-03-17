package org.springframework.lab.dynamicregistry;

import java.util.Map;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 场景2: ImportBeanDefinitionRegistrar — 从 @EnableMetrics 读取注解属性并注册 Bean.
 *
 * 核心能力: 拿到 importingClassMetadata → 读取 @EnableMetrics 的 endpoint 属性
 *          → 构建 RootBeanDefinition → 注册到 Registry.
 *
 * 与 BDRPP 的关键差异:
 *   - Registrar 只在 @Import 链路中被触发, 天然绑定 @Enable 语义
 *   - Registrar 能拿到"谁导入了我"的 AnnotationMetadata (BDRPP 拿不到)
 *   - Registrar 在 Reader 阶段执行 (BDRPP 在 invokeBeanFactoryPostProcessors 阶段)
 *
 * 执行时机: ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsFromRegistrars
 */
public class MetricsRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
			BeanDefinitionRegistry registry) {

		System.out.println("[TIMING] MetricsRegistrar.registerBeanDefinitions()");

		// 读取 @EnableMetrics 注解属性
		Map<String, Object> attrs = importingClassMetadata
				.getAnnotationAttributes(EnableMetrics.class.getName());
		String endpoint = (attrs != null) ? (String) attrs.get("endpoint") : "/metrics";

		// 构建 BD 并注入注解属性值
		RootBeanDefinition bd = new RootBeanDefinition(MetricsCollector.class);
		bd.getConstructorArgumentValues().addIndexedArgumentValue(0, endpoint);
		registry.registerBeanDefinition("metricsCollector", bd);
	}
}
