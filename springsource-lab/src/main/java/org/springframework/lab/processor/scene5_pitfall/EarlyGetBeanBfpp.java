package org.springframework.lab.processor.scene5_pitfall;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Scene-5: 陷阱 — 在 BFPP 阶段过早 getBean()
 *
 * 默认不启用（没有 @Component），在 PitfallMain 中手动注册演示。
 *
 * 问题：BFPP 在 refresh() 的 step5 执行，此时 BPP 还没注册（step6 才注册）。
 * 如果 BFPP 里调用 getBean() 提前实例化了业务 Bean，
 * 那这些 Bean 就不会经过 BPP 的 postProcessAfterInitialization，
 * 导致 AOP 代理、事务增强、@Async 等全部失效。
 *
 * 现象：控制台会输出 Spring 的 INFO 警告:
 *   "Bean 'xxx' is not eligible for getting processed by all BeanPostProcessors"
 */
public class EarlyGetBeanBfpp implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("[PITFALL] >>> BFPP 中过早 getBean('cartService')");
		// 危险：此时 BPP 还未注册，cartService 不会被 TimingBpp 包装
		Object cart = beanFactory.getBean("cartService");
		System.out.println("[PITFALL]     拿到的 cart 类型: " + cart.getClass().getSimpleName());
		System.out.println("[PITFALL]     ↑ 注意：这个实例不会经过 BPP 包装！");
	}
}
