package org.springframework.lab.factorybeandeep;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.SmartFactoryBean;

/**
 * 【场景3: SmartFactoryBean — isPrototype() 与 isSingleton() 的三态语义】
 *
 * 核心矛盾: isSingleton()=false 不等于 isPrototype()=true
 *
 *   | isSingleton | isPrototype | 含义                     | 缓存行为              |
 *   |-------------|-------------|--------------------------|----------------------|
 *   | true        | false       | 单例, 同一对象            | factoryBeanObjectCache |
 *   | false       | true        | 原型, 每次独立实例         | 不缓存                |
 *   | false       | false       | 作用域/非独立 (如 Session) | 不缓存, 但不保证独立    |
 *
 * 真实场景: Session 级别的购物车、请求级别的 TraceContext
 * 它们既不是单例(每个 session 不同), 也不是原型(同一 session 内共享)。
 *
 * SmartFactoryBean 的 isPrototype() 让框架能区分这种中间态,
 * 影响 getBeansOfType() 等查询是否触发 getObject()。
 *
 * 断点:
 *   SmartFactoryBean#isPrototype (本类) → 确认三态语义
 *   DefaultListableBeanFactory#preInstantiateSingletons:993-1009 → SmartFactoryBean 判断
 */
public class ScopedSessionFactoryBean implements SmartFactoryBean<String> {

	private final AtomicInteger callCount = new AtomicInteger(0);

	@Override
	public String getObject() throws Exception {
		int seq = callCount.incrementAndGet();
		String session = "SESSION-" + UUID.randomUUID().toString().substring(0, 8) + "-#" + seq;
		System.out.println("  [ScopedSessionFB] getObject() #" + seq + " → " + session);
		return session;
	}

	@Override
	public Class<?> getObjectType() {
		return String.class;
	}

	@Override
	public boolean isSingleton() {
		return false; // 不是单例
	}

	@Override
	public boolean isPrototype() {
		return false; // 也不是原型! → Session 作用域, 同一 session 内共享
	}

	@Override
	public boolean isEagerInit() {
		return false; // 不需要预初始化
	}

	public int getCallCount() {
		return callCount.get();
	}
}
