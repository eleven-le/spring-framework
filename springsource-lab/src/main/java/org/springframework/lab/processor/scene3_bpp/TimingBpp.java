package org.springframework.lab.processor.scene3_bpp;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lab.processor.CartService;
import org.springframework.stereotype.Component;

/**
 * Scene-3: BPP — 包装 Bean 实例（简化版 AOP）
 *
 * C端业务场景：对下单链路的关键 Service 统一加耗时监控。
 * 在 postProcessAfterInitialization 中把原始 Bean 替换成代理对象。
 * Spring AOP 的 AbstractAutoProxyCreator 就是这个套路。
 *
 * 断点：postProcessAfterInitialization 第一行
 * 对照源码：AbstractAutowireCapableBeanFactory#initializeBean → applyBeanPostProcessorsAfterInitialization
 */
@Component
public class TimingBpp implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		// 在 init 回调之前，可以注入属性、校验标记接口等
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof CartService) {
			System.out.println("[BPP]   >>> postProcessAfterInitialization: 包装 " + beanName + " 为计时代理");
			CartService original = (CartService) bean;
			// 用简单的 wrapper 代替 JDK Proxy（CartService 没有接口，不适合 JDK Proxy）
			return new CartServiceTimingWrapper(original);
		}
		return bean;
	}

	/**
	 * 计时包装器 — 模拟 AOP 织入
	 * 真实场景中 Spring 使用 CGLIB 子类代理
	 */
	public static class CartServiceTimingWrapper extends CartService {
		private final CartService delegate;

		public CartServiceTimingWrapper(CartService delegate) {
			this.delegate = delegate;
		}

		@Override
		public String addItem(String item) {
			long start = System.nanoTime();
			String result = delegate.addItem(item);
			long cost = (System.nanoTime() - start) / 1_000;
			System.out.println("[TIMING] " + "addItem cost " + cost + " us");
			return result;
		}
	}
}
