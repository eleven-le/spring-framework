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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.AutowiredPropertyMarker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.NativeDetector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>继承链的"第五层"——createBean 的真正执行者，Bean 从无到有的"生产车间主任"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory}</li>
 * <li><b>中文名</b>：具备自动装配能力的抽象 Bean 工厂 —— Bean 创建三部曲（实例化→注入→初始化）的总工程师</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 <b>support 包</b>（注意！support 包 = 骨架实现 + 默认实现！
 * 这里是 BeanFactory 继承链的"实现层"——从抽象骨架到最终默认实现全在这个包里。
 * 对比：{@code beans.factory} = 用户可见的顶层接口，{@code beans.factory.config} = 框架内部配置契约，
 * {@code beans.factory.support} = 骨架实现区，Abstract*BeanFactory + DefaultListableBeanFactory + Registry 全在此）</li>
 * <li><b>类层级</b>：{@code AbstractBeanFactory} 的直系子类，实现 {@code AutowireCapableBeanFactory} 接口</li>
 * </ul>
 *
 * <h3>💡 为什么需要这个类？——"提货流程"和"生产流程"必须分离！</h3>
 * <p>回顾继承链的职责分工：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>层次</th><th>类</th><th>核心职责</th><th>关键方法</th></tr>
 * <tr><td>第一层</td><td>DefaultSingletonBeanRegistry</td><td>单例缓存三级架构</td><td>getSingleton/addSingleton</td></tr>
 * <tr><td>第二层</td><td>FactoryBeanRegistrySupport</td><td>FactoryBean 产物缓存</td><td>getObjectFromFactoryBean</td></tr>
 * <tr><td>第三层</td><td>AbstractBeanFactory</td><td>getBean 提货总流程（模板方法）</td><td>doGetBean</td></tr>
 * <tr><td><b>第四层</b></td><td><b>AbstractAutowireCapableBeanFactory</b> ← 👈 你在这里！</td><td><b>createBean 生产总流程</b></td><td><b>createBean/doCreateBean</b></td></tr>
 * <tr><td>第五层</td><td>DefaultListableBeanFactory</td><td>BD 注册表 + 依赖解析</td><td>registerBeanDefinition/resolveDependency</td></tr>
 * </table>
 * <p>AbstractBeanFactory 的 doGetBean 在需要创建新 Bean 时，调用抽象方法 {@code createBean(beanName, mbd, args)}——
 * 这个抽象方法的<b>具体实现就在本类</b>！本类承接了从"拿到图纸"到"交付成品"的全部生产流程。</p>
 *
 * <h3>🧬 createBean 生产三部曲——本类的核心编排</h3>
 * <pre>
 * createBean(beanName, mbd, args)                    ← 入口：BPP 短路 + resolveBeforeInstantiation
 *   └── doCreateBean(beanName, mbd, args)             ← 真正的生产主线
 *         ├── ① createBeanInstance()                   ← 实例化：推断构造器 → 反射/CGLIB 创建原始对象
 *         │     ├── instantiateUsingFactoryMethod()    （@Bean 方法 / factory-method）
 *         │     ├── autowireConstructor()              （多参构造器自动装配）
 *         │     └── instantiateBean()                  （默认无参构造器）
 *         ├── ② populateBean()                         ← 属性注入：@Autowired/@Value/XML property
 *         │     ├── InstantiationAwareBPP.postProcessAfterInstantiation()
 *         │     ├── autowireByName() / autowireByType()
 *         │     └── InstantiationAwareBPP.postProcessProperties() ← @Autowired 真正执行处！
 *         ├── ③ initializeBean()                       ← 初始化：Aware → BPP前 → init → BPP后
 *         │     ├── invokeAwareMethods()               （BeanNameAware/BeanFactoryAware/BeanClassLoaderAware）
 *         │     ├── applyBeanPostProcessorsBeforeInitialization()  ← @PostConstruct 在此执行！
 *         │     ├── invokeInitMethods()                （InitializingBean.afterPropertiesSet + custom init-method）
 *         │     └── applyBeanPostProcessorsAfterInitialization()   ← AOP 代理在此创建！
 *         └── ④ registerDisposableBeanIfNecessary()    ← 销毁注册：记录 @PreDestroy/DisposableBean
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>三级缓存与 early reference 的配合</b><br/>
 * createBeanInstance 之后、populateBean 之前，本类会把"半成品 Bean"的 ObjectFactory 放入三级缓存
 * （{@code addSingletonFactory}）。这是解决循环依赖的关键时机——半成品已经有内存地址了，
 * 可以被其他 Bean 提前引用。<br/>
 * <b>业务借鉴</b>：当两个模块互相依赖时，可以先暴露一个"半初始化的代理/占位符"，
 * 等双方都初始化完再"填充"真实实现——这就是"先建立关系，后填充内容"的解耦思路。</li>
 *
 * <li><b>"实例化-注入-初始化"的标准化生命周期</b><br/>
 * 这三步是 Spring Bean 生命周期的黄金骨架，每一步都有对应的 BPP 扩展点：
 * InstantiationAwareBPP（实例化前后）、普通 BPP（初始化前后）。<br/>
 * <b>业务借鉴</b>：你的领域对象也可以设计类似的生命周期：创建 → 赋值 → 激活，
 * 每个阶段开放钩子让外部介入。</li>
 *
 * <li><b>本类留给子类的最后一个钩子：resolveDependency</b><br/>
 * 本类实现了 AutowireCapableBeanFactory 的所有方法，但<b>依赖解析</b>（按类型在容器中查找匹配 Bean）
 * 留给了 DefaultListableBeanFactory——因为只有它才持有 BD 注册表，能做类型匹配查找。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * DefaultSingletonBeanRegistry          （第一层：三级缓存）
 * └── FactoryBeanRegistrySupport        （第二层：FactoryBean 产物缓存）
 *       └── AbstractBeanFactory          （第三层：doGetBean 提货总流程）
 *             └── AbstractAutowireCapableBeanFactory  ← 👈 你在这里！（第四层：createBean 生产主线）
 *                   └── DefaultListableBeanFactory    （第五层：BD 注册表 + 依赖解析 + 终极合体）
 * </pre>
 *
 * <h3>🗂️ 二、核心方法·全局作战地图</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>关键方法</th></tr>
 * <tr><td>🏭 生产入口</td><td>createBean 总流程 + BPP 短路机制</td><td>createBean, resolveBeforeInstantiation</td></tr>
 * <tr><td>🔨 实例化</td><td>推断构造器 + 创建原始对象</td><td>createBeanInstance, instantiateUsingFactoryMethod, autowireConstructor</td></tr>
 * <tr><td>💉 属性注入</td><td>@Autowired/@Value 落地执行</td><td>populateBean, autowireByName, autowireByType</td></tr>
 * <tr><td>🚀 初始化</td><td>Aware回调 + BPP前后 + init</td><td>initializeBean, invokeAwareMethods, invokeInitMethods</td></tr>
 * <tr><td>🔓 对外能力输出</td><td>AutowireCapableBeanFactory 接口方法</td><td>autowireBean, configureBean, applyBeanPostProcessors*</td></tr>
 * <tr><td>🗑️ 销毁注册</td><td>记录需要销毁回调的 Bean</td><td>registerDisposableBeanIfNecessary</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AbstractAutowireCapableBeanFactory 的核心价值：<b>实现了 Bean 从无到有的完整生产流程——
 * 实例化 → 三级缓存曝光 → 属性注入 → 初始化 → AOP 代理 → 销毁注册</b>。<br/>
 * 它是 Spring IoC 最"重"的一个类（2000+ 行），但逻辑清晰地分为"实例化-注入-初始化"三大阶段，
 * 每个阶段都通过 BPP 钩子开放扩展。掌握了 doCreateBean 的主线，就掌握了 Spring Bean 生命周期的核心骨架。</p>
 *
 * <hr/>
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)}, used for
 * autowiring. In case of a {@link org.springframework.beans.factory.ListableBeanFactory}
 * which is capable of searching its bean definitions, matching beans will typically be
 * implemented through such a search. Otherwise, simplified matching can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/** Strategy for creating bean instances. */
	private InstantiationStrategy instantiationStrategy;

	/** Resolver strategy for method parameter names. */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/** Whether to automatically try to resolve circular references between beans. */
	private boolean allowCircularReferences = true;

	/**
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name to BeanWrapper. */
	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	/** Cache of candidate factory methods per factory class. */
	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	/** Cache of filtered PropertyDescriptors: bean Class to PropertyDescriptor array. */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 */
	public AbstractAutowireCapableBeanFactory() {
		super();
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
		if (NativeDetector.inNativeImage()) {
			this.instantiationStrategy = new SimpleInstantiationStrategy();
		}
		else {
			this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
		}
	}

	/**
	 * Create a new AbstractAutowireCapableBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 */
	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	/**
	 * Set the instantiation strategy to use for creating bean instances.
	 * Default is CglibSubclassingInstantiationStrategy.
	 * @see CglibSubclassingInstantiationStrategy
	 */
	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	/**
	 * Return the instantiation strategy to use for creating bean instances.
	 */
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed (e.g. for constructor names).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Return the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed.
	 */
	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Note that circular reference resolution means that one of the involved beans
	 * will receive a reference to another bean that is not fully initialized yet.
	 * This can lead to subtle and not-so-subtle side effects on initialization;
	 * it does work fine for many scenarios, though.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans. Refactor your application logic to have the two beans
	 * involved delegate to a third bean that encapsulates their common logic.
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	/**
	 * Return whether to allow circular references between beans.
	 * @since 5.3.10
	 * @see #setAllowCircularReferences
	 */
	public boolean isAllowCircularReferences() {
		return this.allowCircularReferences;
	}

	/**
	 * Set whether to allow the raw injection of a bean instance into some other
	 * bean's property, despite the injected bean eventually getting wrapped
	 * (for example, through AOP auto-proxying).
	 * <p>This will only be used as a last resort in case of a circular reference
	 * that cannot be resolved otherwise: essentially, preferring a raw instance
	 * getting injected over a failure of the entire bean wiring process.
	 * <p>Default is "false", as of Spring 2.0. Turn this on to allow for non-wrapped
	 * raw beans injected into some of your references, which was Spring 1.2's
	 * (arguably unclean) default behavior.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans, in particular with auto-proxying involved.
	 * @see #setAllowCircularReferences
	 */
	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	/**
	 * Return whether to allow the raw injection of a bean instance.
	 * @since 5.3.10
	 * @see #setAllowRawInjectionDespiteWrapping
	 */
	public boolean isAllowRawInjectionDespiteWrapping() {
		return this.allowRawInjectionDespiteWrapping;
	}

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 */
	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances
	//-------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		// Use prototype bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(beanName, bd, bw);
		return initializeBean(beanName, existingBean, bd);
	}


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle
	//-------------------------------------------------------------------------

	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		}
		else {
			Object bean;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(bd, null, this),
						getAccessControlContext());
			}
			else {
				bean = getInstantiationStrategy().instantiate(bd, null, this);
			}
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {

		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(
				existingBean, getBeanPostProcessorCache().destructionAware, getAccessControlContext()).destroy();
	}


	//-------------------------------------------------------------------------
	// Delegate methods for resolving injection points
	//-------------------------------------------------------------------------

	@Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			return getBean(name, descriptor.getDependencyType());
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
	 * <h3>💥 厂长亲临前线！机密档案：createBean 机器诞生总阀门 🏭</h3>
	 * <p>报告厂长！这是 Bean 生命周期的“绝对分水岭”与“总控闸门”！在拿着图纸 RootBeanDefinition 真正下车间开动重型机器（<code>doCreateBean</code>）之前，大管家必须在这里完成极其严密的四大核心防御与准备任务！</p>
	 * <blockquote>
	 * <b>【大管家的四大战区防御部署】</b><br/>
	 * 1. <b>动态类加载防御：</b> 把图纸上的字符串代号，死死绑定为 JVM 认识的 <code>Class</code> 实体模具。<br/> （确认实体模具 Class对象动态解析）
	 * 2. <b>多线程污染防御：</b> 绝不直接修改共享图纸，复印专属工作副本，防止并发灾难。<br/> （图纸克隆 保证多线程并发安全）
	 * 3. <b>方法重写预处理：</b> 提前给需要“偷梁换柱”的零件打上红圈，优化后续反射性能。<br/> （方法重写预处理打前置补丁）
	 * 4. <b>截胡后门（核心灵魂）：</b> 给特权质检员（BeanPostProcessor）开启最高权限，允许他们直接用魔法变出代理机器，省去极其昂贵的后续装配！
	 * </blockquote>
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>beanName</code> (入参) -> 【产品的官方名】：</b> 厂长指定的机器流水号。</li>
	 * <li><b><code>mbd</code> (入参) -> 【终极蓝图】：</b> 经过父子继承合并后的全局共享图纸 (RootBeanDefinition)。</li>
	 * <li><b><code>args</code> (入参) -> 【定制参数】：</b> 厂长如果想手动指定构造函数的参数，都在这里（通常为空）。</li>
	 * <li><b><code>return</code> (出参) -> 【完美成品 / 魔法机甲】：</b> 最终交付给厂长的机器，可能是老老实实造出来的，也可能是特权质检员用魔法截胡变出来的代理对象！</li>
	 * </ul>
	 * <hr/>
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * @see #doCreateBean
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
/* ----------------------------- 🚧 第一战区：图纸复印与类加载机制核验 ---------------------------------------- */
		/* [架构师视角] RootBeanDefinition 是 Bean 家族参数合并后的“终极蓝图”。先拿到原始引用准备操作。*/
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		/* [架构师视角] 动态类加载解析。从 String 类型的 className 通过底层的 ClassLoader 解析出真正的 java.lang.Class 对象。
		 * [动作拆解] 厂长！图纸上写的可能是个动态表达式或者字符串代号。大管家调用全厂的“类加载器”，把代号翻译成 JVM 认识的“真实钢铁模具”。如果找不到模具，机器根本无从造起！*/
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		/* 🚨 [致命瓶颈] 并发安全与防御性编程
		 * [架构师视角] 传进来的 mbd 可能是全局共享的“缓存图纸（MergedBeanDefinition）”！在多线程高并发抢单下，如果大家都往共享图纸上执行 setBeanClass，会导致严重的数据污染！
		 * [动作拆解] ⚠️ 极致的底层考量！大管家非常严谨，直接去复印机复印一份专属当前线程的“工作副本”（new RootBeanDefinition），在副本上绑定刚找到的真实模具！*/
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

