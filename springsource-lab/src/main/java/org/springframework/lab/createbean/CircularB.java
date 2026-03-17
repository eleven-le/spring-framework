package org.springframework.lab.createbean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 场景 4: 循环依赖 — CircularB 反向依赖 CircularA。
 * 当 populateBean(B) 触发 getBean(A) 时，A 的早期引用从三级缓存中取出。
 */
@Component
public class CircularB {

	@Autowired
	private CircularA circularA;

	public CircularB() {
		System.out.println("[CircularB]      构造器 (此时 circularA 还是 null)");
	}

	public String whoAmI() {
		return "I am B, my A is: " + circularA.getClass().getSimpleName();
	}
}
