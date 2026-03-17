package org.springframework.lab.circulardep.scene5_lazy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Scene 5: @Lazy 打破构造器循环依赖 — 注入的是 CGLIB 代理, 不触发真实创建。
 *
 * 原理: @Lazy 使 AutowiredAnnotationBeanPostProcessor 注入一个 lazy-resolution proxy,
 * 这个代理在 getBean 时不会真正创建目标 Bean, 而是延迟到第一次方法调用时才触发。
 * 因此构造器阶段不会真正递归创建 B, 也就不会触发循环。
 *
 * 等价于注入了一个 TargetSource 为 BeanFactory.getBean() 的 ProxyFactory 产物。
 */
@Component
public class LazyServiceA {

	private final LazyServiceB b;

	@Autowired
	public LazyServiceA(@Lazy LazyServiceB b) {
		System.out.println("  [Scene5] LazyServiceA 构造 — 注入的 b 是 " + b.getClass().getSimpleName());
		this.b = b;
	}

	public String call() {
		// 这里才会真正触发 LazyServiceB 的创建
		return "LazyA -> " + b.call();
	}
}
