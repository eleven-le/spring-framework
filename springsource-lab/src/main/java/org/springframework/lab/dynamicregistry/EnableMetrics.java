package org.springframework.lab.dynamicregistry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * 场景2: 自定义 @Enable 注解 — Starter 底座模式.
 *
 * @Enable 注解的本质 = @Import(Registrar.class).
 * Registrar 能拿到 importingClassMetadata, 从而读取注解属性,
 * 按业务语义注册 BeanDefinition.
 *
 * 真实案例: @EnableAsync → AsyncConfigurationSelector
 *          @EnableCaching → CachingConfigurationSelector
 *          @EnableFeignClients → FeignClientsRegistrar
 *
 * 源码路径:
 *   ConfigurationClassParser#processImports:588
 *     → candidate.isAssignable(ImportBeanDefinitionRegistrar.class)
 *     → configClass.addImportBeanDefinitionRegistrar(registrar, metadata) [暂存]
 *   ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsFromRegistrars:394
 *     → registrar.registerBeanDefinitions(metadata, registry) [真正执行]
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MetricsRegistrar.class)
public @interface EnableMetrics {

	String endpoint() default "/metrics";
}
