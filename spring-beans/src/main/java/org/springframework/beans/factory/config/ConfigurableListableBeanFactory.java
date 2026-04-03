/*
 * Copyright 2002-2017 the original author or authors.
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

import java.util.Iterator;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BeanFactory 体系的"终极合体"——把所有子接口的能力一口气聚合到一个入口！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.ConfigurableListableBeanFactory}</li>
 * <li><b>中文名</b>：可配置的可枚举 Bean 工厂 —— 超级工厂的"全能遥控器"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包</li>
 * <li><b>接口层级</b>：同时继承 {@code ListableBeanFactory} + {@code AutowireCapableBeanFactory} + {@code ConfigurableBeanFactory}！</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个子接口？——ISP 拆分后，内部协调者需要一个"聚合点"！</h3>
 * <p>前面我们看到 Spring 把 BeanFactory 按 ISP 原则拆成了多个子接口，各司其职：</p>
 * <ul>
 * <li>ListableBeanFactory → 批量枚举</li>
 * <li>AutowireCapableBeanFactory → 自动装配能力输出</li>
 * <li>ConfigurableBeanFactory → 配置管理</li>
 * </ul>
 * <p>但有一个角色<b>确实需要同时使用所有这些能力</b>——就是 {@code AbstractApplicationContext.refresh()}！<br/>
 * refresh() 在执行过程中需要：</p>
 * <ol>
 * <li><b>配置工厂</b>（来自 ConfigurableBeanFactory）：注册 BPP、设置类加载器、注册 Scope</li>
 * <li><b>枚举 Bean</b>（来自 ListableBeanFactory）：找到所有 BeanFactoryPostProcessor、BeanPostProcessor</li>
 * <li><b>预实例化</b>（本接口独有！）：{@code preInstantiateSingletons()} 触发所有非 lazy Singleton 的创建</li>
 * <li><b>修改 BD</b>（本接口独有！）：{@code getBeanDefinition()} 让 BFPP 可以修改 BD</li>
 * <li><b>冻结配置</b>（本接口独有！）：{@code freezeConfiguration()} 锁定 BD 不再修改</li>
 * </ol>
 * <p>如果没有这个聚合接口，refresh() 就得拿到 3-4 个不同类型的引用，不断做类型转换——这既丑陋又脆弱。<br/>
 * ConfigurableListableBeanFactory 就是为这个场景设计的<b>内部门面（Internal Facade）</b>。</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"对外 ISP 拆分，对内 Facade 聚合"——接口设计的正反两面</b><br/>
 * Spring 对外（面向应用代码）严格遵循 ISP——你只用 BeanFactory 或 ListableBeanFactory 就够了。<br/>
 * 但对内（框架协调层）提供了一个聚合入口——ConfigurableListableBeanFactory 汇聚所有能力。<br/>
 * <b>这不是违反 ISP，而是 ISP 的完备实践</b>：拆分是为了"消费者的简单"，聚合是为了"协调者的便利"。<br/>
 * <b>业务借鉴</b>：你的微服务对外暴露精简的 API（ISP），但内部的编排层/协调层可以有一个聚合接口，
 * 避免到处做类型转换和接口适配。比如订单编排层可以有一个 {@code OrderOrchestratorContext} 聚合
 * 库存/支付/物流/通知等多个能力接口，让编排逻辑清爽地写在一处。</li>
 *
 * <li><b>"阶段锁定"设计——freezeConfiguration 的智慧</b><br/>
 * 本接口有一对独有方法：{@code freezeConfiguration()} + {@code isConfigurationFrozen()}。<br/>
 * 含义：在 refresh() 末尾，所有 BFPP 都执行完了，BD 不再变化，此时"冻结"配置，
 * 让后续的 Bean 创建可以放心缓存元数据（不用担心被修改）。<br/>
 * 这是<b>"阶段门控"模式</b>——系统有明确的阶段划分：配置阶段（可改）→ 冻结 → 运行阶段（只读）。<br/>
 * <b>业务借鉴</b>：规则引擎/定价策略等"配置型"系统，应该有"编辑模式"和"生效模式"的阶段划分。
 * 一旦"发布生效"，配置就被冻结——运行时只读，保证一致性和性能（可以激进缓存）。</li>
 *
 * <li><b>preInstantiateSingletons——"饿汉式预热"的终极入口</b><br/>
 * 这是 refresh() 第 11 步 {@code finishBeanFactoryInitialization()} 的核心调用点。<br/>
 * 它遍历所有非 lazy-init 的 Singleton BD，逐个触发 getBean() 完成创建。<br/>
 * 这保证了：<b>容器启动完成后，所有 Singleton 都已就绪，不存在"首次访问才创建"的延迟</b>。<br/>
 * 这是"快速失败"哲学的体现——配置错误在启动时就暴露，而不是运行时才爆炸。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * BeanFactory ─────────────────────────────────┐
 * ├── ListableBeanFactory ─────────────────────┤
 * ├── HierarchicalBeanFactory                  │
 * │     └── ConfigurableBeanFactory ───────────┤
 * │           + SingletonBeanRegistry          │
 * └── AutowireCapableBeanFactory ──────────────┤
 *                                              ▼
 *                         ConfigurableListableBeanFactory ← 👈 你在这里！终极合体！
 *                                              │
 *                                              ▼
 *                         DefaultListableBeanFactory（唯一实现！）
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>本接口自身只有约 10 个方法（大量能力来自继承），划分为 <b>四大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>成员</th></tr>
 * <tr><td><b>🛡️ 第一战区：装配规则管控</b></td><td>控制"什么该注入/什么不该注入"</td><td>ignoreDependencyType, ignoreDependencyInterface, registerResolvableDependency, isAutowireCandidate</td></tr>
 * <tr><td><b>📋 第二战区：BD 直接访问</b></td><td>获取原始 BD / 遍历所有 Bean 名</td><td>getBeanDefinition, getBeanNamesIterator</td></tr>
 * <tr><td><b>❄️ 第三战区：配置冻结与缓存</b></td><td>冻结 BD / 清理缓存</td><td>clearMetadataCache, freezeConfiguration, isConfigurationFrozen</td></tr>
 * <tr><td><b>🚀 第四战区：预实例化</b></td><td>refresh() 的终极一击！</td><td>preInstantiateSingletons</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>ConfigurableListableBeanFactory 是 BeanFactory 体系的<b>内部终极入口</b>。<br/>
 * 它不是给应用代码用的——它是给 {@code AbstractApplicationContext.refresh()} 用的"全能遥控器"。<br/>
 * 通过这一个接口，refresh() 可以完成：配置工厂 → 修改BD → 冻结配置 → 预实例化 的完整流程。<br/>
 * <b>DefaultListableBeanFactory 是这个接口的唯一实现</b>——所有能力最终在这一个类中汇聚成真正运转的引擎。</p>
 *
 * <hr/>
 * Configuration interface to be implemented by most listable bean factories.
 * In addition to {@link ConfigurableBeanFactory}, it provides facilities to
 * analyze and modify bean definitions, and to pre-instantiate singletons.
 *
 * <p>This subinterface of {@link org.springframework.beans.factory.BeanFactory}
 * is not meant to be used in normal application code: Stick to
 * {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * use cases. This interface is just meant to allow for framework-internal
 * plug'n'play even when needing access to bean factory configuration methods.
 *
 * @author Juergen Hoeller
 * @since 03.11.2003
 * @see org.springframework.context.support.AbstractApplicationContext#getBeanFactory()
 */
