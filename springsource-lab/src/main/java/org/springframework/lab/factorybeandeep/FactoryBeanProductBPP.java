package org.springframework.lab.factorybeandeep;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 【场景5: BPP 对 FactoryBean 产物的后处理】
 *
 * 关键源码路径:
 *   AbstractAutowireCapableBeanFactory#postProcessObjectFromFactoryBean
 *     → applyBeanPostProcessorsAfterInitialization(object, beanName)
 *
 * 注意事项:
 *   1. BPP 的 postProcessAfterInitialization 会作用于 FactoryBean 的产物
 *   2. beanName 是产物名 (如 "cacheConn"), 不是 "&cacheConn"
 *   3. 对于 singleton 产物, BPP 只执行一次 (后续从缓存取)
 *   4. AOP 自动代理也是通过这个机制对 FactoryBean 产物生成代理的
 *
 * 断点:
 *   AbstractAutowireCapableBeanFactory:1946 → postProcessObjectFromFactoryBean
 */
public class FactoryBeanProductBPP implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof Connection) {
			System.out.println("  [BPP] postProcessAfterInitialization 对 FactoryBean 产物生效:"
					+ " beanName=" + beanName + ", bean=" + bean);
		}
		return bean;
	}
}
