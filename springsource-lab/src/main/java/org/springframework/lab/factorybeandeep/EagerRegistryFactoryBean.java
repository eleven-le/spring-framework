package org.springframework.lab.factorybeandeep;

import org.springframework.beans.factory.SmartFactoryBean;

/**
 * 【场景4: SmartFactoryBean#isEagerInit() — 启动期强制触发 getObject()】
 *
 * 默认 FactoryBean 是懒创建产物的:
 *   preInstantiateSingletons() 遇到 FactoryBean 时只调 getBean("&beanName") 创建工厂本身,
 *   产物要等第一次 getBean("beanName") 才触发 getObject()。
 *
 * SmartFactoryBean#isEagerInit()=true 改变这个行为:
 *   preInstantiateSingletons() 会额外调 getBean(beanName) (无 & 前缀), 强制创建产物。
 *
 * 源码路径 (DefaultListableBeanFactory#preInstantiateSingletons:993-1009):
 *   if (isFactoryBean(beanName)) {
 *       Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);  // 先创建工厂
 *       if (bean instanceof SmartFactoryBean && ((SmartFactoryBean<?>) bean).isEagerInit()) {
 *           getBean(beanName);  // ← 关键: 追加一次无 & 的 getBean, 触发 getObject()
 *       }
 *   }
 *
 * 真实场景: 服务注册中心客户端, 启动时必须注册到 Nacos/Eureka, 不能等到第一次请求才注册。
 */
public class EagerRegistryFactoryBean implements SmartFactoryBean<String> {

	@Override
	public String getObject() throws Exception {
		String endpoint = "nacos://10.0.0.1:8848/service-order";
		System.out.println("  [EagerRegistryFB] getObject() → 启动期立即注册到: " + endpoint);
		return endpoint;
	}

	@Override
	public Class<?> getObjectType() {
		return String.class;
	}

	@Override
	public boolean isEagerInit() {
		return true; // 关键: 在 preInstantiateSingletons() 期间强制 getBean(beanName)
	}
}
