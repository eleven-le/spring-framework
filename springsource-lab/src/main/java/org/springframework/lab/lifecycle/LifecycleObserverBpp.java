package org.springframework.lab.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 实验 2: 全阶段观察器 — 用 InstantiationAwareBeanPostProcessor 拦截 Bean 从实例化到初始化的 5 个切面。
 *
 * 回调位置:
 *   postProcessBeforeInstantiation → createBean#resolveBeforeInstantiation (可短路)
 *   postProcessAfterInstantiation  → populateBean 开头 (返回 false 跳过注入)
 *   postProcessProperties          → populateBean 中段 (@Autowired/@Value 注入点)
 *   postProcessBeforeInitialization → initializeBean (Aware 之后, @PostConstruct 之前)
 *   postProcessAfterInitialization  → initializeBean 末尾 (AOP 代理生成点)
 */
@Component
public class LifecycleObserverBpp implements InstantiationAwareBeanPostProcessor {

	private static final String TARGET = "fullLifecycleBean";

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		if (TARGET.equals(beanName)) {
			System.out.println("[Observer-IABPP] ◆ postProcessBeforeInstantiation → resolveBeforeInstantiation");
		}
		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		if (TARGET.equals(beanName)) {
			System.out.println("[Observer-IABPP] ◆ postProcessAfterInstantiation  → populateBean 开头");
		}
		return true;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
			throws BeansException {
		if (TARGET.equals(beanName)) {
			System.out.println("[Observer-IABPP] ◆ postProcessProperties          → populateBean 中段");
		}
		return pvs;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (TARGET.equals(beanName)) {
			System.out.println("[Observer-BPP]   ◆ postProcessBeforeInitialization → initializeBean 前段");
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (TARGET.equals(beanName)) {
			System.out.println("[Observer-BPP]   ◆ postProcessAfterInitialization  → initializeBean 末尾 (AOP代理点)");
		}
		return bean;
	}
}
