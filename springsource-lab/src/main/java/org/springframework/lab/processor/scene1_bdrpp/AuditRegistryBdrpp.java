package org.springframework.lab.processor.scene1_bdrpp;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.lab.processor.AuditLogger;
import org.springframework.stereotype.Component;

/**
 * Scene-1: BDRPP — 动态注册 BeanDefinition
 * <p>
 * C端业务场景：电商平台引入 payment-monitor.jar，自动注册 AuditLogger 到容器。
 * 用户无需 @Bean 声明，引入即生效（Spring Boot auto-config 的核心套路）。
 * <p>
 * 断点：postProcessBeanDefinitionRegistry 第一行
 * 对照源码：PostProcessorRegistrationDelegate:112 invokeBeanDefinitionRegistryPostProcessors
 */
@Component
public class AuditRegistryBdrpp implements BeanDefinitionRegistryPostProcessor {

	/**
	 * 阶段一：往 registry 里注册新的 BeanDefinition
	 * 此时还没有任何 Bean 实例化，只是在"登记户口"
	 */
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("[BDRPP] >>> postProcessBeanDefinitionRegistry: 注册 auditLogger BD");
		RootBeanDefinition bd = new RootBeanDefinition(AuditLogger.class);
		bd.setScope("singleton");
		registry.registerBeanDefinition("auditLogger", bd);
		System.out.println("[BDRPP]     当前 BD 总数: " + registry.getBeanDefinitionCount());
	}

	/**
	 * 阶段二：BDRPP 也是 BFPP，所以 postProcessBeanFactory 也会被调用
	 * 但此时不建议再注册新 BD（因为已经过了 BDRPP 阶段）
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("[BDRPP] >>> postProcessBeanFactory (BDRPP 的第二阶段回调)");
	}
}