/* ------------------------------------ 🛠️ 第二战区：图纸魔改与方法重写预处理（Method Overrides）----------------------------------------- */
		// Prepare method overrides.
		try {
			/* [架构师视角] 处理 <lookup-method> 和 <replaced-method> 配置。检查覆盖方法是否真实存在，并打上 overloaded 标记优化后续 CGLIB 反射性能。
			 * [动作拆解] 厂长，这是黑魔法预备役！如果我们配置了要“偷梁换柱”，这里会提前扫描图纸，严格校验想替换的零件方法是否存在，并画上红圈。这样等真正开机制造时，底层的动态代理就知道要去拦截哪些切点，省去了每次调用时再去做昂贵的全盘比对！*/
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			// [动作拆解] 如果在模具上根本找不到你想换的零件方法，图纸直接当场撕毁，拉响警报！
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

/* ---------------------------------- 🕵️‍♂️ 第三战区：特权质检员的“狸猫换太子”（代理黑盒）------------------------------------------------------------------------- */
		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			/* 🚨 [高能预警] 核心扩展后门与短路机制 (Short-circuiting)！
			 * [架构师视角] 调用 InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation。只要这里返回非 null，标准的实例化生命周期直接被放弃！
			 * [动作拆解] 🔥 全厂最逆天的后门“实例化前置解析”！机器连个铁壳子都还没打出来，大管家让【特权质检员】过来看一眼。他们可以直接用魔法（基于 TargetSource 的特殊 AOP、RPC 代理）变出一个完整的代理机器！*/
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			if (bean != null) {
				/* [动作拆解] 💥 截胡了！！质检员真的用魔法变出了成品！流水线总电闸立刻拉下，不再辛苦地反射实例化、注入属性。直接把“代理机器”打包交给您！提前下班！整个 Spring 的标准 Bean 创建生命周期直接被放弃，直接 return！*/
				return bean;
			}
		}
		catch (Throwable ex) {
			// [动作拆解] 质检员玩魔法翻车导致车间起火，带上图纸信息向上级抛出异常！
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

/* ------------------------------- ⚙️ 第四战区：拉下电闸，重型流水线真正轰鸣！ ------------------------------------------------------------------------- */
		try {
			/* [架构师视角] 核心委派。正式执行 Bean 的常规生命周期（实例化 createBeanInstance （反射造壳） -> 属性填充 populateBean （依赖注入塞属性） -> 初始化 initializeBean （初始化与 AOP 后置代理））。
			 * [动作拆解] 前面花里胡哨的截胡都没发生，特权质检员双手一摊。厂长，请深呼吸！真正最硬核、最血腥的重工业流水线 `doCreateBean` 在此正式启动！🚀 先打个生铁壳子，再把成百上千个螺丝和齿轮（依赖的其它机器对象）死命塞进去，最后通电进行极限测试（初始化方法）*/
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			// [动作拆解] 历经千辛万苦，一台冒着热气、充满机油味、完美适配所有零件的原装机器诞生了！请厂长验收入库！📦
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			// [动作拆解] 重型流水线作业中途崩溃（如循环依赖彻底死锁，或属性零件装配失败），保留车间现场原样抛出！
			throw ex;
		}
		catch (Throwable ex) {
			// [动作拆解] 发生了极其罕见的系统级大爆炸，大管家赶紧用 BeanCreationException 把爆炸碎片包起来，交由您定夺！
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
/*
* 📊 【战略复盘：超级工厂顶级架构思想】
* 报告厂长，这段代码是“开闭原则（OCP）”和“模板方法模式（Template Method）”在 Spring 底层的巅峰之作！
* 它最可怕的设计哲学在于：【短路机制（Short-Circuiting）与防腐层】。
* Spring 绝不强迫所有的对象都必须老老实实走完漫长且昂贵的重工业流水线。它在最前端开了一道极其隐蔽但权限极高的“VIP 魔法通道”（resolveBeforeInstantiation）。
* 这种设计使得 Spring 的核心骨架（IoC）和其高级能力扩展（AOP代理、RPC存根注入等）实现了完美解耦！
* 框架自身只提供骨架和扩展点，真正不可思议的魔法全交由后置处理器来实现。这不仅是空间换时间的胜利，更是架构扩展性的绝对教科书！
*/
	}

	/**
	 * <h3>💥 厂长亲临前线！机密档案：doCreateBean 重工业核心装配流水线 🏭</h3>
	 * <p>报告厂长！这里是真正执行 Bean 的创建！分为四大核心步骤：1. 实例化（打生铁壳子）；2. 提前暴露（化解循环依赖）；3. 属性填充（装配零件）；4. 初始化（通电测试与 AOP 代理包装）。最后签署报废协议，更是解决循环依赖的绝对物理核心！</p>
	 * *
	 * * <blockquote>
	 * <b>【大管家的三大断腕痛点与破局】</b><br/>
	 * 1. <b>职责极度解耦：</b> 把对象的“创建”和“赋值”彻底分开，让 Spring 能在中间穿插无数个特权质检员（后置处理器）！<br/>
	 * 2. <b>循环死锁破局：</b> A 等 B，B 等 A？直接动用【三级缓存提前曝光半成品】！没有这招，两条流水线只能互相干瞪眼直到内存溢出（OOM）栈爆！<br/>
	 * 3. <b>终极扩展性：</b> 机器造好通电前后，留出大量后置处理器（PostProcessor）切入点，方便无缝植入 AOP 机甲。
	 * </blockquote>
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>beanName</code> (入参) -> 【产品的官方名】：</b> 流水线上的机器编号。</li>
	 * <li><b><code>mbd</code> (入参) -> 【终极蓝图】：</b> 包含所有制造参数的合并图纸。</li>
	 * <li><b><code>args</code> (入参) -> 【定制参数】：</b> 手动指定的构造参数（通常为空）。</li>
	 * <li><b><code>return</code> (出参) -> 【满血终极机器】：</b> 历经千锤百炼，打好螺丝、披上机甲、签完生死状的完美单例成品！</li>
	 * </ul>
	 * <hr/>
	 *
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 */
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {
/* ----------------------------------- 🚧 第一战区：熔炉锻造！打出一个生铁壳子 🚧------------------------------------- */
		// Instantiate the bean.
		/* [架构师视角] Bean的实例化（Instantiation）。使用反射机制（无参/有参构造器、工厂方法）在堆内存中开辟空间，创建出对象的原生实例。
		 * [动作拆解] 先准备个包装盒（BeanWrapper）来装即将出炉的机器外壳。*/
		BeanWrapper instanceWrapper = null;
		// [动作拆解] 如果是单例机器，先看看工厂内部特殊的缓存区（FactoryBean相关）有没有上次没造完留下的废料或壳子，有的话清掉拿出来用。
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			/* [架构师视角] 核心实例化！基于反射或 CGLIB 字节码生成，调用构造函数创建一个“空属性”的对象。
			 * [动作拆解] 🔥 开炉！调用 createBeanInstance，不管你里面要装多少复杂的齿轮，这里只负责用图纸打出一个空荡荡的“生铁壳子”！注意！这个时候壳子里面是没有任何属性零件的（依赖全为 null）！*/
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		// [动作拆解] 把刚打出来的生铁壳子（原始原生对象）和它的材质型号（Class类型）提出来备用。
		Object bean = instanceWrapper.getWrappedInstance();
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

/* ------------------------------  🛠️ 第二战区：图纸终审，特权质检员做记号  --------------------------------------- */
		// Allow post-processors to modify the merged bean definition.
		/* [架构师视角] 调用 MergedBeanDefinitionPostProcessor。允许后置处理器在 Bean 实例化之后、属性注入之前，修改或缓存 BeanDefinition 的元数据（如解析 @Autowired、@PostConstruct 注解并缓存）。*/
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					/* [动作拆解] 生铁壳子刚打出来，流水线暂停！放那群专门看注解的质检员（比如 AutowiredAnnotationBeanPostProcessor）进场。
					 * 他们拿着放大镜看机器壳子上的 @Autowired 标记，然后在图纸上画圈：“等会儿打螺丝的时候，往这里塞 A 零件，往那里塞 B 零件！”*/
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				// 审过图纸了，盖个戳
				mbd.postProcessed = true;
			}
		}

