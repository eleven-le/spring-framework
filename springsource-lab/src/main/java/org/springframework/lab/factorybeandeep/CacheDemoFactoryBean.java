package org.springframework.lab.factorybeandeep;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.FactoryBean;

/**
 * 【场景1: 产物缓存机制验证】
 *
 * 核心验证点:
 *   1. isSingleton()=true → getObject() 只调一次, 产物缓存到 factoryBeanObjectCache
 *   2. 第二次 getBean() 直接从 ConcurrentHashMap 取, 不走 synchronized 块
 *   3. postProcessObjectFromFactoryBean() 也只执行一次 (BPP 作用于产物)
 *
 * 断点:
 *   FactoryBeanRegistrySupport#getObjectFromFactoryBean:97  → 入口分叉: singleton vs 非singleton
 *   FactoryBeanRegistrySupport#getObjectFromFactoryBean:99  → 进入 synchronized(getSingletonMutex())
 *   FactoryBeanRegistrySupport#getObjectFromFactoryBean:127 → 缓存存入 factoryBeanObjectCache
 */
public class CacheDemoFactoryBean implements FactoryBean<Connection> {

	private final AtomicInteger callCount = new AtomicInteger(0);

	@Override
	public Connection getObject() throws Exception {
		int seq = callCount.incrementAndGet();
		System.out.println("  [CacheDemoFB] getObject() 第 " + seq + " 次被调用");
		return new Connection("jdbc:mysql://prod:3306/order");
	}

	@Override
	public Class<?> getObjectType() {
		return Connection.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	public int getCallCount() {
		return callCount.get();
	}
}
