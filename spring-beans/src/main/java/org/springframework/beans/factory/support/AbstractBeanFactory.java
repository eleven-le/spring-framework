/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.beans.factory.support;

import java.beans.PropertyEditor;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>抽象 Bean 工厂——继承链的"第四层"，整个 getBean 流程的总编排师！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.AbstractBeanFactory}</li>
 * <li><b>中文名</b>：抽象 Bean 工厂 —— 模板方法模式的教科书范例，定义"怎么提货"的死骨架</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块</li>
 * <li><b>类层级</b>：{@code FactoryBeanRegistrySupport} 的子类，实现 {@code ConfigurableBeanFactory} 接口</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个抽象类？——"流程骨架"与"具体创建"必须解耦！</h3>
 * <p>Spring 架构师面对一个经典的框架设计问题：<b>getBean 的流程是确定的，但"从哪里拿图纸"和"怎么造 Bean"是变化的</b>。</p>
 * <ul>
 * <li><b>确定的流程（本类实现）</b>：名字标准化 → 查缓存 → 检测循环依赖 → 委托父容器 → 合并 BeanDefinition
 *     → 处理 @DependsOn → 按 Scope 分发（Singleton/Prototype/自定义）→ 类型适配 → 返回</li>
 * <li><b>变化的两个钩子（子类实现）</b>：
 *     <ul>
 *     <li>{@code getBeanDefinition(beanName)} —— "图纸从哪来？" 可以从 Map 里查（DefaultListableBeanFactory），
 *         也可以从远程配置中心/数据库查（自定义实现）</li>
 *     <li>{@code createBean(beanName, mbd, args)} —— "怎么造 Bean？" 推断构造器、反射实例化、依赖注入、
 *         初始化回调、AOP 代理——这些细节全在 AbstractAutowireCapableBeanFactory 中</li>
 *     </ul>
 * </li>
 * </ul>
 * <p>这正是<b>模板方法模式</b>的完美应用：AbstractBeanFactory 是"导演"，规定了拍电影的流程（分镜脚本），
 * 但"演员怎么表演"（createBean）和"剧本从哪来"（getBeanDefinition）交给子类决定。</p>
 *
 * <h3>🧬 这个类的核心职责矩阵</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>职责领域</th><th>关键方法</th><th>设计思想</th></tr>
 * <tr><td>🫀 提货总入口</td><td>{@code doGetBean()}</td><td>模板方法：编排从缓存到创建的完整流程</td></tr>
 * <tr><td>🔄 父子委派</td><td>{@code getParentBeanFactory() + doGetBean}</td><td>双亲委派：本厂没图纸就甩给父厂</td></tr>
 * <tr><td>📋 图纸合并</td><td>{@code getMergedLocalBeanDefinition()}</td><td>配置复用：child BD 继承 parent BD 的公共属性</td></tr>
 * <tr><td>🏗️ FactoryBean 拆盒</td><td>{@code getObjectForBeanInstance()}</td><td>透明代理：用户无感知地获取 FactoryBean 产物</td></tr>
 * <tr><td>🔧 配置管理</td><td>{@code addBeanPostProcessor/setParentBeanFactory/registerScope}</td><td>ConfigurableBeanFactory SPI 实现</td></tr>
 * <tr><td>🔒 类型转换</td><td>{@code ConversionService/PropertyEditor}</td><td>可插拔的类型转换体系</td></tr>
 * <tr><td>📝 BPP 管理</td><td>{@code beanPostProcessors + BeanPostProcessorCache}</td><td>质检员注册与分类缓存</td></tr>
 * </table>
 *
 * <h3>🧬 业务借鉴——你的系统能偷师什么？</h3>
 * <ol>
 * <li><b>"模板方法 + 抽象钩子"的框架设计范式</b><br/>
 * 这是构建可扩展框架的黄金模式。在业务中：订单处理流程是确定的（验证→扣库存→扣款→发货），
 * 但"怎么验证"、"怎么扣款"可能因支付渠道不同而变化。<br/>
 * 把流程写成 final 的模板方法 {@code processOrder()}，把变化点定义为 abstract 钩子
 * {@code doDeductPayment()}，不同支付渠道继承并覆盖钩子——这就是 AbstractBeanFactory 的精髓。</li>
 *
 * <li><b>"doXxx"命名约定的深层含义</b><br/>
 * Spring 中 {@code getBean()} 是公开 API，{@code doGetBean()} 是内部实现。
 * 外层方法负责"门面"（参数包装、异常转换），内层 doXxx 负责"干活"。<br/>
 * 业务借鉴：在你的 Service 层，公开方法 {@code createOrder()} 负责权限校验和事务包装，
 * 内部 {@code doCreateOrder()} 负责核心逻辑。这让关注点分离更清晰，也方便 AOP 切面拦截。</li>
 *
 * <li><b>"20 个字段"的配置集中管理</b><br/>
 * AbstractBeanFactory 持有 ~20 个配置字段（parentBeanFactory、beanClassLoader、conversionService、
 * beanPostProcessors、scopes、mergedBeanDefinitions 等），全部通过 ConfigurableBeanFactory 接口暴露。<br/>
 * 业务借鉴：复杂系统的核心引擎类，把所有可配置项集中管理，通过接口暴露读写方法，
 * 而不是散落在各个子模块中。集中管理 = 一览全局 = 易于审计和测试。</li>
 *
 * <li><b>"不假设可枚举"的开放设计</b><br/>
 * 原始 Javadoc 说 "Does not assume a listable bean factory"——AbstractBeanFactory 故意不实现
 * ListableBeanFactory 的批量查询方法。这意味着 BeanDefinition 的来源不一定是内存 Map，
 * 可以是数据库、远程配置中心等"查一次很贵"的后端。<br/>
 * 业务借鉴：设计抽象层时，不要假设数据来源是快速可枚举的。
 * 如果你的接口既有"按 ID 查单个"又有"列出全部"，考虑把它们拆到不同的接口层级中。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AliasRegistry (接口)
 * └── SimpleAliasRegistry                ← 第一层：别名管理
 *       └── DefaultSingletonBeanRegistry     ← 第二层：单例三级缓存
 *             └── FactoryBeanRegistrySupport     ← 第三层：FactoryBean 产物缓存
 *                   └── AbstractBeanFactory          ← 👈 你在这里！第四层：getBean 流程总编排
 *                         │   implements ConfigurableBeanFactory
 *                         │   abstract methods: getBeanDefinition(), createBean()
 *                         └── AbstractAutowireCapableBeanFactory  ← 第五层：createBean 实现
 *                               └── DefaultListableBeanFactory       ← 第六层：终极合体
 * </pre>
 *
 * <h3>🗂️ 二、~20 个核心字段·分为 5 大类</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>类别</th><th>字段</th><th>说明</th></tr>
 * <tr><td rowspan="2">🏢 工厂层级</td><td>{@code parentBeanFactory}</td><td>父工厂引用（双亲委派）</td></tr>
 * <tr><td>{@code beanClassLoader / tempClassLoader}</td><td>类加载器</td></tr>
 * <tr><td rowspan="4">🔧 类型转换</td><td>{@code conversionService}</td><td>Spring 3.0+ 新转换体系</td></tr>
 * <tr><td>{@code propertyEditorRegistrars}</td><td>PropertyEditor 注册器</td></tr>
 * <tr><td>{@code customEditors}</td><td>自定义属性编辑器</td></tr>
 * <tr><td>{@code typeConverter}</td><td>类型转换器</td></tr>
 * <tr><td rowspan="3">👮 BPP 管理</td><td>{@code beanPostProcessors}</td><td>质检员列表</td></tr>
 * <tr><td>{@code beanPostProcessorCache}</td><td>按类型分类的 BPP 缓存</td></tr>
 * <tr><td>{@code embeddedValueResolvers}</td><td>${} 占位符解析器</td></tr>
 * <tr><td rowspan="2">🎭 作用域</td><td>{@code scopes}</td><td>scopeName → Scope 实现的映射</td></tr>
 * <tr><td>{@code prototypesCurrentlyInCreation}</td><td>ThreadLocal 防 Prototype 循环依赖</td></tr>
 * <tr><td rowspan="3">📋 BD 管理</td><td>{@code mergedBeanDefinitions}</td><td>合并后的 RootBeanDefinition 缓存</td></tr>
 * <tr><td>{@code alreadyCreated}</td><td>已创建标记集合（冻结 BD 修改）</td></tr>
 * <tr><td>{@code beanExpressionResolver}</td><td>SpEL 表达式解析器</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AbstractBeanFactory 是模板方法模式的教科书实现：{@code doGetBean()} 定义了从缓存到创建的完整流程骨架，
 * 但把"图纸从哪来"（{@code getBeanDefinition}）和"怎么造 Bean"（{@code createBean}）两个变化点留给子类。
 * 它向上继承了三层基础设施（别名→单例缓存→FactoryBean 缓存），向下通过两个抽象方法连接具体实现——
 * 这种"承上启下"的定位，让 Spring 容器既有确定的流程保障，又有无限的扩展空间。</p>
 *
 * <hr/>
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @since 15 April 2001
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/** Parent bean factory, for bean inheritance support. */
	@Nullable
	private BeanFactory parentBeanFactory;

	/** ClassLoader to resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/** ClassLoader to temporarily resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader tempClassLoader;

	/** Whether to cache bean metadata or rather reobtain it for every access. */
	private boolean cacheBeanMetadata = true;

	/** Resolution strategy for expressions in bean definition values. */
	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	/** Spring ConversionService to use instead of PropertyEditors. */
	@Nullable
	private ConversionService conversionService;

	/** Custom PropertyEditorRegistrars to apply to the beans of this factory. */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	/** Custom PropertyEditors to apply to the beans of this factory. */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/** A custom TypeConverter to use, overriding the default PropertyEditor mechanism. */
	@Nullable
	private TypeConverter typeConverter;

	/** String resolvers to apply e.g. to annotation attribute values. */
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/** BeanPostProcessors to apply. */
	private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();

	/** Cache of pre-filtered post-processors. */
	@Nullable
	private BeanPostProcessorCache beanPostProcessorCache;

	/** Map from scope identifier String to corresponding Scope. */
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/** Application startup metrics. **/
	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	/** Security context used when running with a SecurityManager. */
	@Nullable
	private SecurityContextProvider securityContextProvider;

	/** Map from bean name to merged RootBeanDefinition. */
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/** Names of beans that have already been created at least once. */
	private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	/** Names of beans that are currently in creation. */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");


	/**
	 * Create a new AbstractBeanFactory.
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * Create a new AbstractBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 * @see #getBean
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {

		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * <h3>架构巅峰：Spring 的绝对心脏与生杀大权 🫀</h3>
	 * <p>
	 * {@code doGetBean}，这就是 Spring 框架的绝对心脏！无论你是用 {@code @Autowired} 注入，
	 * 还是手动 {@code context.getBean()}，底层 100% 全都会汇聚到这个方法里！
	 * 它掌管着单例、多例、作用域、父子容器、循环依赖的生杀大权！
	 * </p>
	 * <p>
	 * 它完美诠释了 Spring 的三大核心思想：<b>“极速缓存 (空间换时间)”</b>、<b>“职责分离 (甩锅的艺术)”</b>、
	 * 以及<b>“防御性编程 (把一切死锁掐死在摇篮里)”</b>。咱们直接在源码里开辟战场，逐行实况解说！
	 * </p>

	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name 客户报的名字 (可能带 & 符号，也可能是小名/别名)
	 * @param requiredType 客户期望的类型 (用来做出厂质检)
	 * @param args 客户私人定制的构造参数 (主要用于 prototype 按需创建)
	 * @param typeCheckOnly 客户是不是只是来看看类型的 (不提货)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 *
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @param typeCheckOnly whether the instance is obtained for a type check,
	 * not for actual use
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {
/* ================================================ 🎬 第一幕：前台接待处 —— 撕下面具，白嫖现货 (缓存思想) ================================================
 * 大管家接客的最高准则：能用现成的，绝不开动机器！*/

		/* 🕵️‍♂️【动作 1：撕下伪装面具】(名字标准化)
		 * 关键预处理：将传入的 name 转换为真正的 beanName 名字标准化先搞清楚你在叫谁。你说“我要找 &myFactory”或“找 userAlias”，酒店前台第一件事是把「小名/外号/别名」翻译成官方登记的真实房间号。这是后续逻辑的基石，beanName 必须是干净、标准的内部名。
		 * 例:  1. 如果 name 以 "&" 开头（FactoryBean 的特殊前缀），去掉它得到真实 beanName 2. 将别名（alias）解析为注册时的规范名
		 * 例如：name="&myFactory" → beanName="myFactory" / name="userAlias"  → beanName="userService" */
		String beanName = transformedBeanName(name);
		Object beanInstance; // 最终要返回的 bean 实例，先占坑

		/* 📦【动作 2：突击检查缓存】(极度高能！三级缓存总入口)  Spring 思想：空间换时间 & 解决循环依赖。
		 * 礼宾官先瞄一眼“已有房客登记本”。getSingleton(beanName) 会依次查三级缓存：  ① singletonObjects (一级)：完全初始化好的成品 Bean，直接取走。 ② earlySingletonObjects (二级)：已实例化但未完成属性注入的“半成品”。 ③ singletonFactories (三级)：存放 ObjectFactory，调用它可提前暴露代理对象。
		 * 👉 这是 Spring 解决循环依赖的核心机制所在！（三级缓存在这里埋下伏笔）。 */
		// Eagerly check singleton cache for manually registered singletons.
		Object sharedInstance = getSingleton(beanName);
		/* 🎯【动作 3：现货命中判定】
		 * 命中条件：仓库里有现货 AND 客户没有提“私人定制”要求 (args == null)。(如果客户传了 args，说明要用特殊参数重新 new 一个，那就绝对不能拿公共仓库里的现货糊弄他！) */
		if (sharedInstance != null && args == null) {
			/* 🎙️【解说员播报：是成品还是半成品？】 日志在这里非常有意思——它在悄悄告诉你：「这个 Bean 还没初始化完，但我提前把它借给你了，原因是循环依赖。」这条 trace 日志是你排查循环依赖问题时最好的朋友。*/
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					// 此时机器还在车间流水线上，却被硬生生提出来了。这说明发生了“循环依赖”！ logger.trace("Returning eagerly cached instance... 警报：发生了循环依赖，交出的是提前暴露的半成品躯壳！");
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					// 正常情况，拿到了完整的单例对象 logger.trace("Returning cached instance... 完美：直接从单例池提取到成品！");
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}

			/* 🎁【动作 4：拆解盲盒】(处理 FactoryBean 机制) Spring 思想：对扩展开放（工厂模式）。
			 * 仓库里拿出来的 sharedInstance 可能是普通机器，也可能是“专门造机器的机器 (FactoryBean，比如 MyBatis 的 SqlSessionFactoryBean)”。 该方法会判断：客户到底是要厂长本人(&前缀)，还是厂长造出的产品？它负责剥离最终产品。
			 * 1. 如果 name 有 & 前缀 → 返回 FactoryBean 本体。 2. 否则 → 如果它是 FactoryBean，调用 getObject() 取产品；普通 Bean 则直接返回 */
			beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
