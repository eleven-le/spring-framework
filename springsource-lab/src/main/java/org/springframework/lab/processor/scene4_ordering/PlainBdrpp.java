package org.springframework.lab.processor.scene4_ordering;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Scene-4c: 无排序 BDRPP — 最后一批执行（while 循环兜底）
 *
 * 这批 BDRPP 在 PostProcessorRegistrationDelegate:128-144 的 while 循环里执行，
 * 支持 "BDRPP 注册新 BDRPP" 的递归发现。
 */
@Component
public class PlainBdrpp implements BeanDefinitionRegistryPostProcessor {

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("[ORDER] 3rd - Plain BDRPP (no ordering interface)");
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
	}
}
