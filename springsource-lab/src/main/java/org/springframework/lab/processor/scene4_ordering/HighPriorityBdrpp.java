package org.springframework.lab.processor.scene4_ordering;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

/**
 * Scene-4a: PriorityOrdered BDRPP — 最先执行
 *
 * ConfigurationClassPostProcessor 就是 PriorityOrdered，
 * 它最先跑，负责解析 @Configuration/@Bean/@Import/@ComponentScan，
 * 注册所有扫描到的 BeanDefinition。
 */
@Component
public class HighPriorityBdrpp implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("[ORDER] 1st - PriorityOrdered BDRPP (order=0)");
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
	}
}