public interface ConfigurableListableBeanFactory
		extends ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {

	/* =======================================================================================================
	          🛡️ 第一战区：装配规则管控 —— 控制"什么该注入、什么不该注入"
	   =======================================================================================================*/

	/**
	 * <h3>🛡️ 方法 1：void ignoreDependencyType(Class&lt;?&gt; type)</h3>
	 * <p><b>🏭【黑名单——这种类型的依赖不要自动注入！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 告诉容器：当做自动装配时，如果某个属性的类型是这里指定的类型，直接跳过，不要注入。<br/>
	 * 比如 {@code ignoreDependencyType(String.class)}——setter 参数是 String 的统统不要自动注入。<br/>
	 * 默认没有任何忽略类型。这是一个全局"黑名单"机制。</p>
	 * <hr/>
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 * @param type the dependency type to ignore
	 */
	void ignoreDependencyType(Class<?> type);

	/**
	 * <h3>🛡️ 方法 2：void ignoreDependencyInterface(Class&lt;?&gt; ifc)</h3>
	 * <p><b>🏭【Aware 接口的自动注入屏蔽——这些接口由容器亲自回调，不走自动装配！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 告诉容器：实现了指定接口的 Bean，该接口定义的 setter 方法<b>不要自动装配</b>。<br/>
	 * <b>典型场景：</b>BeanFactoryAware 定义了 {@code setBeanFactory(BeanFactory)}，
	 * 如果不忽略，自动装配会试图从容器里找 BeanFactory 类型的 Bean 注入——但这应该由容器在
	 * Aware 回调阶段亲自注入，不是通过自动装配！<br/>
	 * {@code AbstractApplicationContext.prepareBeanFactory()} 中会调用此方法忽略：
	 * BeanFactoryAware、ApplicationContextAware、ResourceLoaderAware 等。<br/>
	 * 然后通过 {@code registerResolvableDependency()} 把这些类型的正确实例注册为"可解析依赖"。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "所有实现了 BeanFactoryAware 接口的机器，setBeanFactory 这个插口不要自动装配！"<br/>
	 * "为什么？因为这个插口由我（容器）亲自来接线，不需要自动装配系统越俎代庖！"</blockquote>
	 * <hr/>
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @param ifc the dependency interface to ignore
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	void ignoreDependencyInterface(Class<?> ifc);

	/**
	 * <h3>🛡️ 方法 3：void registerResolvableDependency(Class&lt;?&gt;, Object)</h3>
	 * <p><b>🏭【特殊通道注册——这些"不是 Bean 的东西"也能被 @Autowired 注入！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 注册一个"特殊可解析依赖"——当有人 @Autowired 指定类型时，返回这里注册的对象，而不是去 Bean 容器里找。<br/>
	 * <b>经典用途：</b>{@code prepareBeanFactory()} 中注册了：
	 * <ul>
	 * <li>{@code BeanFactory.class → beanFactory 实例}</li>
	 * <li>{@code ApplicationContext.class → applicationContext 实例}</li>
	 * <li>{@code ResourceLoader.class → applicationContext 实例}</li>
	 * <li>{@code ApplicationEventPublisher.class → applicationContext 实例}</li>
	 * </ul>
	 * 这就是为什么你可以 {@code @Autowired ApplicationContext ctx} 而 ApplicationContext 并不是一个普通 Bean！<br/>
	 * 它是通过这个"特殊通道"注册的，绕过了正常的 Bean 查找流程。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 正常的 @Autowired 流程：去仓库（BeanDefinition 注册表）里按类型找 Bean。<br/>
	 * 但有些东西不在仓库里——比如工厂本身（BeanFactory）、应用上下文（ApplicationContext）。<br/>
	 * 它们通过这个"VIP 通道"直接注册："有人要 ApplicationContext 类型的？直接给他这个实例，不用去仓库找！"</blockquote>
	 * <hr/>
	 * Register a special dependency type with corresponding autowired value.
	 * <p>This is intended for factory/context references that are supposed
	 * to be autowirable but are not defined as beans in the factory:
	 * e.g. a dependency of type ApplicationContext resolved to the
	 * ApplicationContext instance that the bean is living in.
	 * <p>Note: There are no such default types registered in a plain BeanFactory,
	 * not even for the BeanFactory interface itself.
	 * @param dependencyType the dependency type to register. This will typically
	 * be a base interface such as BeanFactory, with extensions of it resolved
	 * as well if declared as an autowiring dependency (e.g. ListableBeanFactory),
	 * as long as the given value actually implements the extended interface.
	 * @param autowiredValue the corresponding autowired value. This may also be an
	 * implementation of the {@link org.springframework.beans.factory.ObjectFactory}
	 * interface, which allows for lazy resolution of the actual target value.
	 */
	void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue);

	/**
	 * <h3>🛡️ 方法 4：boolean isAutowireCandidate(String, DependencyDescriptor)</h3>
	 * <p><b>🏭【资格审查——这个 Bean 有没有资格被自动注入到别人身上？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 判断指定 Bean 是否可以作为某个注入点的自动装配候选者。检查逻辑包括：<br/>
	 * ① BD 中的 autowire-candidate 属性（XML 中 {@code autowire-candidate="false"} 可以排除）<br/>
	 * ② @Qualifier 匹配<br/>
	 * ③ 泛型匹配<br/>
	 * 也检查父工厂。这是 resolveDependency 内部的核心筛选逻辑之一。</p>
	 * <hr/>
	 * Determine whether the specified bean qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * <p>This method checks ancestor factories as well.
	 * @param beanName the name of the bean to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @return whether the bean should be considered as autowire candidate
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 */
	boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
			throws NoSuchBeanDefinitionException;

	/* =======================================================================================================
	          📋 第二战区：BD 直接访问 —— 获取原始 BD（可修改！） / 遍历所有 Bean 名
	   =======================================================================================================*/

	/**
	 * <h3>📋 方法 5：BeanDefinition getBeanDefinition(String beanName)</h3>
	 * <p><b>🏭【拿到原始图纸——而且你可以改它！这就是 BFPP 的权力来源！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回指定 Bean 的<b>原始 BeanDefinition 对象</b>——不是副本，是原件！你可以直接修改它！<br/>
	 * 与 ConfigurableBeanFactory 的 {@code getMergedBeanDefinition()} 的区别：<br/>
	 * - getMergedBeanDefinition → 返回合并后的只读视图（RootBeanDefinition）<br/>
	 * - getBeanDefinition → 返回原始注册的 BD 对象，可修改！<br/>
	 * <b>这就是 BeanFactoryPostProcessor 能修改 BD 的底层 API！</b><br/>
	 * 比如 {@code PropertySourcesPlaceholderConfigurer} 通过这个方法拿到 BD，
	 * 把 {@code ${db.url}} 替换为实际值。<br/>
	 * 只查本厂，不查父工厂。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * getMergedBeanDefinition = "给我看看最终合并后的图纸长什么样"（只读参考）<br/>
	 * getBeanDefinition = "把原始图纸给我，我要改它！"（可写！BFPP 的权力！）<br/>
	 * BFPP 拿到原始图纸后，可以改 class、改 scope、改属性值、改构造器参数……无所不能！</blockquote>
	 * <hr/>
	 * Return the registered BeanDefinition for the specified bean, allowing access
	 * to its property values and constructor argument value (which can be
	 * modified during bean factory post-processing).
	 * <p>A returned BeanDefinition object should not be a copy but the original
	 * definition object as registered in the factory. This means that it should
	 * be castable to a more specific implementation type, if necessary.
	 * <p><b>NOTE:</b> This method does <i>not</i> consider ancestor factories.
	 * It is only meant for accessing local bean definitions of this factory.
	 * @param beanName the name of the bean
	 * @return the registered BeanDefinition
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * defined in this factory
	 */
	BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>📋 方法 6：Iterator&lt;String&gt; getBeanNamesIterator()</h3>
	 * <p><b>🏭【全量花名册迭代器——BD 名 + 手动注册的 Singleton 名，一网打尽！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回本工厂管理的<b>所有 Bean 名称</b>的迭代器，包含：<br/>
	 * ① BeanDefinition 注册的名称（排在前面）<br/>
	 * ② 通过 registerSingleton 手动注册的名称（排在后面）<br/>
	 * 与 getBeanDefinitionNames() 的区别：那个只看 BD，这个还包含手动注册的。</p>
	 * <hr/>
	 * Return a unified view over all bean names managed by this factory.
	 * <p>Includes bean definition names as well as names of manually registered
	 * singleton instances, with bean definition names consistently coming first,
	 * analogous to how type/annotation specific retrieval of bean names works.
	 * @return the composite iterator for the bean names view
	 * @since 4.1.2
	 * @see #containsBeanDefinition
	 * @see #registerSingleton
	 * @see #getBeanNamesForType
	 * @see #getBeanNamesForAnnotation
	 */
	Iterator<String> getBeanNamesIterator();

	/* =======================================================================================================
	          ❄️ 第三战区：配置冻结与缓存 —— "配置阶段"结束，进入"运行阶段"的门控机制
	   =======================================================================================================*/

	/**
	 * <h3>❄️ 方法 7：void clearMetadataCache()</h3>
	 * <p><b>🏭【清理元数据缓存——BFPP 改了图纸后，旧缓存得刷新！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 清理合并 BD 的缓存。当 BFPP 修改了原始 BD 后，之前缓存的合并 BD 就过期了，需要清理。<br/>
	 * 已经创建的 Bean 的元数据会保留（不能影响已运行的实例）。<br/>
	 * 在 {@code PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors()} 执行完 BFPP 后被调用。</p>
	 * <hr/>
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * e.g. after applying a {@link BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 * @see #getBeanDefinition
	 * @see #getMergedBeanDefinition
	 */
	void clearMetadataCache();

	/**
	 * <h3>❄️ 方法 8：void freezeConfiguration()</h3>
	 * <p><b>🏭【冻结！从此刻起，图纸不再修改！进入运行态！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 冻结所有 BD——宣告"配置阶段结束"，此后 BD 不再被修改或后处理。<br/>
	 * 冻结后，工厂可以激进地缓存 BD 元数据（因为知道不会再变了），提升运行时性能。<br/>
	 * 在 {@code AbstractApplicationContext.finishBeanFactoryInitialization()} 中，
	 * preInstantiateSingletons() 之前被调用——先冻结，再批量创建。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "所有改图纸的人（BFPP）都改完了吗？改完了！好，从现在起图纸全部封存！任何人不得再改！"<br/>
	 * 冻结后工厂放心大胆地把图纸信息缓存到各种快速查找结构中——因为知道不会再变了。<br/>
	 * 这就像生产线正式开工前的"设计冻结"——设计阶段结束，进入生产阶段。</blockquote>
	 * <hr/>
	 * Freeze all bean definitions, signalling that the registered bean definitions
	 * will not be modified or post-processed any further.
	 * <p>This allows the factory to aggressively cache bean definition metadata.
	 */
	void freezeConfiguration();

	/**
	 * <h3>❄️ 方法 9：boolean isConfigurationFrozen()</h3>
	 * <p>查询当前配置是否已冻结。</p>
	 * <hr/>
	 * Return whether this factory's bean definitions are frozen,
	 * i.e. are not supposed to be modified or post-processed any further.
	 * @return {@code true} if the factory's configuration is considered frozen
	 */
	boolean isConfigurationFrozen();

	/* =======================================================================================================
	          🚀 第四战区：预实例化 —— refresh() 的终极一击！容器启动的"最后一脚油门"！
	   =======================================================================================================*/

	/**
	 * <h3>🚀 方法 10：void preInstantiateSingletons()</h3>
	 * <p><b>🏭【全面开机！——批量创建所有非 lazy-init 的 Singleton！容器启动的高潮！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 遍历所有注册的 BeanDefinition，对所有非 lazy-init 的 Singleton 逐个调用 {@code getBean(beanName)} 触发创建。<br/>
	 * 对于 FactoryBean，还会检查 isEagerInit 决定是否提前创建其产品。<br/>
	 * 创建完成后，还会检查是否实现了 {@code SmartInitializingSingleton} 接口，调用其 {@code afterSingletonsInstantiated()} 回调。<br/>
	 * <b>这是 refresh() 第 11 步 finishBeanFactoryInitialization() 的核心！</b></p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "所有图纸都冻结了，质检员都就位了，现在——全面开机！"<br/>
	 * 工厂拿着花名册，一个一个念名字："userService！" → getBean → 造好了！<br/>
	 * "orderService！" → getBean → 造好了！<br/>
	 * "dataSource！" → getBean → 造好了！<br/>
	 * ……一直到所有非 lazy 的 Singleton 全部造完。<br/>
	 * 如果中间有任何一台机器造失败了？报错！但已经造好的不会被回滚——<br/>
	 * 需要调 destroySingletons() 来手动清理"半成品"。<br/>
	 * <b>这就是为什么 Spring 启动时配置错误会立刻报错——快速失败！</b></blockquote>
	 * <p><b>【Spring 内部调用链】</b><br/>
	 * {@code AbstractApplicationContext.refresh()}<br/>
	 * → 第 11 步 {@code finishBeanFactoryInitialization(beanFactory)}<br/>
	 * → {@code beanFactory.preInstantiateSingletons()}<br/>
	 * → 循环所有 BD，逐个 {@code getBean(beanName)}</p>
	 * <hr/>
	 * Ensure that all non-lazy-init singletons are instantiated, also considering
	 * {@link org.springframework.beans.factory.FactoryBean FactoryBeans}.
	 * Typically invoked at the end of factory setup, if desired.
	 * @throws BeansException if one of the singleton beans could not be created.
	 * Note: This may have left the factory with some beans already initialized!
	 * Call {@link #destroySingletons()} for full cleanup in this case.
	 * @see #destroySingletons()
	 */
	void preInstantiateSingletons() throws BeansException;

}
