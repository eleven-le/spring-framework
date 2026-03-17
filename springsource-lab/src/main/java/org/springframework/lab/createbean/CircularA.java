package org.springframework.lab.createbean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 场景 4: 循环依赖 — CircularA → CircularB → CircularA
 * 观察三级缓存如何解决 setter 注入的循环依赖。
 *
 * 断点:
 *   - DefaultSingletonBeanRegistry#getSingleton(beanName, true) → 观察从哪级缓存命中
 *   - AbstractAutowireCapableBeanFactory#doCreateBean:613 → addSingletonFactory (放入三级缓存)
 *   - AbstractAutowireCapableBeanFactory#getEarlyBeanReference → 观察 SmartIABPP 是否包装
 */
@Component
public class CircularA {

	@Autowired
	private CircularB circularB;

	public CircularA() {
		System.out.println("[CircularA]      构造器 (此时 circularB 还是 null)");
	}

	public String whoAmI() {
		return "I am A, my B is: " + circularB.getClass().getSimpleName();
	}
}