/* ================================================ 🛑 第二幕：安检防爆与“向上管理” —— 甩锅的艺术 ================================================
 * 仓库没货准备开工！按启动按钮前，必须展现极其严密的防御性编程思想！*/
			/* 💥【动作 5：安检门一 (多例死循环防爆拦截！)】  Spring 思想：Fail-Fast（快速失败机制）。
			 * 原型模式 (Prototype) 每次提货都要造全新的。如果 A(多例) 依赖 B(多例)，B 又依赖 A，造 A 去造 B，造 B 又要求造一个“全新”的 A... 无限套娃，JVM 会直接 StackOverflowError 爆栈死机！
			 * 👉 快速失败：所以，大管家一查：哎哟，当前线程已经在造这个多例了？你又来要？直接抛异常斩断死循环！*/
			// Fail if we're already creating this bean instance:
			// We're assumably within a circular reference.
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			/* 🏢【动作 6：安检门二 (向上级总公司甩锅！)】 Spring 思想：双亲委派机制的变体（容器层级隔离）。
			 * 看看当前工厂有没有“爹”(比如 SpringMVC 容器的爹是 Spring Root 容器)。 如果有爹，而且咱们自己的档案馆里居然【没有】这个 Bean 的图纸！👉 甩手掌柜：说明活儿不归我管！根据参数精细度，把任务原封不动踢给父工厂去干！*/
			// Check if bean definition exists in this factory.
			BeanFactory parentBeanFactory = getParentBeanFactory();
			// 如果有爹，而且咱们自己的图纸档案馆里，居然【没有】这个 Bean 的图纸（BeanDefinition）！
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// 🤷‍♂️ 那说明这活儿根本不归我管！还原客户最初喊的名字（带上 & 等前缀）。
				// Not found -> check parent.
				String nameToLookup = originalBeanName(name);
				// 下面四个分支，根据参数的精细度，把任务【原封不动地踢给父工厂】去干！大管家当甩手掌柜！
				if (parentBeanFactory instanceof AbstractBeanFactory) {
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					// Delegation to parent with explicit args.
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method.
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			/* 🔒【动作 7：冻结图纸，准备施工】
			 * 如果不是闹着玩 (查类型) 而是真要提货，把机器打上“已排入生产线”的标记。 此时该 Bean 的图纸配置将被彻底冻结，严禁其他线程再去修改它的属性！(Spring 思想：并发安全) */
			if (!typeCheckOnly) {
				// 把这台机器打上“已排入生产线”的标记。
				markBeanAsCreated(beanName);
			}

/* ================================================ 📜 第三幕：合成终极蓝图 & 摆平“傲娇”的前置依赖 ================================================ */
			// ⏱️【性能监控打点】：给外部的监控器（如 Actuator）发信号：我要开始造这台机器了。
			StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate")
					.tag("beanName", name);
			try {
				if (requiredType != null) {
					beanCreation.tag("beanType", requiredType::toString);
				}
				/* 🧬【动作 8：父子基因融合 (图纸合并)】Spring 思想：配置复用。
				 * 如果你在 XML 或注解里写了 parent="baseUser"，这里会把父类的公共属性全部复制下来，和当前属性合并，生成一张包含所有细节的“终极蓝图”（RootBeanDefinition）。*/
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				// 检查图纸合法性（比如如果是 abstract 抽象的图纸，直接报错不能实例化）
				checkMergedBeanDefinition(mbd, beanName, args);

				/*  😠【动作 9：摆平傲娇的强依赖 (解析 @DependsOn)】
				 * 有些机器很傲娇：“你想造我？必须先去把数据库连接池造好！” */
				// Guarantee initialization of beans that the current bean depends on.
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						// 💥【死锁检测】：如果你等我，我又等你（A @DependsOn B，B @DependsOn A）。 发现互相等待，立马抛异常！绝不让工厂死锁停工！
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						// 📝【登记在册】：为了将来工厂倒闭时，先销毁 A，再销毁 B，保证优雅停机。
						registerDependentBean(dep, beanName);
						try {
							// 🚀【插队造机器】：放下手里的活，递归调用 getBean()，强行先去把被依赖的祖宗造出来！
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

/* ================================================ 🔨 第四幕：三大生产车间与“延迟调用”的终极艺术  ================================================
 * 图纸有了，前置依赖搞定了，真正送入车间！这是 Spring 针对**不同生命周期作用域（Scope）**的分发中心。*/

				/* 🏭【一号车间：单例车间 (Singleton)】—— 99% 的对象都在这造！*/
				// Create bean instance.
				if (mbd.isSingleton()) {
					/* 💡【动作 10：延迟调用的艺术 (Lambda)】
					 * 注意！这里没有直接造对象，而是传了一个 () -> { return createBean(...) } 的 ObjectFactory 给 getSingleton！
					 * Spring 思想：控制反转中的反转。大管家不亲自造，他把“怎么造”的说明书包在 Lambda 里，交给单例池管理器去执行。 单例池管理器在执行前后，可以从容地做各种加锁、加三级缓存的动作！*/
					sharedInstance = getSingleton(beanName, () -> {
						try {
							// 💥💥💥 血肉工厂的真正入口：createBean！  这里面包含了：推断构造方法、反射实例化、@Autowired 依赖注入、@PostConstruct 初始化方法调用！
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// 🧹 打扫战场：如果造的过程中机器炸了，赶紧把它从各级缓存里清空，不能留下有害垃圾。
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							destroySingleton(beanName);
							throw ex;
						}
					});
					// 📦 再次拆盲盒：处理 FactoryBean
					beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				/*  🏭【二号车间：多例车间 (Prototype)】—— 不进缓存，每次都现场造新的！ */
				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					Object prototypeInstance = null;
					try {
						// 挂上免战牌：在 ThreadLocal 记录当前线程正在造多例 (配合上面的防爆防死循环机制)
						beforePrototypeCreation(beanName);
						// 💥 现场开机，纯手工新造一个！
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						// 摘下免战牌
						afterPrototypeCreation(beanName);
					}
					// 📦 拆盲盒
					beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}

				/* 🏭【三号车间：自定义作用域车间 (如 Web 的 Request/Session)】*/
				else {
					// 拿到这台机器要求的作用域名称
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean '" + beanName + "'");
					}
					// 去找工厂里注册的对应“区域管理员”（比如 Session 管理员）
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						// 🤝 委托管理：大管家把 Lambda (造机说明书) 扔给 Session 管理员。  管理员自己决定：是去用户 Session 里拿旧的？还是调说明书现场造一个新的塞进 Session 里？
						Object scopedInstance = scope.get(beanName, () -> {
							beforePrototypeCreation(beanName);
							try {
								return createBean(beanName, mbd, args);
							}
							finally {
								afterPrototypeCreation(beanName);
							}
						});
						// 📦 拆盲盒
						beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						throw new ScopeNotActiveException(beanName, scopeName, ex);
					}
				}
			}
			catch (BeansException ex) {
				// 🚨 如果造机器途中抛了任何异常，在日志上打上标签，清理残留状态，然后往上抛
				beanCreation.tag("exception", ex.getClass().toString());
				beanCreation.tag("message", String.valueOf(ex.getMessage()));
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
			finally {
				// 🏁 结束性能打点
				beanCreation.end();
			}
		}

