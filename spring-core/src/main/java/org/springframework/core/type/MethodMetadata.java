/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.core.type;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>方法的"体检报告"——不加载类就能读取方法的名字、返回类型、修饰符和注解！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.MethodMetadata}</li>
 * <li><b>中文名</b>：方法元数据接口 —— 方法的"身份证"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type} 包
 *     <br/>（{@code type} 包 = 类型元数据抽象层，方法元数据是其中"方法维度"的契约）</li>
 * <li><b>接口层级</b>：继承 {@link AnnotatedTypeMetadata}，<b>7 个抽象方法</b>——方法结构信息 + 注解信息</li>
 * </ul>
 *
 * <h3>💡 为什么需要 MethodMetadata？</h3>
 * <p>Spring 处理 @Bean 方法时，需要知道方法的返回类型（作为 Bean 类型）、方法名（作为 Bean 名称）、
 * 是否是 static（决定是否需要配置类实例）等信息。<br/>
 * 如果每次都通过反射获取 Method 对象，就必须先加载类——但在扫描阶段类还没加载。<br/>
 * MethodMetadata 让 Spring 在 ASM 路径下也能获取方法信息，<b>与 ClassMetadata 配合实现"全程不加载"</b>。</p>
 *
 * <h3>🧬 继承体系</h3>
 * <pre>
 *              AnnotatedTypeMetadata           ← 注解读取能力
 *               ↗               ↘
 *      AnnotationMetadata      MethodMetadata        ← 你在这里！方法维度的元数据
 *                                ↗          ↘
 *           StandardMethodMetadata      SimpleMethodMetadata
 *            (反射路径：Method 对象)     (ASM 路径：字节码解析)
 * </pre>
 *
 * <h3>⚡ 关键使用场景</h3>
 * <ul>
 * <li>@Bean 方法处理：ConfigurationClassBeanDefinitionReader 读取 getReturnTypeName() 作为 BD 的 beanClassName</li>
 * <li>@Bean 方法静态判断：isStatic() 决定 @Bean 方法是否需要配置类实例（static @Bean 不需要）</li>
 * <li>@EventListener 方法发现：getAnnotatedMethods("EventListener") 返回 MethodMetadata 集合</li>
 * </ul>
 *
 * <hr></>
 *
 * Interface that defines abstract access to the annotations of a specific
 * method, in a form that does not require that method's class to be loaded yet.
 *
 * @author Juergen Hoeller
 * @author Mark Pollack
 * @author Chris Beams
 * @author Phillip Webb
 * @since 3.0
 * @see StandardMethodMetadata
 * @see AnnotationMetadata#getAnnotatedMethods
 * @see AnnotatedTypeMetadata
 */
public interface MethodMetadata extends AnnotatedTypeMetadata {

	// =====================================================================================
	// 一、方法基础身份信息
	// =====================================================================================

	/**
	 * 【方法名】如 "createOrderService"。
	 * <p>@Bean 方法的名字默认就是 Bean 的名字（除非 @Bean(name="xxx") 显式指定）。
	 * <hr>
	 *
	 * Get the name of the underlying method.
	 */
	String getMethodName();

	/**
	 * 【声明该方法的类的全限定名】
	 * <p>例如 "com.example.AppConfig"。用于定位方法所属的配置类。
	 * <hr>
	 *
	 * Get the fully-qualified name of the class that declares the underlying method.
	 */
	String getDeclaringClassName();

	/**
	 * 【方法返回类型的全限定名】
	 * <p>例如 "com.example.OrderService"。
	 * <p><b>关键用途</b>：@Bean 方法的返回类型就是 Bean 的类型——
	 * ConfigurationClassBeanDefinitionReader 用它设置 BD 的 beanClassName。
	 * <hr>
	 *
	 * Get the fully-qualified name of the underlying method's declared return type.
	 * @since 4.2
	 */
	String getReturnTypeName();

	// =====================================================================================
	// 二、修饰符信息——决定方法的代理和覆写行为
	// =====================================================================================

	/**
	 * 【是否是抽象方法？】
	 * <p>抽象方法在接口或抽象类中声明但没有实现体。
	 * <hr>
	 *
	 * Determine whether the underlying method is effectively abstract:
	 * i.e. marked as abstract in a class or declared as a regular,
	 * non-default method in an interface.
	 * @since 4.2
	 */
	boolean isAbstract();

	/**
	 * 【是否是静态方法？】
	 * <p><b>对 @Bean 方法很重要</b>：static @Bean 方法不需要配置类实例就能调用，
	 * 可以避免配置类的过早初始化——在处理 BFPP 类型的 @Bean 时推荐用 static。
	 * <hr>
	 *
	 * Determine whether the underlying method is declared as 'static'.
	 */
	boolean isStatic();

	/**
	 * 【是否是 final 方法？】
	 * <p>final 方法不能被子类覆写——CGLIB 代理无法拦截 final 方法。
	 * 如果 @Configuration 类的 @Bean 方法是 final 的，CGLIB 无法代理它，
	 * @Bean 方法间的相互调用不会走代理（即不会保证单例语义）。
	 * <hr>
	 *
	 * Determine whether the underlying method is marked as 'final'.
	 */
	boolean isFinal();

	/**
	 * 【是否可被覆写？】= 不是 static，不是 final，不是 private。
	 * <p>可覆写意味着 CGLIB 能拦截这个方法——这是 @Configuration 的 @Bean 方法保证单例语义的前提。
	 * <hr>
	 *
	 * Determine whether the underlying method is overridable,
	 * i.e. not marked as static, final, or private.
	 */
	boolean isOverridable();

}
