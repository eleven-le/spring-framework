package org.springframework.lab.processor.scene2_bfpp;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Scene-2: BFPP — 修改已有 BeanDefinition
 *
 * C端业务场景：购物车 CartService 默认 @Component 是 singleton，
 * 但业务要求每次请求一个新购物车（prototype）。
 * 治理组件通过 BFPP 统一把 cartService 的 scope 改成 prototype，
 * 避免侵入业务代码。类比 PropertySourcesPlaceholderConfigurer 替换 ${...}。
 *
 * 断点：postProcessBeanFactory 第一行
 * 对照源码：PostProcessorRegistrationDelegate:198 invokeBeanFactoryPostProcessors
 */
@Component
public class ScopeOverrideBfpp implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("[BFPP]  >>> postProcessBeanFactory: 修改 cartService 的 scope");

		if (beanFactory.containsBeanDefinition("cartService")) {
			BeanDefinition bd = beanFactory.getBeanDefinition("cartService");
			String oldScope = bd.getScope();
			bd.setScope("prototype");
			System.out.println("[BFPP]      cartService scope: " + oldScope + " -> prototype");
		}
	}
}
