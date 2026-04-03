package org.springframework.lab.factorybeandeep;

import org.springframework.beans.factory.FactoryBean;

/**
 * 【场景2: 类型预测 — 泛型推导】
 *
 * Spring 在 autowire 时需要知道 FactoryBean 产物类型, 但不想提前调 getObject()。
 * 类型预测降级链 (AbstractAutowireCapableBeanFactory#getTypeForFactoryBean):
 *
 *   1️⃣ BeanDefinition 上的 OBJECT_TYPE_ATTRIBUTE 属性
 *   2️⃣ InstanceSupplier 的泛型提取
 *   3️⃣ @Bean 工厂方法返回类型的泛型推导 (getTypeForFactoryBeanFromMethod)
 *   4️⃣ allowInit=true 时创建"快捷实例" → 调 getObjectType()
 *   5️⃣ FactoryBean<T> 类声明上的泛型 T
 *   6️⃣ 全部失败 → ResolvableType.NONE
 *
 * 本类通过声明 FactoryBean<Connection>, Spring 可以从泛型 T=Connection 推断产物类型,
 * 在 @Autowired Connection conn 时不需要实例化 FactoryBean 就能匹配。
 *
 * 断点:
 *   AbstractAutowireCapableBeanFactory#getTypeForFactoryBean:848 → 整个降级链入口
 *   AbstractAutowireCapableBeanFactory#getFactoryBeanGeneric:933  → 泛型提取
 */
public class TypePredictionFactoryBean implements FactoryBean<Connection> {

	private boolean objectTypeCallTracked = false;

	@Override
	public Connection getObject() throws Exception {
		System.out.println("  [TypePredictionFB] getObject() 被调用");
		return new Connection("jdbc:mysql://prod:3306/user");
	}

	@Override
	public Class<?> getObjectType() {
		objectTypeCallTracked = true;
		System.out.println("  [TypePredictionFB] getObjectType() 被调用 → Connection.class");
		return Connection.class;
	}

	public boolean isObjectTypeCallTracked() {
		return objectTypeCallTracked;
	}
}
