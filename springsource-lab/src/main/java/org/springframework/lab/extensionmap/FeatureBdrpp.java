package org.springframework.lab.extensionmap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.PriorityOrdered;

/**
 * 定义期 —— BeanDefinitionRegistryPostProcessor
 * <p>
 * 业务场景: Feature Toggle / 灰度发布时动态注册或移除 BD
 * 时机: 在 ConfigurationClassPostProcessor 之后、普通 BFPP 之前
 */
public class FeatureBdrpp implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		TimelineTracker.record("定义",
				"BDRPP.postProcessBeanDefinitionRegistry",
				"可增删 BD (动态注册 grayService)");
		GenericBeanDefinition bd = new GenericBeanDefinition();
		bd.setBeanClassName(GrayService.class.getName());
		registry.registerBeanDefinition("grayService", bd);
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory bf) throws BeansException {
		TimelineTracker.record("定义",
				"BDRPP.postProcessBeanFactory",
				"BDRPP 也会执行 BFPP 阶段 (先于普通 BFPP)");
	}

	@Override
	public int getOrder() {
		return 0;
	}
}
