package org.springframework.lab.circulardep.scene1_setter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Scene 1: ServiceB 反向依赖 ServiceA。
 * populateBean(B) 触发 getBean("serviceA") 时, A 的早期引用从三级缓存取出。
 */
@Component
public class ServiceB {

	@Autowired
	private ServiceA serviceA;

	public ServiceB() {
		System.out.println("  [Scene1] ServiceB 构造 (serviceA == null)");
	}

	public String call() {
		return "B -> " + serviceA.getClass().getSimpleName();
	}
}