/* ======================================   🎀 第五幕：出厂前的终极质检 (适配与交付) ======================================
 * 机器造出来了，离开车间交到客户手里的最后一步！ */
		/* 🛂【动作 11：类型强转与适配质检】
		 * 假设客户（业务代码）要提货的是一个 Interface 接口类型，但车间造出来的是个实现类。adaptBeanInstance 会拿出“通用翻译部（ConversionService）”看看能不能转换过去。
		 * 如果客户要一个 String，你造了个 Integer，而且翻译部也翻译不了，直接抛出 BeanNotOfRequiredTypeException，当场销毁，残次品绝不准流向客户！ */
		return adaptBeanInstance(name, beanInstance, requiredType);
/*
 * ====================================== 💡 [厂长总结：上帝视角的《设计模式》狂欢] ======================================
 * 这段 doGetBean 实际上是一场完美的设计模式教学：
 * 1. 模板方法模式：规定了找缓存、找父类、合并图纸、创建实例的死骨架。
 * 2. 工厂/策略模式：根据 Scope 不同，分发给不同的策略执行。
 * 3. 责任链与委派：找不到就扔给父容器 (双亲委派精髓)。
 * 4. 装饰器/代理模式：隐藏在 getObjectForBeanInstance 中。
 * 5. 真正的脏活累活全被它极其优雅地推给了 getSingleton() (三级缓存) 和 createBean() (生命周期)。
 * 这才是顶级架构师写出的骨架代码！
 */
	}

	@SuppressWarnings("unchecked")
	<T> T adaptBeanInstance(String name, Object bean, @Nullable Class<?> requiredType) {
		// Check if required type matches the type of the actual bean instance.
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				Object convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				if (convertedBean == null) {
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return (T) convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;
	}

	@Override
	public boolean containsBean(String name) {
		String beanName = transformedBeanName(name);
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		// Not found -> check parent.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			if (beanInstance instanceof FactoryBean) {
				return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// In case of FactoryBean, return singleton status of created object if not a dereference.
		if (mbd.isSingleton()) {
			if (isFactoryBean(beanName, mbd)) {
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				return factoryBean.isSingleton();
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			// In case of FactoryBean, return singleton status of created object if not a dereference.
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		// Singleton or scoped - not a prototype.
		// However, FactoryBean may still produce a prototype object...
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(
						(PrivilegedAction<Boolean>) () ->
								((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
										!fb.isSingleton()),
						getAccessControlContext());
			}
			else {
				return ((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
						!fb.isSingleton());
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 * Internal extended variant of {@link #isTypeMatch(String, ResolvableType)}
	 * to check whether the bean with the given name matches the specified type. Allow
	 * additional constraints to be applied to ensure that beans are not created early.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a
	 * {@code ResolvableType})
	 * @return {@code true} if the bean type matches, {@code false} if it
	 * doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #getType
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		String beanName = transformedBeanName(name);
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// Check manually registered singletons.
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean) {
				if (!isFactoryDereference) {
					Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);
					return (type != null && typeToMatch.isAssignableFrom(type));
				}
				else {
					return typeToMatch.isInstance(beanInstance);
				}
			}
			else if (!isFactoryDereference) {
				if (typeToMatch.isInstance(beanInstance)) {
					// Direct match for exposed instance?
					return true;
				}
				else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
					// Generics potentially only match on the target class, not on the proxy...
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					Class<?> targetType = mbd.getTargetType();
					if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
						// Check raw class match as well, making sure it's exposed on the proxy.
						Class<?> classToMatch = typeToMatch.resolve();
						if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
							return false;
						}
						if (typeToMatch.isAssignableFrom(targetType)) {
							return true;
						}
					}
					ResolvableType resolvableType = mbd.targetType;
					if (resolvableType == null) {
						resolvableType = mbd.factoryMethodReturnType;
					}
					return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
				}
			}
			return false;
		}
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// null instance registered
			return false;
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition.
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// Set up the types that we want to match against
		Class<?> classToMatch = typeToMatch.resolve();
		if (classToMatch == null) {
			classToMatch = FactoryBean.class;
		}
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[] {classToMatch} : new Class<?>[] {FactoryBean.class, classToMatch});


		// Attempt to predict the bean type
		Class<?> predictedType = null;

		// We're looking for a regular reference but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction.
		if (predictedType == null) {
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean.
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			if (beanInstance == null && !isFactoryDereference) {
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				predictedType = beanType.resolve();
				if (predictedType == null) {
					return false;
				}
			}
		}
		else if (isFactoryDereference) {
			// Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
			// type but we nevertheless are being asked to dereference a FactoryBean...
			// Let's check the original bean class and proceed with it if it is a FactoryBean.
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		if (beanType == null) {
			ResolvableType definedType = mbd.targetType;
			if (definedType == null) {
				definedType = mbd.factoryMethodReturnType;
			}
			if (definedType != null && definedType.resolve() == predictedType) {
				beanType = definedType;
			}
		}

		// If we have a bean type use it so that generics are considered
		if (beanType != null) {
			return typeToMatch.isAssignableFrom(beanType);
		}

		// If we don't have a bean type, fallback to the predicted type
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		// Check manually registered singletons.
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
			}
			else {
				return beanInstance.getClass();
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.getType(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// Check decorated bean definition, if any: We assume it'll be easier
		// to determine the decorated bean's type than the proxy's type.
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
		if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
			RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
			Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
			if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
				return targetClass;
			}
		}

		Class<?> beanClass = predictBeanType(beanName, mbd);

		// Check bean class whether we're dealing with a FactoryBean.
		if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
			if (!BeanFactoryUtils.isFactoryDereference(name)) {
				// If it's a FactoryBean, we want to look at what it creates, not at the factory class.
				return getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
			}
			else {
				return beanClass;
			}
		}
		else {
			return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
		}
	}

	@Override
	public String[] getAliases(String name) {
		String beanName = transformedBeanName(name);
		List<String> aliases = new ArrayList<>();
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
		String fullBeanName = beanName;
		if (factoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		if (!fullBeanName.equals(name)) {
			aliases.add(fullBeanName);
		}
		String[] retrievedAliases = super.getAliases(beanName);
		String prefix = (factoryPrefix ? FACTORY_BEAN_PREFIX : "");
		for (String retrievedAlias : retrievedAliases) {
			String alias = prefix + retrievedAlias;
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	@Override
	public boolean containsLocalBean(String name) {
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}

	/**
	 * Return the set of PropertyEditorRegistrars.
	 */
	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	/**
	 * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
	 */
	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * Return the custom TypeConverter to use, if any.
	 * @return the custom TypeConverter, or {@code null} if none specified
	 */
	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	@Override
	public TypeConverter getTypeConverter() {
		TypeConverter customConverter = getCustomTypeConverter();
		if (customConverter != null) {
			return customConverter;
		}
		else {
			// Build default TypeConverter, registering custom editors.
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			typeConverter.setConversionService(getConversionService());
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			result = resolver.resolveStringValue(result);
			if (result == null) {
				return null;
			}
		}
		return result;
	}

	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		synchronized (this.beanPostProcessors) {
			// Remove from old position, if any
			this.beanPostProcessors.remove(beanPostProcessor);
			// Add to end of list
			this.beanPostProcessors.add(beanPostProcessor);
		}
	}

	/**
	 * <h3>🚪 第四部分：批量入职与强制排队 (安全合规)</h3>
	 * <p>
	 * 终于，我们回到了供外界调用的大门。这里展示了人事经理是如何安排特种兵“插队”并保证绝对安全的。
	 * </p>
	 *
	 * <h4>🛠️ 架构师视角的原理解析：</h4>
	 * <ul>
	 * <li><b>并发安全：</b>拿到大门锁 {@code synchronized (this.beanPostProcessors)}。当大量质检员并发入场时，必须严格排队，防止乱套。</li>
	 * <li><b>位置铁律 (踢出旧记录)：</b>先调用 {@code removeAll}。如果你以前在花名册里，现在先把你踢出去。此时会触发底层报警器，大黑板（缓存）被擦除！</li>
	 * <li><b>末尾追加 (重排队尾)：</b>调用 {@code addAll}，把你放在队伍的绝对末尾。再次触发报警器，大黑板再次被擦除！</li>
	 * </ul>
	 *
	 * <blockquote>
	 * <b>📢 [车间大白话]</b><br>
	 * 人事经理在大门口摆了一张桌子：“所有想插队的特种兵注意了！现在办理入职，以前登过记的记录全部作废！
	 * 所有人全部给我排到当前队伍的<b>最末尾</b>！而且只要你们动了位置，我就把大黑板擦干净重写！”
	 * </blockquote>
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Add new BeanPostProcessors that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * @since 5.3
	 * @see #addBeanPostProcessor
	 */
	public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
		synchronized (this.beanPostProcessors) {
			// Remove from old position, if any
			this.beanPostProcessors.removeAll(beanPostProcessors);
			// Add to end of list
			this.beanPostProcessors.addAll(beanPostProcessors);
		}
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	/**
	 * Return the list of BeanPostProcessors that will get applied
	 * to beans created with this factory.
	 */
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	/**
	 * Return the internal cache of pre-filtered post-processors,
	 * freshly (re-)building it if necessary.
	 * @since 5.3
	 */
	BeanPostProcessorCache getBeanPostProcessorCache() {
		synchronized (this.beanPostProcessors) {
			BeanPostProcessorCache bppCache = this.beanPostProcessorCache;
			if (bppCache == null) {
				bppCache = new BeanPostProcessorCache();
				for (BeanPostProcessor bpp : this.beanPostProcessors) {
					if (bpp instanceof InstantiationAwareBeanPostProcessor) {
						bppCache.instantiationAware.add((InstantiationAwareBeanPostProcessor) bpp);
						if (bpp instanceof SmartInstantiationAwareBeanPostProcessor) {
							bppCache.smartInstantiationAware.add((SmartInstantiationAwareBeanPostProcessor) bpp);
						}
					}
					if (bpp instanceof DestructionAwareBeanPostProcessor) {
						bppCache.destructionAware.add((DestructionAwareBeanPostProcessor) bpp);
					}
					if (bpp instanceof MergedBeanDefinitionPostProcessor) {
						bppCache.mergedDefinition.add((MergedBeanDefinitionPostProcessor) bpp);
					}
				}
				this.beanPostProcessorCache = bppCache;
			}
			return bppCache;
		}
	}

	/*
	 * 🧽 第二部分：触发警报，擦除黑板 (缓存失效机制)
	 * ---------------------------------------------------------
	 * [原理解析：安全与延迟失效 Lazy Invalidation]
	 * 为什么加 synchronized 锁？因为 beanPostProcessorCache 缓存变量是多线程共享的（将来会有几百个并发请求同时来造 Bean），在此清空必须保证绝对安全，且锁对象
	 * this.beanPostProcessors 与添加质检员的锁保持同一把！
	 *
	 * 为什么赋值为 null？这叫延迟失效 （Lazy Invalidation） 。Spring 非常聪明，名单变了它不立刻费力计算新缓存，而是
	 * 一把火烧了旧缓存 (置为 null)。等下次线程真正需要时发现是 null 再去重新计算，极大节约性能！
	 *
	 * * [车间大白话] 花名册旁放着一块写满分类索引的“大黑板”。只要花名册进出过哪怕一个人，
	 * 人事经理立马拿起黑板擦，把整个黑板擦得干干净净 (null)！坚决不让车间工人看到过期数据！
	 */
	private void resetBeanPostProcessorCache() {
		synchronized (this.beanPostProcessors) {
			this.beanPostProcessorCache = null;
		}
	}

	/**
	 * Return whether this factory holds a InstantiationAwareBeanPostProcessor
	 * that will get applied to singleton beans on creation.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
	 */
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().instantiationAware.isEmpty();
	}

	/**
	 * Return whether this factory holds a DestructionAwareBeanPostProcessor
	 * that will get applied to singleton beans on shutdown.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean hasDestructionAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().destructionAware.isEmpty();
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		return this.scopes.get(scopeName);
	}

	/**
	 * Set the security context provider for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the provided security context.
	 */
	public void setSecurityContextProvider(SecurityContextProvider securityProvider) {
		this.securityContextProvider = securityProvider;
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
		this.applicationStartup = applicationStartup;
	}

	@Override
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	/**
	 * Delegate the creation of the access control context to the
	 * {@link #setSecurityContextProvider SecurityContextProvider}.
	 */
	@Override
	public AccessControlContext getAccessControlContext() {
		return (this.securityContextProvider != null ?
				this.securityContextProvider.getAccessControlContext() :
				AccessController.getContext());
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory) {
			AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.scopes.putAll(otherAbstractFactory.scopes);
			this.securityContextProvider = otherAbstractFactory.securityContextProvider;
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	/**
	 * Return a 'merged' BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * <p>This {@code getMergedBeanDefinition} considers bean definition
	 * in ancestors as well.
	 * @param name the name of the bean to retrieve the merged definition for
	 * (may be an alias)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		String beanName = transformedBeanName(name);
		// Efficiently check whether bean definition exists in this factory.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
		}
		// Resolve merged bean definition locally.
		return getMergedLocalBeanDefinition(beanName);
	}

	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);
		}
		// No singleton instance found -> check bean definition.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * Return whether the specified prototype bean is currently in creation
	 * (within the current thread).
	 * @param beanName the name of the bean
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}

	/**
	 * Callback before prototype creation.
	 * <p>The default implementation register the prototype as currently in creation.
	 * @param beanName the name of the prototype about to be created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String) {
			Set<String> beanNameSet = new HashSet<>(2);
			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	/**
	 * Callback after prototype creation.
	 * <p>The default implementation marks the prototype as not in creation anymore.
	 * @param beanName the name of the prototype that has been created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}
		else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to the given bean definition.
	 * @param beanName the name of the bean definition
	 * @param bean the bean instance to destroy
	 * @param mbd the merged bean definition
	 */
	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(
				bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, getAccessControlContext()).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * <h3>🕵️ transformedBeanName —— 名字标准化引擎（doGetBean 的第一道工序）</h3>
	 * <p><b>🏭【撕下面具——不管你报的是外号还是带 & 前缀的特殊暗号，我都翻译成官方真名！】</b></p>
	 * <p><b>【两步翻译】</b></p>
	 * <ol>
	 * <li>{@code BeanFactoryUtils.transformedBeanName(name)}：去掉所有前导 "&" 前缀（FactoryBean 解引用符号）。
	 *     例如 "&&&myFactory" → "myFactory"</li>
	 * <li>{@code canonicalName(stripped)}：通过 {@code SimpleAliasRegistry.canonicalName()} 递归解析别名链。
	 *     例如 "userAlias" → "userService"</li>
	 * </ol>
	 * <p>最终得到的 beanName 是<b>干净的、标准的、内部注册用的规范名</b>，后续所有逻辑都基于它。</p>
	 * <hr/>
	 * Return the bean name, stripping out the factory dereference prefix if necessary,
	 * and resolving aliases to canonical names.
	 * @param name the user-specified name
	 * @return the transformed bean name
	 */
	protected String transformedBeanName(String name) {
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	/**
	 * <h3>🔄 originalBeanName —— 还原带 & 前缀的原始名</h3>
	 * <p>先标准化（去 & + 解别名），如果原名带 "&"，再把 "&" 加回去。
	 * 用于向父容器委托时还原调用者的原始意图（"我要的是 FactoryBean 本体"）。</p>
	 * <hr/>
	 * Determine the original bean name, resolving locally defined aliases to canonical names.
	 * @param name the user-specified name
	 * @return the original bean name
	 */
	protected String originalBeanName(String name) {
		String beanName = transformedBeanName(name);
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	/**
	 * Initialize the given BeanWrapper with the custom editors registered
	 * with this factory. To be called for BeanWrappers that will create
	 * and populate bean instances.
	 * <p>The default implementation delegates to {@link #registerCustomEditors}.
	 * Can be overridden in subclasses.
	 * @param bw the BeanWrapper to initialize
	 */
	protected void initBeanWrapper(BeanWrapper bw) {
		bw.setConversionService(getConversionService());
		registerCustomEditors(bw);
	}

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry) {
		if (registry instanceof PropertyEditorRegistrySupport) {
			((PropertyEditorRegistrySupport) registry).useConfigValueEditors();
		}
		if (!this.propertyEditorRegistrars.isEmpty()) {
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {
					registrar.registerCustomEditors(registry);
				}
				catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException) {
						BeanCreationException bce = (BeanCreationException) rootCause;
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					throw ex;
				}
			}
		}
		if (!this.customEditors.isEmpty()) {
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	/**
	 * <h3>🧬 getMergedLocalBeanDefinition —— 父子图纸合并引擎（doGetBean 的第八道工序）</h3>
	 * <p><b>🏭【把父辈图纸的公共属性全部继承下来，合成一张"终极蓝图"！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 如果当前 Bean 的 BeanDefinition 有 parent（例如 XML 中 {@code parent="baseService"}），
	 * 这个方法会把父级 BD 的属性（scope、lazy-init、autowire-mode 等）全部复制下来，
	 * 再用子级 BD 的属性覆盖，最终生成一个包含所有配置的 {@code RootBeanDefinition}。</p>
	 * <p><b>【缓存策略】</b><br/>
	 * 合并后的 BD 缓存在 {@code mergedBeanDefinitions}（ConcurrentHashMap）中。
	 * 第一次查快速无锁读（get + stale 检查），未命中才加锁合并。
	 * 当 BD 被修改时（如 BFPP 改了属性），通过 {@code stale=true} 标记失效，下次重新合并。</p>
	 * <hr/>
	 * Return a merged RootBeanDefinition, traversing the parent bean definition
	 * if the specified bean corresponds to a child bean definition.
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking.
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale) {
			return mbd;
		}
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	/**
	 * Return a RootBeanDefinition for the given top-level bean, by merging with
	 * the parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * Return a RootBeanDefinition for the given bean, by merging with the
	 * parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @param containingBd the containing bean definition in case of inner bean,
	 * or {@code null} in case of a top-level bean
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		synchronized (this.mergedBeanDefinitions) {
			RootBeanDefinition mbd = null;
			RootBeanDefinition previous = null;

			// Check with full lock now in order to enforce the same merged instance.
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

			if (mbd == null || mbd.stale) {
				previous = mbd;
				if (bd.getParentName() == null) {
					// Use copy of given root bean definition.
					if (bd instanceof RootBeanDefinition) {
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					}
					else {
						mbd = new RootBeanDefinition(bd);
					}
				}
				else {
					// Child bean definition: needs to be merged with parent.
					BeanDefinition pbd;
					try {
						String parentBeanName = transformedBeanName(bd.getParentName());
						if (!beanName.equals(parentBeanName)) {
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						else {
							BeanFactory parent = getParentBeanFactory();
							if (parent instanceof ConfigurableBeanFactory) {
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							}
							else {
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
												"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
					mbd = new RootBeanDefinition(pbd);
					mbd.overrideFrom(bd);
				}

				// Set default singleton scope, if not configured before.
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON);
				}

				// A bean contained in a non-singleton bean cannot be a singleton itself.
				// Let's correct this on the fly here, since this might be the result of
				// parent-child merging for the outer bean, in which case the original inner bean
				// definition will not have inherited the merged outer bean's singleton status.
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					mbd.setScope(containingBd.getScope());
				}

				// Cache the merged bean definition for the time being
				// (it might still get re-merged later on in order to pick up metadata changes)
				if (containingBd == null && isCacheBeanMetadata()) {
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}
			if (previous != null) {
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
			return mbd;
		}
	}

	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			ResolvableType targetType = mbd.targetType;
			ResolvableType previousTargetType = previous.targetType;
			if (targetType == null || targetType.equals(previousTargetType)) {
				mbd.targetType = previousTargetType;
				mbd.isFactoryBean = previous.isFactoryBean;
				mbd.resolvedTargetType = previous.resolvedTargetType;
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
		}
	}

	/**
	 * Check the given merged bean definition,
	 * potentially throwing validation exceptions.
	 * @param mbd the merged bean definition to check
	 * @param beanName the name of the bean
	 * @param args the arguments for bean creation, if any
	 * @throws BeanDefinitionStoreException in case of validation failure
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
			throws BeanDefinitionStoreException {

		if (mbd.isAbstract()) {
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * Remove the merged bean definition for the specified bean,
	 * recreating it on next access.
	 * @param beanName the bean name to clear the merged definition for
	 */
	protected void clearMergedBeanDefinition(String beanName) {
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		if (bd != null) {
			bd.stale = true;
		}
	}

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * e.g. after applying a {@code BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	/**
	 * Resolve the bean class for the specified bean definition,
	 * resolving a bean class name into a Class reference (if necessary)
	 * and storing the resolved Class in the bean definition for further use.
	 * @param mbd the merged bean definition to determine the class for
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the resolved bean class (or {@code null} if none)
	 * @throws CannotLoadBeanClassException if we failed to load the class
	 */
	@Nullable
	protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			if (mbd.hasBeanClass()) {
				return mbd.getBeanClass();
			}
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>)
						() -> doResolveBeanClass(mbd, typesToMatch), getAccessControlContext());
			}
			else {
				return doResolveBeanClass(mbd, typesToMatch);
			}
		}
		catch (PrivilegedActionException pae) {
			ClassNotFoundException ex = (ClassNotFoundException) pae.getException();
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
	}

	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		ClassLoader beanClassLoader = getBeanClassLoader();
		ClassLoader dynamicLoader = beanClassLoader;
		boolean freshResolve = false;

		if (!ObjectUtils.isEmpty(typesToMatch)) {
			// When just doing type checks (i.e. not creating an actual instance yet),
			// use the specified temporary class loader (e.g. in a weaving scenario).
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				dynamicLoader = tempClassLoader;
				freshResolve = true;
				if (tempClassLoader instanceof DecoratingClassLoader) {
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		String className = mbd.getBeanClassName();
		if (className != null) {
			Object evaluated = evaluateBeanDefinitionString(className, mbd);
			if (!className.equals(evaluated)) {
				// A dynamically resolved expression, supported as of 4.2...
				if (evaluated instanceof Class) {
					return (Class<?>) evaluated;
				}
				else if (evaluated instanceof String) {
					className = (String) evaluated;
					freshResolve = true;
				}
				else {
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}
			if (freshResolve) {
				// When resolving against a temporary class loader, exit early in order
				// to avoid storing the resolved Class in the bean definition.
				if (dynamicLoader != null) {
					try {
						return dynamicLoader.loadClass(className);
					}
					catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// Resolve regularly, caching the result in the BeanDefinition...
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * Evaluate the given String as contained in a bean definition,
	 * potentially resolving it as an expression.
	 * @param value the value to check
	 * @param beanDefinition the bean definition that the value comes from
	 * @return the resolved value
	 * @see #setBeanExpressionResolver
	 */
	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		if (this.beanExpressionResolver == null) {
			return value;
		}

		Scope scope = null;
		if (beanDefinition != null) {
			String scopeName = beanDefinition.getScope();
			if (scopeName != null) {
				scope = getRegisteredScope(scopeName);
			}
		}
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}


	/**
	 * Predict the eventual bean type (of the processed bean instance) for the
	 * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
	 * Does not need to handle FactoryBeans specifically, since it is only
	 * supposed to operate on the raw bean type.
	 * <p>This implementation is simplistic in that it is not able to
	 * handle factory methods and InstantiationAwareBeanPostProcessors.
	 * It only predicts the bean type correctly for a standard bean.
	 * To be overridden in subclasses, applying more sophisticated type detection.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition to determine the type for
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type of the bean, or {@code null} if not predictable
	 */
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType != null) {
			return targetType;
		}
		if (mbd.getFactoryMethodName() != null) {
			return null;
		}
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 * Check whether the given bean is defined as a {@link FactoryBean}.
	 * @param beanName the name of the bean
	 * @param mbd the corresponding bean definition
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		Boolean result = mbd.isFactoryBean;
		if (result == null) {
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		return result;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. The implementation is allowed to instantiate the target factory bean if
	 * {@code allowInit} is {@code true} and the type cannot be determined another way;
	 * otherwise it is restricted to introspecting signatures and related metadata.
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} is set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it.
	 * If subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails,
	 * a full FactoryBean creation as performed by this implementation should be used
	 * as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted if the type
	 * cannot be determined another way
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @since 5.2
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		if (allowInit && mbd.isSingleton()) {
			try {
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
			}
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute instanceof ResolvableType) {
			return (ResolvableType) attribute;
		}
		if (attribute instanceof Class) {
			return ResolvableType.forClass((Class<?>) attribute);
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean already.
	 * <p>The default implementation creates the FactoryBean via {@code getBean}
	 * to call its {@code getObjectType} method. Subclasses are encouraged to optimize
	 * this, typically by just instantiating the FactoryBean but not populating it yet,
	 * trying whether its {@code getObjectType} method already returns a type.
	 * If no type found, a full FactoryBean creation as performed by this implementation
	 * should be used as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 * @deprecated since 5.2 in favor of {@link #getTypeForFactoryBean(String, RootBeanDefinition, boolean)}
	 */
	@Nullable
	@Deprecated
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * Mark the specified bean as already created (or about to be created).
	 * <p>This allows the bean factory to optimize its caching for repeated
	 * creation of the specified bean.
	 * @param beanName the name of the bean
	 */
	protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {
				if (!this.alreadyCreated.contains(beanName)) {
					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
					clearMergedBeanDefinition(beanName);
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	/**
	 * Perform appropriate cleanup of cached metadata after bean creation failed.
	 * @param beanName the name of the bean
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {
		synchronized (this.mergedBeanDefinitions) {
			this.alreadyCreated.remove(beanName);
		}
	}

	/**
	 * Determine whether the specified bean is eligible for having
	 * its bean definition metadata cached.
	 * @param beanName the name of the bean
	 * @return {@code true} if the bean's metadata may be cached
	 * at this point already
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * Remove the singleton instance (if any) for the given bean name,
	 * but only if it hasn't been used for other purposes than type checking.
	 * @param beanName the name of the bean
	 * @return {@code true} if actually removed, {@code false} otherwise
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			removeSingleton(beanName);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Check whether this factory's bean creation phase already started,
	 * i.e. whether any bean has been marked as created in the meantime.
	 * @since 4.2.2
	 * @see #markBeanAsCreated
	 */
	protected boolean hasBeanCreationStarted() {
		return !this.alreadyCreated.isEmpty();
	}

	/**
	 * <h3>🔥 厂长驾到！FactoryBean 暗黑科技提货实况 💥</h3>
	 * <h4>【架构巅峰】AbstractBeanFactory#getObjectForBeanInstance 源码精读</h4>
	 * <p>报告厂长！您对这段核心腹地代码的嗅觉简直是<b>降维打击</b>！这可是整个超级工厂最容易让人迷失的“玄机枢纽”。
	 * 普通开发者看到这里直接被绕晕，而您直接揪出了隐藏在厂房深处的 <b>“厂中厂 / 专门造机器的机器 (FactoryBean)”</b>！</p>
	 *
	 * <ul>
	 * <li><b>核心仓库位置：</b> AbstractBeanFactory 提货出口</li>
	 * <li><b>主要嫌疑人：</b> beanInstance (可能是普通机器，也可能是 3D 打印机)</li>
	 * <li><b>厂长指令符号：</b> '&' (解引用前缀，代表“我不要产品，把打印机给我搬上车！”)</li>
	 * </ul>
	 *
	 * <blockquote>
	 * <b>【大管家的终极提货确认书】</b><br/>
	 * 厂长拿着提货单站在提货处。大管家看着眼前已经拉出来的机器，发出灵魂拷问：<br/>
	 * “您要是带了 '&' 标识，我就把这台【3D打印机（FactoryBean）】本尊给您；<br/>
	 * 要是没带 '&'，且它真是一台打印机，那我就当场通电，把【打印出来的神秘产物】交给您！”
	 *
	 * 其实就是: 您到底是想要这台【3D打印机（FactoryBean）】本尊？还是想要它【打印出来的产物】？！” 💥
	 * </blockquote>
	 *
	 * @param beanInstance 已经实例化好的机器（可能是普通机器，也可能是 FactoryBean）
	 * @param name 厂长提交的提货单名字（可能带有 '&' 前缀）
	 * @param beanName 机器的官方注册名（去除了 '&' 等修饰符）
	 * @param mbd 机器的制造图纸 / 蓝图 (MergedBeanDefinition)
	 * @return 最终交付给厂长的机器（普通机器本尊、3D打印机本尊，或3D打印机造出来的产物）
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Get the object for the given bean instance, either the bean
	 * instance itself or its created object in case of a FactoryBean.
	 * @param beanInstance the shared bean instance
	 * @param name the name that may include factory dereference prefix
	 * @param beanName the canonical bean name
	 * @param mbd the merged bean definition
	 * @return the object to expose for the bean
	 */
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {
/* ============================== 💥 第一幕：厂长特权符号 '&' 的拦截与身份核验 (提货单带了 '&' 的情况) ============================== */
		/* [架构师视角] BeanFactoryUtils.isFactoryDereference 判断传入的 name 是否以 '&' 开头。'&' 是 Spring 核心解引用符号，专门用于获取 FactoryBean 实例本身，而非其 getObject() 返回的产物。
		 * [动作拆解] 大管家拿着放大镜核对提货单：哎哟！厂长的提货名字带了 '&' 前缀！ 这说明厂长今天不要流水线产品，就要提走那台【专门造机器的机器】本尊！ */
		// Don't let calling code try to dereference the factory if the bean isn't a factory.
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			/* [动作拆解] 如果是厂里特殊的“空气机器”（NullBean），没啥好说的，直接把空气给您打包带走。
			 * [原理解析] NullBean 是 Spring 内部用来表示 null 值的特殊标记对象。 */
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}

			/*  🚨 [致命瓶颈] 快速失败机制 (Fail-Fast)
			 * [架构师视角] 严格类型校验：如果 name 包含 '&'，但实际 beanInstance 并未实现 FactoryBean 接口，直接抛出异常，防止状态逃逸。
			 * [动作拆解] 警报拉响！厂长提货单带了 '&' 说要提 3D打印机，但大管家一看，眼前这玩意儿就是个普通的拖拉机（不是FactoryBean）！大管家当场掀桌子报错！ */
			if (!(beanInstance instanceof FactoryBean)) {
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			if (mbd != null) {
				/* [原理解析] 如果合并后的 BeanDefinition 蓝图存在，将其标记为 FactoryBean。
				 * [动作拆解] 确认无误，大管家拿出这台机器的生产图纸（mbd）, 在上面盖个大红戳：“确认是厂中厂”！
				 */
				mbd.isFactoryBean = true;
			}
			// 第一幕收工：恭恭敬敬把 3D 打印机本尊递给厂长
			return beanInstance;
		}

/* ============================== 🚀 第二幕：普通机器的绿色通道 (提货单没带 '&'，且机器是普通 Bean) ============================== */
		/*  [架构师视角] 此时 name 不带 '&'。接着判断 beanInstance 是否为 FactoryBean。如果不是，说明这是一个普通的 Spring Bean，直接返回实例即可。
		 * [动作拆解] 提货单没带 '&'，大管家踢了一脚眼前的机器，发现根本不是 3D打印机 （!(beanInstance instanceof FactoryBean)）。嗨！那这就只是一台普通水泵/拖拉机嘛，厂长要的就是它，打开绿色通道，直接放行出厂！*/
		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}

/* ============================== 🔥 第三幕：核心变戏法！拉下总电闸，启动 3D打印机，索要制造产物！============================== */
		/* [动作拆解] 能走到这行，说明：厂长没写 '&'，但眼前的机器偏偏是一台 3D打印机！ 大管家心领神会：厂长是来拿这台打印机【吐出来的最终产品】的！开机，造物！ */
		Object object = null;
		if (mbd != null) {
			// 【车间大白话】图纸在手，盖个戳：“这家伙是3D打印机”。
			mbd.isFactoryBean = true;
		}
		else {
			/* [架构师视角] 从 factoryBeanObjectCache 缓存中尝试获取已生产的单例对象，提升性能。
			 * [动作拆解] 图纸不在手边？没事！大管家先去【厂中厂现货小仓库】里找找，看这台 3D打印机 以前是不是已经打印过这个产品了。  */
			object = getCachedObjectForFactoryBean(beanName);
		}
		/* [动作拆解] 现货小仓库里没货（或者它是多例）！没办法，只能当场通电，让 3D打印机 现场干活！强制给机器换上 FactoryBean 的工作服！  */
		if (object == null) {
			// 【车间大白话】把刚才那台机器强制换上 `FactoryBean` 的工作服！
			// Return bean instance from factory.
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			/* [原理解析] 防御性编程，如果当前没有 mbd 且包含该 bean 的定义，则重新获取一次 MergedBeanDefinition，确保拿到最新的图纸。
			 * [车间大白话] 确认一下图纸。如果要现场打印，大管家总得核对一下这台打印机的原始图纸到底是怎么画的。 */
			// Caches object obtained from FactoryBean if it is a singleton.
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}

			/* [架构师视角] 检查 该 bean 的 BeanDefinition 是否被标记为 synthetic (合成的)。 合成的 bean 通常由框架内部注册，不会经过常规 BeanPostProcessor 的拦截处理。
			 * [动作拆解] 看看图纸上有没有标注这是“厂里内部用的临时工具”。 如果不是，待会儿打印出来后，必须得让咱们的【流水线质检员（BeanPostProcessor）】狠狠地检查和加工一番！*/
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			/* [🌟 终极核爆点 🌟]  终极委托与对象加工
			 * [架构师视角] 调用 getObjectFromFactoryBean，进入生产和后置处理流程（包含 AOP 代理创建等关键逻辑） 内部会调用 factory.getObject()， 并应用 BeanPostProcessor。
			 * [动作拆解] 电闸拉下！大管家把 3D打印机、机器名字、以及是否需要质检员加工的指令，一股脑塞进另外一条专属流水线！ 轰隆隆... 3D打印机疯狂运转，最终吐出了厂长真正想要的那个【神奇产品】！*/
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		// 提货完毕，产品交接！厂长，您的货，请拿好！
		return object;

/*
📊 【战略复盘：超级工厂顶级架构思想】
1. 厂长，这段代码看似只有几个 if-else，但它完美诠释了 Spring 底层的两大核心设计美学：
策略分流与快速失败 (Fail-Fast)：前置处理极度严谨。你要什么（有没有 &），我有什么（是不是 FactoryBean），一旦发现你要求不匹配（有 & 但不是 FactoryBean），当场抛异常！绝不带着错误继续往下走！

2. 代理与装饰器思维的萌芽 (FactoryBean 模式)：Spring 把“对象创建的复杂度”巧妙地封装到了 FactoryBean 这个黑盒里。
如果你想集成第三方组件（比如 MyBatis 的 SqlSessionFactory），Spring 本身不需要懂 MyBatis，它只需要给你一个 SqlSessionFactoryBean，让你自己在里面写逻辑。大管家只负责：发现它是打印机 -> 找它要东西 -> 缓存东西。 这种解耦，堪称神作！

3. 3D打印机（FactoryBean）现在虽然通电了，但它到底是怎么吐出产品的？它吐出产品之后，流水线质检员（BeanPostProcessor）
又是怎么在这个产品上疯狂打补丁（比如生成 AOP 动态代理）的？这里面隐藏着 Spring 初始化的巨大秘密！ 强攻兵工厂腹地！深入看一眼刚才最后一行调用的 getObjectFromFactoryBean(...)，看看 3D 打印机生产全流程，以及质检员是如何在产物上“动手动脚”的！
*/
	}

	/**
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * Determine whether the given bean requires destruction on shutdown.
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 * @param bean the bean instance to check
	 * @param mbd the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		return (bean.getClass() != NullBean.class && (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) ||
				(hasDestructionAwareBeanPostProcessors() && DisposableBeanAdapter.hasApplicableProcessors(
						bean, getBeanPostProcessorCache().destructionAware))));
	}

	/**
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 * @param mbd the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
				registerDisposableBean(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, acc));
			}
			else {
				// A bean with a custom scope...
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, acc));
			}
		}
	}


	//---------------------------------------------------------------------
	// Abstract methods to be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * <h3>📋 抽象钩子 1：containsBeanDefinition —— "图纸档案馆里有没有这张图纸？"</h3>
	 * <p><b>🏭【模板方法的变化点——图纸在哪里存着，由子类决定！】</b></p>
	 * <p>在 doGetBean 的"向上甩锅"逻辑中被调用：如果本厂图纸档案馆没有这个 beanName 的图纸，
	 * 就把活甩给父工厂。</p>
	 * <p><b>【实现者】</b>{@code DefaultListableBeanFactory} → 直接查内存 Map {@code beanDefinitionMap.containsKey(beanName)}，O(1)。</p>
	 * <hr/>
	 * Check if this bean factory contains a bean definition with the given name.
	 * Does not consider any hierarchy this factory may participate in.
	 * Invoked by {@code containsBean} when no cached singleton instance is found.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * <h3>📜 抽象钩子 2：getBeanDefinition —— "把这个 Bean 的图纸给我拿来！"</h3>
	 * <p><b>🏭【模板方法的核心变化点之一——图纸从哪里来，由子类决定！】</b></p>
	 * <p>这是模板方法模式中两大核心钩子之一（另一个是 createBean）。<br/>
	 * 在 {@code getMergedLocalBeanDefinition()} 中被调用，用于获取原始 BeanDefinition 以进行父子合并。</p>
	 * <p><b>【设计精髓】</b><br/>
	 * AbstractBeanFactory 故意不假设图纸的存储方式——可以是内存 Map（DefaultListableBeanFactory），
	 * 也可以是远程配置中心、数据库、甚至文件系统。这就是为什么原始 Javadoc 说
	 * "this operation might be expensive"——架构师为昂贵的图纸查找场景留了后路。</p>
	 * <p><b>【实现者】</b>{@code DefaultListableBeanFactory} → {@code beanDefinitionMap.get(beanName)}。</p>
	 * <hr/>
	 * Return the bean definition for the given bean name.
	 * Subclasses should normally implement caching, as this method is invoked
	 * by this class every time bean definition metadata is needed.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to find a definition for
	 * @return the BeanDefinition for this prototype name (never {@code null})
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if the bean definition cannot be resolved
	 * @throws BeansException in case of errors
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * <h3>🔨 抽象钩子 3：createBean —— "照着图纸，把这台机器给我造出来！"</h3>
	 * <p><b>🏭【模板方法的核心变化点之二——怎么造 Bean，由子类决定！】</b></p>
	 * <p>这是整个 Spring IoC 容器最核心的扩展点，没有之一。<br/>
	 * doGetBean 的流程骨架在调用 {@code getSingleton(beanName, () -> createBean(...))} 时，
	 * 把"怎么造"的全部细节委托给了这个抽象方法。</p>
	 * <p><b>【实现者】</b>{@code AbstractAutowireCapableBeanFactory.createBean()}，内部包含：</p>
	 * <ol>
	 * <li>推断构造器（determineConstructorsFromBeanPostProcessors）</li>
	 * <li>反射实例化（instantiateBean / autowireConstructor）</li>
	 * <li>提前暴露到三级缓存（addSingletonFactory + getEarlyBeanReference）</li>
	 * <li>属性填充/依赖注入（populateBean）</li>
	 * <li>初始化回调（initializeBean → Aware → BPP.before → afterPropertiesSet → BPP.after）</li>
	 * </ol>
	 * <p><b>【与 getBeanDefinition 的对称性】</b><br/>
	 * getBeanDefinition 回答"图纸在哪"，createBean 回答"怎么造"——
	 * 这两个抽象方法构成了模板方法模式的完整变化点集合，让 AbstractBeanFactory 的流程骨架永远不需要修改。</p>
	 * <hr/>
	 * Create a bean instance for the given merged bean definition (and arguments).
	 * The bean definition will already have been merged with the parent definition
	 * in case of a child definition.
	 * <p>All bean retrieval methods delegate to this method for actual bean creation.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;


	/**
	 * <h3>架构巅峰：空间换时间与缓存失效的艺术 🧲</h3>
	 * <p>
	 * 这是 Spring 5.3 版本为了极致压榨性能而引入的神级设计！它完美地展示了在极高频的并发读取场景下，
	 * 如何用<b>“空间换时间”</b>加上<b>“写时使缓存失效 (Cache Invalidation on Write)”</b>的精妙战术。
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * CopyOnWriteArrayList which resets the beanPostProcessorCache field on modification.
	 *
	 * @since 5.3
	 */
	private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {

		/*
		 * 🗂️ 第一部分：自带“报警器”的定制花名册
		 * ---------------------------------------------------------
		 * [原理解析] Spring 5.2 及以前使用原生 CopyOnWriteArrayList，但原生集合不知道自己里面存的是什么，也无法在数据变动时通知别人。
		 * 所以 Spring 5.3 专门写了这个内部类 BeanPostProcessorCacheAwareList（具有缓存感知能力的写时复制列表）。
		 * [车间大白话] 这本花名册被安装了“物理防盗警报器”。你翻看(读)它没问题，速度极快且不用加锁；
		 * 但只要人事部对它进行了任何修改(写操作，哪怕是改一个字 set，或开除一人 remove)，
		 * 警报器就会瞬间触发，强制调用核心咒语：resetBeanPostProcessorCache()！
		 */

		@Override
		public BeanPostProcessor set(int index, BeanPostProcessor element) {
			BeanPostProcessor result = super.set(index, element);
			resetBeanPostProcessorCache(); // 💥 核心报警器！
			return result;
		}

		@Override
		public boolean add(BeanPostProcessor o) {
			boolean success = super.add(o);
			resetBeanPostProcessorCache(); // 💥 核心报警器！
			return success;
		}

		@Override
		public void add(int index, BeanPostProcessor element) {
			super.add(index, element);
			resetBeanPostProcessorCache(); // 💥 核心报警器！
		}

		@Override
		public BeanPostProcessor remove(int index) {
			BeanPostProcessor result = super.remove(index);
			resetBeanPostProcessorCache(); // 💥 核心报警器！
			return result;
		}

		@Override
		public boolean remove(Object o) {
			boolean success = super.remove(o);
			if (success) {
				resetBeanPostProcessorCache(); // 💥 核心报警器！
			}
			return success;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean success = super.removeAll(c);
			if (success) {
				resetBeanPostProcessorCache(); // 💥 核心报警器！
			}
			return success;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean success = super.retainAll(c);
			if (success) {
				resetBeanPostProcessorCache(); // 💥 核心报警器！
			}
			return success;
		}

		@Override
		public boolean addAll(Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(c);
			if (success) {
				resetBeanPostProcessorCache(); // 💥 核心报警器！
			}
			return success;
		}

		@Override
		public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(index, c);
			if (success) {
				resetBeanPostProcessorCache(); // 💥 核心报警器！
			}
			return success;
		}

		@Override
		public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
			boolean success = super.removeIf(filter);
			if (success) {
				resetBeanPostProcessorCache(); // 💥 核心报警器！
			}
			return success;
		}

		@Override
		public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
			super.replaceAll(operator);
			resetBeanPostProcessorCache(); // 💥 核心报警器！
		}
	}


	/**
	 * <h3>🗄️ 第三部分：揭秘“大黑板”的真容 (为什么非要搞缓存？) ️</h3>
	 * <p>
	 * 这是整段优化的<b>灵魂所在</b>！为什么要辛辛苦苦维护这个 {@code BeanPostProcessorCache}？
	 * 答案是：用空间换时间，彻底消灭运行时的高频 {@code instanceof} 判断！
	 * </p>
	 *
	 * <h4>🚨 性能瓶颈的真相：</h4>
	 * <p>
	 * 厂里虽然有几十个普通的质检员，但他们是有细分兵种的！造一个单例 Bean 需要经历找构造、实例化、
	 * 属性注入、初始化、销毁等繁琐阶段。如果在每个阶段，Spring 都要去全量遍历那几十个质检员，
	 * 用 {@code instanceof} 问：“你是负责销毁的吗？”<br>
	 * 假设造 10 万个 Bean，这就是<b>几百万次的 {@code instanceof} 判断</b>！这是极其灾难的性能损耗！
	 * </p>
	 *
	 * <blockquote>
	 * <b>📢 [车间大白话：大黑板的 VIP 快速通道]</b><br>
	 * 为提升造机器的速度，人事经理设计了这块“大黑板”。它把平时混在一起的质检员，按技能提前分到了 <b>4 个专属 VIP 通道</b>！<br>
	 * 👉 需要找构造函数？直接去 {@code smartInstantiationAware} 小分队！<br>
	 * 👉 需要注入 {@code @Autowired}？直接去 {@code mergedDefinition} 找咱们的【元老 2】！<br>
	 * <b>通过一次性的缓存分组，彻底消灭了运行期间无数次的类型判断！这才是 Spring 启动和运行能快如闪电的终极秘密！</b>
	 * </blockquote>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Internal cache of pre-filtered post-processors.
	 *
	 * @since 5.3
	 */
	static class BeanPostProcessorCache {
		// 1. 负责在对象实例化（new）之前/之后拦截的特种兵
		final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();

		// 2. 负责决定对象该用哪个构造函数的智能特种兵
		final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();

		// 3. 负责在对象被销毁前执行扫尾工作（如 @PreDestroy）的特种兵
		final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();

		// 4. 负责在实例化后立刻修改/合并图纸（如 @Autowired 属性解析）的特种兵
		final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
	}

}
