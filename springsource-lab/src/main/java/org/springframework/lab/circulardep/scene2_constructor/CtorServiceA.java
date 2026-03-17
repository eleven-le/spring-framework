package org.springframework.lab.circulardep.scene2_constructor;

import org.springframework.stereotype.Component;

/**
 * Scene 2: 构造器注入循环依赖 — 三级缓存救不了。
 *
 * 原因: 构造器注入发生在 createBeanInstance 阶段,
 * 此时 A 还没走到 addSingletonFactory(放入三级缓存) 那一步,
 * 所以 B 回头找 A 时, 三级缓存里根本没有 A 的工厂。
 *
 * 异常: BeanCurrentlyInCreationException
 *
 * 断点: DefaultSingletonBeanRegistry#beforeSingletonCreation 行 353
 *       → singletonsCurrentlyInCreation.add(beanName) 返回 false 时抛异常
 */
@Component
public class CtorServiceA {

	private final CtorServiceB b;

	public CtorServiceA(CtorServiceB b) {
		System.out.println("  [Scene2] CtorServiceA 构造 — 需要 B, 但 B 也需要 A → 死锁");
		this.b = b;
	}
}