/* ----------------------------------- 🚨 第三战区：化解死锁！三级缓存的提前暴露机制 ------------------------------------------ */
		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.

		/* 🚨 [致命瓶颈] 判断是否需要提前曝光自己。
		 * [架构师视角] 条件：是单例 && 允许循环依赖 && 当前 Bean 正在创建中。
		 * [动作拆解] 厂长，全厂最精妙的“防死锁”逻辑来了！此时机器只是个壳子，还没装零件。*/
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			/* 🌟 [高能预警] 提前暴露的三级缓存钩子！
			 * [架构师视角] 将一段包含“早期半成品引用”的 Lambda 表达式塞进三级缓存（singletonFactories）中。如果发生循环依赖，其他 Bean 可以通过它提前拿到未完全装配的引用（或提早生成的 AOP 代理引用）。
			 * [动作拆解] 为了防止等会儿装零件时别人也在等我造完而互相卡死（循环依赖）。大管家直接把这个“生铁壳子”绑上一个“微型处理厂（Lambda表达式）”，扔进【三级缓存】（VIP预售货架）！ 并向全厂广播：“A 机器壳子在这！如果谁着急要装配 A，别等 A 完全造好了，先从预售货架把这壳子的线头拉过去用着！”*/
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

/* --------------------------------------  ⚙️ 第四战区：疯狂打螺丝与通电激活 ---------------------------------- */
		// Initialize the bean instance.
		// 暴露给外部的机器，目前还是那个壳子
		Object exposedObject = bean;
		try {
			/* [架构师视角] 依赖注入（DI）核心环节。解析 @Autowired 等注解，并将其他 Bean 注入到当前 Bean 的属性中。
			 * [动作拆解] 🔥 流水线最狂暴的一段！几百个机械臂一起上阵！图纸上写了缺什么零件，这里就去全厂翻箱倒柜找零件塞进去！如果零件没造好，就触发递归去造零件！*/
			populateBean(beanName, mbd, instanceWrapper);

			/* 🌟 [终极原爆点] 代理机甲的诞生！
			 * [架构师视角] 初始化（Initialization）。执行 Aware 接口方法、@PostConstruct、InitializingBean#afterPropertiesSet、自定义 init-method，最后执行 BeanPostProcessor#postProcessAfterInitialization（这里是 AOP 产生动态代理的绝对核心点）！
			 * [动作拆解] ⚡ 通电开机！执行 initializeBean！检查机器能不能转，跑跑开机脚本。🔥 最关键的是：在这步的最后，高级质检员可能会走过来，嫌弃原生机器太脆弱，直接拿一个穿了厚重铠甲的“AOP 代理机器”把原来的壳子给掉包了（exposedObject 变身）！*/
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			// [动作拆解] 装配或者通电测试时炸机了，向上汇报爆炸原因！
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

/* ------------------------------------------- 🕵️‍♂️ 第五战区：循环依赖的极其严格审计 ------------------------------------------ */
		if (earlySingletonExposure) {
			/* [架构师视角] 从一级或二级缓存中获取早期引用（此时 getSingleton 传 false，表示不触发三级缓存，只查一二级缓存）。
			 * [动作拆解] 如果之前我们把壳子挂到预售货架了，现在机器完全造好了，大管家要来对账了！*/
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				/* [动作拆解] 如果 exposedObject == bean，说明刚才通电时（initializeBean）没有被套 AOP 皮！  那别人提前拿走的生铁外壳，跟我现在打完螺丝的机器是同一个，完全合法，直接把最终版本替换过去！*/
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				/* 🚨 [高能预警] 版本不一致的死罪审计！
				 * [架构师视角] 极其严苛的防御！如果对象在 initializeBean 时被包装成了代理对象（exposedObject != bean），并且有其他 Bean 已经通过循环依赖注入了早期的“原生对象”...
				 * [动作拆解] 🚨 致命错误发生！如果现在的机器被套皮了（!=bean），而且别人已经把旧的生铁壳子拿走装进了他们自己的机器里！这意味着全厂出现了数据不一致！别人装的是老壳子，你这里最终出厂的是新装甲！这是死罪，绝不允许发生！*/
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						// [动作拆解] 大管家去查账本：“刚才谁提前拿走了我的生铁壳子去装配了？”
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							// 记录下被坑的机器
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						/* [动作拆解] 💣 严重生产事故预警！B 机器之前拿走了 A 的生铁壳子装在自己身上，结果 A 在最后通电时被质检员（AOP）换成了“铠甲代理版 A”！
						 * 这意味着 B 机器里装的 A 是旧版残次品，而最终交到客户手里的 A 是尊贵代理版，版本不一致，直接抛异常拉响最高警报！抛出 BeanCurrentlyInCreationException！*/
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

/* ---------------------------------------- 📝 第六战区：签订报废协议 ------------------------------------------- */
		// Register bean as disposable.
		try {
			/* [架构师视角] 注册实现了 DisposableBean 接口或自定义 destroy-method 的 Bean，以便在 Spring 容器关闭时能够正确地被销毁。
			 * [动作拆解] 这台机器即将出厂！出厂前，大管家会拿个小本本把这台机器的名字记下来，等哪天整个超级工厂破产倒闭（容器关闭）时，按名册一台台去执行报废流程排空机油！*/
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}
		// [动作拆解] 🎉 厂长！披甲上阵、通电完美运行、签订好报废协议的满血版终极机器，正式交付给您！
		return exposedObject;
/*
 * 📊 【战略复盘：超级工厂顶级架构思想】
 * 厂长，这段代码是整个 Spring 框架之所以能称霸 Java 界的定海神针！它完美诠释了两个顶级架构思想：
 *
 * 1. 生命周期的极致碎片化 (Lifecycle Fragmentation & Hooks)：
 * 造机器绝不是一气呵成的。Spring 把制造过程残酷地肢解为：实例化（打壳）、属性赋值（装零件）、初始化（通电测试）。这种精密的解耦设计，为第三方框架（如 MyBatis、Dubbo）提供了无数个无缝“插桩”和“换零件”的干预点，使得扩展能力达到极限。
 *
 * 2. 三级缓存破解死锁（空间换时间）：
 * 面对 A、B 互相依赖的循环死局，Spring 利用 Java 对象“引用传递”的底层特性。在对象刚“打出个壳子”还没装零件时，就果断将其提前“半曝光”（封装为 ObjectFactory 加入第三级缓存）。这种“先上车后补票”的设计，是现代复杂依赖倒置框架解决拓扑环状图的最佳实战典范！
 */
	}

	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);
		// Apply SmartInstantiationAwareBeanPostProcessors to predict the
		// eventual type after a before-instantiation shortcut.
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			boolean matchingOnlyFactoryBean = (typesToMatch.length == 1 && typesToMatch[0] == FactoryBean.class);
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				Class<?> predicted = bp.predictBeanType(targetType, beanName);
				if (predicted != null &&
						(!matchingOnlyFactoryBean || FactoryBean.class.isAssignableFrom(predicted))) {
					return predicted;
				}
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 */
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition which is based on
	 * a factory method. Only called if there is no singleton instance registered
	 * for the target bean already.
	 * <p>This implementation determines the type matching {@link #createBean}'s
	 * different creation strategies. As far as possible, we'll perform static
	 * type checking to avoid creation of the target bean.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see #createBean
	 */
	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			return cachedReturnType.resolve();
		}

		Class<?> commonType = null;
		Method uniqueCandidate = mbd.factoryMethodToIntrospect;

		if (uniqueCandidate == null) {
			Class<?> factoryClass;
			boolean isStatic = true;

			String factoryBeanName = mbd.getFactoryBeanName();
			if (factoryBeanName != null) {
				if (factoryBeanName.equals(beanName)) {
					throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
							"factory-bean reference points back to the same bean definition");
				}
				// Check declared factory method return type on factory class.
				factoryClass = getType(factoryBeanName);
				isStatic = false;
			}
			else {
				// Check declared factory method return type on bean class.
				factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
			}

			if (factoryClass == null) {
				return null;
			}
			factoryClass = ClassUtils.getUserClass(factoryClass);

			// If all factory methods have the same return type, return that type.
			// Can't clearly figure out exact method due to type converting / autowiring!
			int minNrOfArgs =
					(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
			Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(factoryClass,
					clazz -> ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS));

			for (Method candidate : candidates) {
				if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
						candidate.getParameterCount() >= minNrOfArgs) {
					// Declared type variables to inspect?
					if (candidate.getTypeParameters().length > 0) {
						try {
							// Fully resolve parameter names and argument values.
							Class<?>[] paramTypes = candidate.getParameterTypes();
							String[] paramNames = null;
							ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
							if (pnd != null) {
								paramNames = pnd.getParameterNames(candidate);
							}
							ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
							Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
							Object[] args = new Object[paramTypes.length];
							for (int i = 0; i < args.length; i++) {
								ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
										i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
								if (valueHolder == null) {
									valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
								}
								if (valueHolder != null) {
									args[i] = valueHolder.getValue();
									usedValueHolders.add(valueHolder);
								}
							}
							Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
									candidate, args, getBeanClassLoader());
							uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
									candidate : null);
							commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
							if (commonType == null) {
								// Ambiguous return types found: return null to indicate "not determinable".
								return null;
							}
						}
						catch (Throwable ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Failed to resolve generic return type for factory method: " + ex);
							}
						}
					}
					else {
						uniqueCandidate = (commonType == null ? candidate : null);
						commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
						if (commonType == null) {
							// Ambiguous return types found: return null to indicate "not determinable".
							return null;
						}
					}
				}
			}

			mbd.factoryMethodToIntrospect = uniqueCandidate;
			if (commonType == null) {
				return null;
			}
		}

		// Common return type found: all factory methods return same type. For a non-parameterized
		// unique candidate, cache the full type declaration context of the target factory method.
		cachedReturnType = (uniqueCandidate != null ?
				ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		mbd.factoryMethodReturnType = cachedReturnType;
		return cachedReturnType.resolve();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, it checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet and {@code allowInit} is {@code true}, full
	 * creation of the FactoryBean is attempted as fallback (through delegation to the
	 * superclass implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		// Check if the bean definition itself has defined the type with an attribute
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		ResolvableType beanType =
				(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : ResolvableType.NONE);

		// For instance supplied beans try the target type and bean class
		if (mbd.getInstanceSupplier() != null) {
			result = getFactoryBeanGeneric(mbd.targetType);
			if (result.resolve() != null) {
				return result;
			}
			result = getFactoryBeanGeneric(beanType);
			if (result.resolve() != null) {
				return result;
			}
		}

		// Consider factory methods
		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		// Scan the factory bean methods
		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				// Try to obtain the FactoryBean's object type from its factory method
				// declaration without instantiating the containing bean at all.
				BeanDefinition factoryBeanDefinition = getBeanDefinition(factoryBeanName);
				Class<?> factoryBeanClass;
				if (factoryBeanDefinition instanceof AbstractBeanDefinition &&
						((AbstractBeanDefinition) factoryBeanDefinition).hasBeanClass()) {
					factoryBeanClass = ((AbstractBeanDefinition) factoryBeanDefinition).getBeanClass();
				}
				else {
					RootBeanDefinition fbmbd = getMergedBeanDefinition(factoryBeanName, factoryBeanDefinition);
					factoryBeanClass = determineTargetType(factoryBeanName, fbmbd);
				}
				if (factoryBeanClass != null) {
					result = getTypeForFactoryBeanFromMethod(factoryBeanClass, factoryMethodName);
					if (result.resolve() != null) {
						return result;
					}
				}
			}
			// If not resolvable above and the referenced factory bean doesn't exist yet,
			// exit here - we don't want to force the creation of another bean just to
			// obtain a FactoryBean's object type...
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return ResolvableType.NONE;
			}
		}

		// If we're allowed, we can create the factory bean and call getObjectType() early
		if (allowInit) {
			FactoryBean<?> factoryBean = (mbd.isSingleton() ?
					getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
					getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));
			if (factoryBean != null) {
				// Try to obtain the FactoryBean's object type from this early stage of the instance.
				Class<?> type = getTypeForFactoryBean(factoryBean);
				if (type != null) {
					return ResolvableType.forClass(type);
				}
				// No type found for shortcut FactoryBean instance:
				// fall back to full creation of the FactoryBean instance.
				return super.getTypeForFactoryBean(beanName, mbd, true);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass() && factoryMethodName != null) {
			// No early bean instantiation possible: determine FactoryBean's type from
			// static factory method signature or from class inheritance hierarchy...
			return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
		}
		result = getFactoryBeanGeneric(beanType);
		if (result.resolve() != null) {
			return result;
		}
		return ResolvableType.NONE;
	}

	private ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		if (type == null) {
			return ResolvableType.NONE;
		}
		return type.as(FactoryBean.class).getGeneric();
	}

	/**
	 * Introspect the factory method signatures on the given bean class,
	 * trying to find a common {@code FactoryBean} object type declared there.
	 * @param beanClass the bean class to find the factory method on
	 * @param factoryMethodName the name of the factory method
	 * @return the common {@code FactoryBean} object type, or {@code null} if none
	 */
	private ResolvableType getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {
		// CGLIB subclass methods hide generic parameters; look at the original user class.
		Class<?> factoryBeanClass = ClassUtils.getUserClass(beanClass);
		FactoryBeanMethodTypeFinder finder = new FactoryBeanMethodTypeFinder(factoryMethodName);
		ReflectionUtils.doWithMethods(factoryBeanClass, finder, ReflectionUtils.USER_DECLARED_METHODS);
		return finder.getResult();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, a full creation of the FactoryBean is
	 * used as fallback (through delegation to the superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	@Deprecated
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param bean the raw bean instance
	 * @return the object to expose as bean reference
	 */
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
			}
		}
		return exposedObject;
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Obtain a "shortcut" singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			Object beanInstance = getSingleton(beanName, false);
			if (beanInstance instanceof FactoryBean) {
				return (FactoryBean<?>) beanInstance;
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance;
			try {
				// Mark this bean as currently in creation, even if just partially.
				beforeSingletonCreation(beanName);
				// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			}
			catch (UnsatisfiedDependencyException ex) {
				// Don't swallow, probably misconfiguration...
				throw ex;
			}
			catch (BeanCreationException ex) {
				// Don't swallow a linkage error since it contains a full stacktrace on
				// first occurrence... and just a plain NoClassDefFoundError afterwards.
				if (ex.contains(LinkageError.class)) {
					throw ex;
				}
				// Instantiation failure, maybe too early...
				if (logger.isDebugEnabled()) {
					logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
				}
				onSuppressedException(ex);
				return null;
			}
			finally {
				// Finished partial creation of this bean.
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	/**
	 * Obtain a "shortcut" non-singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance;
		try {
			// Mark this bean as currently in creation, even if just partially.
			beforePrototypeCreation(beanName);
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		}
		catch (UnsatisfiedDependencyException ex) {
			// Don't swallow, probably misconfiguration...
			throw ex;
		}
		catch (BeanCreationException ex) {
			// Instantiation failure, maybe too early...
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		}
		finally {
			// Finished partial creation of this bean.
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	/**
	 * Apply MergedBeanDefinitionPostProcessors to the specified bean definition,
	 * invoking their {@code postProcessMergedBeanDefinition} methods.
	 * @param mbd the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
	 */
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
			processor.postProcessMergedBeanDefinition(mbd, beanType, beanName);
		}
	}

	/**
	 * Apply before-instantiation post-processors, resolving whether there is a
	 * before-instantiation shortcut for the specified bean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the shortcut-determined bean instance, or {@code null} if none
	 */
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}

	/**
	 * Apply InstantiationAwareBeanPostProcessors to the specified bean definition
	 * (by class and name), invoking their {@code postProcessBeforeInstantiation} methods.
	 * <p>Any returned object will be used as the bean instead of actually instantiating
	 * the target bean. A {@code null} return value from the post-processor will
	 * result in the target bean being instantiated.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to use instead of a default instance of the target bean, or {@code null}
	 * @see InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 */
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
			Object result = bp.postProcessBeforeInstantiation(beanClass, beanName);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * <h3>💥 厂长亲临前线！机密档案：createBeanInstance 生铁壳子锻造熔炉 🏭</h3>
	 * <p>报告厂长！这里是装配流水线的第一座高炉！它不负责装配任何精密的依赖零件，它的唯一使命就是：不管用什么奇技淫巧，必须在 JVM 的堆内存（全厂最大空地）里，硬生生地砸出一个机器的原生外壳！</p>
	 * *
	 * * <blockquote>
	 * <b>【大管家面临的炼狱级推断与破局】</b><br/>
	 * 1. <b>海量模具选型：</b> 机器来源太复杂！有 <code>@Bean</code> 工厂方法、Java 8 <code>Supplier</code>、带 <code>@Autowired</code> 的多参构造器、普通无参构造器……大管家必须精准命中目标模具！<br/>
	 * 2. <b>反射性能灾难：</b> 找构造器、匹配参数极其耗费 CPU。如果是多例（Prototype）机器第二次来造，绝对不能再走一遍推断逻辑，必须做【缓存短路】！
	 * </blockquote>
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>beanName</code> (入参) -> 【产品的官方名】：</b> 流水线上的机器编号。</li>
	 * <li><b><code>mbd</code> (入参) -> 【终极蓝图】：</b> 包含所有制造参数的合并图纸。</li>
	 * <li><b><code>args</code> (入参) -> 【定制参数】：</b> 手动指定的构造参数（通常为空）。</li>
	 * <li><b><code>return</code> (出参) -> 【包装好的生铁壳子】：</b> 用 <code>BeanWrapper</code> 紧紧包裹的原生实例，方便后续通过反射疯狂打螺丝。</li>
	 * </ul>
	 * <hr/>
	 * Create a new instance for the specified bean, using an appropriate instantiation strategy:
	 * factory method, constructor autowiring, or simple instantiation.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a BeanWrapper for the new instance
	 * @see #obtainFromSupplier
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * @see #instantiateBean
	 */
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.
/* ------------------------------  🚧 第一战区：材质安检与图纸终审 -------------------------------------- */

		/* [架构师视角] 确保 Bean 的 Class 已经被解析加载。
		 * [动作拆解] 开炉前，大管家最后一次确认图纸上写的机器型号（Class）是不是真的已经被全厂的“模具库（ClassLoader）”找到了！找不到绝对不准开火！*/
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		/* [架构师视角] 访问权限校验。如果类不是 public 的，且配置不允许非 public 访问，则直接抛异常拦截。
		 * [动作拆解] 大管家严查模具的“保密级别”！如果这套模具是别人家私有的（非 public），而且厂长没给大管家批“强行撬锁（暴力反射）”的特权，那就直接拉响警报，图纸撕毁！*/
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

