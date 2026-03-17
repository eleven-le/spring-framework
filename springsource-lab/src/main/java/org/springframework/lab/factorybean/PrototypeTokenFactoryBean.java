package org.springframework.lab.factorybean;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.FactoryBean;

/**
 * 场景 2: 非单例 FactoryBean -- 每次 getObject() 生成新令牌
 *
 * isSingleton()=false 时, 产物不会被缓存到 factoryBeanObjectCache,
 * 每次 getBean("tokenGenerator") 都会触发 getObject()。
 * 但 FactoryBean 本身仍然是容器中的单例 bean。
 */
public class PrototypeTokenFactoryBean implements FactoryBean<String> {

	private final AtomicInteger counter = new AtomicInteger(0);

	public PrototypeTokenFactoryBean() {
		System.out.println("[PrototypeTokenFactoryBean] 构造 (仅一次)");
	}

	@Override
	public String getObject() throws Exception {
		int seq = counter.incrementAndGet();
		String token = "TKN-" + seq + "-" + UUID.randomUUID().toString().substring(0, 8);
		System.out.println("[PrototypeTokenFactoryBean] getObject() #" + seq + " → " + token);
		return token;
	}

	@Override
	public Class<?> getObjectType() {
		return String.class;
	}

	@Override
	public boolean isSingleton() {
		return false; // 每次都新建
	}
}
