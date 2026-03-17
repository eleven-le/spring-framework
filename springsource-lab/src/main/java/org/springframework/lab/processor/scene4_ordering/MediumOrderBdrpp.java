package org.springframework.lab.processor.scene4_ordering;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Scene-4b: Ordered BDRPP — 第二批执行
 */
@Component
public class MediumOrderBdrpp implements BeanDefinitionRegistryPostProcessor, Ordered {

	@Override
	public int getOrder() {
		return 100;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("[ORDER] 2nd - Ordered BDRPP (order=100)");
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
	}
}