/* --------------------------  🚀 第二战区：外包供应商（Supplier）直达通道 ----------------------------------- */
		// 【原理解析：Spring 5.0 引入的新特性，支持通过 Java 8 的 Supplier 函数式接口直接提供实例，跳过所有反射和构造器推断逻辑。】
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			/* [动作拆解] ✨ 厂长，最新的 VIP 绿色通道！如果图纸上绑了外部供应商（Supplier），大管家根本不去反射找什么构造器了，直接给供应商打电话，让他把壳子送过来！极速下班！*/
			return obtainFromSupplier(instanceSupplier, beanName);
		}

/* ---------------------------------  🏭 第三战区：专属代工厂（Factory-Method）直达通道 ------------------------------------ */
		/* [架构师视角] 处理 XML 中的 factory-method 属性或 @Configuration 类中的 @Bean 方法。*/
		if (mbd.getFactoryMethodName() != null) {
			/* [动作拆解] 如果是通过 @Bean 注册的图纸，图纸上会写明“这个壳子必须由 xxx 代工厂的方法来造”。大管家立刻启动代工厂专用流水线（instantiateUsingFactoryMethod）去调用那个特定的方法把机器造出来，彻底跳过当前类的构造器！*/
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// Shortcut when re-creating the same bean...
/* ---------------------------------------  ⚡ 第四战区：多例量产捷径（短路缓存） ---------------------------- */
		/* 🚨 [致命瓶颈] 反射性能极客优化
		 * [架构师视角] 多例 Bean（Prototype）每次 getBean 都会来此。为了防止每次极其昂贵地推断构造器和解析参数，这里使用锁和缓存机制直接复用上次解析好的构造器。*/
		// 标记：构造器是不是之前已经推断过了？
		boolean resolved = false;
		// 标记：这个构造器是不是带参数、需要自动注入零件的？
		boolean autowireNecessary = false;
		// [动作拆解] 如果没传特殊参数，大管家要看看这图纸是不是“回头客”（主要针对 Prototype 多例机器）。
		if (args == null) {
			// 加锁，防止多个产线同时试图解析同一个多例图纸的构造器
			synchronized (mbd.constructorArgumentLock) {
				/*  [动作拆解] 翻开图纸的缓存页，看看 resolvedConstructorOrFactoryMethod 有值没？ 如果有，说明上次造同型号机器时，大管家已经费尽九牛二虎之力算出了该用哪个构造器了！ */
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					// 直接标记为已解析！
					resolved = true;
					// 顺便查一下上次记录的，这个构造器需不需要塞参数？
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			// [动作拆解] 🏎️ 既然是回头客，直接走量产捷径！
			if (autowireNecessary) {
				// [动作拆解] 带参数，拿上次算好的参数去造（调用 autowireConstructor）。
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				// [动作拆解] 如果不带参数，直接无脑开模反射（调用 instantiateBean）！
				return instantiateBean(beanName, mbd);
			}
		}

		// Candidate constructors for autowiring?
/* ------------------------------------------🧠 第五战区：地狱级智能推断（Autowired 构造器解析）-------------------- */
		/* 🌟 [高能预警] 构造器推断核心切入点
		 * [架构师视角] 调用 SmartInstantiationAwareBeanPostProcessor 后置处理器，推断带有 @Autowired 的构造器。这是 Spring 依赖注入在构造器层面的核心。
		 * [动作拆解] 🔥 如果上面捷径没走通，说明是第一次开荒！大管家喊来高级质检员（AutowiredAnnotationBeanPostProcessor），用放大镜去模具上扫描，看看哪个构造器打了 @Autowired 标签？
		 */
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);

		/* [架构师视角] 命中带参构造器的 4 种条件：1. 后置处理器找到 @Autowired 构造器 (ctors != null) ; 2. XML/API 强制配置了构造器自动注入 (AUTOWIRE_CONSTRUCTOR); 3.  图纸里明确给了构造器参数值 (hasConstructorArgumentValues); 4.  用户调用 getBean 时手动传了参数 (!ObjectUtils.isEmpty(args))。*/
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			/* [动作拆解] 只要满足上述条件，生铁壳子“不能用普通空模具打”，必须用特种模具！大管家启动全厂最复杂的 `autowireConstructor` 流水线，在造壳子的瞬间把零件塞进去！*/
			return autowireConstructor(beanName, mbd, ctors, args);
		}

