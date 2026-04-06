/*
 * Copyright 2002-2014 the original author or authors.
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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>BD 与 AnnotationMetadata 的"桥梁"——让 BD 能直接暴露类的注解信息！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.annotation.AnnotatedBeanDefinition}</li>
 * <li><b>中文名</b>：带注解的 Bean 定义 —— BD 的"注解升级版"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 {@code annotation} 包
 *     <br/>（{@code annotation} 包 = 注解驱动的 Bean 工厂扩展）</li>
 * <li><b>接口层级</b>：继承 {@link BeanDefinition}，增加 2 个方法</li>
 * </ul>
 *
 * <h3>💡 为什么 BD 需要持有 AnnotationMetadata？</h3>
 * <p>普通 BeanDefinition 只有 beanClassName（String）——知道类名但不知道注解信息。<br/>
 * 但注解驱动场景下，很多后续处理需要读取注解：</p>
 * <ul>
 * <li>ConfigurationClassPostProcessor 需要判断 BD 是否是 @Configuration</li>
 * <li>@Lazy/@Primary/@DependsOn 的属性需要从注解读取后设置到 BD 上</li>
 * <li>@Bean 方法的 MethodMetadata 需要保留（知道哪个方法产生了这个 BD）</li>
 * </ul>
 * <p>AnnotatedBeanDefinition 通过持有 AnnotationMetadata，让后续处理器<b>不需要重新解析类</b>。</p>
 *
 * <h3>🧬 实现类</h3>
 * <ul>
 * <li>AnnotatedGenericBeanDefinition —— @Import 导入的类、手动 register 的类</li>
 * <li>ScannedGenericBeanDefinition —— @ComponentScan 扫描出来的类（持有 ASM 路径的 AnnotationMetadata）</li>
 * <li>ConfigurationClassBeanDefinition —— @Bean 方法产生的 BD（持有 MethodMetadata）</li>
 * </ul>
 * <hr>
 *
 * Extended {@link org.springframework.beans.factory.config.BeanDefinition}
 * interface that exposes {@link org.springframework.core.type.AnnotationMetadata}
 * about its bean class - without requiring the class to be loaded yet.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see AnnotatedGenericBeanDefinition
 * @see org.springframework.core.type.AnnotationMetadata
 */
public interface AnnotatedBeanDefinition extends BeanDefinition {

	/**
	 * 【类的注解元数据】包含类结构信息 + 注解信息。
	 * <p>ScannedGenericBeanDefinition 持有的是 SimpleAnnotationMetadata（ASM 路径），
	 * AnnotatedGenericBeanDefinition 持有的是 StandardAnnotationMetadata（反射路径）。
	 * <p>调用者不需要关心是哪条路径——统一用 AnnotationMetadata 接口。
	 * <hr>
	 *
	 * Obtain the annotation metadata (as well as basic class metadata)
	 * for this bean definition's bean class.
	 * @return the annotation metadata object (never {@code null})
	 */
	AnnotationMetadata getMetadata();

	/**
	 * 【工厂方法的元数据】如果这个 BD 是由 @Bean 方法产生的，返回该方法的元数据。
	 * <p>包含方法名（Bean 名称）、返回类型（Bean 类型）、方法注解属性等。
	 * <p>如果不是 @Bean 方法产生的 BD，返回 null。
	 * <hr>
	 *
	 * Obtain metadata for this bean definition's factory method, if any.
	 * @return the factory method metadata, or {@code null} if none
	 * @since 4.1.1
	 */
	@Nullable
	MethodMetadata getFactoryMethodMetadata();

}
