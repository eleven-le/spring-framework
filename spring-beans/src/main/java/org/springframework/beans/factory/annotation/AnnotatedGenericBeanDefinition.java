/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.annotation;

import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Reader 注册时的专属图纸——GenericBD + 注解元信息的组合体！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition}</li>
 * <li><b>中文名</b>：带注解元信息的通用 Bean 定义 —— Reader 的"图纸产物"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 annotation 包（annotation 包 = Bean 级注解支持）</li>
 * <li><b>类层级</b>：{@code GenericBeanDefinition} 的子类 + 实现 {@code AnnotatedBeanDefinition} 接口</li>
 * </ul>
 *
 * <h3>💡 它和 ScannedGenericBeanDefinition 的区别</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比</th><th>AnnotatedGenericBeanDefinition（本类）</th><th>ScannedGenericBeanDefinition</th></tr>
 * <tr><td>产出方</td><td>AnnotatedBeanDefinitionReader（左膀）</td><td>ClassPathBeanDefinitionScanner（右臂）</td></tr>
 * <tr><td>场景</td><td>{@code ctx.register(AppConfig.class)}</td><td>{@code @ComponentScan} 扫描</td></tr>
 * <tr><td>元信息来源</td><td>StandardAnnotationMetadata（反射读取）</td><td>SimpleAnnotationMetadata（ASM 读取）</td></tr>
 * <tr><td>类加载</td><td>需要加载类到 JVM</td><td>不需要（ASM 直接读 .class）</td></tr>
 * </table>
 *
 * <h3>🧬 图纸家族定位</h3>
 * <pre>
 * GenericBeanDefinition
 * ├── AnnotatedGenericBeanDefinition  ← 👈 你在这里！（Reader 产出，反射获取注解元信息）
 * └── ScannedGenericBeanDefinition    （Scanner 产出，ASM 获取注解元信息）
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>AnnotatedGenericBeanDefinition 是 Reader 的专属产物——在 GenericBD 之上增加了
 * {@code AnnotationMetadata}（类级注解信息）和可选的 {@code MethodMetadata}（@Bean 工厂方法信息），
 * 让后续处理器能直接读取注解属性，而无需再次反射。</p>
 *
 * <hr/>
 * Extension of the {@link org.springframework.beans.factory.support.GenericBeanDefinition}
 * class, adding support for annotation metadata exposed through the
 * {@link AnnotatedBeanDefinition} interface.
 *
 * <p>This GenericBeanDefinition variant is mainly useful for testing code that expects
 * to operate on an AnnotatedBeanDefinition, for example strategy implementations
 * in Spring's component scanning support (where the default definition class is
 * {@link org.springframework.context.annotation.ScannedGenericBeanDefinition},
 * which also implements the AnnotatedBeanDefinition interface).
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 2.5
 * @see AnnotatedBeanDefinition#getMetadata()
 * @see org.springframework.core.type.StandardAnnotationMetadata
 */
@SuppressWarnings("serial")
public class AnnotatedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition {

	private final AnnotationMetadata metadata;

	@Nullable
	private MethodMetadata factoryMethodMetadata;


	/**
	 * Create a new AnnotatedGenericBeanDefinition for the given bean class.
	 * @param beanClass the loaded bean class
	 */
	public AnnotatedGenericBeanDefinition(Class<?> beanClass) {
		setBeanClass(beanClass);
		this.metadata = AnnotationMetadata.introspect(beanClass);
	}

	/**
	 * Create a new AnnotatedGenericBeanDefinition for the given annotation metadata,
	 * allowing for ASM-based processing and avoidance of early loading of the bean class.
	 * Note that this constructor is functionally equivalent to
	 * {@link org.springframework.context.annotation.ScannedGenericBeanDefinition
	 * ScannedGenericBeanDefinition}, however the semantics of the latter indicate that a
	 * bean was discovered specifically via component-scanning as opposed to other means.
	 * @param metadata the annotation metadata for the bean class in question
	 * @since 3.1.1
	 */
	public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata) {
		Assert.notNull(metadata, "AnnotationMetadata must not be null");
		if (metadata instanceof StandardAnnotationMetadata) {
			setBeanClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass());
		}
		else {
			setBeanClassName(metadata.getClassName());
		}
		this.metadata = metadata;
	}

	/**
	 * Create a new AnnotatedGenericBeanDefinition for the given annotation metadata,
	 * based on an annotated class and a factory method on that class.
	 * @param metadata the annotation metadata for the bean class in question
	 * @param factoryMethodMetadata metadata for the selected factory method
	 * @since 4.1.1
	 */
	public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata, MethodMetadata factoryMethodMetadata) {
		this(metadata);
		Assert.notNull(factoryMethodMetadata, "MethodMetadata must not be null");
		setFactoryMethodName(factoryMethodMetadata.getMethodName());
		this.factoryMethodMetadata = factoryMethodMetadata;
	}


	@Override
	public final AnnotationMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	@Nullable
	public final MethodMetadata getFactoryMethodMetadata() {
		return this.factoryMethodMetadata;
	}

}