/* ---------------------------------  ⭐ 第六战区：首选构造器兜底（Preferred Constructors） ------------------ */
		/*  [架构师视角] 为 Kotlin 等语言提供支持，获取类设计时指定的主构造器（Primary Constructor）。 */
		// Preferred constructors for default construction?
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			// [动作拆解] 如果在 Kotlin 里写了主构造器，大管家优先拿这个主模具去打壳子！
			return autowireConstructor(beanName, mbd, ctors, null);
		}

/* ------------------------------ 🛡️ 第七战区：最最基础的无参兜底 ------------------------------ */
		/* [架构师视角] 如果所有推断都没命中，说明这是一个最普通的 POJO，直接调用类的无参构造器进行反射实例化。
		 * [动作拆解] 前面高级玩法都没有？没有任何 @Autowired，没有任何 @Bean？那就什么都不想了！直接拿最基础的“无参模具”，一锤子砸下去，一个干干净净的生铁壳子诞生！*/
		// No special handling: simply use no-arg constructor.
		return instantiateBean(beanName, mbd);
/* 📊 【战略复盘：超级工厂顶级架构思想】
 * 厂长，这段代码是“策略模式（Strategy Pattern）”和“性能极客精神”在 Spring 底层的完美秀场！
 *
 * 1. 海纳百川 (Unified Instantiation Gateway)：
 * 不管您的机器是怎么定义的（Supplier、@Bean方法、@Autowired构造器、普通无参），它提供了一个极其统一的调度枢纽。把复杂的“怎么造”剥离成了不同的策略子程序。
 *
 * 2. 极速短路 (Caching & Short-circuiting)：
 * Java 的反射找构造方法，尤其在有继承和多个重载时，是性能灾难难（需要全盘遍历并做类型匹配）。Spring 在第四战区巧妙地使用 resolvedConstructorOrFactoryMethod 做了缓存，对于 Prototype 多例机器，只有第一次造需要推断，后面全都是 O(1) 的极速通道！这才是真正的架构极客！*/
	}

	/**
	 * Obtain a bean instance from the given supplier.
	 * @param instanceSupplier the configured supplier
	 * @param beanName the corresponding bean name
	 * @return a BeanWrapper for the new instance
	 * @since 5.0
	 * @see #getObjectForBeanInstance
	 */
	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		String outerBean = this.currentlyCreatedBean.get();
		this.currentlyCreatedBean.set(beanName);
		try {
			instance = instanceSupplier.get();
		}
		finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}

		if (instance == null) {
			instance = new NullBean();
		}
		BeanWrapper bw = new BeanWrapperImpl(instance);
		initBeanWrapper(bw);
		return bw;
	}

	/**
	 * Overridden in order to implicitly register the currently created bean as
	 * dependent on further beans getting programmatically retrieved during a
	 * {@link Supplier} callback.
	 * @since 5.0
	 * @see #obtainFromSupplier
	 */
	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	/**
	 * Determine candidate constructors to use for the given bean, checking all registered
	 * {@link SmartInstantiationAwareBeanPostProcessor SmartInstantiationAwareBeanPostProcessors}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors
	 */
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				Constructor<?>[] ctors = bp.determineCandidateConstructors(beanClass, beanName);
				if (ctors != null) {
					return ctors;
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate the given bean using its default constructor.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			if (System.getSecurityManager() != null) {
				beanInstance = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
						getAccessControlContext());
			}
			else {
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
			}
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * mbd parameter specifies a class, rather than a factoryBean, or an instance variable
	 * on a factory object itself configured using Dependency Injection.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (implying the use of constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * @see #getBean(String, Object[])
	 */
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}

	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param ctors the chosen candidate constructors
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (implying the use of constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * <h3>💥 厂长亲临前线！机密档案：populateBean 精密装配流水线 🏭</h3>
	 * <p>报告厂长！如果说前一步 <code>createBeanInstance</code> 只是造了个“没有灵魂的躯壳”，那么这里就是把数据库连接、各种 Service 引擎、成百上千的齿轮，暴力又精准地硬塞进这个躯壳里，赋予它真正的生命！</p>
	 * <p>这里是 Spring 帝国最繁忙、火花最四溅的十字路口！全厂最引以为傲的依赖注入（DI）、您最熟悉的 @Autowired、@Resource，以及老派的 XML 自动装配，全部都在这里进行最血腥的落地执行！<p/>
	 * * <blockquote>
	 * <b>【大管家的装配哲学与破局】</b><br/>
	 * 1. <b>极致解耦：</b> 坚决把“造壳”和“塞零件”物理切开！这是 Spring 能够利用三级缓存打破“循环依赖”死锁的绝对前提！（壳子先曝光，零件慢慢塞）。<br/>
	 * 2. <b>总线级兼容：</b> XML 的 <code>byName/byType</code>、现代的 <code>@Autowired</code>、Java EE 的 <code>@Resource</code>……这里提供了一个极其统一的“总线架构”，用各种 PostProcessor（特权质检员）把所有注入方式统统兼容吸收！
	 * </blockquote>
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>beanName</code> (入参) -> 【产品的官方名】：</b> 正在流水线上挨着螺丝枪的机器编号。</li>
	 * <li><b><code>mbd</code> (入参) -> 【终极蓝图】：</b> 记录了这台机器到底需要塞多少个依赖零件的图纸。</li>
	 * <li><b><code>bw</code> (入参) -> 【包装好的生铁壳子】：</b> 上一步 <code>createBeanInstance</code> 刚打出来、里面空空如也的原生实例。</li>
	 * </ul>
	 * <hr/>
	 *
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw the BeanWrapper with bean instance
	 */
	@SuppressWarnings("deprecation")  // for postProcessPropertyValues
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
/* ------------------------------ 🚧 第一战区：空壳子防痴呆校验 ----------------------------------- */
		/* [架构师视角] 判空防御。如果连包装机器的 Wrapper 都没有，说明壳子都没打出来，直接抛异常或跳过。
		 * [动作拆解] 图纸上明明写着要装 10 个零件（hasPropertyValues），结果你连个机器壳子（bw）都没给我送过来？皮之不存毛将焉附，直接抛异常拉响警报！*/
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				// 【车间大白话】：图纸上明明写着要装 10 个零件（hasPropertyValues），结果你连个机器壳子（bw）都没给我送过来？皮之不存毛将焉附，直接抛异常拉响警报！
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				// [动作拆解] 图纸上本来也就没写要装零件，壳子也没有，那就算了，直接下班！
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
/* -------------------------------------- 🛑 第二战区：装配前夕的“一票否决权” --------------------------- */
		/* 🚨 [致命瓶颈] 装配阻断后门
		 * [架构师视角] 调用 InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation。这是留给第三方框架的终极后门，允许在属性注入前强行改变 Bean 的状态，或直接返回 false 中断整个 Spring 的 DI 流程。*/
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				/* [动作拆解] 🔥 高能预警！开始搬零件前，大管家让一群“特权质检员”最后看一眼生铁壳子。如果某个审计员大喊一声“停！这台机器我亲自来接管，不需要走你们的装配线了”（返回 false）。
				 * 那么整个 populateBean 方法直接 return，后面的 @Autowired 统统失效，装配彻底中断！这通常用于极少数特殊的自定义场外注入框架！*/
				if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					return;
				}
			}
		}

		// [动作拆解] 准备小推车：把图纸上已经写明要手动装的零件清单（PropertyValues）先拿在手里。
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

