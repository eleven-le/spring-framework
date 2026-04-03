package org.springframework.lab.factorybeandeep;

import org.springframework.beans.factory.FactoryBean;

/**
 * 【场景2补充: getObjectType() 返回 null 的后果】
 *
 * 当 getObjectType() 返回 null:
 *   - autowire 时被跳过, @Autowired 按类型找不到这个 FactoryBean 的产物
 *   - Spring 会尝试 getSingletonFactoryBeanForTypeCheck() 创建"快捷实例"来调 getObjectType()
 *   - 如果快捷实例的 getObjectType() 也返回 null, 则该 FactoryBean 的类型完全不可预测
 *
 * 教训: getObjectType() 必须尽早返回确定类型, 否则 @Autowired 会失败
 */
public class NullTypeFactoryBean implements FactoryBean<String> {

	@Override
	public String getObject() throws Exception {
		System.out.println("  [NullTypeFB] getObject()");
		return "I am invisible to autowire";
	}

	@Override
	public Class<?> getObjectType() {
		System.out.println("  [NullTypeFB] getObjectType() → null (故意返回 null)");
		return null; // 故意返回 null 演示 autowire 失败场景
	}
}
