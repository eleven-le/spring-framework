package org.springframework.lab.scanner;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;

/**
 * 全限定名 BeanNameGenerator
 * 业务场景：多模块项目中不同包下有同名类（如 order.UserService 和 user.UserService），
 * 使用全限定名避免冲突
 *
 * 对应源码：FullyQualifiedAnnotationBeanNameGenerator（Spring 5.2.3+ 内置）
 * 断点：AnnotationBeanNameGenerator#generateBeanName:80
 */
public class FullyQualifiedBeanNameGenerator implements BeanNameGenerator {

	@Override
	public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
		// 直接使用全限定类名作为 beanName
		return definition.getBeanClassName();
	}
}
