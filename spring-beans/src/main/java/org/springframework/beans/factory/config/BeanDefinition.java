/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Spring 的"图纸"接口——每一个 Bean 在容器中的元数据描述，一切的起点！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.BeanDefinition}</li>
 * <li><b>中文名</b>：Bean 定义 —— Bean 的"施工图纸"，描述"这个 Bean 长什么样、怎么造"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包（注意！config 包 = 框架内部配置契约！
 * 这个包定义了 Spring 容器运转所需的<b>核心配置接口</b>——BeanDefinition（图纸）、
 * BeanPostProcessor（质检员）、BeanFactoryPostProcessor（图纸审核员）、Scope（作用域策略）等。
 * 一句话：<b>config 包 = "容器怎么配置、怎么扩展"的契约中心！</b>）</li>
 * <li><b>接口层级</b>：继承 {@code AttributeAccessor}（通用属性存取）+ {@code BeanMetadataElement}（源信息追溯）</li>
 * </ul>
 *
 * <h3>💡 为什么需要 BeanDefinition？——"图纸"和"实物"必须分离！</h3>
 * <p>Spring 容器<b>不直接操作 Bean 实例</b>——它先把所有 Bean 的描述信息收集成"图纸"（BeanDefinition），
 * 然后在 refresh 的最后阶段才根据图纸批量"施工"（实例化 + 注入 + 初始化）。</p>
 * <p>这种"图纸先行"的设计带来了巨大的灵活性：</p>
 * <ul>
 * <li><b>BFPP 可以修改图纸</b>：在 Bean 创建之前，BeanFactoryPostProcessor 可以修改图纸上的属性
 * （如 PropertySourcesPlaceholderConfigurer 把 ${...} 替换为真实值）</li>
 * <li><b>BDRPP 可以新增图纸</b>：ConfigurationClassPostProcessor 在解析 @Configuration 后，
 * 向容器新增大量 BD——如果没有"图纸"这个中间层，就没法实现这种动态扩展</li>
 * <li><b>懒加载/条件化</b>：图纸上标记了 lazyInit=true 或 @Conditional 不满足时，可以跳过施工</li>
 * <li><b>作用域管理</b>：图纸上的 scope 字段决定了施工策略（singleton 只造一次 / prototype 每次新造）</li>
 * </ul>
 *
 * <h3>🧬 图纸上记录了什么？——BeanDefinition 的核心属性</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>属性</th><th>方法</th><th>含义</th></tr>
 * <tr><td>beanClassName</td><td>get/setBeanClassName</td><td>Bean 的全限定类名（或 null，由 FactoryMethod 决定）</td></tr>
 * <tr><td>scope</td><td>get/setScope</td><td>singleton / prototype / request / session 等</td></tr>
 * <tr><td>lazyInit</td><td>is/setLazyInit</td><td>是否懒加载（true = 第一次 getBean 时才创建）</td></tr>
 * <tr><td>dependsOn</td><td>get/setDependsOn</td><td>依赖的其他 Bean 名称（保证先初始化）</td></tr>
 * <tr><td>primary</td><td>is/setPrimary</td><td>是否为首选候选（@Primary）</td></tr>
 * <tr><td>factoryBeanName + factoryMethodName</td><td>get/set*</td><td>@Bean 方法的来源（哪个配置类的哪个方法）</td></tr>
 * <tr><td>constructorArgumentValues</td><td>get*</td><td>构造器参数（XML 或编程式配置）</td></tr>
 * <tr><td>propertyValues</td><td>get*</td><td>setter 注入的属性值</td></tr>
 * <tr><td>initMethodName / destroyMethodName</td><td>get/set*</td><td>初始化/销毁方法名</td></tr>
 * <tr><td>role</td><td>get/setRole</td><td>角色标识：APPLICATION(用户) / SUPPORT(框架辅助) / INFRASTRUCTURE(内部)</td></tr>
 * </table>
 *
 * <h3>🧬 继承体系——"图纸家族"</h3>
 * <pre>
 * BeanDefinition（接口）   ← 👈 你在这里！（图纸的标准契约）
 * └── AbstractBeanDefinition（抽象骨架：实现了所有通用属性的存取）
 *       ├── RootBeanDefinition（终态图纸：合并后的最终版，createBean 直接使用的）
 *       ├── GenericBeanDefinition（通用图纸：XML/注解解析后的原始 BD，可指定 parent）
 *       └── ChildBeanDefinition（子图纸：已废弃，被 GenericBeanDefinition 取代）
 *
 * 特殊变体（带注解元信息）：
 * ├── AnnotatedGenericBeanDefinition（Reader 注册时使用：ctx.register(AppConfig.class)）
 * └── ScannedGenericBeanDefinition（Scanner 扫描时使用：@ComponentScan 扫到的类）
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"元数据先行"的两阶段处理</b><br/>
 * Spring 把 Bean 的处理分为"收集图纸"和"按图施工"两个阶段，中间有一个"图纸审核"窗口期。<br/>
 * <b>业务借鉴</b>：订单系统可以先收集"订单草稿"（元数据），经过风控审核后再"确认下单"（实例化）。
 * 这比"边收集边执行"更安全、更灵活。</li>
 *
 * <li><b>role 字段的三级分类——区分"用户的"和"框架的"</b><br/>
 * ROLE_APPLICATION（用户业务 Bean）/ ROLE_SUPPORT（框架辅助）/ ROLE_INFRASTRUCTURE（纯内部）。
 * 这让工具（如 IDE、Actuator）可以过滤掉框架内部的 Bean，只展示用户关心的。<br/>
 * <b>业务借鉴</b>：你的配置项也应该分级——"用户可见配置" vs "系统内部配置"。</li>
 * </ol>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>BeanDefinition 的核心价值：<b>作为 Bean 的"施工图纸"，在实例化之前完整描述 Bean 的所有元数据，
 * 为 BFPP/BDRPP 的动态修改和扩展提供了操作窗口</b>。<br/>
 * 没有 BeanDefinition 这一层抽象，Spring 的 @Configuration 解析、属性占位符替换、条件化装配等核心特性都无从实现。
 * 它是 Spring IoC 架构的"第零号概念"——理解 BD，才能理解 Spring 为什么这样设计。</p>
 *
 * <hr/>
 * A BeanDefinition describes a bean instance, which has property values,
 * constructor argument values, and further information supplied by
 * concrete implementations.
 *
 * <p>This is just a minimal interface: The main intention is to allow a
 * {@link BeanFactoryPostProcessor} to introspect and modify property values
 * and other bean metadata.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 19.03.2004
 * @see ConfigurableListableBeanFactory#getBeanDefinition
 * @see org.springframework.beans.factory.support.RootBeanDefinition
 * @see org.springframework.beans.factory.support.ChildBeanDefinition
 */
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {

	/**
	 * Scope identifier for the standard singleton scope: {@value}.
	 * <p>Note that extended bean factories might support further scopes.
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_SINGLETON
	 */
	String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

	/**
	 * Scope identifier for the standard prototype scope: {@value}.
	 * <p>Note that extended bean factories might support further scopes.
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_PROTOTYPE
	 */
	String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;


	/**
	 * Role hint indicating that a {@code BeanDefinition} is a major part
	 * of the application. Typically corresponds to a user-defined bean.
	 */
	int ROLE_APPLICATION = 0;

	/**
	 * Role hint indicating that a {@code BeanDefinition} is a supporting
	 * part of some larger configuration, typically an outer
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 * {@code SUPPORT} beans are considered important enough to be aware
	 * of when looking more closely at a particular
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition},
	 * but not when looking at the overall configuration of an application.
	 */
	int ROLE_SUPPORT = 1;

	/**
	 * Role hint indicating that a {@code BeanDefinition} is providing an
	 * entirely background role and has no relevance to the end-user. This hint is
	 * used when registering beans that are completely part of the internal workings
	 * of a {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 */
	int ROLE_INFRASTRUCTURE = 2;


	// Modifiable attributes

	/**
	 * Set the name of the parent definition of this bean definition, if any.
	 */
	void setParentName(@Nullable String parentName);

	/**
	 * Return the name of the parent definition of this bean definition, if any.
	 */
	@Nullable
	String getParentName();

	/**
	 * Specify the bean class name of this bean definition.
	 * <p>The class name can be modified during bean factory post-processing,
	 * typically replacing the original class name with a parsed variant of it.
	 * @see #setParentName
	 * @see #setFactoryBeanName
	 * @see #setFactoryMethodName
	 */
	void setBeanClassName(@Nullable String beanClassName);

	/**
	 * Return the current bean class name of this bean definition.
	 * <p>Note that this does not have to be the actual class name used at runtime, in
	 * case of a child definition overriding/inheriting the class name from its parent.
	 * Also, this may just be the class that a factory method is called on, or it may
	 * even be empty in case of a factory bean reference that a method is called on.
	 * Hence, do <i>not</i> consider this to be the definitive bean type at runtime but
	 * rather only use it for parsing purposes at the individual bean definition level.
	 * @see #getParentName()
	 * @see #getFactoryBeanName()
	 * @see #getFactoryMethodName()
	 */
	@Nullable
	String getBeanClassName();

	/**
	 * Override the target scope of this bean, specifying a new scope name.
	 * @see #SCOPE_SINGLETON
	 * @see #SCOPE_PROTOTYPE
	 */
	void setScope(@Nullable String scope);

	/**
	 * Return the name of the current target scope for this bean,
	 * or {@code null} if not known yet.
	 */
	@Nullable
	String getScope();

	/**
	 * Set whether this bean should be lazily initialized.
	 * <p>If {@code false}, the bean will get instantiated on startup by bean
	 * factories that perform eager initialization of singletons.
	 */
	void setLazyInit(boolean lazyInit);

	/**
	 * Return whether this bean should be lazily initialized, i.e. not
	 * eagerly instantiated on startup. Only applicable to a singleton bean.
	 */
	boolean isLazyInit();

	/**
	 * Set the names of the beans that this bean depends on being initialized.
	 * The bean factory will guarantee that these beans get initialized first.
	 */
	void setDependsOn(@Nullable String... dependsOn);

	/**
	 * Return the bean names that this bean depends on.
	 */
	@Nullable
	String[] getDependsOn();

	/**
	 * Set whether this bean is a candidate for getting autowired into some other bean.
	 * <p>Note that this flag is designed to only affect type-based autowiring.
	 * It does not affect explicit references by name, which will get resolved even
	 * if the specified bean is not marked as an autowire candidate. As a consequence,
	 * autowiring by name will nevertheless inject a bean if the name matches.
	 */
	void setAutowireCandidate(boolean autowireCandidate);

	/**
	 * Return whether this bean is a candidate for getting autowired into some other bean.
	 */
	boolean isAutowireCandidate();

	/**
	 * Set whether this bean is a primary autowire candidate.
	 * <p>If this value is {@code true} for exactly one bean among multiple
	 * matching candidates, it will serve as a tie-breaker.
	 */
	void setPrimary(boolean primary);

	/**
	 * Return whether this bean is a primary autowire candidate.
	 */
	boolean isPrimary();

	/**
	 * Specify the factory bean to use, if any.
	 * This the name of the bean to call the specified factory method on.
	 * @see #setFactoryMethodName
	 */
	void setFactoryBeanName(@Nullable String factoryBeanName);

	/**
	 * Return the factory bean name, if any.
	 */
	@Nullable
	String getFactoryBeanName();

	/**
	 * Specify a factory method, if any. This method will be invoked with
	 * constructor arguments, or with no arguments if none are specified.
	 * The method will be invoked on the specified factory bean, if any,
	 * or otherwise as a static method on the local bean class.
	 * @see #setFactoryBeanName
	 * @see #setBeanClassName
	 */
	void setFactoryMethodName(@Nullable String factoryMethodName);

	/**
	 * Return a factory method, if any.
	 */
	@Nullable
	String getFactoryMethodName();

	/**
	 * Return the constructor argument values for this bean.
	 * <p>The returned instance can be modified during bean factory post-processing.
	 * @return the ConstructorArgumentValues object (never {@code null})
	 */
	ConstructorArgumentValues getConstructorArgumentValues();

	/**
	 * Return if there are constructor argument values defined for this bean.
	 * @since 5.0.2
	 */
	default boolean hasConstructorArgumentValues() {
		return !getConstructorArgumentValues().isEmpty();
	}

	/**
	 * Return the property values to be applied to a new instance of the bean.
	 * <p>The returned instance can be modified during bean factory post-processing.
	 * @return the MutablePropertyValues object (never {@code null})
	 */
	MutablePropertyValues getPropertyValues();

	/**
	 * Return if there are property values defined for this bean.
	 * @since 5.0.2
	 */
	default boolean hasPropertyValues() {
		return !getPropertyValues().isEmpty();
	}

	/**
	 * Set the name of the initializer method.
	 * @since 5.1
	 */
	void setInitMethodName(@Nullable String initMethodName);

	/**
	 * Return the name of the initializer method.
	 * @since 5.1
	 */
	@Nullable
	String getInitMethodName();

	/**
	 * Set the name of the destroy method.
	 * @since 5.1
	 */
	void setDestroyMethodName(@Nullable String destroyMethodName);

	/**
	 * Return the name of the destroy method.
	 * @since 5.1
	 */
	@Nullable
	String getDestroyMethodName();

	/**
	 * Set the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * @since 5.1
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	void setRole(int role);

	/**
	 * Get the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	int getRole();

	/**
	 * Set a human-readable description of this bean definition.
	 * @since 5.1
	 */
	void setDescription(@Nullable String description);

	/**
	 * Return a human-readable description of this bean definition.
	 */
	@Nullable
	String getDescription();


	// Read-only attributes

	/**
	 * Return a resolvable type for this bean definition,
	 * based on the bean class or other specific metadata.
	 * <p>This is typically fully resolved on a runtime-merged bean definition
	 * but not necessarily on a configuration-time definition instance.
	 * @return the resolvable type (potentially {@link ResolvableType#NONE})
	 * @since 5.2
	 * @see ConfigurableBeanFactory#getMergedBeanDefinition
	 */
	ResolvableType getResolvableType();

	/**
	 * Return whether this a <b>Singleton</b>, with a single, shared instance
	 * returned on all calls.
	 * @see #SCOPE_SINGLETON
	 */
	boolean isSingleton();

	/**
	 * Return whether this a <b>Prototype</b>, with an independent instance
	 * returned for each call.
	 * @since 3.0
	 * @see #SCOPE_PROTOTYPE
	 */
	boolean isPrototype();

	/**
	 * Return whether this bean is "abstract", that is, not meant to be instantiated.
	 */
	boolean isAbstract();

	/**
	 * Return a description of the resource that this bean definition
	 * came from (for the purpose of showing context in case of errors).
	 */
	@Nullable
	String getResourceDescription();

	/**
	 * Return the originating BeanDefinition, or {@code null} if none.
	 * <p>Allows for retrieving the decorated bean definition, if any.
	 * <p>Note that this method returns the immediate originator. Iterate through the
	 * originator chain to find the original BeanDefinition as defined by the user.
	 */
	@Nullable
	BeanDefinition getOriginatingBeanDefinition();

}
