package org.springframework.lab.naming;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 自定义 BPP——驱动 PayContextAware 回调。
 *
 * <p>Spring 内置的 Aware（ApplicationContextAware 等）由 ApplicationContextAwareProcessor 驱动。
 * 自定义 Aware 必须自己写一个 BPP 来驱动回调——这是 Aware 模式的完整闭环。
 *
 * <p>对照：ApplicationContextAwareProcessor#postProcessBeforeInitialization
 * <pre>
 *   if (bean instanceof EnvironmentAware) {
 *       ((EnvironmentAware) bean).setEnvironment(this.applicationContext.getEnvironment());
 *   }
 *   if (bean instanceof ApplicationContextAware) {
 *       ((ApplicationContextAware) bean).setApplicationContext(this.applicationContext);
 *   }
 * </pre>
 */
public class PayContextAwareBeanPostProcessor implements BeanPostProcessor {

	private final PayContext payContext;

	public PayContextAwareBeanPostProcessor(PayContext payContext) {
		this.payContext = payContext;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		// 对照 ApplicationContextAwareProcessor：检查是否实现了 Aware 接口，如果是就回调注入
		if (bean instanceof PayContextAware) {
			System.out.println("    [Aware BPP] 检测到 " + beanName + " 实现了 PayContextAware, 注入 PayContext");
			((PayContextAware) bean).setPayContext(this.payContext);
		}
		return bean;
	}
}
