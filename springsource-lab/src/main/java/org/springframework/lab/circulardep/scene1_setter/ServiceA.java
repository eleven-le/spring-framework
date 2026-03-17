package org.springframework.lab.circulardep.scene1_setter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Scene 1: Setter/@Autowired 字段注入循环依赖 — 三级缓存可救。
 *
 * 流程:
 *   1. getBean("serviceA") → createBeanInstance → 空壳 A 放入三级缓存 singletonFactories
 *   2. populateBean(A) → 发现依赖 B → getBean("serviceB")
 *   3. createBeanInstance(B) → 空壳 B 放入三级缓存
 *   4. populateBean(B) → 发现依赖 A → getSingleton("serviceA", true)
 *   5. 命中三级缓存 → ObjectFactory.getObject() → 拿到 A 的早期引用
 *   6. B 注入完成 → initializeBean(B) → B 完成 → 回到 A 的 populateBean
 *   7. A 注入 B → initializeBean(A) → A 完成
 *
 * 断点: DefaultSingletonBeanRegistry#getSingleton(beanName, true) 行 195
 */
@Component
public class ServiceA {

	@Autowired
	private ServiceB serviceB;

	public ServiceA() {
		System.out.println("  [Scene1] ServiceA 构造 (serviceB == null)");
	}

	public String call() {
		return "A -> " + serviceB.getClass().getSimpleName();
	}
}
