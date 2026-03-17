package org.springframework.lab.hierarchy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 只注册在父容器的 BPP —— 验证 BPP 不会被子容器继承
 */
public class TimingBpp implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("  [TimingBpp-PARENT] beforeInit -> " + beanName);
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}
}
