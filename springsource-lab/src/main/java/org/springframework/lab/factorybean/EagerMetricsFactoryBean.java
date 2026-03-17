package org.springframework.lab.factorybean;

import org.springframework.beans.factory.SmartFactoryBean;

/**
 * 场景 3: SmartFactoryBean -- isEagerInit()=true 触发启动期提前创建产物
 *
 * 默认 FactoryBean 是懒加载的: 只在第一次 getBean() 时才调 getObject()。
 * SmartFactoryBean#isEagerInit()=true 会在 preInstantiateSingletons() 阶段
 * 主动调用 getBean(beanName) 来触发产物创建。
 *
 * 真实场景: 监控 SDK 需要启动时就建好连接, 而不是等第一次请求才初始化。
 */
public class EagerMetricsFactoryBean implements SmartFactoryBean<MetricsClient> {

	public EagerMetricsFactoryBean() {
		System.out.println("[EagerMetricsFactoryBean] 构造");
	}

	@Override
	public MetricsClient getObject() throws Exception {
		System.out.println("[EagerMetricsFactoryBean] getObject() → 创建 MetricsClient (启动期立刻执行)");
		return new MetricsClient();
	}

	@Override
	public Class<?> getObjectType() {
		return MetricsClient.class;
	}

	@Override
	public boolean isEagerInit() {
		return true; // 关键: 让 preInstantiateSingletons() 主动触发 getBean(beanName)
	}

	@Override
	public boolean isPrototype() {
		return false;
	}
}