/* ------------------------------------------------🕰️ 第三战区：老派重工业的自动装配（XML 时代的荣光）------------------------------- */
		/* [架构师视角] 处理基于 XML 配置的 autowire="byName" 或 "byType"。扫描 Bean 中所有的 Setter 方法，去容器里找匹配的 Bean 塞进去（早期的推断逻辑）。*/
		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// Add property values based on autowire by name if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				// [动作拆解] 按名字找零件！去找 Setter 方法（如 setDbEngine），然后去全厂按名字找 "dbEngine" 机器，找到就扔进小推车（newPvs）！
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				// [动作拆解] 按类型找零件！去找 Setter 方法的参数类型，然后去全厂扫雷，把类型匹配的机器扔进小推车！
				autowireByType(beanName, mbd, bw, newPvs);
			}
			// [动作拆解] 把小推车里新找来的零件也合并起来
			pvs = newPvs;
		}

		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		PropertyDescriptor[] filteredPds = null;
/* ------------------------------------------------  ⚡ 第四战区：现代高科技装配的灵魂（注解解析狂欢）-------------- */
		if (hasInstAwareBpps) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			/* 🌟 [终极原爆点] 现代注解驱动依赖注入
			 * [架构师视角] 循环调用 postProcessProperties。这里是 @Autowired、@Value、@Resource 等现代注解生效的绝对核心现场！AutowiredAnnotationBeanPostProcessor 和 CommonAnnotationBeanPostProcessor 在此大显神威。*/
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				/* [动作拆解] 💥 厂长，全场最高潮来了！大管家把高级质检员（比如专门负责 @Autowired 的质检员）喊过来。 他们拿着激光扫描仪，直接扫描机器壳子里的每一个 private 字段、每一个方法。
				 * 只要看到上面贴了 @Autowired 或 @Resource 的标签，立马现场跑去其他车间拉客（触发 getBean），把依赖的机器死活拉过来，直接利用反射强行注入进去！*/
				PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
				if (pvsToUse == null) {
					// 兼容老版本 Spring 的废弃方法 postProcessPropertyValues
					if (filteredPds == null) {
						filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
					}
					pvsToUse = bp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
					if (pvsToUse == null) {
						// 如果质检员处理失败，强行终止流水线
						return;
					}
				}
				pvs = pvsToUse;
			}
		}
