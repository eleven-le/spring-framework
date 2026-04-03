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

import java.beans.PropertyEditor;
import java.security.AccessControlContext;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BeanFactory 的"配置/管理扩展"——把工厂从"只读服务窗口"升级为"可配置的管理后台"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.ConfigurableBeanFactory}</li>
 * <li><b>中文名</b>：可配置的 Bean 工厂 —— 超级工厂的"运维管理后台"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包</li>
 * <li><b>接口层级</b>：继承 {@code HierarchicalBeanFactory}（拥有父子层级能力）+ {@code SingletonBeanRegistry}（拥有单例注册能力）</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个子接口？—— "使用工厂" 和 "配置工厂" 必须分开！</h3>
 * <p>回顾 BeanFactory——它是"总服务窗口"，所有方法都是"读"操作：getBean（提货）、containsBean（查户口）、getType（查型号）。<br/>
 * 但一座工厂不可能只被使用而不被配置！配置工厂需要：</p>
 * <ul>
 * <li>设置父工厂（组织关系管理）</li>
 * <li>注册 BeanPostProcessor（安装质检员）</li>
 * <li>注册 Scope（定义新的作用域）</li>
 * <li>设置类加载器、类型转换器、表达式解析器</li>
 * <li>管理别名、管理依赖关系、管理销毁</li>
 * </ul>
 * <p>这些"写"操作如果和"读"操作混在同一个接口里，会导致两个灾难性后果：</p>
 * <ol>
 * <li><b>权限泄露</b>：每个拿到 BeanFactory 引用的人都能修改工厂配置——任何一个 Bean 都能往容器里塞 BPP、改类加载器！</li>
 * <li><b>契约膨胀</b>：BeanFactory 的实现类必须同时实现"读+写"所有方法，即使有的场景只需要读（比如只读代理）。</li>
 * </ol>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>CQRS（命令-查询分离）的接口级实践</b><br/>
 * BeanFactory = Query 接口（只读，面向客户端）<br/>
 * ConfigurableBeanFactory = Command 接口（可写，面向框架内部）<br/>
 * 这是<b>接口级 CQRS</b>！把"读"和"写"分到不同接口，让不同角色持有不同接口：<br/>
 * - 普通 Bean：只拿到 BeanFactory 引用（只能提货，不能改工厂）<br/>
 * - 框架内部 / BeanFactoryPostProcessor：拿到 ConfigurableBeanFactory（能配置工厂）<br/>
 * <b>业务借鉴</b>：你的服务接口设计中，"消费者 API"和"管理员 API"必须是两个不同的接口！
 * 比如 {@code OrderService}（查单/下单，给前端调）和 {@code OrderAdminService}（改配置/改规则/批量操作，给运营后台调）。
 * 千万不要把 "修改定价策略" 的方法和 "查询订单" 的方法放在同一个接口里！</li>
 *
 * <li><b>"Configurable" 前缀范式——Spring 的命名即设计</b><br/>
 * Spring 中凡是带 "Configurable" 前缀的接口，都遵循同一模式：<br/>
 * - {@code BeanFactory}（读） → {@code ConfigurableBeanFactory}（读+写）<br/>
 * - {@code ListableBeanFactory}（读） → {@code ConfigurableListableBeanFactory}（读+写）<br/>
 * - {@code ApplicationContext}（读） → {@code ConfigurableApplicationContext}（读+写）<br/>
 * - {@code Environment}（读） → {@code ConfigurableEnvironment}（读+写）<br/>
 * 这种一致的命名范式让你一看到 "Configurable" 就知道：<b>这是内部管理接口，普通代码不该用！</b><br/>
 * <b>业务借鉴</b>：为你的接口建立统一的命名范式——比如 XxxService（业务接口）、XxxAdminService（管理接口）、
 * XxxInternalService（内部接口）。命名即文档，命名即设计意图。</li>
 *
 * <li><b>多继承组合——同时拥有"层级"和"注册"两种基础能力</b><br/>
 * ConfigurableBeanFactory 同时继承了 HierarchicalBeanFactory 和 SingletonBeanRegistry：<br/>
 * - HierarchicalBeanFactory → 能设置/获取父工厂<br/>
 * - SingletonBeanRegistry → 能手动注册/查询 Singleton 实例<br/>
 * 这种多继承组合让 ConfigurableBeanFactory 成为了"管理后台"的完备基座——
 * 它既能管理组织关系（父子层级），又能管理核心资产（Singleton 注册表）。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * BeanFactory
 * └── HierarchicalBeanFactory（父子层级）
 *       └── ConfigurableBeanFactory  ← 👈 你在这里！（可配置 = 管理后台）
 *             │    + SingletonBeanRegistry（单例注册）
 *             └── ConfigurableListableBeanFactory（终极合体）
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>2 个常量 + 约 30 个方法，划分为 <b>七大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>核心成员</th></tr>
 * <tr><td><b>🏢 第一战区：工厂架构配置</b></td><td>父工厂/类加载器/元数据缓存</td><td>setParentBeanFactory, setBeanClassLoader, setCacheBeanMetadata</td></tr>
 * <tr><td><b>🔧 第二战区：类型转换与表达式</b></td><td>值解析/类型转换基础设施</td><td>setBeanExpressionResolver, setConversionService, addPropertyEditorRegistrar, setTypeConverter, addEmbeddedValueResolver</td></tr>
 * <tr><td><b>👮 第三战区：BPP 管理</b></td><td>注册/查询 Bean 后处理器</td><td>addBeanPostProcessor, getBeanPostProcessorCount</td></tr>
 * <tr><td><b>🎭 第四战区：Scope 管理</b></td><td>注册/查询自定义作用域</td><td>registerScope, getRegisteredScopeNames, getRegisteredScope</td></tr>
 * <tr><td><b>📋 第五战区：BD与别名管理</b></td><td>合并BD/别名注册/FactoryBean判断</td><td>getMergedBeanDefinition, registerAlias, resolveAliases, isFactoryBean</td></tr>
 * <tr><td><b>🔗 第六战区：依赖关系与创建状态</b></td><td>注册/查询Bean间依赖/创建状态</td><td>registerDependentBean, getDependentBeans, getDependenciesForBean, isCurrentlyInCreation</td></tr>
 * <tr><td><b>💀 第七战区：销毁</b></td><td>Bean销毁/全量Singleton销毁</td><td>destroyBean, destroyScopedBean, destroySingletons</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>ConfigurableBeanFactory 的核心价值：<b>为工厂提供完整的"管理面"API，与 BeanFactory 的"客户面"API 形成读写分离</b>。<br/>
 * 它是 Spring IoC 容器的"运维控制台"——通过这个接口，框架内部可以安装 BPP、注册 Scope、设置父容器、管理别名、
 * 控制销毁……而这些危险操作对普通应用代码完全不可见。<br/>
 * 这种"面向不同角色暴露不同接口"的设计哲学，是构建大型框架/平台时最值得借鉴的模式之一。</p>
 *
 * <hr/>
 * Configuration interface to be implemented by most bean factories. Provides
 * facilities to configure a bean factory, in addition to the bean factory
 * client methods in the {@link org.springframework.beans.factory.BeanFactory}
 * interface.
 *
 * <p>This bean factory interface is not meant to be used in normal application
 * code: Stick to {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * needs. This extended interface is just meant to allow for framework-internal
 * plug'n'play and for special access to bean factory configuration methods.
 *
 * @author Juergen Hoeller
 * @since 03.11.2003
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.ListableBeanFactory
 * @see ConfigurableListableBeanFactory
 */
public interface ConfigurableBeanFactory extends HierarchicalBeanFactory, SingletonBeanRegistry {

	/* =======================================================================================================
	          🏢 第一战区：工厂架构配置 —— 父工厂/类加载器/元数据缓存
	   =======================================================================================================*/

	/**
	 * <h3>🏢 常量：SCOPE_SINGLETON / SCOPE_PROTOTYPE</h3>
	 * <p>两个内置 Scope 的标识符。自定义 Scope（如 request/session）通过 {@code registerScope()} 注册。</p>
	 * <hr/>
	 * Scope identifier for the standard singleton scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * @see #registerScope
	 */
	String SCOPE_SINGLETON = "singleton";

	/**
	 * Scope identifier for the standard prototype scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * @see #registerScope
	 */
	String SCOPE_PROTOTYPE = "prototype";



	/**
	 * <h3>🏢 方法 1：void setParentBeanFactory(BeanFactory parentBeanFactory)</h3>
	 * <p><b>🏭【给工厂认爹——设置父工厂！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * HierarchicalBeanFactory 只有 getParentBeanFactory()（读），这里补上了 set（写）——
	 * 体现了<b>"读接口给客户端，写接口给管理员"</b>的读写分离设计。<br/>
	 * 一旦设置，不可更改——抛 IllegalStateException。这是<b>不可变设计</b>的体现：
	 * 父子关系是容器拓扑的基础，运行时改变会导致不可预期的行为。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 分厂开业时需要认一次总公司（父工厂）——一旦认了就不能换！<br/>
	 * 总公司的货（Bean）分厂可以委托提取，但分厂的货总公司看不到。</blockquote>
	 * <hr/>
	 * Set the parent of this bean factory.
	 * <p>Note that the parent cannot be changed: It should only be set outside
	 * a constructor if it isn't available at the time of factory instantiation.
	 * @param parentBeanFactory the parent BeanFactory
	 * @throws IllegalStateException if this factory is already associated with
	 * a parent BeanFactory
	 * @see #getParentBeanFactory()
	 */
	void setParentBeanFactory(BeanFactory parentBeanFactory) throws IllegalStateException;

	/**
	 * Set the class loader to use for loading bean classes.
	 * Default is the thread context class loader.
	 * <p>Note that this class loader will only apply to bean definitions
	 * that do not carry a resolved bean class yet. This is the case as of
	 * Spring 2.0 by default: Bean definitions only carry bean class names,
	 * to be resolved once the factory processes the bean definition.
	 * @param beanClassLoader the class loader to use,
	 * or {@code null} to suggest the default class loader
	 */
	void setBeanClassLoader(@Nullable ClassLoader beanClassLoader);

	/**
	 * Return this factory's class loader for loading bean classes
	 * (only {@code null} if even the system ClassLoader isn't accessible).
	 * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
	 */
	@Nullable
	ClassLoader getBeanClassLoader();

	/**
	 * Specify a temporary ClassLoader to use for type matching purposes.
	 * Default is none, simply using the standard bean ClassLoader.
	 * <p>A temporary ClassLoader is usually just specified if
	 * <i>load-time weaving</i> is involved, to make sure that actual bean
	 * classes are loaded as lazily as possible. The temporary loader is
	 * then removed once the BeanFactory completes its bootstrap phase.
	 * @since 2.5
	 */
	void setTempClassLoader(@Nullable ClassLoader tempClassLoader);

	/**
	 * Return the temporary ClassLoader to use for type matching purposes,
	 * if any.
	 * @since 2.5
	 */
	@Nullable
	ClassLoader getTempClassLoader();

	/**
	 * Set whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes. Default is on.
	 * <p>Turn this flag off to enable hot-refreshing of bean definition objects
	 * and in particular bean classes. If this flag is off, any creation of a bean
	 * instance will re-query the bean class loader for newly resolved classes.
	 */
	void setCacheBeanMetadata(boolean cacheBeanMetadata);

	/**
	 * Return whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes.
	 */
	boolean isCacheBeanMetadata();

	/* =======================================================================================================
	          🔧 第二战区：类型转换与表达式 —— 值解析/类型转换基础设施
	   =======================================================================================================*/

	/**
	 * <h3>🔧 SpEL 表达式解析器</h3>
	 * <p>设置 #{...} 表达式的解析策略。BeanFactory 默认无表达式支持！<br/>
	 * ApplicationContext 会在 prepareBeanFactory() 中设置 StandardBeanExpressionResolver。</p>
	 * <hr/>
	 * Specify the resolution strategy for expressions in bean definition values.
	 * <p>There is no expression support active in a BeanFactory by default.
	 * An ApplicationContext will typically set a standard expression strategy
	 * here, supporting "#{...}" expressions in a Unified EL compatible style.
	 * @since 3.0
	 */
	void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver);

	/**
	 * Return the resolution strategy for expressions in bean definition values.
	 * @since 3.0
	 */
	@Nullable
	BeanExpressionResolver getBeanExpressionResolver();

	/**
	 * Specify a Spring 3.0 ConversionService to use for converting
	 * property values, as an alternative to JavaBeans PropertyEditors.
	 * @since 3.0
	 */
	void setConversionService(@Nullable ConversionService conversionService);

	/**
	 * Return the associated ConversionService, if any.
	 * @since 3.0
	 */
	@Nullable
	ConversionService getConversionService();

	/**
	 * Add a PropertyEditorRegistrar to be applied to all bean creation processes.
	 * <p>Such a registrar creates new PropertyEditor instances and registers them
	 * on the given registry, fresh for each bean creation attempt. This avoids
	 * the need for synchronization on custom editors; hence, it is generally
	 * preferable to use this method instead of {@link #registerCustomEditor}.
	 * @param registrar the PropertyEditorRegistrar to register
	 */
	void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar);

	/**
	 * Register the given custom property editor for all properties of the
	 * given type. To be invoked during factory configuration.
	 * <p>Note that this method will register a shared custom editor instance;
	 * access to that instance will be synchronized for thread-safety. It is
	 * generally preferable to use {@link #addPropertyEditorRegistrar} instead
	 * of this method, to avoid for the need for synchronization on custom editors.
	 * @param requiredType type of the property
	 * @param propertyEditorClass the {@link PropertyEditor} class to register
	 */
	void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass);

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	void copyRegisteredEditorsTo(PropertyEditorRegistry registry);

	/**
	 * Set a custom type converter that this BeanFactory should use for converting
	 * bean property values, constructor argument values, etc.
	 * <p>This will override the default PropertyEditor mechanism and hence make
	 * any custom editors or custom editor registrars irrelevant.
	 * @since 2.5
	 * @see #addPropertyEditorRegistrar
	 * @see #registerCustomEditor
	 */
	void setTypeConverter(TypeConverter typeConverter);

	/**
	 * Obtain a type converter as used by this BeanFactory. This may be a fresh
	 * instance for each call, since TypeConverters are usually <i>not</i> thread-safe.
	 * <p>If the default PropertyEditor mechanism is active, the returned
	 * TypeConverter will be aware of all custom editors that have been registered.
	 * @since 2.5
	 */
	TypeConverter getTypeConverter();

	/**
	 * Add a String resolver for embedded values such as annotation attributes.
	 * @param valueResolver the String resolver to apply to embedded values
	 * @since 3.0
	 */
	void addEmbeddedValueResolver(StringValueResolver valueResolver);

	/**
	 * Determine whether an embedded value resolver has been registered with this
	 * bean factory, to be applied through {@link #resolveEmbeddedValue(String)}.
	 * @since 4.3
	 */
	boolean hasEmbeddedValueResolver();

	/**
	 * Resolve the given embedded value, e.g. an annotation attribute.
	 * @param value the value to resolve
	 * @return the resolved value (may be the original value as-is)
	 * @since 3.0
	 */
	@Nullable
	String resolveEmbeddedValue(String value);

	/* =======================================================================================================
	          👮 第三战区：BPP 管理 —— 注册/查询 Bean 后处理器（质检员管理！）
	   =======================================================================================================*/

	/**
	 * <h3>👮 addBeanPostProcessor —— 往流水线上安装一个质检员！</h3>
	 * <p><b>【硬核释义】</b><br/>
	 * 注册一个 BPP，之后本工厂创建的所有 Bean 都会经过这个 BPP 的检查。<br/>
	 * 关键细节：<b>通过这里编程式注册的 BPP，按注册顺序执行，Ordered 接口无效！</b><br/>
	 * ApplicationContext 中自动发现的 BPP（作为 Bean 注册的）永远排在编程式注册的后面。<br/>
	 * <b>这就是为什么 Spring 内部 BPP 的执行顺序有时反直觉——注册方式决定优先级！</b></p>
	 * <hr/>
	 * Add a new BeanPostProcessor that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * <p>Note: Post-processors submitted here will be applied in the order of
	 * registration; any ordering semantics expressed through implementing the
	 * {@link org.springframework.core.Ordered} interface will be ignored. Note
	 * that autodetected post-processors (e.g. as beans in an ApplicationContext)
	 * will always be applied after programmatically registered ones.
	 * @param beanPostProcessor the post-processor to register
	 */
	void addBeanPostProcessor(BeanPostProcessor beanPostProcessor);

	/**
	 * Return the current number of registered BeanPostProcessors, if any.
	 */
	int getBeanPostProcessorCount();

	/* =======================================================================================================
	          🎭 第四战区：Scope 管理 —— 注册/查询自定义作用域（singleton/prototype 之外的天地！）
	   =======================================================================================================*/

	/**
	 * <h3>🎭 registerScope —— 注册自定义作用域</h3>
	 * <p>Singleton 和 Prototype 是内置的，但 request/session/自定义 scope 需要通过这里注册。<br/>
	 * Web 环境下，WebApplicationContextUtils 会自动注册 request/session/application scope。</p>
	 * <hr/>
	 * Register the given scope, backed by the given Scope implementation.
	 * @param scopeName the scope identifier
	 * @param scope the backing Scope implementation
	 */
	void registerScope(String scopeName, Scope scope);

	/**
	 * Return the names of all currently registered scopes.
	 * <p>This will only return the names of explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * @return the array of scope names, or an empty array if none
	 * @see #registerScope
	 */
	String[] getRegisteredScopeNames();

	/**
	 * Return the Scope implementation for the given scope name, if any.
	 * <p>This will only return explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * @param scopeName the name of the scope
	 * @return the registered Scope implementation, or {@code null} if none
	 * @see #registerScope
	 */
	@Nullable
	Scope getRegisteredScope(String scopeName);

	/**
	 * Set the {@code ApplicationStartup} for this bean factory.
	 * <p>This allows the application context to record metrics during application startup.
	 * @param applicationStartup the new application startup
	 * @since 5.3
	 */
	void setApplicationStartup(ApplicationStartup applicationStartup);

	/**
	 * Return the {@code ApplicationStartup} for this bean factory.
	 * @since 5.3
	 */
	ApplicationStartup getApplicationStartup();

	/**
	 * Provides a security access control context relevant to this factory.
	 * @return the applicable AccessControlContext (never {@code null})
	 * @since 3.0
	 */
	AccessControlContext getAccessControlContext();

	/* =======================================================================================================
	          📋 第五战区：BD与别名管理 —— 合并BD/别名注册/FactoryBean判断
	   =======================================================================================================*/

	/**
	 * <h3>📋 copyConfigurationFrom —— 克隆工厂配置（不含 BD！）</h3>
	 * <p>复制另一个工厂的配置（BPP、Scope、类加载器等），但不复制 BeanDefinition。<br/>
	 * 用途：创建子工厂时，继承父工厂的基础设施配置。</p>
	 * <hr/>
	 * Copy all relevant configuration from the given other factory.
	 * <p>Should include all standard configuration settings as well as
	 * BeanPostProcessors, Scopes, and factory-specific internal settings.
	 * Should not include any metadata of actual bean definitions,
	 * such as BeanDefinition objects and bean name aliases.
	 * @param otherFactory the other BeanFactory to copy from
	 */
	void copyConfigurationFrom(ConfigurableBeanFactory otherFactory);

	/**
	 * Given a bean name, create an alias. We typically use this method to
	 * support names that are illegal within XML ids (used for bean names).
	 * <p>Typically invoked during factory configuration, but can also be
	 * used for runtime registration of aliases. Therefore, a factory
	 * implementation should synchronize alias access.
	 * @param beanName the canonical name of the target bean
	 * @param alias the alias to be registered for the bean
	 * @throws BeanDefinitionStoreException if the alias is already in use
	 */
	void registerAlias(String beanName, String alias) throws BeanDefinitionStoreException;

	/**
	 * Resolve all alias target names and aliases registered in this
	 * factory, applying the given StringValueResolver to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * @param valueResolver the StringValueResolver to apply
	 * @since 2.5
	 */
	void resolveAliases(StringValueResolver valueResolver);

	/**
	 * <h3>📋 getMergedBeanDefinition —— 获取合并后的 BD（核心方法！）</h3>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回合并了父 BD 的最终 BeanDefinition。Spring 的 BD 支持"继承"——子 BD 可以 override 父 BD 的属性。<br/>
	 * 这个方法返回的是合并后的完整视图（RootBeanDefinition），是 createBean 流程真正使用的 BD。<br/>
	 * <b>这是理解 Spring BD 合并机制的入口——doGetBean 的第一步就是 getMergedLocalBeanDefinition！</b></p>
	 * <hr/>
	 * Return a merged BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * Considers bean definitions in ancestor factories as well.
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) BeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean definition with the given name
	 * @since 2.5
	 */
	BeanDefinition getMergedBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * Determine whether the bean with the given name is a FactoryBean.
	 * @param name the name of the bean to check
	 * @return whether the bean is a FactoryBean
	 * ({@code false} means the bean exists but is not a FactoryBean)
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.5
	 */
	boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException;

	/* =======================================================================================================
	          🔗 第六战区：依赖关系与创建状态 —— 循环依赖检测/依赖图管理/销毁顺序编排的基础！
	   =======================================================================================================*/

	/**
	 * <h3>�� setCurrentlyInCreation / isCurrentlyInCreation —— 创建状态标记</h3>
	 * <p><b>这就是循环依赖检测的基础设施！</b>Bean A 开始创建时标记 inCreation=true，
	 * 如果创建 A 的过程中又触发了 A 的创建，isCurrentlyInCreation("A") 返回 true → 检测到循环依赖！</p>
	 * <hr/>
	 * Explicitly control the current in-creation status of the specified bean.
	 * For container-internal use only.
	 * @param beanName the name of the bean
	 * @param inCreation whether the bean is currently in creation
	 * @since 3.1
	 */
	void setCurrentlyInCreation(String beanName, boolean inCreation);

	/**
	 * Determine whether the specified bean is currently in creation.
	 * @param beanName the name of the bean
	 * @return whether the bean is currently in creation
	 * @since 2.5
	 */
	boolean isCurrentlyInCreation(String beanName);

	/**
	 * <h3>🔗 registerDependentBean —— 注册 Bean 之间的依赖关系（影响销毁顺序！）</h3>
	 * <p><b>【硬核释义】</b><br/>
	 * 声明 dependentBeanName 依赖于 beanName——销毁时，dependentBeanName 会在 beanName 之前被销毁。<br/>
	 * 这就是容器关闭时"先销毁下游，再销毁上游"的依赖感知销毁顺序的基础！<br/>
	 * 内部维护了两个 Map：dependentBeanMap（谁依赖我）和 dependenciesForBeanMap（我依赖谁）。</p>
	 * <hr/>
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 * @since 2.5
	 */
	void registerDependentBean(String beanName, String dependentBeanName);

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 * @since 2.5
	 */
	String[] getDependentBeans(String beanName);

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 * @since 2.5
	 */
	String[] getDependenciesForBean(String beanName);

	/* =======================================================================================================
	          💀 第七战区：销毁 —— Bean 销毁/全量 Singleton 销毁（容器关闭的最后一步���）
	   =======================================================================================================*/

	/**
	 * <h3>💀 destroyBean —— 按 BD 规定的方式销毁单个 Bean</h3>
	 * <p>通常用于 Prototype Bean 的手动销毁——因为容器不负责 Prototype 的销毁！<br/>
	 * 销毁异常会被捕获记录，不会向上抛。这是<b>容错降级</b>设计——销毁阶段不能因为一个 Bean 爆炸就中断整个关停流程。</p>
	 * <hr/>
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to its bean definition.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * @param beanName the name of the bean definition
	 * @param beanInstance the bean instance to destroy
	 */
	void destroyBean(String beanName, Object beanInstance);

	/**
	 * Destroy the specified scoped bean in the current target scope, if any.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * @param beanName the name of the scoped bean
	 */
	void destroyScopedBean(String beanName);

	/**
	 * Destroy all singleton beans in this factory, including inner beans that have
	 * been registered as disposable. To be called on shutdown of a factory.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 */
	void destroySingletons();

}
