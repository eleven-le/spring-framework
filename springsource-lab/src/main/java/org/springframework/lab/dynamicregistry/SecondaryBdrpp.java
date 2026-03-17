package org.springframework.lab.dynamicregistry;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * 被 PluginScannerBdrpp 动态注册的"二级 BDRPP".
 *
 * 验证: PostProcessorRegistrationDelegate 的 while(reiterate) 循环
 * 会在下一轮扫描中发现这个新注册的 BDRPP 并执行它.
 *
 * 源码关键行:
 *   PostProcessorRegistrationDelegate:186 → if (!processedBeans.contains(ppName))
 *   PostProcessorRegistrationDelegate:190 → reiterate = true
 */
public class SecondaryBdrpp implements BeanDefinitionRegistryPostProcessor {

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("[TIMING] SecondaryBdrpp.postProcessBeanDefinitionRegistry() — chain reaction executed!");
		registry.registerBeanDefinition("auditPlugin", new RootBeanDefinition(AuditPlugin.class));
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// no-op
	}
}