/* ---------------------------------------🕵️‍♂️ 第五战区：严苛的漏检核查（Dependency Check） ---------------- */
		/*  [架构师视角] 如果图纸要求依赖检查（如规定所有对象类型的属性必须被赋值），进行反射校验。如果发现有 Setter 没被注入，直接抛异常。 */
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			/* [动作拆解] 老派的质检环节。如果厂长配置了严格的依赖检查，大管家会对照机器上的所有孔位（Setter方法）。
			 * 只要发现有一个孔位空着没塞零件，当场翻脸，摔图纸抛异常！不过这功能现在很少用了，大家都用 @Autowired(required = true) 代替了。*/
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

/* ------------------------------ 🔧 第六战区：暴力拧螺丝，物理锁定（Apply Property Values）------------------------------------------------------ */
		/* [架构师视角] 如果在前面第一、三战区中 通过 XML 或手动配置收集到了属性值（存放在 pvs 中），这里将通过 BeanWrapper 进行统一的类型转换和反射 Set 设值。 */
		if (pvs != null) {
			/* [动作拆解] 最后一步！前面收集了一小推车的零件（比如 <property name="age" value="18"/>）。
			 * 这里就是真正的“拧螺丝工序”！遇到字符串 "18"，它会智能调取“类型转换器”变成 Integer，然后一脚踹开 Setter 方法的大门，把零件死死拧在机器上！ */
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
/* 📊 【战略复盘：超级工厂顶级架构思想】
* 厂长，这段代码完美展示了 Spring “海纳百川”的兼容性设计与“扩展点架构（Extension Points）”的极限操作！
*
* 1. 权力下放与策略模式 (Delegation & Strategy)：
* 它没有硬编码去写死到底是怎么找依赖的，而是通过引入 InstantiationAwareBeanPostProcessor 这一批“特权质检员”，把依赖注入的权力全部下放！
*
* 2. 绝对的开闭原则 (Open-Closed Principle)：
* 您用老古董 XML 配置？好，我用第三战区的 autowireByName 帮您推断。您用现代的 @Autowired？好，我让第四战区的 AutowiredAnnotationBeanPostProcessor 帮您通过反射扫描注入。
* 以后出了新的注入注解？完全没问题！只要新写一个 PostProcessor 塞进这个流水线，核心的 populateBean 代码一行都不用改！这就是教科书级别的架构设计！*/
	}

	/**
	 * Fill in any missing property values with references to
	 * other beans in this factory if autowire is set to "byName".
	 * @param beanName the name of the bean we're wiring up.
	 * Useful for debugging messages; not used functionally.
	 * @param mbd bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			if (containsBean(propertyName)) {
				Object bean = getBean(propertyName);
				pvs.add(propertyName, bean);
				registerDependentBean(propertyName, beanName);
				if (logger.isTraceEnabled()) {
					logger.trace("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	/**
	 * Abstract method defining "autowire by type" (bean properties by type) behavior.
	 * <p>This is like PicoContainer default, in which there must be exactly one bean
	 * of the property type in the bean factory. This makes bean factories simple to
	 * configure for small namespaces, but doesn't work as well as standard Spring
	 * behavior for bigger applications.
	 * @param beanName the name of the bean to autowire by type
	 * @param mbd the merged bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}

		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		Set<String> autowiredBeanNames = new LinkedHashSet<>(propertyNames.length * 2);
		for (String propertyName : propertyNames) {
			try {
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				// Don't try autowiring by type for type Object: never makes sense,
				// even if it technically is an unsatisfied, non-simple property.
				if (Object.class != pd.getPropertyType()) {
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
					// Do not allow eager init for type matching in case of a prioritized post-processor.
					boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
					if (autowiredArgument != null) {
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isTraceEnabled()) {
							logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	/**
	 * Return an array of non-simple bean properties that are unsatisfied.
	 * These are probably unsatisfied references to other beans in the
	 * factory. Does not include simple properties like primitives or Strings.
	 * @param mbd the merged bean definition the bean was created with
	 * @param bw the BeanWrapper the bean was created with
	 * @return an array of bean property names
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		Set<String> result = new TreeSet<>();
		PropertyValues pvs = mbd.getPropertyValues();
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
					!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
				result.add(pd.getName());
			}
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @param cache whether to cache filtered PropertyDescriptors for the given bean Class
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 * @see #filterPropertyDescriptorsForDependencyCheck(org.springframework.beans.BeanWrapper)
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
		pds.removeIf(this::isExcludedFromDependencyCheck);
		return pds.toArray(new PropertyDescriptor[0]);
	}

	/**
	 * Determine whether the given bean property is excluded from dependency checks.
	 * <p>This implementation excludes properties defined by CGLIB and
	 * properties whose type matches an ignored dependency type or which
	 * are defined by an ignored dependency interface.
	 * @param pd the PropertyDescriptor of the bean property
	 * @return whether the bean property is excluded
	 * @see #ignoreDependencyType(Class)
	 * @see #ignoreDependencyInterface(Class)
	 */
	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	/**
	 * Perform a dependency check that all properties exposed have been set,
	 * if desired. Dependency checks can be objects (collaborating beans),
	 * simple (primitives and String), or all (both).
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition the bean was created with
	 * @param pds the relevant property descriptors for the target bean
	 * @param pvs the property values to be applied to the bean
	 * @see #isExcludedFromDependencyCheck(java.beans.PropertyDescriptor)
	 */
	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * @param beanName the bean name passed for better exception information
	 * @param mbd the merged bean definition
	 * @param bw the BeanWrapper wrapping the target object
	 * @param pvs the new property values
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs.isEmpty()) {
			return;
		}

		if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
			((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
		}

		MutablePropertyValues mpvs = null;
		List<PropertyValue> original;

		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			if (mpvs.isConverted()) {
				// Shortcut: use the pre-converted values as-is.
				try {
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			original = mpvs.getPropertyValueList();
		}
		else {
			original = Arrays.asList(pvs.getPropertyValues());
		}

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		// Create a deep copy, resolving any references for values.
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;
		for (PropertyValue pv : original) {
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			else {
				String propertyName = pv.getName();
				Object originalValue = pv.getValue();
				if (originalValue == AutowiredPropertyMarker.INSTANCE) {
					Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
					if (writeMethod == null) {
						throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
					}
					originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
				}
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				Object convertedValue = resolvedValue;
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		// Set our (possibly massaged) deep copy.
		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	/**
	 * Convert the given value for the specified target property.
	 */
	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName);
		}
		else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * <h3>💥 厂长亲临前线！机密档案：initializeBean 机器觉醒与机甲合体车间 🏭</h3>
	 * <p>报告厂长！如果说前面的 <code>createBeanInstance</code> 是打了个生铁壳子，<code>populateBean</code> 是疯狂往里面塞零件和拧螺丝，那么现在，这台机器已经组装完毕，但它还只是一堆死气沉沉的废铁！</p>
	 * <p>在 <code>initializeBean</code> 这个车间里，我们将真正拉下总闸，给机器通高压电、跑开机自检脚本，并且最核心的是——全厂最神秘的“高级质检员”将在这里给原装机器穿上极其厚重的“AOP 动态代理装甲”！</p>
	 * <p>initializeBean做什么: 负责 Bean 生命周期的初始化阶段。它按极其严格的顺序执行四大操作：1. 注入 Aware 接口（发厂牌和对讲机）；2. 执行前置质检（@PostConstruct 等前置魔法）；3. 跑开机自检脚本（afterPropertiesSet / init-method）；4. 执行后置质检（AOP 代理机甲合体，狸猫换太子！）。 </p>
	 *
	 * * <blockquote>
	 * <b>【大管家的觉醒仪式与破局】</b><br/>
	 * 1. <b>框架反向通讯：</b> 机器造好了，想知道自己的名字或调动工厂资源？大管家主动把“对讲机”（Aware 接口）递给机器。<br/>
	 * 2. <b>极致扩展痛点（AOP 的灵魂归宿）：</b> Spring 绝不允许在造壳子和塞零件时混入复杂的代理逻辑。它把代理增强（AOP）完美地推迟到了机器彻底成型的最后一刻（后置质检），保证了流水线的高内聚和极度解耦！
	 * </blockquote>
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>beanName</code> (入参) -> 【产品的官方名】：</b> 准备通电觉醒的机器编号。</li>
	 * <li><b><code>bean</code> (入参) -> 【装配完毕的原生机器】：</b> 刚从 <code>populateBean</code> 流水线推出来、螺丝拧紧但还没通电的原味机器。</li>
	 * <li><b><code>mbd</code> (入参) -> 【终极蓝图】：</b> 记录了开机自检脚本等信息的图纸。</li>
	 * <li><b><code>return</code> (出参) -> 【觉醒机甲】：</b> 跑完自检、可能已经被 AOP 质检员掉包穿上重型装甲的最终成品！</li>
	 * </ul>
	 * <hr/>
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {

/* ----------------------------------------------  🛂 第一战区：身份赐予与发放全厂对讲机 (Aware Interfaces) ------------------------------------------- */
		/* [架构师视角] 如果 Bean 实现了 BeanNameAware、BeanClassLoaderAware、BeanFactoryAware 接口，这里会主动调用它们的方法，把 Spring 容器底层的核心资源注入给这个 Bean。*/
		if (System.getSecurityManager() != null) {
			/* [动作拆解] 如果厂长开启了全厂最高级别的军事化安全管制（SecurityManager），那就走特权通道（doPrivileged），防止被恶意代码篡改权限。*/
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			/* [动作拆解] 正常情况下，大管家亲自走到机器面前，执行 invokeAwareMethods！检查这台机器有没有张嘴要东西（实现 Aware 接口）？如果要名字（BeanNameAware），大管家就给它贴个专属厂牌。如果要大管家的联系方式（BeanFactoryAware），大管家就把自己的私人对讲机塞给它，让它以后能自己呼叫工厂拿零件！*/
			invokeAwareMethods(beanName, bean);
		}

		// 留个底，把刚才觉醒的机器保存在 wrappedBean 里，因为接下来它很可能被“掉包”！
		Object wrappedBean = bean;
/* ------------------------------------  🪄 第二战区：通电前夕的前置魔法 (BeanPostProcessor Before)  ------------------------------------------------------------------------- */
		/* [架构师视角] 调用所有 BeanPostProcessor 的 postProcessBeforeInitialization 方法。著名的 @PostConstruct 注解、以及 ApplicationContextAware 等高级 Aware 接口，全都是在这个环节被解析和执行的！*/
		if (mbd == null || !mbd.isSynthetic()) {
			/* [动作拆解] 🔥 高能预警！大管家吹响哨子，喊来全厂所有的“特权质检员”！
			 * 质检员们一拥而上，在机器真正开机前疯狂操作。比如有个叫 InitDestroyAnnotationBeanPostProcessor 的质检员，拿着扫描仪发现机器内部有个方法贴了 @PostConstruct 标签，二话不说，直接掏出电瓶给这方法强行通电运行！*/
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

/* ------------------------------------------------- ⚡ 第三战区：暴力通电与极限自检 (Init Methods) ---------------------------------------------------- */
		try {
			/* [架构师视角] 执行 Spring 传统的初始化方法。包括实现了 InitializingBean 接口的 afterPropertiesSet() 方法，以及在 XML/@Bean 中自定义的 init-method 方法。
			 * [动作拆解] 前置魔法搞完了，大管家亲自拉下万伏高压电闸！轰！！机器正式启动！大管家翻开图纸：
			 * 1. 说明书上写了“必须跑一圈赛道”（实现了 InitializingBean），就强行踩下油门（afterPropertiesSet）。
			 * 2. 图纸备注了“开机先喷火”（init-method），就再执行一遍喷火程序！
			 * 这是出厂前最后的极限压力测试，测试不过（抛异常），整条流水线直接拉响警报，当场报废！*/
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			// [动作拆解] 机器通电测试时当场爆炸，大管家立刻封装爆炸原因，向上级汇报！
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}
/* ---------------------------------- 第四战区：终极机甲合体与狸猫换太子 (BeanPostProcessor After) --------------------------------- */
		/*  🌟 [终极原爆点] AOP 动态代理的绝对入口！
		 * [架构师视角] 调用所有 BeanPostProcessor 的 postProcessAfterInitialization 方法。这是 Spring AOP 生效的核心！动态代理对象（JDK Proxy 或 CGLIB）就是在这里被创建并替换掉原生对象的。*/
		if (mbd == null || !mbd.isSynthetic()) {
			/* [动作拆解] 💥 厂长，全场最高潮！！！机器自检完美通过，本以为可以出厂了。 突然，名叫 AnnotationAwareAspectJAutoProxyCreator 的终极黑客质检员冲了出来！他发现厂长给这机器买了“AOP 事务装甲”。
			 * 他趁人不注意，直接把那台带着机油味的原装机器锁进了地下室，然后用 3D 打印技术（CGLIB/JDK动态代理），当场 1:1 打印了一台一模一样、但外表裹满了重型装甲的“机甲版机器”！返回的 wrappedBean 已经不再是当初的生铁壳子了，而是被掉包后的无敌机甲！*/
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}
		// [动作拆解] 🎉 仪式结束！大管家把这台贴满标签、跑过自检、甚至披上了 AOP 重型机甲的最终成品，双手奉上，准备存入一级缓存 VIP 仓库！
		return wrappedBean;
/*
 * 📊 【战略复盘：超级工厂顶级架构思想】
 * 厂长，这段代码彻底展现了 Spring 框架最深不可测的“责任链模式（Chain of Responsibility）”与“生命周期扩展（Lifecycle Callbacks）”的设计艺术！
 *
 * 1. 高内聚低耦合的极致骨架：
 * Spring 的核心 IoC 容器在这里变得极其克制，它只负责拉起一个舞台（规定好 Aware -> Before -> Init -> After 的执行顺序），而把所有的主角戏份交给了 BeanPostProcessor 这个神级接口！
 *
 * 2. 钩子 (Hook) 函数的神之手笔：
 * 无论是自动注入的完善、各种 Aware 接口的适配，还是惊天动地的 AOP 动态代理机制，底层全部是通过这种“钩子函数”无缝插拔进来的！
 * 厂长您想加任何自定义的魔法？只需要写一个质检员（PostProcessor）扔进工厂，核心代码永远不需要为您改动一行！这简直是架构设计的教科书！
 */
	}

	private void invokeAwareMethods(String beanName, Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	/**
	 * Give a bean a chance to react now all its properties are set,
	 * and a chance to know about its owning bean factory (this object).
	 * This means checking whether the bean implements InitializingBean or defines
	 * a custom init method, and invoking the necessary callback(s) if it does.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the merged bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @throws Throwable if thrown by init methods or by the invocation process
	 * @see #invokeCustomInitMethod
	 */
	protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {

		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.hasAnyExternallyManagedInitMethod("afterPropertiesSet"))) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		if (mbd != null && bean.getClass() != NullBean.class) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.hasAnyExternallyManagedInitMethod(initMethodName)) {
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	/**
	 * Invoke the specified custom init method on the given bean.
	 * Called by invokeInitMethods.
	 * <p>Can be overridden in subclasses for custom resolution of init
	 * methods with arguments.
	 * @see #invokeInitMethods
	 */
	protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd)
			throws Throwable {

		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod, bean.getClass());

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>)
						() -> methodToInvoke.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				ReflectionUtils.makeAccessible(methodToInvoke);
				methodToInvoke.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Applies the {@code postProcessAfterInitialization} callback of all
	 * registered BeanPostProcessors, giving them a chance to post-process the
	 * object obtained from FactoryBeans (for example, to auto-proxy them).
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanInstanceCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanInstanceCache.clear();
		}
	}

	/**
	 * Expose the logger to collaborating delegates.
	 * @since 5.0.7
	 */
	Log getLogger() {
		return logger;
	}


	/**
	 * Special DependencyDescriptor variant for Spring's good old autowire="byType" mode.
	 * Always optional; never considering the parameter name for choosing a primary candidate.
	 */
	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}


	/**
	 * {@link MethodCallback} used to find {@link FactoryBean} type information.
	 */
	private static class FactoryBeanMethodTypeFinder implements MethodCallback {

		private final String factoryMethodName;

		private ResolvableType result = ResolvableType.NONE;

		FactoryBeanMethodTypeFinder(String factoryMethodName) {
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
			if (isFactoryBeanMethod(method)) {
				ResolvableType returnType = ResolvableType.forMethodReturnType(method);
				ResolvableType candidate = returnType.as(FactoryBean.class).getGeneric();
				if (this.result == ResolvableType.NONE) {
					this.result = candidate;
				}
				else {
					Class<?> resolvedResult = this.result.resolve();
					Class<?> commonAncestor = ClassUtils.determineCommonAncestor(candidate.resolve(), resolvedResult);
					if (!ObjectUtils.nullSafeEquals(resolvedResult, commonAncestor)) {
						this.result = ResolvableType.forClass(commonAncestor);
					}
				}
			}
		}

		private boolean isFactoryBeanMethod(Method method) {
			return (method.getName().equals(this.factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType()));
		}

		ResolvableType getResult() {
			Class<?> resolved = this.result.resolve();
			boolean foundResult = resolved != null && resolved != Object.class;
			return (foundResult ? this.result : ResolvableType.NONE);
		}
	}

}
