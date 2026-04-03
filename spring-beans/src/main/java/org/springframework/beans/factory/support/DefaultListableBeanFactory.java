/*
 * Copyright 2002-2023 the original author or authors.
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

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Provider;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.core.OrderComparator;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.CompositeIterator;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Spring IoC 容器的"终极大管家"——唯一的、默认的、全功能 BeanFactory 实现！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.DefaultListableBeanFactory}</li>
 * <li><b>中文名</b>：默认的可枚举 Bean 工厂 —— 整个 Spring 容器的<b>心脏引擎</b></li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 <b>support 包</b>（注意！support 包 = 骨架实现 + 默认实现！）<br/>
 *     与 config 包的区别：config 包定义<b>"接口契约"</b>（如 ConfigurableListableBeanFactory），
 *     support 包提供<b>"骨架实现 + 默认实现"</b>（如 AbstractBeanFactory → AbstractAutowireCapableBeanFactory → <b>本类</b>）。<br/>
 *     <b>包设计寓意</b>：Spring 的包结构遵循 "接口→抽象骨架→默认实现" 的三层分包法则——<br/>
 *     ① {@code beans.factory} = 用户可见的顶层接口（BeanFactory/ListableBeanFactory/HierarchicalBeanFactory）<br/>
 *     ② {@code beans.factory.config} = 框架内部配置契约（ConfigurableBeanFactory/AutowireCapableBeanFactory/BeanPostProcessor/BeanDefinition）<br/>
 *     ③ {@code beans.factory.support} = 骨架实现 + 默认实现（Abstract*BeanFactory + <b>DefaultListableBeanFactory</b> + BeanDefinitionRegistry）<br/>
 *     <b>你在写业务代码时，应该只依赖①层的接口；②③层是框架内部使用的，不该出现在业务代码中！</b></li>
 * <li><b>实现层级</b>：<b>5 个接口的终极合体 + 继承链的最底层具体类</b></li>
 * </ul>
 *
 * <h3>💡 为什么 DefaultListableBeanFactory 是 Spring 的心脏？</h3>
 * <p>在整个 Spring 框架中，<b>所有的 ApplicationContext 内部都持有（或就是）一个 DefaultListableBeanFactory</b>。<br/>
 * 它是"唯一的全功能实现"——把 BeanFactory 接口体系的所有能力（查找/枚举/配置/装配/注册）全部收拢到一个类中：</p>
 * <ul>
 * <li><b>BeanFactory</b>：getBean() —— 按名字/类型获取单个 Bean</li>
 * <li><b>ListableBeanFactory</b>：getBeanNamesForType()/getBeansOfType() —— 批量枚举能力</li>
 * <li><b>HierarchicalBeanFactory</b>：getParentBeanFactory() —— 父子层级（通过继承链获得）</li>
 * <li><b>AutowireCapableBeanFactory</b>：createBean()/autowireBean() —— 创建/注入/初始化（通过继承 AbstractAutowireCapableBeanFactory 获得）</li>
 * <li><b>ConfigurableListableBeanFactory</b>：freezeConfiguration()/preInstantiateSingletons() —— 配置冻结 + 全量预实例化</li>
 * <li><b>BeanDefinitionRegistry</b>：registerBeanDefinition()/removeBeanDefinition() —— BD 的增删改查</li>
 * </ul>
 * <p><b>一句话总结：DefaultListableBeanFactory = 图纸仓库（BD注册表）+ 制造车间（createBean）+ 成品仓库（单例池）+ 查询窗口（getBean）的四合一超级工厂！</b></p>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 *                         BeanFactory（顶层：按名取 Bean）
 *                        /          |              \
 *     HierarchicalBeanFactory    ListableBeanFactory    AutowireCapableBeanFactory
 *          （纵向：父子层级）    （横向：批量枚举）        （深度：创建/注入/初始化）
 *                \                  |                   /
 *            ConfigurableBeanFactory                  /
 *               （可配置：类加载器/BPP/Scope/销毁）    /
 *                        \                          /
 *                    ConfigurableListableBeanFactory（终极合体接口）
 *                                  |
 *               ──── 接口世界 ↑ ────────────── 实现世界 ↓ ────
 *                                  |
 *     SingletonBeanRegistry ← DefaultSingletonBeanRegistry（三级缓存/单例池）
 *                                  |
 *                        FactoryBeanRegistrySupport（FactoryBean 产物缓存）
 *                                  |
 *                         AbstractBeanFactory（getBean/doGetBean 主干流程）
 *                                  |
 *                   AbstractAutowireCapableBeanFactory（createBean/autowire/initializeBean 实现）
 *                                  |
 *                      👉 DefaultListableBeanFactory 👈 ← 你在这里！
 *                         ↑ 实现 ConfigurableListableBeanFactory（终极合体接口）
 *                         ↑ 实现 BeanDefinitionRegistry（BD 注册表）
 *                         ↑ 实现 Serializable（序列化支持）
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"接口隔离 + 终极合体"的分层设计</b><br/>
 * Spring 没有把所有能力堆到一个接口里，而是拆成 5 个接口（每个接口职责单一），
 * 然后在实现层通过一条继承链逐层叠加能力，最终在 DefaultListableBeanFactory 合体。<br/>
 * <b>业务借鉴</b>：你的平台也可以用"窄接口 + 组合继承"模式——
 * 比如 OrderReadService（只读查询）、OrderWriteService（写入）、OrderAdminService（管理），
 * 最终在 DefaultOrderService 合体实现所有接口，但外部调用者只依赖它需要的那个窄接口。</li>
 *
 * <li><b>"数据结构即架构"——用 Map 定义系统的核心能力</b><br/>
 * 本类的核心就是<b>几个精心设计的 ConcurrentHashMap</b>：<br/>
 * {@code beanDefinitionMap}（图纸仓库）+ {@code allBeanNamesByType}（类型索引）+
 * {@code resolvableDependencies}（特殊依赖映射）。<br/>
 * 所有的 getBean/getBeanNamesForType/resolveDependency 方法，本质上都是在这几个 Map 上做查找！<br/>
 * <b>业务借鉴</b>：当你设计一个核心服务时，先想清楚"核心数据结构是什么"——数据结构定了，API 自然就出来了。</li>
 *
 * <li><b>"读多写少"场景的极致优化——冻结 + 缓存</b><br/>
 * Spring 容器启动后，BD 注册表基本不再变化。所以本类提供了 {@code freezeConfiguration()} 冻结机制：<br/>
 * 冻结后 {@code getBeanNamesForType()} 的结果会被缓存到 {@code allBeanNamesByType} 中，
 * 后续查询直接从缓存取，避免每次都遍历全部 BD。<br/>
 * <b>业务借鉴</b>：如果你的配置数据"启动时加载、运行时不变"，也应该提供"冻结+缓存"机制，
 * 把启动时的开销换成运行时的极速查询。</li>
 * </ol>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>~2300 行代码，划分为 <b>七大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>核心成员</th></tr>
 * <tr><td><b>🏗️ 第零战区：核心数据结构</b></td><td>定义 Bean 工厂的"内存模型"——图纸仓库 + 类型索引 + 缓存</td>
 *     <td>beanDefinitionMap, beanDefinitionNames, allBeanNamesByType, singletonBeanNamesByType, resolvableDependencies, manualSingletonNames</td></tr>
 * <tr><td><b>⚙️ 第一战区：配置调节器</b></td><td>调节工厂行为的旋钮——BD覆盖开关、类加载策略、排序器、候选解析器</td>
 *     <td>setAllowBeanDefinitionOverriding, setDependencyComparator, setAutowireCandidateResolver, copyConfigurationFrom</td></tr>
 * <tr><td><b>🔍 第二战区：BeanFactory + ListableBeanFactory 查询</b></td><td>按类型获取/枚举 Bean 和 BD</td>
 *     <td>getBean(Class), getBeanProvider, getBeanNamesForType, getBeansOfType, getBeanNamesForAnnotation, findAnnotationOnBean</td></tr>
 * <tr><td><b>🔧 第三战区：ConfigurableListableBeanFactory 配置</b></td><td>运行时配置 + 预实例化</td>
 *     <td>registerResolvableDependency, isAutowireCandidate, freezeConfiguration, <b>preInstantiateSingletons</b></td></tr>
 * <tr><td><b>📋 第四战区：BeanDefinitionRegistry 注册</b></td><td>BD 的增删改查</td>
 *     <td>registerBeanDefinition, removeBeanDefinition, getBeanDefinition, resetBeanDefinition</td></tr>
 * <tr><td><b>🎯 第五战区：依赖解析引擎</b></td><td>@Autowired 的"最后一公里"——找候选者、消歧、解析多元素</td>
 *     <td>resolveDependency, doResolveDependency, findAutowireCandidates, determineAutowireCandidate, determinePrimaryCandidate</td></tr>
 * <tr><td><b>🧩 第六战区：内部支撑类</b></td><td>序列化、ObjectProvider 实现、JSR-330 兼容</td>
 *     <td>SerializedBeanFactoryReference, DependencyObjectProvider, Jsr330Factory, FactoryAwareOrderSourceProvider</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>DefaultListableBeanFactory 的核心价值：<b>把 Spring IoC 容器的所有能力（注册/查找/创建/注入/枚举/配置）
 * 汇聚到一个类中，成为整个 Spring 框架的"心脏引擎"</b>。<br/>
 * 无论你用的是 AnnotationConfigApplicationContext 还是 ClassPathXmlApplicationContext，
 * 底层干活的都是这个 DefaultListableBeanFactory。<br/>
 * 它的设计哲学是："<b>接口层按职责拆到极细，实现层通过继承链逐层合体</b>"——
 * 让上层应用只看到需要的接口切面，而实现层共享一套完整的能力。</p>
 *
 * <hr/>
 * Spring's default implementation of the {@link ConfigurableListableBeanFactory}
 * and {@link BeanDefinitionRegistry} interfaces: a full-fledged bean factory
 * based on bean definition metadata, extensible through post-processors.
 *
 * <p>Typical usage is registering all bean definitions first (possibly read
 * from a bean definition file), before accessing beans. Bean lookup by name
 * is therefore an inexpensive operation in a local bean definition table,
 * operating on pre-resolved bean definition metadata objects.
 *
 * <p>Note that readers for specific bean definition formats are typically
 * implemented separately rather than as bean factory subclasses: see for example
 * {@link org.springframework.beans.factory.xml.XmlBeanDefinitionReader}.
 *
 * <p>For an alternative implementation of the
 * {@link org.springframework.beans.factory.ListableBeanFactory} interface,
 * have a look at {@link StaticListableBeanFactory}, which manages existing
 * bean instances rather than creating new ones based on bean definitions.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 16 April 2001
 * @see #registerBeanDefinition
 * @see #addBeanPostProcessor
 * @see #getBean
 * @see #resolveDependency
 */
@SuppressWarnings("serial")
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
		implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

	/* =======================================================================================================
	        🏗️ 第零战区：核心数据结构 —— Bean 工厂的"内存模型"
	           这些字段就是 Spring IoC 容器的全部家当！
	           理解了这些 Map/List，就理解了 Spring 容器"存什么、怎么找、怎么缓存"
	   =======================================================================================================*/

	/**
	 * 🔌 JSR-330 (javax.inject) 的 Provider 接口 Class 对象。
	 * 如果 classpath 上没有 javax.inject 依赖，则为 null。
	 * 用于 resolveDependency 时判断注入点类型是否为 javax.inject.Provider，
	 * 从而兼容 JSR-330 标准的依赖注入。
	 */
	@Nullable
	private static Class<?> javaxInjectProviderClass;

	static {
		try {
			javaxInjectProviderClass =
					ClassUtils.forName("javax.inject.Provider", DefaultListableBeanFactory.class.getClassLoader());
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - Provider interface simply not supported then.
			javaxInjectProviderClass = null;
		}
	}


	/**
	 * 📋 全局工厂黄页——用 WeakReference 防止内存泄漏的静态注册表。
	 * key = serializationId, value = WeakReference<工厂实例>。
	 * 用于反序列化时通过 ID 找回工厂实例。用弱引用是因为：
	 * 静态 Map 的生命周期 = JVM，如果用强引用会导致工厂实例永远无法被 GC 回收！
	 */
	/** Map from serialized id to factory instance. */
	private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories =
			new ConcurrentHashMap<>(8);

	/**
	 * 🏷️ 本工厂的序列化 ID——反序列化时通过这个 ID 从 serializableFactories 中找回工厂实例。
	 */
	/** Optional id for this factory, for serialization purposes. */
	@Nullable
	private String serializationId;

	/**
	 * 🔀 BD 覆盖开关——是否允许同名 BD 覆盖注册。
	 * 默认 true（允许覆盖），Spring Boot 2.1+ 默认改为 false（禁止覆盖，避免隐式冲突）。
	 * 当两个 @Configuration 类都定义了同名 @Bean 时，这个开关决定是覆盖还是报错。
	 */
	/** Whether to allow re-registration of a different definition with the same name. */
	private boolean allowBeanDefinitionOverriding = true;

	/**
	 * 📦 急性子加载开关——是否允许提前加载 lazy-init Bean 的 Class。
	 * 默认 true。当 getBeanNamesForType() 需要做类型匹配时，即使 BD 标记了 lazy-init，
	 * 也会加载它的 Class 来判断类型。设为 false 则跳过未解析 Class 的 lazy-init BD。
	 */
	/** Whether to allow eager class loading even for lazy-init beans. */
	private boolean allowEagerClassLoading = true;

	/**
	 * 📊 依赖排序器——注入 List<T>/T[] 时用来排序的比较器。
	 * 通常是 AnnotationAwareOrderComparator，支持 @Order/@Priority 注解排序。
	 * 在 prepareBeanFactory() 阶段由 ApplicationContext 设置。
	 */
	/** Optional OrderComparator for dependency Lists and arrays. */
	@Nullable
	private Comparator<Object> dependencyComparator;

	/**
	 * 🎯 自动装配候选者解析器——决定一个 BD 是否有资格参与 @Autowired 注入。
	 * 默认是 SimpleAutowireCandidateResolver（只看 BD 的 autowireCandidate 属性）。
	 * 实际运行时会被替换为 ContextAnnotationAutowireCandidateResolver，支持 @Qualifier/@Lazy/@Value 解析。
	 * 这是 @Autowired 消歧的核心裁判！
	 */
	/** Resolver to use for checking if a bean definition is an autowire candidate. */
	private AutowireCandidateResolver autowireCandidateResolver = SimpleAutowireCandidateResolver.INSTANCE;

	/**
	 * 🌟 特殊依赖映射表——"不在 BD 花名册上，但也能被 @Autowired 注入"的对象！
	 * key = 依赖类型（如 BeanFactory.class、ApplicationContext.class、ResourceLoader.class）
	 * value = 对应的实例或 ObjectFactory。
	 * 在 prepareBeanFactory() 中注册，让你可以 @Autowired ApplicationContext 即使它不是一个标准 Bean。
	 * 这就是为什么你能注入 ApplicationContext 但在 getBeansOfType(ApplicationContext.class) 中找不到它！
	 */
	/** Map from dependency type to corresponding autowired value. */
	private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);

	/**
	 * ⭐⭐⭐ 图纸仓库（BD 注册表）——Spring 容器最核心的数据结构，没有之一！
	 * key = beanName, value = BeanDefinition。
	 * 所有通过 @Component/@Bean/XML 注册的 BD 都存在这里。
	 * registerBeanDefinition() 往里放，getBeanDefinition() 从里取。
	 * 初始容量 256——Spring 预期一个应用通常有几百个 Bean。
	 */
	/** Map of bean definition objects, keyed by bean name. */
	private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

	/**
	 * 🏷️ 合并后 BD 持有者缓存——isAutowireCandidate() 判断时的缓存加速。
	 * key = beanName, value = BeanDefinitionHolder（包含 BD + beanName + aliases）。
	 * 避免每次 isAutowireCandidate 都重新创建 BeanDefinitionHolder。
	 */
	/** Map from bean name to merged BeanDefinitionHolder. */
	private final Map<String, BeanDefinitionHolder> mergedBeanDefinitionHolders = new ConcurrentHashMap<>(256);

	/**
	 * 🔍 类型→名字的全量索引缓存（含 singleton + prototype + 其他 scope）。
	 * key = Bean 类型（Class），value = 匹配的 beanName 数组。
	 * getBeanNamesForType() 的查询结果缓存——冻结配置后生效，避免每次都遍历全部 BD 做类型匹配。
	 * 和 singletonBeanNamesByType 的区别：这个包含所有 scope 的 Bean，那个只包含 singleton。
	 */
	/** Map of singleton and non-singleton bean names, keyed by dependency type. */
	private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);

	/**
	 * 🔍 类型→名字的单例索引缓存（仅 singleton）。
	 * 当 getBeanNamesForType(type, false, true) 时（includeNonSingletons=false），查这个缓存。
	 * 用于需要"只找单例"的场景。
	 */
	/** Map of singleton-only bean names, keyed by dependency type. */
	private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);

	/**
	 * 📜 BD 名字的有序列表——保持注册顺序！
	 * 这是 getBeanDefinitionNames() 的数据源。
	 * 为什么要单独维护一个 List？因为 ConcurrentHashMap 不保证遍历顺序，
	 * 但 Spring 需要按注册顺序遍历 BD（preInstantiateSingletons 就靠它）！
	 * 用 volatile 修饰是因为在运行时注册 BD 时，会整体替换引用（copy-on-write 策略防止 CME）。
	 */
	/** List of bean definition names, in registration order. */
	private volatile List<String> beanDefinitionNames = new ArrayList<>(256);

	/**
	 * 🤲 手动注册的单例名字集合（区别于通过 BD 注册的）。
	 * 通过 registerSingleton(name, obj) 直接塞入单例池的对象，名字会记录在这里。
	 * 用途：getBeanNamesForType() 时，除了遍历 beanDefinitionNames，还要遍历这个集合，
	 * 因为手动注册的单例没有 BD，不在 beanDefinitionMap 里！
	 */
	/** List of names of manually registered singletons, in registration order. */
	private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

	/**
	 * ❄️ 冻结后的 BD 名字数组快照。
	 * freezeConfiguration() 时拍一张快照存这里，getBeanDefinitionNames() 直接返回 clone。
	 * 如果没冻结则为 null，每次调用都从 beanDefinitionNames 转数组。
	 */
	/** Cached array of bean definition names in case of frozen configuration. */
	@Nullable
	private volatile String[] frozenBeanDefinitionNames;

	/**
	 * ❄️ 配置冻结标记——冻结后 getBeanNamesForType() 的结果会被缓存。
	 * ApplicationContext.refresh() 的 finishBeanFactoryInitialization() 步骤会调用 freezeConfiguration()，
	 * 此后 BD 不再变化，类型索引可以安全缓存。
	 */
	/** Whether bean definition metadata may be cached for all beans. */
	private volatile boolean configurationFrozen;


	/* =======================================================================================================
	        🏗️ 构造器 —— 工厂开张！可以独立运营，也可以挂靠父工厂
	   =======================================================================================================*/

	/**
	 * 无参构造——创建一个独立的顶层工厂（没有父容器）。
	 * AnnotationConfigApplicationContext 内部默认就是 new DefaultListableBeanFactory()。
	 */
	public DefaultListableBeanFactory() {
		super();
	}

	/**
	 * 有参构造——创建一个挂靠父工厂的子工厂。
	 * Spring MVC 的 DispatcherServlet 容器就是以根容器为 parent 创建的子容器。
	 * @param parentBeanFactory the parent BeanFactory
	 */
	public DefaultListableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		super(parentBeanFactory);
	}


	/**
	 * <br>
	 * <h3>架构深度解析：内存泄漏防御与 WeakReference (弱引用) 🛡️</h3>
	 * <p>
	 * 看似只是简单地给工厂赋个 ID 值，但它里面却藏着一个 Java 高级开发/架构师面试中
	 * 极其高频的硬核知识点——<b>静态集合的内存泄漏防御</b>与 <b>WeakReference (弱引用)</b>。
	 * </p>
	 * <p>
	 * 让我们继续用“工厂登记”的比喻，来拆解这段教科书级别的防御性源码：
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Specify an id for serialization purposes, allowing this BeanFactory to be
	 * deserialized from this id back into the BeanFactory object, if needed.
	 */
	public void setSerializationId(@Nullable String serializationId) {
		/*
		 * 1. 全局黄页登记 (注册到静态集合)
		 * ---------------------------------------------------------
		 * [原理解析] serializableFactories 是一个 static 的全局 ConcurrentHashMap。
		 *
		 * [车间大白话] 这就像是国家工商总局维护的一本“全国超级工厂黄页”。大管家拿到
		 * 序列化 ID (营业执照号) 后，第一件事就是去黄页上登记：“大家好，如果有人通过网络
		 * 反序列化找我，请到这里来提货。”
		 */

		/*
		 * 🌟 2. 核心高光时刻：为什么要套一层 WeakReference？
		 * ---------------------------------------------------------
		 * [灵魂拷问] 为什么不直接 serializableFactories.put(id, this)？
		 *
		 * [致命危机 OOM] 如果直接存，因为 Map 是 static 的（生命周期和整个 JVM 虚拟机一样长），
		 * 这意味着静态 Map 将持有大管家的强引用 (Strong Reference)。哪怕有一天 Spring 容器
		 * 被关闭，业务不需要该工厂了，垃圾回收器 (GC) 也绝对不敢回收大管家及其仓库里成千上万
		 * 的单例 Bean！这会导致极其严重的内存泄漏。
		 *
		 * [弱引用的魔法] WeakReference 就像是一张“临时暂住证”。它告诉 GC：“虽然我在 Map 里
		 * 记下了这个工厂，但如果除了我之外，系统里没有别人在使用它了，你随时可以把它当垃圾
		 * 回收掉，不用管我！” 这样一来，既实现了全局统一登记，又完美避开了静态集合带来的内存泄漏风险。
		 */
		if (serializationId != null) {
			serializableFactories.put(serializationId, new WeakReference<>(this));
		}

		/*
		 * 3. 吊销营业执照 (主动清理机制)
		 * ---------------------------------------------------------
		 * [原理解析] 如果传入的 serializationId 是 null，但工厂以前登记过。
		 * [车间大白话] 相当于工厂要注销或者改组了。大管家会主动去工商总局的黄页上，
		 * 把自己的那条记录删掉 (remove)，保持全局注册表的干净整洁。
		 */
		else if (this.serializationId != null) {
			serializableFactories.remove(this.serializationId);
		}
		/*
		 * 4. 盖章生效 (赋值成员变量)
		 * ---------------------------------------------------------
		 * 最后一步，把传进来的 ID 真正赋值给工厂内部的成员变量，完成登记，盖章生效。
		 */
		this.serializationId = serializationId;

		/*
		 * 🎉 [底层功力总结]
		 * ---------------------------------------------------------
		 * 简简单单的一个 ID 赋值操作，背后不仅考虑了跨网络的序列化找回机制，还用
		 * WeakReference 优雅地化解了静态集合的内存泄漏危机。Spring 源码之所以健壮，
		 * 正是由无数个这样严谨的细节堆砌出来的。
		 */
	}

	/**
	 * Return an id for serialization purposes, if specified, allowing this BeanFactory
	 * to be deserialized from this id back into the BeanFactory object, if needed.
	 * @since 4.1.2
	 */
	@Nullable
	public String getSerializationId() {
		return this.serializationId;
	}

	/* =======================================================================================================
	        ⚙️ 第一战区：配置调节器 —— 调节工厂行为的各种旋钮
	           这些 setter/getter 是 ApplicationContext 在 refresh() 过程中用来"调教"工厂的
	   =======================================================================================================*/

	/**
	 * <h3>⚙️ 旋钮 1：BD 覆盖开关</h3>
	 * <p>是否允许同名 BD 覆盖注册。默认 true（允许后注册的覆盖先注册的）。<br/>
	 * Spring Boot 2.1+ 默认关闭此开关（spring.main.allow-bean-definition-overriding=false），
	 * 因为隐式覆盖是很多"Bean 找不到"bug 的根源——你以为注册了一个 Bean，结果被别的配置偷偷覆盖了。</p>
	 * <hr/>
	 * Set whether it should be allowed to override bean definitions by registering
	 * a different definition with the same name, automatically replacing the former.
	 * If not, an exception will be thrown. This also applies to overriding aliases.
	 * <p>Default is "true".
	 * @see #registerBeanDefinition
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Return whether it should be allowed to override bean definitions by registering
	 * a different definition with the same name, automatically replacing the former.
	 * @since 4.1.2
	 */
	public boolean isAllowBeanDefinitionOverriding() {
		return this.allowBeanDefinitionOverriding;
	}

	/**
	 * <h3>⚙️ 旋钮 2：急性子类加载开关</h3>
	 * <p>是否允许为 lazy-init 的 BD 提前加载 Class。默认 true。<br/>
	 * 关闭后，getBeanNamesForType() 做类型匹配时会跳过未解析 Class 的 lazy-init BD，
	 * 可以加速启动，但可能导致某些按类型查找的结果不完整。</p>
	 * <hr/>
	 * Set whether the factory is allowed to eagerly load bean classes
	 * even for bean definitions that are marked as "lazy-init".
	 * <p>Default is "true". Turn this flag off to suppress class loading
	 * for lazy-init beans unless such a bean is explicitly requested.
	 * In particular, by-type lookups will then simply ignore bean definitions
	 * without resolved class name, instead of loading the bean classes on
	 * demand just to perform a type check.
	 * @see AbstractBeanDefinition#setLazyInit
	 */
	public void setAllowEagerClassLoading(boolean allowEagerClassLoading) {
		this.allowEagerClassLoading = allowEagerClassLoading;
	}

	/**
	 * Return whether the factory is allowed to eagerly load bean classes
	 * even for bean definitions that are marked as "lazy-init".
	 * @since 4.1.2
	 */
	public boolean isAllowEagerClassLoading() {
		return this.allowEagerClassLoading;
	}

	/**
	 * <h3>⚙️ 旋钮 3：依赖排序器</h3>
	 * <p>注入 {@code List<T>}、{@code T[]} 时的排序比较器。<br/>
	 * 通常设置为 {@code AnnotationAwareOrderComparator}，支持 @Order/@Priority 注解排序。<br/>
	 * 在 {@code AnnotationConfigUtils.registerAnnotationConfigProcessors()} 中自动设置。</p>
	 * <hr/>
	 * Set a {@link java.util.Comparator} for dependency Lists and arrays.
	 * @since 4.0
	 * @see org.springframework.core.OrderComparator
	 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
	 */
	public void setDependencyComparator(@Nullable Comparator<Object> dependencyComparator) {
		this.dependencyComparator = dependencyComparator;
	}

	/**
	 * Return the dependency comparator for this BeanFactory (may be {@code null}).
	 * @since 4.0
	 */
	@Nullable
	public Comparator<Object> getDependencyComparator() {
		return this.dependencyComparator;
	}

	/**
	 * <h3>⚙️ 旋钮 4：自动装配候选者解析器——@Autowired 消歧的核心裁判</h3>
	 * <p>决定一个 BD 是否有资格参与 @Autowired 自动装配。<br/>
	 * 默认 SimpleAutowireCandidateResolver（只看 BD.autowireCandidate 属性），
	 * 但在 {@code AnnotationConfigUtils} 中会被替换为
	 * {@code ContextAnnotationAutowireCandidateResolver}，支持：<br/>
	 * ① @Qualifier 限定匹配<br/>
	 * ② @Lazy 延迟代理注入<br/>
	 * ③ @Value 占位符/SpEL 解析<br/>
	 * 注意：如果 resolver 实现了 BeanFactoryAware，会自动回调 setBeanFactory(this)。</p>
	 * <hr/>
	 * Set a custom autowire candidate resolver for this BeanFactory to use
	 * when deciding whether a bean definition should be considered as a
	 * candidate for autowiring.
	 */
	public void setAutowireCandidateResolver(AutowireCandidateResolver autowireCandidateResolver) {
		Assert.notNull(autowireCandidateResolver, "AutowireCandidateResolver must not be null");
		if (autowireCandidateResolver instanceof BeanFactoryAware) {
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
					return null;
				}, getAccessControlContext());
			}
			else {
				((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
			}
		}
		this.autowireCandidateResolver = autowireCandidateResolver;
	}

	/**
	 * Return the autowire candidate resolver for this BeanFactory (never {@code null}).
	 */
	public AutowireCandidateResolver getAutowireCandidateResolver() {
		return this.autowireCandidateResolver;
	}


	/**
	 * <h3>⚙️ 配置克隆——从另一个工厂复制全部配置到本工厂</h3>
	 * <p>除了父类的配置（类加载器/BPP/Scope/属性编辑器等），还额外复制本类独有的 4 个配置：<br/>
	 * BD 覆盖开关、急性子加载开关、依赖排序器、候选者解析器，以及 resolvableDependencies 特殊依赖映射。<br/>
	 * 用于"工厂复制"场景（如 Spring 测试框架的上下文缓存复用）。</p>
	 */
	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof DefaultListableBeanFactory) {
			DefaultListableBeanFactory otherListableFactory = (DefaultListableBeanFactory) otherFactory;
			this.allowBeanDefinitionOverriding = otherListableFactory.allowBeanDefinitionOverriding;
			this.allowEagerClassLoading = otherListableFactory.allowEagerClassLoading;
			this.dependencyComparator = otherListableFactory.dependencyComparator;
			// A clone of the AutowireCandidateResolver since it is potentially BeanFactoryAware
			setAutowireCandidateResolver(otherListableFactory.getAutowireCandidateResolver().cloneIfNecessary());
			// Make resolvable dependencies (e.g. ResourceLoader) available here as well
			this.resolvableDependencies.putAll(otherListableFactory.resolvableDependencies);
		}
	}


	/* =======================================================================================================
	        🔍 第二战区：BeanFactory + ListableBeanFactory 查询实现
	           按类型获取 Bean、枚举所有 Bean、ObjectProvider 延迟查询
	           这里是你 getBean(XXX.class) 和 @Autowired List<XXX> 的入口
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of remaining BeanFactory methods
	//---------------------------------------------------------------------

	/**
	 * <h3>🔍 getBean(Class) —— 按类型获取唯一 Bean</h3>
	 * <p>内部委托 resolveBean() → resolveNamedBean()，最终走类型匹配 + @Primary/@Priority 消歧。</p>
	 */
	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		return getBean(requiredType, (Object[]) null);
	}

	/**
	 * <h3>🔍 getBean(Class, args) —— 按类型获取 + 传入构造参数</h3>
	 * <p>核心调用链：resolveBean() → resolveNamedBean() → 候选者筛选 → 消歧 → getBean(name)。<br/>
	 * 如果找不到匹配的 Bean，抛 NoSuchBeanDefinitionException；
	 * 如果找到多个且无法消歧，抛 NoUniqueBeanDefinitionException。</p>
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T getBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
		Assert.notNull(requiredType, "Required type must not be null");
		Object resolved = resolveBean(ResolvableType.forRawClass(requiredType), args, false);
		if (resolved == null) {
			throw new NoSuchBeanDefinitionException(requiredType);
		}
		return (T) resolved;
	}

	/**
	 * <h3>🔍 getBeanProvider(Class) —— 获取延迟查询的 ObjectProvider</h3>
	 * <p>不立即触发 Bean 创建！返回一个 ObjectProvider，调用 getObject()/getIfAvailable()/stream() 时才真正解析。<br/>
	 * 这是 Spring 4.3 推荐的"安全注入"方式——避免启动时因依赖缺失而失败。</p>
	 */
	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		Assert.notNull(requiredType, "Required type must not be null");
		return getBeanProvider(ResolvableType.forRawClass(requiredType), true);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		return getBeanProvider(requiredType, true);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	/**
	 * <h3>🔍 containsBeanDefinition —— 图纸仓库里有没有这个名字的图纸？</h3>
	 * <p>直接查 beanDefinitionMap.containsKey()，O(1) 操作。<br/>
	 * 注意：这只检查 BD，不检查手动注册的单例（manualSingletonNames）！</p>
	 */
	@Override
	public boolean containsBeanDefinition(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return this.beanDefinitionMap.containsKey(beanName);
	}

	/** BD 总数——beanDefinitionMap 的 size */
	@Override
	public int getBeanDefinitionCount() {
		return this.beanDefinitionMap.size();
	}

	/**
	 * <h3>🔍 getBeanDefinitionNames —— 获取所有 BD 名字（保持注册顺序）</h3>
	 * <p>如果配置已冻结，返回快照数组的 clone（避免外部修改）；
	 * 否则从 beanDefinitionNames 列表转数组。<br/>
	 * preInstantiateSingletons() 就是遍历这个方法的返回值来逐个创建单例的！</p>
	 */
	@Override
	public String[] getBeanDefinitionNames() {
		String[] frozenNames = this.frozenBeanDefinitionNames;
		if (frozenNames != null) {
			return frozenNames.clone();
		}
		else {
			return StringUtils.toStringArray(this.beanDefinitionNames);
		}
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
		Assert.notNull(requiredType, "Required type must not be null");
		return getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit);
	}

	/**
	 * <h3>🔍 getBeanProvider(ResolvableType) —— ObjectProvider 的核心工厂方法</h3>
	 * <p>返回一个匿名内部类实现的 BeanObjectProvider，它的每个方法都是<b>延迟求值</b>的：<br/>
	 * ① getObject() —— resolveBean() 找唯一匹配，找不到报错<br/>
	 * ② getIfAvailable() —— 找不到返回 null，不报错<br/>
	 * ③ getIfUnique() —— 找到唯一匹配才返回，多个匹配也返回 null<br/>
	 * ④ stream() —— 返回所有匹配 Bean 的 Stream<br/>
	 * ⑤ orderedStream() —— 同 stream() 但按 @Order 排序<br/>
	 * <b>这个 ObjectProvider 就是 @Autowired ObjectProvider&lt;T&gt; 注入时拿到的那个对象！</b></p>
	 */
	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
		return new BeanObjectProvider<T>() {
			@Override
			public T getObject() throws BeansException {
				T resolved = resolveBean(requiredType, null, false);
				if (resolved == null) {
					throw new NoSuchBeanDefinitionException(requiredType);
				}
				return resolved;
			}
			@Override
			public T getObject(Object... args) throws BeansException {
				T resolved = resolveBean(requiredType, args, false);
				if (resolved == null) {
					throw new NoSuchBeanDefinitionException(requiredType);
				}
				return resolved;
			}
			@Override
			@Nullable
			public T getIfAvailable() throws BeansException {
				try {
					return resolveBean(requiredType, null, false);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope
					return null;
				}
			}
			@Override
			public void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
				T dependency = getIfAvailable();
				if (dependency != null) {
					try {
						dependencyConsumer.accept(dependency);
					}
					catch (ScopeNotActiveException ex) {
						// Ignore resolved bean in non-active scope, even on scoped proxy invocation
					}
				}
			}
			@Override
			@Nullable
			public T getIfUnique() throws BeansException {
				try {
					return resolveBean(requiredType, null, true);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope
					return null;
				}
			}
			@Override
			public void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
				T dependency = getIfUnique();
				if (dependency != null) {
					try {
						dependencyConsumer.accept(dependency);
					}
					catch (ScopeNotActiveException ex) {
						// Ignore resolved bean in non-active scope, even on scoped proxy invocation
					}
				}
			}
			@SuppressWarnings("unchecked")
			@Override
			public Stream<T> stream() {
				return Arrays.stream(getBeanNamesForTypedStream(requiredType, allowEagerInit))
						.map(name -> (T) getBean(name))
						.filter(bean -> !(bean instanceof NullBean));
			}
			@SuppressWarnings("unchecked")
			@Override
			public Stream<T> orderedStream() {
				String[] beanNames = getBeanNamesForTypedStream(requiredType, allowEagerInit);
				if (beanNames.length == 0) {
					return Stream.empty();
				}
				Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);
				for (String beanName : beanNames) {
					Object beanInstance = getBean(beanName);
					if (!(beanInstance instanceof NullBean)) {
						matchingBeans.put(beanName, (T) beanInstance);
					}
				}
				Stream<T> stream = matchingBeans.values().stream();
				return stream.sorted(adaptOrderComparator(matchingBeans));
			}
		};
	}

	/**
	 * <h3>🔍 resolveBean —— getBean(Class) 的内部核心，按类型解析 Bean</h3>
	 * <p>三步走：<br/>
	 * ① 先在本容器调 resolveNamedBean() 按类型匹配（含 @Primary/@Priority 消歧）<br/>
	 * ② 本容器没找到 → 委托父容器递归查找（父子容器穿透！）<br/>
	 * ③ {@code nonUniqueAsNull=true} 时，多个匹配返回 null 而不是报错（用于 getIfUnique）</p>
	 */
	@Nullable
	private <T> T resolveBean(ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) {
		NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args, nonUniqueAsNull);
		if (namedBean != null) {
			return namedBean.getBeanInstance();
		}
		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			return ((DefaultListableBeanFactory) parent).resolveBean(requiredType, args, nonUniqueAsNull);
		}
		else if (parent != null) {
			ObjectProvider<T> parentProvider = parent.getBeanProvider(requiredType);
			if (args != null) {
				return parentProvider.getObject(args);
			}
			else {
				return (nonUniqueAsNull ? parentProvider.getIfUnique() : parentProvider.getIfAvailable());
			}
		}
		return null;
	}

	private String[] getBeanNamesForTypedStream(ResolvableType requiredType, boolean allowEagerInit) {
		return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, true, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		return getBeanNamesForType(type, true, true);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		Class<?> resolved = type.resolve();
		if (resolved != null && !type.hasGenerics()) {
			return getBeanNamesForType(resolved, includeNonSingletons, allowEagerInit);
		}
		else {
			return doGetBeanNamesForType(type, includeNonSingletons, allowEagerInit);
		}
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		return getBeanNamesForType(type, true, true);
	}

	/**
	 * <h3>🔍 getBeanNamesForType(Class, boolean, boolean) —— 按类型查名字（带缓存策略！）</h3>
	 * <p>这是 ListableBeanFactory 的核心查询方法，被大量内部方法调用。<br/>
	 * <b>缓存策略</b>：如果配置已冻结（configurationFrozen=true），查询结果会缓存到
	 * allBeanNamesByType/singletonBeanNamesByType 中；后续调用直接从缓存取，O(1)！<br/>
	 * 如果未冻结，每次都走 doGetBeanNamesForType() 全量遍历——这就是为什么
	 * refresh() 末尾要调 freezeConfiguration() 的原因：<b>冻结 = 开启缓存 = 运行时查询提速</b>。</p>
	 */
	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		if (!isConfigurationFrozen() || type == null || !allowEagerInit) {
			return doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit);
		}
		Map<Class<?>, String[]> cache =
				(includeNonSingletons ? this.allBeanNamesByType : this.singletonBeanNamesByType);
		String[] resolvedBeanNames = cache.get(type);
		if (resolvedBeanNames != null) {
			return resolvedBeanNames;
		}
		resolvedBeanNames = doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true);
		if (ClassUtils.isCacheSafe(type, getBeanClassLoader())) {
			cache.put(type, resolvedBeanNames);
		}
		return resolvedBeanNames;
	}

	/**
	 * <h3>🔍 doGetBeanNamesForType —— 按类型查名字的真正干活方法（全量遍历！）</h3>
	 * <p>遍历两个来源：<br/>
	 * ① <b>beanDefinitionNames</b>（BD 注册的 Bean）—— 合并图纸 → 跳过抽象BD → 类型匹配<br/>
	 * ② <b>manualSingletonNames</b>（手动注册的单例）—— 直接 isTypeMatch 判断<br/>
	 * <b>FactoryBean 的双重匹配</b>：先匹配 FactoryBean.getObject() 的产物类型，
	 * 不匹配再加 "&amp;" 前缀匹配 FactoryBean 自身类型。</p>
	 */
	private String[] doGetBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		List<String> result = new ArrayList<>();

		// Check all bean definitions.
		for (String beanName : this.beanDefinitionNames) {
			// Only consider bean as eligible if the bean name is not defined as alias for some other bean.
			if (!isAlias(beanName)) {
				try {
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					// Only check bean definition if it is complete.
					if (!mbd.isAbstract() && (allowEagerInit ||
							(mbd.hasBeanClass() || !mbd.isLazyInit() || isAllowEagerClassLoading()) &&
									!requiresEagerInitForType(mbd.getFactoryBeanName()))) {
						boolean isFactoryBean = isFactoryBean(beanName, mbd);
						BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
						boolean matchFound = false;
						boolean allowFactoryBeanInit = (allowEagerInit || containsSingleton(beanName));
						boolean isNonLazyDecorated = (dbd != null && !mbd.isLazyInit());
						if (!isFactoryBean) {
							if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
								matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
							}
						}
						else {
							if (includeNonSingletons || isNonLazyDecorated ||
									(allowFactoryBeanInit && isSingleton(beanName, mbd, dbd))) {
								matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
							}
							if (!matchFound) {
								// In case of FactoryBean, try to match FactoryBean instance itself next.
								beanName = FACTORY_BEAN_PREFIX + beanName;
								if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
									matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
								}
							}
						}
						if (matchFound) {
							result.add(beanName);
						}
					}
				}
				catch (CannotLoadBeanClassException | BeanDefinitionStoreException ex) {
					if (allowEagerInit) {
						throw ex;
					}
					// Probably a placeholder: let's ignore it for type matching purposes.
					LogMessage message = (ex instanceof CannotLoadBeanClassException ?
							LogMessage.format("Ignoring bean class loading failure for bean '%s'", beanName) :
							LogMessage.format("Ignoring unresolvable metadata in bean definition '%s'", beanName));
					logger.trace(message, ex);
					// Register exception, in case the bean was accidentally unresolvable.
					onSuppressedException(ex);
				}
				catch (NoSuchBeanDefinitionException ex) {
					// Bean definition got removed while we were iterating -> ignore.
				}
			}
		}

		// Check manually registered singletons too.
		for (String beanName : this.manualSingletonNames) {
			try {
				// In case of FactoryBean, match object created by FactoryBean.
				if (isFactoryBean(beanName)) {
					if ((includeNonSingletons || isSingleton(beanName)) && isTypeMatch(beanName, type)) {
						result.add(beanName);
						// Match found for this bean: do not match FactoryBean itself anymore.
						continue;
					}
					// In case of FactoryBean, try to match FactoryBean itself next.
					beanName = FACTORY_BEAN_PREFIX + beanName;
				}
				// Match raw bean instance (might be raw FactoryBean).
				if (isTypeMatch(beanName, type)) {
					result.add(beanName);
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Shouldn't happen - probably a result of circular reference resolution...
				logger.trace(LogMessage.format(
						"Failed to check manually registered singleton with name '%s'", beanName), ex);
			}
		}

		return StringUtils.toStringArray(result);
	}

	private boolean isSingleton(String beanName, RootBeanDefinition mbd, @Nullable BeanDefinitionHolder dbd) {
		return (dbd != null ? mbd.isSingleton() : isSingleton(beanName));
	}

	/**
	 * Check whether the specified bean would need to be eagerly initialized
	 * in order to determine its type.
	 * @param factoryBeanName a factory-bean reference that the bean definition
	 * defines a factory method for
	 * @return whether eager initialization is necessary
	 */
	private boolean requiresEagerInitForType(@Nullable String factoryBeanName) {
		return (factoryBeanName != null && isFactoryBean(factoryBeanName) && !containsSingleton(factoryBeanName));
	}

	/**
	 * <h3>🔍 getBeansOfType —— 按类型批量获取 Bean 实例（名字→实例 Map）</h3>
	 * <p>先调 getBeanNamesForType() 拿到所有匹配的名字，再逐个 getBean() 获取实例。<br/>
	 * <b>容错设计</b>：如果某个 Bean 正在创建中（循环依赖），会捕获 BeanCurrentlyInCreationException 并跳过，
	 * 避免因一个 Bean 的循环依赖导致整个批量查询失败。</p>
	 */
	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		return getBeansOfType(type, true, true);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Map<String, T> getBeansOfType(
			@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {

		String[] beanNames = getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
		Map<String, T> result = CollectionUtils.newLinkedHashMap(beanNames.length);
		for (String beanName : beanNames) {
			try {
				Object beanInstance = getBean(beanName);
				if (!(beanInstance instanceof NullBean)) {
					result.put(beanName, (T) beanInstance);
				}
			}
			catch (BeanCreationException ex) {
				Throwable rootCause = ex.getMostSpecificCause();
				if (rootCause instanceof BeanCurrentlyInCreationException) {
					BeanCreationException bce = (BeanCreationException) rootCause;
					String exBeanName = bce.getBeanName();
					if (exBeanName != null && isCurrentlyInCreation(exBeanName)) {
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring match to currently created bean '" + exBeanName + "': " +
									ex.getMessage());
						}
						onSuppressedException(ex);
						// Ignore: indicates a circular reference when autowiring constructors.
						// We want to find matches other than the currently created bean itself.
						continue;
					}
				}
				throw ex;
			}
		}
		return result;
	}

	/**
	 * <h3>🔍 getBeanNamesForAnnotation —— 按注解类型查找 Bean 名字</h3>
	 * <p>遍历 beanDefinitionNames + manualSingletonNames，
	 * 对每个 Bean 调 findAnnotationOnBean() 判断是否标注了指定注解。<br/>
	 * 典型用途：{@code getBeanNamesForAnnotation(Controller.class)} 找所有 Controller。</p>
	 */
	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		List<String> result = new ArrayList<>();
		for (String beanName : this.beanDefinitionNames) {
			BeanDefinition bd = this.beanDefinitionMap.get(beanName);
			if (bd != null && !bd.isAbstract() && findAnnotationOnBean(beanName, annotationType) != null) {
				result.add(beanName);
			}
		}
		for (String beanName : this.manualSingletonNames) {
			if (!result.contains(beanName) && findAnnotationOnBean(beanName, annotationType) != null) {
				result.add(beanName);
			}
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * <h3>🔍 getBeansWithAnnotation —— 按注解类型批量获取 Bean 实例</h3>
	 * <p>getBeanNamesForAnnotation() + getBean() 的组合版。返回 name→instance Map。</p>
	 */
	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
		String[] beanNames = getBeanNamesForAnnotation(annotationType);
		Map<String, Object> result = CollectionUtils.newLinkedHashMap(beanNames.length);
		for (String beanName : beanNames) {
			Object beanInstance = getBean(beanName);
			if (!(beanInstance instanceof NullBean)) {
				result.put(beanName, beanInstance);
			}
		}
		return result;
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		return findAnnotationOnBean(beanName, annotationType, true);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		return findMergedAnnotationOnBean(beanName, annotationType, allowFactoryBeanInit)
				.synthesize(MergedAnnotation::isPresent).orElse(null);
	}

	/**
	 * <h3>🔍 findMergedAnnotationOnBean —— 在 Bean 上查找合并注解（三级降级查找）</h3>
	 * <p>Spring 的注解查找非常严谨，分三级降级：<br/>
	 * ① 先通过 getType() 获取 Bean 类型，在类型层级上搜索注解（TYPE_HIERARCHY 策略，含父类+接口）<br/>
	 * ② 如果 Bean 被代理了，类型可能是代理类 → 回退到 BD 里记录的原始 beanClass 上搜索<br/>
	 * ③ 如果 Bean 是 @Bean 方法产出的，还要在工厂方法（factoryMethod）上搜索<br/>
	 * <b>这就是为什么 @Transactional 标在接口上也能生效的底层原因！</b></p>
	 */
	private <A extends Annotation> MergedAnnotation<A> findMergedAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) {

		Class<?> beanType = getType(beanName, allowFactoryBeanInit);
		if (beanType != null) {
			MergedAnnotation<A> annotation =
					MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
			if (annotation.isPresent()) {
				return annotation;
			}
		}
		if (containsBeanDefinition(beanName)) {
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
			// Check raw bean class, e.g. in case of a proxy.
			if (bd.hasBeanClass()) {
				Class<?> beanClass = bd.getBeanClass();
				if (beanClass != beanType) {
					MergedAnnotation<A> annotation =
							MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
					if (annotation.isPresent()) {
						return annotation;
					}
				}
			}
			// Check annotations declared on factory method, if any.
			Method factoryMethod = bd.getResolvedFactoryMethod();
			if (factoryMethod != null) {
				MergedAnnotation<A> annotation =
						MergedAnnotations.from(factoryMethod, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
				if (annotation.isPresent()) {
					return annotation;
				}
			}
		}
		return MergedAnnotation.missing();
	}


	/* =======================================================================================================
	        🔧 第三战区：ConfigurableListableBeanFactory 配置实现
	           运行时配置 + 候选者判断 + 冻结配置 + 预实例化单例（最重要的 preInstantiateSingletons！）
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of ConfigurableListableBeanFactory interface
	//---------------------------------------------------------------------

	/**
	 * <h3>🔧 registerResolvableDependency —— 注册"特殊依赖"到 resolvableDependencies</h3>
	 * <p>让一个<b>不在 BD 花名册上的对象</b>也能被 @Autowired 注入！<br/>
	 * 典型调用场景在 {@code prepareBeanFactory()} 中：<br/>
	 * {@code beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);}<br/>
	 * {@code beanFactory.registerResolvableDependency(ApplicationContext.class, this);}<br/>
	 * 这就是为什么你能 @Autowired ApplicationContext 的原因——它不是一个标准 Bean，
	 * 而是通过这个方法注册到特殊依赖表中的！</p>
	 */
	@Override
	public void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue) {
		Assert.notNull(dependencyType, "Dependency type must not be null");
		if (autowiredValue != null) {
			if (!(autowiredValue instanceof ObjectFactory || dependencyType.isInstance(autowiredValue))) {
				throw new IllegalArgumentException("Value [" + autowiredValue +
						"] does not implement specified dependency type [" + dependencyType.getName() + "]");
			}
			this.resolvableDependencies.put(dependencyType, autowiredValue);
		}
	}

	/**
	 * <h3>🔧 isAutowireCandidate —— 判断某个 Bean 是否有资格参与 @Autowired 注入</h3>
	 * <p>这是 @Autowired 消歧的入口。内部委托 AutowireCandidateResolver 做实际判断。<br/>
	 * 判断维度包括：BD 的 autowireCandidate 属性、@Qualifier 匹配、泛型匹配等。</p>
	 */
	@Override
	public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
			throws NoSuchBeanDefinitionException {

		return isAutowireCandidate(beanName, descriptor, getAutowireCandidateResolver());
	}

	/**
	 * <h3>🔧 isAutowireCandidate（三参版）—— 候选者判断的实际执行者</h3>
	 * <p>三级查找链：<br/>
	 * ① 本容器有 BD → 合并 BD 后交给 resolver 判断<br/>
	 * ② 本容器有单例但无 BD（手动注册的）→ 用类型构造临时 BD 再判断<br/>
	 * ③ 本容器都没有 → 委托父容器判断（父子容器穿透）</p>
	 * <hr/>
	 * Determine whether the specified bean definition qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * @param beanName the name of the bean definition to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @param resolver the AutowireCandidateResolver to use for the actual resolution algorithm
	 * @return whether the bean should be considered as autowire candidate
	 */
	protected boolean isAutowireCandidate(
			String beanName, DependencyDescriptor descriptor, AutowireCandidateResolver resolver)
			throws NoSuchBeanDefinitionException {

		String bdName = BeanFactoryUtils.transformedBeanName(beanName);
		if (containsBeanDefinition(bdName)) {
			return isAutowireCandidate(beanName, getMergedLocalBeanDefinition(bdName), descriptor, resolver);
		}
		else if (containsSingleton(beanName)) {
			return isAutowireCandidate(beanName, new RootBeanDefinition(getType(beanName)), descriptor, resolver);
		}

		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			return ((DefaultListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor, resolver);
		}
		else if (parent instanceof ConfigurableListableBeanFactory) {
			// If no DefaultListableBeanFactory, can't pass the resolver along.
			return ((ConfigurableListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor);
		}
		else {
			return true;
		}
	}

	/**
	 * Determine whether the specified bean definition qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * @param beanName the name of the bean definition to check
	 * @param mbd the merged bean definition to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @param resolver the AutowireCandidateResolver to use for the actual resolution algorithm
	 * @return whether the bean should be considered as autowire candidate
	 */
	protected boolean isAutowireCandidate(String beanName, RootBeanDefinition mbd,
			DependencyDescriptor descriptor, AutowireCandidateResolver resolver) {

		String bdName = BeanFactoryUtils.transformedBeanName(beanName);
		resolveBeanClass(mbd, bdName);
		if (mbd.isFactoryMethodUnique && mbd.factoryMethodToIntrospect == null) {
			new ConstructorResolver(this).resolveFactoryMethodIfPossible(mbd);
		}
		BeanDefinitionHolder holder = (beanName.equals(bdName) ?
				this.mergedBeanDefinitionHolders.computeIfAbsent(beanName,
						key -> new BeanDefinitionHolder(mbd, beanName, getAliases(bdName))) :
				new BeanDefinitionHolder(mbd, beanName, getAliases(bdName)));
		return resolver.isAutowireCandidate(holder, descriptor);
	}

	/**
	 * <h3>🔧 getBeanDefinition —— 从图纸仓库取图纸</h3>
	 * <p>直接从 beanDefinitionMap 中按 name 取 BD。找不到抛 NoSuchBeanDefinitionException。<br/>
	 * 注意：这里返回的是"原始 BD"（可能是 GenericBeanDefinition/ScannedGenericBeanDefinition），
	 * 不是合并后的 RootBeanDefinition。如果需要合并后的，应调 getMergedBeanDefinition()。</p>
	 */
	@Override
	public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		BeanDefinition bd = this.beanDefinitionMap.get(beanName);
		if (bd == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("No bean named '" + beanName + "' found in " + this);
			}
			throw new NoSuchBeanDefinitionException(beanName);
		}
		return bd;
	}

	/**
	 * <h3>🔧 getBeanNamesIterator —— 获取所有 Bean 名字的组合迭代器</h3>
	 * <p>组合了两个来源：beanDefinitionNames（BD 注册的）+ manualSingletonNames（手动注册的）。<br/>
	 * 使用 CompositeIterator 串联两个迭代器，遍历时先 BD、后手动单例。</p>
	 */
	@Override
	public Iterator<String> getBeanNamesIterator() {
		CompositeIterator<String> iterator = new CompositeIterator<>();
		iterator.add(this.beanDefinitionNames.iterator());
		iterator.add(this.manualSingletonNames.iterator());
		return iterator;
	}

	@Override
	protected void clearMergedBeanDefinition(String beanName) {
		super.clearMergedBeanDefinition(beanName);
		this.mergedBeanDefinitionHolders.remove(beanName);
	}

	@Override
	public void clearMetadataCache() {
		super.clearMetadataCache();
		this.mergedBeanDefinitionHolders.clear();
		clearByTypeCache();
	}

	/**
	 * <h3>❄️ freezeConfiguration —— 冻结配置！开启缓存加速！</h3>
	 * <p>由 {@code AbstractApplicationContext.finishBeanFactoryInitialization()} 在预实例化之前调用。<br/>
	 * 两件事：① 设置 configurationFrozen=true ② 拍一份 beanDefinitionNames 的快照。<br/>
	 * 冻结后：<br/>
	 * - getBeanNamesForType() 的结果会被缓存到 allBeanNamesByType/singletonBeanNamesByType<br/>
	 * - getBeanDefinitionNames() 直接返回快照 clone<br/>
	 * - 所有 Bean 的合并 BD 元数据都被视为可缓存<br/>
	 * <b>冻结 = "BD 注册阶段结束，进入运行阶段"的信号！</b></p>
	 */
	@Override
	public void freezeConfiguration() {
		this.configurationFrozen = true;
		this.frozenBeanDefinitionNames = StringUtils.toStringArray(this.beanDefinitionNames);
	}

	@Override
	public boolean isConfigurationFrozen() {
		return this.configurationFrozen;
	}

	/**
	 * 冻结后所有 Bean 都可缓存元数据；未冻结时只有已开始创建的 Bean 可以缓存。
	 * @see #freezeConfiguration()
	 */
	@Override
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return (this.configurationFrozen || super.isBeanEligibleForMetadataCaching(beanName));
	}

	/**
	 * <h3>架构巅峰：总电闸拉下与全厂机器出厂 (预实例化单例) 🚀</h3>
	 * <p>
	 * 如果说前面的 10 步都在“招兵买马、画图纸、排队列”，那么这段 {@code preInstantiateSingletons} （预实例化所有单例）
	 * 代码，就是工厂流水线全面开机、马达疯狂轰鸣的真正时刻！
	 * </p>
	 * <p>
	 * 这里诞生了你的 {@code @Service}、你的 {@code @Controller}、你的 {@code JdbcTemplate}。
	 * 所有的对象，都在这里化作内存中真实存在的物理实体。<br>
	 * 让我们按捺住激动的心情，把这台轰鸣的机器拆解成 4 个极其精彩的开工动作：
	 * </p>
	 *
	 * @throws BeansException
	 */
	@Override
	public void preInstantiateSingletons() throws BeansException {
		if (logger.isTraceEnabled()) {
			logger.trace("Pre-instantiating singletons in " + this);
		}

/* 🖨️ 动作一：复印一份“最终开工花名册”
 * [原理解析] 把仓库里所有图纸的名字，单独拷贝 (new ArrayList) 成了一份全新名单。
 * [车间大白话] 大管家极其严谨！为什么不直接拿仓库里的原版目录去造机器，而非要复印一份？因为在造机器的过程中，某些特殊的组件可能会在自己出厂时，又偷偷往仓库里塞入新的图纸！
 * 🚨 致命报错：如果一边遍历原目录一边往里塞新东西，Java 会直接抛出“并发修改异常 (ConcurrentModificationException)”。所以，大管家手里拿着复印件去点名，绝不出错！*/
		// Iterate over a copy to allow for init methods which in turn register new bean definitions.
		// While this may not be part of the regular factory bootstrap, it does otherwise work fine.
		List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

/* 🛡️ 动作二：提取最终图纸与“黄金三筛”, 大管家拿着复印件，开始挨个点名造机器了 (第一个 for 循环)：
 * [合并图纸] 如果 UserBean 继承了 BaseBean，大管家会把父类的属性全部合并下来，形成一张最终的“终极图纸 (RootBeanDefinition)”。
 * [🏆 黄金三筛：Spring 单例池的最高铁律！] 要想现在出厂，必须同时满足三个条件：
 * ① !bd.isAbstract()：不能是抽象的图纸 (只做概念设计，不生产实物)。
 * ② bd.isSingleton()：必须是单例的 (如果是多例 Prototype，每次用到才造，启动时不造)。
 * ③ !bd.isLazyInit()：不能是懒汉 (标了 @Lazy 的机器，只有别人第一次叫它时才醒，现在让它接着睡)。*/
		// Trigger initialization of all non-lazy singleton beans...
		for (String beanName : beanNames) {
			// 1. 把图纸及其父类的图纸合并成一张完整的图纸 (MergedLocalBeanDefinition)
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
			// 2. 💥 黄金三筛：只有同时满足这三个条件的图纸，才有资格现在被造出来！
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
/* 🏭 动作三：处理“厂中厂”与终极点火！
 * 满足了三筛，终于要按下制造按钮了。这里分了两种情况：*/
				/* [情况 A：图纸是 FactoryBean (厂中厂)]
				 * 如果你实现过 FactoryBean (比如 MyBatis 的 SqlSessionFactoryBean)，Spring 会非常小心。它先用 & 前缀把你这个“生成机器的机器(厂中厂)”造出来。然后再看你急不急 (isEagerInit)，如果急，就立刻调用你的 getObject() 把最终产品造出来。*/
				if (isFactoryBean(beanName)) {
					// 先造出这个“小工厂”本身（注意前缀 FACTORY_BEAN_PREFIX 是 "&" 符号）
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
					if (bean instanceof FactoryBean) {
						FactoryBean<?> factory = (FactoryBean<?>) bean;
						boolean isEagerInit;
						if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
							isEagerInit = AccessController.doPrivileged(
									(PrivilegedAction<Boolean>) ((SmartFactoryBean<?>) factory)::isEagerInit,
									getAccessControlContext());
						}
						else {
							isEagerInit = (factory instanceof SmartFactoryBean &&
									((SmartFactoryBean<?>) factory).isEagerInit());
						}
						// 如果小工厂说“我很急，马上把我的产品造出来”
						if (isEagerInit) {
							// 💥 点火造产品！
							getBean(beanName);
						}
					}
				}
				else {
				/* [情况 B：普通业务 Bean (99.9% 的情况)]
				 * 大管家看着手里 UserService 的图纸，大喊一声：“造！”。厂长，这短短的一行 getBean(beanName)，包揽了 Spring 最核心的千军万马！ 找构造器、反射 new 对象、属性填充 (依赖注入 @Autowired)、初始化 (@PostConstruct)、AOP 代理生成…… 全部都在这一个方法内部瞬间完成！*/
					getBean(beanName);
				}
			}
		}

/* 🎊 动作四：竣工剪彩仪式 (极其眼熟的彩蛋！)
 * 等到第一个 for 循环结束，全厂所有非懒加载的单例机器，已经全部制造完毕，整整齐齐地摆在了单例池里！ 接下来是第二个 for 循环：大管家巡视了一圈全厂，对着那些造好的机器问：“大家还有什么想在全厂竣工这一刻发表的感言吗？”
 * 如果你实现了 SmartInitializingSingleton，你就可以在这个时候发言（执行 afterSingletonsInstantiated）。*/
		// Trigger post-initialization callback for all applicable beans...
		for (String beanName : beanNames) {
			// 从单例池把造好的机器拉出来
			Object singletonInstance = getSingleton(beanName);

			// 看看它有没有实现 SmartInitializingSingleton 接口？
			if (singletonInstance instanceof SmartInitializingSingleton) {
				StartupStep smartInitialize = getApplicationStartup().start("spring.beans.smart-initialize")
						.tag("beanName", beanName);
				SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
				if (System.getSecurityManager() != null) {
					AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
						smartSingleton.afterSingletonsInstantiated();
						return null;
					}, getAccessControlContext());
				}
				else {
					/*
					 * 🤯 [终极大彩蛋闭环：广播站站长的苏醒！]
					 * 厂长！还记得几个小时前，咱们看的那个**【广播站站长】（EventListenerMethodProcessor）**吗？ 当时你问我：“他怎么只把造喇叭的包工头揣在兜里，不干活啊？”
					 * 我当时回答你：“因为时机未到，等全厂机器造完，他就会触发 afterSingletonsInstantiated 来干活！” 没错！广播站站长就是在这里被唤醒的！ 他在这里掏出了包工头，去所有的机器身上找 @EventListener，并把大喇叭全部接好！完美的逻辑闭环！！！*/
					// 💥 触发全厂竣工剪彩仪式！
					smartSingleton.afterSingletonsInstantiated();
				}
				smartInitialize.end();
			}
		}
/* 💡 [厂长，通往地心深处的电梯门已打开！]
 * 这整段代码，就是在排兵布阵、挑选有资格出厂的名单、并在最后举办剪彩仪式。 而所有真正“血腥、暴力、硬核”的螺丝拧紧动作，全都在那极其不起眼的一行代码里：
 * 👉 getBean(beanName); 👈 这里面，藏着震惊 Java 界的“三级缓存 (解决循环依赖)”！、藏着【2号大将】是怎么偷偷用反射把 UserDao 塞进 UserService 里的！让我们点进这个 getBean，去掀翻 Spring 容器最底层的引擎盖！*/
	}


	/* =======================================================================================================
	        📋 第四战区：BeanDefinitionRegistry 注册实现
	           BD 的增删改查——所有 @Component/@Bean/XML 定义的 Bean 都通过这里入库！
	           这里是 Spring 容器的"户口登记处"
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of BeanDefinitionRegistry interface
	//---------------------------------------------------------------------

	/**
	 * <h3>📋 registerBeanDefinition —— 把一张图纸（BD）登记入库</h3>
	 * <p><b>这是 Spring 容器的"户口登记"方法！</b>所有 Bean 的 BD 最终都通过这个方法进入 beanDefinitionMap。<br/>
	 * 调用链举例：@Component 扫描 → ClassPathBeanDefinitionScanner → 本方法；@Bean → ConfigurationClassBeanDefinitionReader → 本方法。</p>
	 *
	 * <p><b>核心流程：</b><br/>
	 * ① <b>校验</b>：如果是 AbstractBeanDefinition，先 validate()（检查 methodOverrides 与 factoryMethod 不冲突等）<br/>
	 * ② <b>覆盖检查</b>：如果同名 BD 已存在——<br/>
	 *    - allowBeanDefinitionOverriding=false → 直接抛 BeanDefinitionOverrideException<br/>
	 *    - 允许覆盖 → 按 role 级别打不同级别的日志（APPLICATION 被 INFRASTRUCTURE 覆盖打 info，同级覆盖打 debug）<br/>
	 *    - 然后 put 覆盖<br/>
	 * ③ <b>新注册</b>（不存在同名 BD）——<br/>
	 *    - 如果工厂还没开始创建 Bean（启动注册阶段）→ 直接 put + add<br/>
	 *    - 如果工厂已经开始创建 Bean（运行时动态注册）→ synchronized + <b>copy-on-write</b>：<br/>
	 *      新建一个 ArrayList 复制旧数据 + 追加新名字，然后整体替换 beanDefinitionNames 引用。<br/>
	 *      <b>为什么用 copy-on-write？</b>因为此时可能有其他线程正在遍历 beanDefinitionNames（如 preInstantiateSingletons），
	 *      直接修改会 ConcurrentModificationException！整体替换引用则安全。<br/>
	 * ④ <b>善后</b>：如果覆盖了已有 BD 或已有单例 → resetBeanDefinition() 清除缓存 + 销毁旧单例</p>
	 */
	@Override
	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException {

		Assert.hasText(beanName, "Bean name must not be empty");
		Assert.notNull(beanDefinition, "BeanDefinition must not be null");

		if (beanDefinition instanceof AbstractBeanDefinition) {
			try {
				((AbstractBeanDefinition) beanDefinition).validate();
			}
			catch (BeanDefinitionValidationException ex) {
				throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName,
						"Validation of bean definition failed", ex);
			}
		}

		BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
		if (existingDefinition != null) {
			if (!isAllowBeanDefinitionOverriding()) {
				throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
			}
			else if (existingDefinition.getRole() < beanDefinition.getRole()) {
				// e.g. was ROLE_APPLICATION, now overriding with ROLE_SUPPORT or ROLE_INFRASTRUCTURE
				if (logger.isInfoEnabled()) {
					logger.info("Overriding user-defined bean definition for bean '" + beanName +
							"' with a framework-generated bean definition: replacing [" +
							existingDefinition + "] with [" + beanDefinition + "]");
				}
			}
			else if (!beanDefinition.equals(existingDefinition)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Overriding bean definition for bean '" + beanName +
							"' with a different definition: replacing [" + existingDefinition +
							"] with [" + beanDefinition + "]");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Overriding bean definition for bean '" + beanName +
							"' with an equivalent definition: replacing [" + existingDefinition +
							"] with [" + beanDefinition + "]");
				}
			}
			this.beanDefinitionMap.put(beanName, beanDefinition);
		}
		else {
			if (hasBeanCreationStarted()) {
				// Cannot modify startup-time collection elements anymore (for stable iteration)
				synchronized (this.beanDefinitionMap) {
					this.beanDefinitionMap.put(beanName, beanDefinition);
					List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
					updatedDefinitions.addAll(this.beanDefinitionNames);
					updatedDefinitions.add(beanName);
					this.beanDefinitionNames = updatedDefinitions;
					removeManualSingletonName(beanName);
				}
			}
			else {
				// Still in startup registration phase
				this.beanDefinitionMap.put(beanName, beanDefinition);
				this.beanDefinitionNames.add(beanName);
				removeManualSingletonName(beanName);
			}
			this.frozenBeanDefinitionNames = null;
		}

		if (existingDefinition != null || containsSingleton(beanName)) {
			resetBeanDefinition(beanName);
		}
		else if (isConfigurationFrozen()) {
			clearByTypeCache();
		}
	}

	/**
	 * <h3>📋 removeBeanDefinition —— 从图纸仓库中移除一张图纸</h3>
	 * <p>从 beanDefinitionMap 中 remove，然后从 beanDefinitionNames 中也移除。<br/>
	 * 同样使用 copy-on-write 策略：运行时移除时新建 ArrayList 副本再操作。<br/>
	 * 最后调 resetBeanDefinition() 清除合并BD缓存 + 销毁已创建的单例 + 通知所有 MergedBeanDefinitionPostProcessor。</p>
	 */
	@Override
	public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		Assert.hasText(beanName, "'beanName' must not be empty");

		BeanDefinition bd = this.beanDefinitionMap.remove(beanName);
		if (bd == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("No bean named '" + beanName + "' found in " + this);
			}
			throw new NoSuchBeanDefinitionException(beanName);
		}

		if (hasBeanCreationStarted()) {
			// Cannot modify startup-time collection elements anymore (for stable iteration)
			synchronized (this.beanDefinitionMap) {
				List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames);
				updatedDefinitions.remove(beanName);
				this.beanDefinitionNames = updatedDefinitions;
			}
		}
		else {
			// Still in startup registration phase
			this.beanDefinitionNames.remove(beanName);
		}
		this.frozenBeanDefinitionNames = null;

		resetBeanDefinition(beanName);
	}

	/**
	 * <h3>📋 resetBeanDefinition —— BD 变更后的"善后三连"</h3>
	 * <p>当 BD 被覆盖或移除时，必须做三件善后：<br/>
	 * ① {@code clearMergedBeanDefinition(beanName)} —— 清除合并 BD 缓存（因为原始 BD 变了，合并结果也失效了）<br/>
	 * ② {@code destroySingleton(beanName)} —— 销毁已创建的单例实例（图纸变了，旧产品作废）<br/>
	 * ③ 通知所有 {@code MergedBeanDefinitionPostProcessor.resetBeanDefinition()} —— 让 BPP 也清除自己的缓存<br/>
	 * ④ <b>递归处理子 BD</b>——如果有其他 BD 以本 BD 为 parent，也要递归 reset！</p>
	 * <hr/>
	 * Reset all bean definition caches for the given bean,
	 * including the caches of beans that are derived from it.
	 * <p>Called after an existing bean definition has been replaced or removed,
	 * triggering {@link #clearMergedBeanDefinition}, {@link #destroySingleton}
	 * and {@link MergedBeanDefinitionPostProcessor#resetBeanDefinition} on the
	 * given bean and on all bean definitions that have the given bean as parent.
	 * @param beanName the name of the bean to reset
	 * @see #registerBeanDefinition
	 * @see #removeBeanDefinition
	 */
	protected void resetBeanDefinition(String beanName) {
		// Remove the merged bean definition for the given bean, if already created.
		clearMergedBeanDefinition(beanName);

		// Remove corresponding bean from singleton cache, if any. Shouldn't usually
		// be necessary, rather just meant for overriding a context's default beans
		// (e.g. the default StaticMessageSource in a StaticApplicationContext).
		destroySingleton(beanName);

		// Notify all post-processors that the specified bean definition has been reset.
		for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
			processor.resetBeanDefinition(beanName);
		}

		// Reset all bean definitions that have the given bean as parent (recursively).
		for (String bdName : this.beanDefinitionNames) {
			if (!beanName.equals(bdName)) {
				BeanDefinition bd = this.beanDefinitionMap.get(bdName);
				// Ensure bd is non-null due to potential concurrent modification of beanDefinitionMap.
				if (bd != null && beanName.equals(bd.getParentName())) {
					resetBeanDefinition(bdName);
				}
			}
		}
	}

	/**
	 * Only allows alias overriding if bean definition overriding is allowed.
	 */
	@Override
	protected boolean allowAliasOverriding() {
		return isAllowBeanDefinitionOverriding();
	}

	/**
	 * Also checks for an alias overriding a bean definition of the same name.
	 */
	@Override
	protected void checkForAliasCircle(String name, String alias) {
		super.checkForAliasCircle(name, alias);
		if (!isAllowBeanDefinitionOverriding() && containsBeanDefinition(alias)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Alias would override bean definition '" + alias + "'");
		}
	}

	/**
	 * <h3>📋 registerSingleton —— 手动注册单例（绕过 BD，直接塞入单例池）</h3>
	 * <p>与 registerBeanDefinition 不同：这里不注册 BD，直接把对象放入单例池。<br/>
	 * 同时记录名字到 manualSingletonNames（因为没有 BD，只有这个集合能记住它的存在）。<br/>
	 * 最后清除类型缓存（因为新加了一个单例，按类型查找的结果可能变了）。</p>
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		super.registerSingleton(beanName, singletonObject);
		updateManualSingletonNames(set -> set.add(beanName), set -> !this.beanDefinitionMap.containsKey(beanName));
		clearByTypeCache();
	}

	/**
	 * <h3>📋 destroySingletons —— 销毁全部单例（容器关闭时调用）</h3>
	 * <p>三件事：① 父类销毁所有单例池中的对象 ② 清空 manualSingletonNames ③ 清除类型缓存。</p>
	 */
	@Override
	public void destroySingletons() {
		super.destroySingletons();
		updateManualSingletonNames(Set::clear, set -> !set.isEmpty());
		clearByTypeCache();
	}

	/** 销毁单个单例——父类销毁 + 从 manualSingletonNames 移除 + 清除类型缓存 */
	@Override
	public void destroySingleton(String beanName) {
		super.destroySingleton(beanName);
		removeManualSingletonName(beanName);
		clearByTypeCache();
	}

	private void removeManualSingletonName(String beanName) {
		updateManualSingletonNames(set -> set.remove(beanName), set -> set.contains(beanName));
	}

	/**
	 * <h3>📋 updateManualSingletonNames —— 安全更新手动单例名字集合</h3>
	 * <p>和 registerBeanDefinition 中更新 beanDefinitionNames 一样的套路：<br/>
	 * - 启动阶段（未开始创建 Bean）→ 直接修改原集合<br/>
	 * - 运行阶段（已开始创建 Bean）→ synchronized + <b>copy-on-write</b>：
	 *   复制一份新集合 → 修改副本 → 整体替换引用，保证遍历安全<br/>
	 * condition 参数做前置判断，避免不必要的复制操作（性能优化）。</p>
	 * <hr/>
	 * Update the factory's internal set of manual singleton names.
	 * @param action the modification action
	 * @param condition a precondition for the modification action
	 * (if this condition does not apply, the action can be skipped)
	 */
	private void updateManualSingletonNames(Consumer<Set<String>> action, Predicate<Set<String>> condition) {
		if (hasBeanCreationStarted()) {
			// Cannot modify startup-time collection elements anymore (for stable iteration)
			synchronized (this.beanDefinitionMap) {
				if (condition.test(this.manualSingletonNames)) {
					Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
					action.accept(updatedSingletons);
					this.manualSingletonNames = updatedSingletons;
				}
			}
		}
		else {
			// Still in startup registration phase
			if (condition.test(this.manualSingletonNames)) {
				action.accept(this.manualSingletonNames);
			}
		}
	}

	/**
	 * Remove any assumptions about by-type mappings.
	 */
	private void clearByTypeCache() {
		this.allBeanNamesByType.clear();
		this.singletonBeanNamesByType.clear();
	}


	/* =======================================================================================================
	        🎯 第五战区：依赖解析引擎 —— @Autowired 的"最后一公里"！
	           从 resolveDependency 入口 → doResolveDependency 干活
	           → findAutowireCandidates 找候选 → determineAutowireCandidate 消歧
	           这里是 Spring DI 机制的终极落地点！
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Dependency resolution functionality
	//---------------------------------------------------------------------

	/**
	 * <h3>🎯 resolveNamedBean(Class) —— 按类型解析并返回 名字+实例</h3>
	 * <p>getBean(Class) 的增强版——不仅返回实例，还返回 beanName。<br/>
	 * 内部委托 resolveNamedBean(ResolvableType, args, nonUniqueAsNull)，
	 * 本容器找不到则委托父容器。</p>
	 */
	@Override
	public <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException {
		Assert.notNull(requiredType, "Required type must not be null");
		NamedBeanHolder<T> namedBean = resolveNamedBean(ResolvableType.forRawClass(requiredType), null, false);
		if (namedBean != null) {
			return namedBean;
		}
		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof AutowireCapableBeanFactory) {
			return ((AutowireCapableBeanFactory) parent).resolveNamedBean(requiredType);
		}
		throw new NoSuchBeanDefinitionException(requiredType);
	}

	/**
	 * <h3>🎯 resolveNamedBean（私有核心版）—— 按类型解析的真正执行者</h3>
	 * <p><b>消歧五步法</b>：<br/>
	 * ① getBeanNamesForType() 找出所有类型匹配的候选者名字<br/>
	 * ② 候选者 &gt;1 → 过滤掉 autowireCandidate=false 的<br/>
	 * ③ 候选者 == 1 → 直接 getBean() 返回<br/>
	 * ④ 候选者 &gt;1 → <b>determinePrimaryCandidate()</b> 找 @Primary 标注的<br/>
	 * ⑤ 还没消歧 → <b>determineHighestPriorityCandidate()</b> 找 @Priority 值最小的<br/>
	 * ⑥ 都没找到唯一 → 抛 NoUniqueBeanDefinitionException（除非 nonUniqueAsNull=true）</p>
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	private <T> NamedBeanHolder<T> resolveNamedBean(
			ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) throws BeansException {

		Assert.notNull(requiredType, "Required type must not be null");
		String[] candidateNames = getBeanNamesForType(requiredType);

		if (candidateNames.length > 1) {
			List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
			for (String beanName : candidateNames) {
				if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
					autowireCandidates.add(beanName);
				}
			}
			if (!autowireCandidates.isEmpty()) {
				candidateNames = StringUtils.toStringArray(autowireCandidates);
			}
		}

		if (candidateNames.length == 1) {
			return resolveNamedBean(candidateNames[0], requiredType, args);
		}
		else if (candidateNames.length > 1) {
			Map<String, Object> candidates = CollectionUtils.newLinkedHashMap(candidateNames.length);
			for (String beanName : candidateNames) {
				if (containsSingleton(beanName) && args == null) {
					Object beanInstance = getBean(beanName);
					candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
				}
				else {
					candidates.put(beanName, getType(beanName));
				}
			}
			String candidateName = determinePrimaryCandidate(candidates, requiredType.toClass());
			if (candidateName == null) {
				candidateName = determineHighestPriorityCandidate(candidates, requiredType.toClass());
			}
			if (candidateName != null) {
				Object beanInstance = candidates.get(candidateName);
				if (beanInstance == null) {
					return null;
				}
				if (beanInstance instanceof Class) {
					return resolveNamedBean(candidateName, requiredType, args);
				}
				return new NamedBeanHolder<>(candidateName, (T) beanInstance);
			}
			if (!nonUniqueAsNull) {
				throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
			}
		}

		return null;
	}

	@Nullable
	private <T> NamedBeanHolder<T> resolveNamedBean(
			String beanName, ResolvableType requiredType, @Nullable Object[] args) throws BeansException {

		Object bean = getBean(beanName, null, args);
		if (bean instanceof NullBean) {
			return null;
		}
		return new NamedBeanHolder<T>(beanName, adaptBeanInstance(beanName, bean, requiredType.toClass()));
	}

	/**
	 * <h3>🎯 resolveDependency —— DI 的最后一公里入口！@Autowired 最终调到这里！</h3>
	 * <p><b>完整调用链</b>：@Autowired 注解 → AutowiredAnnotationBeanPostProcessor 扫描注入点
	 * → 构建 DependencyDescriptor → 调用本方法解析依赖。</p>
	 * <p><b>四条分支路径</b>（按注入点的类型分流）：<br/>
	 * ① {@code Optional<T>} → createOptionalDependency()，解析后包装为 Optional<br/>
	 * ② {@code ObjectFactory<T>} / {@code ObjectProvider<T>} → 返回 DependencyObjectProvider（延迟解析）<br/>
	 * ③ {@code javax.inject.Provider<T>} → 返回 Jsr330Provider（JSR-330 兼容）<br/>
	 * ④ 其他普通类型 → 先看 @Lazy 是否需要创建延迟代理，否则走 <b>doResolveDependency()</b></p>
	 */
	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

		descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
		if (Optional.class == descriptor.getDependencyType()) {
			return createOptionalDependency(descriptor, requestingBeanName);
		}
		else if (ObjectFactory.class == descriptor.getDependencyType() ||
				ObjectProvider.class == descriptor.getDependencyType()) {
			return new DependencyObjectProvider(descriptor, requestingBeanName);
		}
		else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
			return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
		}
		else {
			Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
					descriptor, requestingBeanName);
			if (result == null) {
				result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
			}
			return result;
		}
	}

	/**
	 * <h3>🎯⭐ doResolveDependency —— DI 解析的真正核心！</h3>
	 * <p><b>这是 @Autowired 背后到底发生了什么的终极答案！</b>完整流程：</p>
	 * <ol>
	 * <li><b>快捷路径</b>：descriptor.resolveShortcut() —— 缓存命中直接返回（第二次注入同类型时）</li>
	 * <li><b>@Value 处理</b>：AutowireCandidateResolver.getSuggestedValue() 提取 @Value 注解的值
	 *     → resolveEmbeddedValue() 解析占位符 ${...} → evaluateBeanDefinitionString() 解析 SpEL #{...}
	 *     → TypeConverter 转换为目标类型</li>
	 * <li><b>多元素注入</b>：resolveMultipleBeans() 处理 Array/Collection/Map/Stream 类型注入
	 *     （@Autowired List&lt;T&gt; 就走这条路）</li>
	 * <li><b>单元素注入</b>（最常见路径）：<br/>
	 *     ① findAutowireCandidates() 查找所有类型匹配的候选者<br/>
	 *     ② 候选者 == 0 → required=true 报 NoSuchBeanDefinitionException<br/>
	 *     ③ 候选者 == 1 → 直接取<br/>
	 *     ④ 候选者 &gt; 1 → <b>determineAutowireCandidate()</b> 消歧：
	 *        @Primary → @Priority → 名字匹配（beanName == 字段名/参数名）</li>
	 * <li><b>InjectionPoint 线程上下文</b>：整个过程中通过 ThreadLocal 记录当前注入点，
	 *     让 @Bean 工厂方法可以通过 InjectionPoint 参数感知"谁在注入我"</li>
	 * </ol>
	 */
	@Nullable
	public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			Object shortcut = descriptor.resolveShortcut(this);
			if (shortcut != null) {
				return shortcut;
			}

			Class<?> type = descriptor.getDependencyType();
			Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
			if (value != null) {
				if (value instanceof String) {
					String strVal = resolveEmbeddedValue((String) value);
					BeanDefinition bd = (beanName != null && containsBean(beanName) ?
							getMergedBeanDefinition(beanName) : null);
					value = evaluateBeanDefinitionString(strVal, bd);
				}
				TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
				try {
					return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
				}
				catch (UnsupportedOperationException ex) {
					// A custom TypeConverter which does not support TypeDescriptor resolution...
					return (descriptor.getField() != null ?
							converter.convertIfNecessary(value, type, descriptor.getField()) :
							converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
				}
			}

			Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
			if (multipleBeans != null) {
				return multipleBeans;
			}

			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (matchingBeans.isEmpty()) {
				if (isRequired(descriptor)) {
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				return null;
			}

			String autowiredBeanName;
			Object instanceCandidate;

			if (matchingBeans.size() > 1) {
				autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
				if (autowiredBeanName == null) {
					if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
						return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
					}
					else {
						// In case of an optional Collection/Map, silently ignore a non-unique case:
						// possibly it was meant to be an empty collection of multiple regular beans
						// (before 4.3 in particular when we didn't even look for collection beans).
						return null;
					}
				}
				instanceCandidate = matchingBeans.get(autowiredBeanName);
			}
			else {
				// We have exactly one match.
				Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
				autowiredBeanName = entry.getKey();
				instanceCandidate = entry.getValue();
			}

			if (autowiredBeanNames != null) {
				autowiredBeanNames.add(autowiredBeanName);
			}
			if (instanceCandidate instanceof Class) {
				instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
			}
			Object result = instanceCandidate;
			if (result instanceof NullBean) {
				if (isRequired(descriptor)) {
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				result = null;
			}
			if (!ClassUtils.isAssignableValue(type, result)) {
				throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
			}
			return result;
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	/**
	 * <h3>🎯 resolveMultipleBeans —— 多元素注入的专用处理器</h3>
	 * <p>当 @Autowired 的注入类型是<b>集合/数组/Map/Stream</b> 时走这条路。<br/>
	 * 四种多元素类型的处理：<br/>
	 * ① {@code Stream<T>} → findAutowireCandidates + Stream.map(resolveCandidate)，可排序<br/>
	 * ② {@code T[]} → findAutowireCandidates + convertIfNecessary 转数组，按 @Order 排序<br/>
	 * ③ {@code Collection<T>}（List/Set 等接口类型）→ 同数组，按 @Order 排序<br/>
	 * ④ {@code Map<String, T>}（key 必须是 String！）→ findAutowireCandidates 直接返回 name→instance Map<br/>
	 * <b>返回 null 表示"不是多元素类型"，回退到单元素解析路径。</b></p>
	 */
	@Nullable
	private Object resolveMultipleBeans(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) {

		Class<?> type = descriptor.getDependencyType();

		if (descriptor instanceof StreamDependencyDescriptor) {
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			Stream<Object> stream = matchingBeans.keySet().stream()
					.map(name -> descriptor.resolveCandidate(name, type, this))
					.filter(bean -> !(bean instanceof NullBean));
			if (((StreamDependencyDescriptor) descriptor).isOrdered()) {
				stream = stream.sorted(adaptOrderComparator(matchingBeans));
			}
			return stream;
		}
		else if (type.isArray()) {
			Class<?> componentType = type.getComponentType();
			ResolvableType resolvableType = descriptor.getResolvableType();
			Class<?> resolvedArrayType = resolvableType.resolve(type);
			if (resolvedArrayType != type) {
				componentType = resolvableType.getComponentType().resolve();
			}
			if (componentType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
			if (result instanceof Object[]) {
				Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
				if (comparator != null) {
					Arrays.sort((Object[]) result, comparator);
				}
			}
			return result;
		}
		else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
			Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric();
			if (elementType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), type);
			if (result instanceof List) {
				if (((List<?>) result).size() > 1) {
					Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
					if (comparator != null) {
						((List<?>) result).sort(comparator);
					}
				}
			}
			return result;
		}
		else if (Map.class == type) {
			ResolvableType mapType = descriptor.getResolvableType().asMap();
			Class<?> keyType = mapType.resolveGeneric(0);
			if (String.class != keyType) {
				return null;
			}
			Class<?> valueType = mapType.resolveGeneric(1);
			if (valueType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			return matchingBeans;
		}
		else {
			return null;
		}
	}

	private boolean isRequired(DependencyDescriptor descriptor) {
		return getAutowireCandidateResolver().isRequired(descriptor);
	}

	private boolean indicatesMultipleBeans(Class<?> type) {
		return (type.isArray() || (type.isInterface() &&
				(Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))));
	}

	@Nullable
	private Comparator<Object> adaptDependencyComparator(Map<String, ?> matchingBeans) {
		Comparator<Object> comparator = getDependencyComparator();
		if (comparator instanceof OrderComparator) {
			return ((OrderComparator) comparator).withSourceProvider(
					createFactoryAwareOrderSourceProvider(matchingBeans));
		}
		else {
			return comparator;
		}
	}

	private Comparator<Object> adaptOrderComparator(Map<String, ?> matchingBeans) {
		Comparator<Object> dependencyComparator = getDependencyComparator();
		OrderComparator comparator = (dependencyComparator instanceof OrderComparator ?
				(OrderComparator) dependencyComparator : OrderComparator.INSTANCE);
		return comparator.withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
	}

	private OrderComparator.OrderSourceProvider createFactoryAwareOrderSourceProvider(Map<String, ?> beans) {
		IdentityHashMap<Object, String> instancesToBeanNames = new IdentityHashMap<>();
		beans.forEach((beanName, instance) -> instancesToBeanNames.put(instance, beanName));
		return new FactoryAwareOrderSourceProvider(instancesToBeanNames);
	}

	/**
	 * <h3>🎯⭐ findAutowireCandidates —— @Autowired 候选者搜索引擎</h3>
	 * <p><b>这是 Spring DI 查找"谁能被注入"的核心方法！</b>三轮搜索：</p>
	 *
	 * <p><b>第一轮：正常匹配</b><br/>
	 * ① 先从 resolvableDependencies（特殊依赖表）中找匹配——如 BeanFactory/ApplicationContext/ResourceLoader<br/>
	 * ② 从容器中按类型找所有候选者（BeanFactoryUtils.beanNamesForTypeIncludingAncestors，含父容器）<br/>
	 * ③ 过滤：排除自引用（自己不能注入自己）+ isAutowireCandidate() 检查（@Qualifier/泛型匹配）</p>
	 *
	 * <p><b>第二轮：降级匹配（fallback）</b><br/>
	 * 如果第一轮找不到候选者 → 使用 descriptor.forFallbackMatch() 放宽匹配条件再找一遍。<br/>
	 * 但是如果是多元素注入（Collection/Map），还要求候选者有 @Qualifier 限定。</p>
	 *
	 * <p><b>第三轮：自引用兜底</b><br/>
	 * 如果前两轮都找不到 → 允许自引用（自己注入自己），作为最后的兜底。<br/>
	 * 但多元素注入时不允许注入自身（避免无限递归）。</p>
	 * <hr/>
	 * Find bean instances that match the required type.
	 * Called during autowiring for the specified bean.
	 * @param beanName the name of the bean that is about to be wired
	 * @param requiredType the actual type of bean to look for
	 * (may be an array component type or collection element type)
	 * @param descriptor the descriptor of the dependency to resolve
	 * @return a Map of candidate names and candidate instances that match
	 * the required type (never {@code null})
	 * @throws BeansException in case of errors
	 * @see #autowireByType
	 * @see #autowireConstructor
	 */
	protected Map<String, Object> findAutowireCandidates(
			@Nullable String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {

		String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
				this, requiredType, true, descriptor.isEager());
		Map<String, Object> result = CollectionUtils.newLinkedHashMap(candidateNames.length);
		for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
			Class<?> autowiringType = classObjectEntry.getKey();
			if (autowiringType.isAssignableFrom(requiredType)) {
				Object autowiringValue = classObjectEntry.getValue();
				autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
				if (requiredType.isInstance(autowiringValue)) {
					result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
					break;
				}
			}
		}
		for (String candidate : candidateNames) {
			if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
				addCandidateEntry(result, candidate, descriptor, requiredType);
			}
		}
		if (result.isEmpty()) {
			boolean multiple = indicatesMultipleBeans(requiredType);
			// Consider fallback matches if the first pass failed to find anything...
			DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();
			for (String candidate : candidateNames) {
				if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) &&
						(!multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
					addCandidateEntry(result, candidate, descriptor, requiredType);
				}
			}
			if (result.isEmpty() && !multiple) {
				// Consider self references as a final pass...
				// but in the case of a dependency collection, not the very same bean itself.
				for (String candidate : candidateNames) {
					if (isSelfReference(beanName, candidate) &&
							(!(descriptor instanceof MultiElementDescriptor) || !beanName.equals(candidate)) &&
							isAutowireCandidate(candidate, fallbackDescriptor)) {
						addCandidateEntry(result, candidate, descriptor, requiredType);
					}
				}
			}
		}
		return result;
	}

	/**
	 * <h3>🎯 addCandidateEntry —— 候选者登记（延迟实例化优化！）</h3>
	 * <p>不是所有候选者都需要立即创建实例！分三种情况：<br/>
	 * ① 多元素注入（MultiElementDescriptor）→ 立即 resolveCandidate() 创建实例（因为全部都要用）<br/>
	 * ② 候选者已在单例池中 → 直接取出实例<br/>
	 * ③ 其他 → <b>只记录类型（Class），不创建实例！</b>等消歧完毕确定唯一候选者后再创建<br/>
	 * <b>这是一个性能优化</b>——如果有 10 个候选者，最终只用 1 个，不需要全部创建！</p>
	 */
	private void addCandidateEntry(Map<String, Object> candidates, String candidateName,
			DependencyDescriptor descriptor, Class<?> requiredType) {

		if (descriptor instanceof MultiElementDescriptor) {
			Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
			if (!(beanInstance instanceof NullBean)) {
				candidates.put(candidateName, beanInstance);
			}
		}
		else if (containsSingleton(candidateName) || (descriptor instanceof StreamDependencyDescriptor &&
				((StreamDependencyDescriptor) descriptor).isOrdered())) {
			Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
			candidates.put(candidateName, (beanInstance instanceof NullBean ? null : beanInstance));
		}
		else {
			candidates.put(candidateName, getType(candidateName));
		}
	}

	/**
	 * <h3>🎯 determineAutowireCandidate —— 多候选者消歧裁判（三级降级！）</h3>
	 * <p>当 findAutowireCandidates 找到多个候选者时，由这个方法决定谁胜出。<br/>
	 * <b>三级消歧策略（优先级递减）</b>：<br/>
	 * ① <b>@Primary</b>：determinePrimaryCandidate() —— 谁标了 @Primary 谁赢（有且只能有一个！多个报错）<br/>
	 * ② <b>@Priority</b>：determineHighestPriorityCandidate() —— javax.annotation.Priority 值最小的赢<br/>
	 * ③ <b>名字匹配</b>：候选者的 beanName == 注入点的字段名/参数名 → 自动选中<br/>
	 *    或者候选者是 resolvableDependencies 中的值 → 也选中<br/>
	 * ④ 都不匹配 → 返回 null → 上层抛 NoUniqueBeanDefinitionException</p>
	 * <p><b>这就是 @Autowired 遇到多个同类型 Bean 时的消歧全流程！</b></p>
	 * <hr/>
	 * Determine the autowire candidate in the given set of beans.
	 * <p>Looks for {@code @Primary} and {@code @Priority} (in that order).
	 * @param candidates a Map of candidate names and candidate instances
	 * that match the required type, as returned by {@link #findAutowireCandidates}
	 * @param descriptor the target dependency to match against
	 * @return the name of the autowire candidate, or {@code null} if none found
	 */
	@Nullable
	protected String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
		Class<?> requiredType = descriptor.getDependencyType();
		String primaryCandidate = determinePrimaryCandidate(candidates, requiredType);
		if (primaryCandidate != null) {
			return primaryCandidate;
		}
		String priorityCandidate = determineHighestPriorityCandidate(candidates, requiredType);
		if (priorityCandidate != null) {
			return priorityCandidate;
		}
		// Fallback
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateName = entry.getKey();
			Object beanInstance = entry.getValue();
			if ((beanInstance != null && this.resolvableDependencies.containsValue(beanInstance)) ||
					matchesBeanName(candidateName, descriptor.getDependencyName())) {
				return candidateName;
			}
		}
		return null;
	}

	/**
	 * <h3>🎯 determinePrimaryCandidate —— @Primary 消歧</h3>
	 * <p>遍历所有候选者，找标了 @Primary 的。<br/>
	 * <b>规则</b>：<br/>
	 * - 有且仅有一个 @Primary → 返回它<br/>
	 * - 两个都标了 @Primary 且都在本容器 → 抛 NoUniqueBeanDefinitionException<br/>
	 * - 一个在本容器、一个在父容器 → 本容器的优先<br/>
	 * - 没有 @Primary → 返回 null，走下一级消歧</p>
	 * <hr/>
	 * Determine the primary candidate in the given set of beans.
	 * @param candidates a Map of candidate names and candidate instances
	 * (or candidate classes if not created yet) that match the required type
	 * @param requiredType the target dependency type to match against
	 * @return the name of the primary candidate, or {@code null} if none found
	 * @see #isPrimary(String, Object)
	 */
	@Nullable
	protected String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		String primaryBeanName = null;
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();
			if (isPrimary(candidateBeanName, beanInstance)) {
				if (primaryBeanName != null) {
					boolean candidateLocal = containsBeanDefinition(candidateBeanName);
					boolean primaryLocal = containsBeanDefinition(primaryBeanName);
					if (candidateLocal && primaryLocal) {
						throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
								"more than one 'primary' bean found among candidates: " + candidates.keySet());
					}
					else if (candidateLocal) {
						primaryBeanName = candidateBeanName;
					}
				}
				else {
					primaryBeanName = candidateBeanName;
				}
			}
		}
		return primaryBeanName;
	}

	/**
	 * <h3>🎯 determineHighestPriorityCandidate —— @Priority 消歧</h3>
	 * <p>遍历所有候选者，找 {@code @javax.annotation.Priority} 值最小的（数值越小优先级越高）。<br/>
	 * 注意：这里用的是 {@code OrderComparator.getPriority()} 获取优先级值。<br/>
	 * 如果两个候选者 Priority 值相同 → 抛 NoUniqueBeanDefinitionException。<br/>
	 * 没有候选者标 @Priority → 返回 null，走名字匹配兜底。</p>
	 * <hr/>
	 * Determine the candidate with the highest priority in the given set of beans.
	 * <p>Based on {@code @javax.annotation.Priority}. As defined by the related
	 * {@link org.springframework.core.Ordered} interface, the lowest value has
	 * the highest priority.
	 * @param candidates a Map of candidate names and candidate instances
	 * (or candidate classes if not created yet) that match the required type
	 * @param requiredType the target dependency type to match against
	 * @return the name of the candidate with the highest priority,
	 * or {@code null} if none found
	 * @see #getPriority(Object)
	 */
	@Nullable
	protected String determineHighestPriorityCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		String highestPriorityBeanName = null;
		Integer highestPriority = null;
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();
			if (beanInstance != null) {
				Integer candidatePriority = getPriority(beanInstance);
				if (candidatePriority != null) {
					if (highestPriorityBeanName != null) {
						if (candidatePriority.equals(highestPriority)) {
							throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
									"Multiple beans found with the same priority ('" + highestPriority +
									"') among candidates: " + candidates.keySet());
						}
						else if (candidatePriority < highestPriority) {
							highestPriorityBeanName = candidateBeanName;
							highestPriority = candidatePriority;
						}
					}
					else {
						highestPriorityBeanName = candidateBeanName;
						highestPriority = candidatePriority;
					}
				}
			}
		}
		return highestPriorityBeanName;
	}

	/**
	 * <h3>🎯 isPrimary —— 判断 Bean 是否标了 @Primary</h3>
	 * <p>先查本容器的 BD，没有则委托父容器查。父子容器都可以有 @Primary。</p>
	 * <hr/>
	 * Return whether the bean definition for the given bean name has been
	 * marked as a primary bean.
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance (can be null)
	 * @return whether the given bean qualifies as primary
	 */
	protected boolean isPrimary(String beanName, Object beanInstance) {
		String transformedBeanName = transformedBeanName(beanName);
		if (containsBeanDefinition(transformedBeanName)) {
			return getMergedLocalBeanDefinition(transformedBeanName).isPrimary();
		}
		BeanFactory parent = getParentBeanFactory();
		return (parent instanceof DefaultListableBeanFactory &&
				((DefaultListableBeanFactory) parent).isPrimary(transformedBeanName, beanInstance));
	}

	/**
	 * Return the priority assigned for the given bean instance by
	 * the {@code javax.annotation.Priority} annotation.
	 * <p>The default implementation delegates to the specified
	 * {@link #setDependencyComparator dependency comparator}, checking its
	 * {@link OrderComparator#getPriority method} if it is an extension of
	 * Spring's common {@link OrderComparator} - typically, an
	 * {@link org.springframework.core.annotation.AnnotationAwareOrderComparator}.
	 * If no such comparator is present, this implementation returns {@code null}.
	 * @param beanInstance the bean instance to check (can be {@code null})
	 * @return the priority assigned to that bean or {@code null} if none is set
	 */
	@Nullable
	protected Integer getPriority(Object beanInstance) {
		Comparator<Object> comparator = getDependencyComparator();
		if (comparator instanceof OrderComparator) {
			return ((OrderComparator) comparator).getPriority(beanInstance);
		}
		return null;
	}

	/**
	 * <h3>🎯 matchesBeanName —— 消歧第三级的名字匹配</h3>
	 * <p>判断候选者名字是否与注入点的依赖名（字段名/参数名）匹配。<br/>
	 * 同时检查别名（getAliases）——即使 beanName 不直接匹配，别名匹配也算。</p>
	 */
	protected boolean matchesBeanName(String beanName, @Nullable String candidateName) {
		return (candidateName != null &&
				(candidateName.equals(beanName) || ObjectUtils.containsElement(getAliases(beanName), candidateName)));
	}

	/**
	 * <h3>🎯 isSelfReference —— 判断是否"自己注入自己"</h3>
	 * <p>两种自引用：<br/>
	 * ① beanName == candidateName（直接自引用）<br/>
	 * ② candidateName 的 BD.factoryBeanName == beanName（@Bean 方法定义在自己身上的工厂自引用）<br/>
	 * findAutowireCandidates 前两轮会排除自引用，第三轮兜底才允许。</p>
	 */
	private boolean isSelfReference(@Nullable String beanName, @Nullable String candidateName) {
		return (beanName != null && candidateName != null &&
				(beanName.equals(candidateName) || (containsBeanDefinition(candidateName) &&
						beanName.equals(getMergedLocalBeanDefinition(candidateName).getFactoryBeanName()))));
	}

	/**
	 * <h3>🎯 raiseNoMatchingBeanFound —— 找不到候选者时的报错方法</h3>
	 * <p>先调 checkBeanNotOfRequiredType() 检查是否是"代理导致类型不匹配"的问题
	 * （比如 JDK 代理只实现接口，但你注入的是具体类），如果是则抛 BeanNotOfRequiredTypeException。<br/>
	 * 否则抛 NoSuchBeanDefinitionException，附带注入点的注解信息帮助定位问题。</p>
	 */
	private void raiseNoMatchingBeanFound(
			Class<?> type, ResolvableType resolvableType, DependencyDescriptor descriptor) throws BeansException {

		checkBeanNotOfRequiredType(type, descriptor);

		throw new NoSuchBeanDefinitionException(resolvableType,
				"expected at least 1 bean which qualifies as autowire candidate. " +
				"Dependency annotations: " + ObjectUtils.nullSafeToString(descriptor.getAnnotations()));
	}

	/**
	 * <h3>🎯 checkBeanNotOfRequiredType —— 代理导致的类型不匹配检测</h3>
	 * <p>一个常见的坑：Bean 的原始类型匹配，但 AOP 代理后的类型不匹配！<br/>
	 * 比如：你注入 {@code UserServiceImpl}（具体类），但 Bean 被 JDK 动态代理了，
	 * 代理类只实现了 {@code UserService} 接口，不是 {@code UserServiceImpl} 类型。<br/>
	 * 这个方法就是检测这种情况并给出更有意义的错误信息：<br/>
	 * "你的 Bean 存在，但它被代理了，代理类型和你要求的类型不匹配！"<br/>
	 * 还会递归检查父容器。</p>
	 */
	private void checkBeanNotOfRequiredType(Class<?> type, DependencyDescriptor descriptor) {
		for (String beanName : this.beanDefinitionNames) {
			try {
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				Class<?> targetType = mbd.getTargetType();
				if (targetType != null && type.isAssignableFrom(targetType) &&
						isAutowireCandidate(beanName, mbd, descriptor, getAutowireCandidateResolver())) {
					// Probably a proxy interfering with target type match -> throw meaningful exception.
					Object beanInstance = getSingleton(beanName, false);
					Class<?> beanType = (beanInstance != null && beanInstance.getClass() != NullBean.class ?
							beanInstance.getClass() : predictBeanType(beanName, mbd));
					if (beanType != null && !type.isAssignableFrom(beanType)) {
						throw new BeanNotOfRequiredTypeException(beanName, type, beanType);
					}
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Bean definition got removed while we were iterating -> ignore.
			}
		}

		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) parent).checkBeanNotOfRequiredType(type, descriptor);
		}
	}

	/**
	 * <h3>🎯 createOptionalDependency —— Optional&lt;T&gt; 类型注入的处理</h3>
	 * <p>将 DependencyDescriptor 的 required 改为 false（Optional 天然表示可选），
	 * 然后调 doResolveDependency()，结果包装为 Optional。找到了就 Optional.of()，找不到就 Optional.empty()。</p>
	 */
	private Optional<?> createOptionalDependency(
			DependencyDescriptor descriptor, @Nullable String beanName, final Object... args) {

		DependencyDescriptor descriptorToUse = new NestedDependencyDescriptor(descriptor) {
			@Override
			public boolean isRequired() {
				return false;
			}
			@Override
			public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
				return (!ObjectUtils.isEmpty(args) ? beanFactory.getBean(beanName, args) :
						super.resolveCandidate(beanName, requiredType, beanFactory));
			}
		};
		Object result = doResolveDependency(descriptorToUse, beanName, null, null);
		return (result instanceof Optional ? (Optional<?>) result : Optional.ofNullable(result));
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(ObjectUtils.identityToString(this));
		sb.append(": defining beans [");
		sb.append(StringUtils.collectionToCommaDelimitedString(this.beanDefinitionNames));
		sb.append("]; ");
		BeanFactory parent = getParentBeanFactory();
		if (parent == null) {
			sb.append("root of factory hierarchy");
		}
		else {
			sb.append("parent: ").append(ObjectUtils.identityToString(parent));
		}
		return sb.toString();
	}


	/* =======================================================================================================
	        🧩 第六战区：内部支撑类 —— 序列化、ObjectProvider 实现、JSR-330 兼容、排序支持
	           这些内部类是主流程的辅助设施
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	/**
	 * <h3>🧩 序列化防御——DefaultListableBeanFactory 自身不能被反序列化！</h3>
	 * <p>直接抛异常。反序列化时不是还原工厂本身，而是通过 SerializedBeanFactoryReference
	 * 用 serializationId 从全局黄页（serializableFactories）中找回工厂实例。</p>
	 */
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		throw new NotSerializableException("DefaultListableBeanFactory itself is not deserializable - " +
				"just a SerializedBeanFactoryReference is");
	}

	protected Object writeReplace() throws ObjectStreamException {
		if (this.serializationId != null) {
			return new SerializedBeanFactoryReference(this.serializationId);
		}
		else {
			throw new NotSerializableException("DefaultListableBeanFactory has no serialization id");
		}
	}


	/**
	 * <h3>🧩 SerializedBeanFactoryReference —— 序列化替身</h3>
	 * <p>writeReplace() 时返回这个轻量替身（只携带 serializationId），而不是序列化整个工厂。<br/>
	 * 反序列化时 readResolve() 用 id 从 serializableFactories（全局黄页）中找回真正的工厂实例。<br/>
	 * 如果找不到（WeakReference 已被 GC 回收），返回一个空的 dummy 工厂作为降级。</p>
	 * <hr/>
	 * Minimal id reference to the factory.
	 * Resolved to the actual factory instance on deserialization.
	 */
	private static class SerializedBeanFactoryReference implements Serializable {

		private final String id;

		public SerializedBeanFactoryReference(String id) {
			this.id = id;
		}

		private Object readResolve() {
			Reference<?> ref = serializableFactories.get(this.id);
			if (ref != null) {
				Object result = ref.get();
				if (result != null) {
					return result;
				}
			}
			// Lenient fallback: dummy factory in case of original factory not found...
			DefaultListableBeanFactory dummyFactory = new DefaultListableBeanFactory();
			dummyFactory.serializationId = this.id;
			return dummyFactory;
		}
	}


	/**
	 * <h3>🧩 NestedDependencyDescriptor —— 嵌套依赖描述符</h3>
	 * <p>用于 {@code Optional<T>} 和 {@code ObjectProvider<T>} 场景：
	 * 将 DependencyDescriptor 的嵌套层级+1，以解析 Optional/Provider 内部的真实类型 T。</p>
	 */
	private static class NestedDependencyDescriptor extends DependencyDescriptor {

		public NestedDependencyDescriptor(DependencyDescriptor original) {
			super(original);
			increaseNestingLevel();
		}
	}


	/**
	 * <h3>🧩 MultiElementDescriptor —— 多元素注入标记</h3>
	 * <p>当注入 {@code List<T>}、{@code T[]}、{@code Map<String,T>} 时，
	 * 用这个标记告诉 addCandidateEntry()：所有候选者都要立即创建实例（因为全部都要注入）。</p>
	 */
	private static class MultiElementDescriptor extends NestedDependencyDescriptor {

		public MultiElementDescriptor(DependencyDescriptor original) {
			super(original);
		}
	}


	/**
	 * <h3>🧩 StreamDependencyDescriptor —— Stream 注入标记</h3>
	 * <p>当注入 {@code ObjectProvider<T>.stream()/orderedStream()} 时使用。<br/>
	 * {@code ordered=true} 表示需要按 @Order 排序（orderedStream 场景）。</p>
	 */
	private static class StreamDependencyDescriptor extends DependencyDescriptor {

		private final boolean ordered;

		public StreamDependencyDescriptor(DependencyDescriptor original, boolean ordered) {
			super(original);
			this.ordered = ordered;
		}

		public boolean isOrdered() {
			return this.ordered;
		}
	}


	/**
	 * 🧩 BeanObjectProvider —— ObjectProvider + Serializable 的标记接口。
	 * getBeanProvider() 返回的匿名类实现此接口，确保 ObjectProvider 可序列化。
	 */
	private interface BeanObjectProvider<T> extends ObjectProvider<T>, Serializable {
	}


	/**
	 * <h3>🧩 DependencyObjectProvider —— 延迟依赖解析的 ObjectProvider 实现</h3>
	 * <p>当 resolveDependency() 遇到 {@code ObjectFactory<T>} 或 {@code ObjectProvider<T>} 类型的注入点时，
	 * 不立即解析依赖，而是返回这个 DependencyObjectProvider。<br/>
	 * 每次调用 getObject()/getIfAvailable()/stream() 时才真正调 doResolveDependency() 解析。<br/>
	 * <b>这就是 @Autowired ObjectProvider&lt;T&gt; 的底层实现！</b><br/>
	 * 延迟解析的价值：<br/>
	 * ① 解决可选依赖问题（getIfAvailable 不报错）<br/>
	 * ② 解决 Prototype 作用域每次获取新实例的需求<br/>
	 * ③ 解决循环依赖的某些场景</p>
	 * <hr/>
	 * Serializable ObjectFactory/ObjectProvider for lazy resolution of a dependency.
	 */
	private class DependencyObjectProvider implements BeanObjectProvider<Object> {

		private final DependencyDescriptor descriptor;

		private final boolean optional;

		@Nullable
		private final String beanName;

		public DependencyObjectProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
			this.descriptor = new NestedDependencyDescriptor(descriptor);
			this.optional = (this.descriptor.getDependencyType() == Optional.class);
			this.beanName = beanName;
		}

		@Override
		public Object getObject() throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName);
			}
			else {
				Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
				if (result == null) {
					throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
				}
				return result;
			}
		}

		@Override
		public Object getObject(final Object... args) throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName, args);
			}
			else {
				DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
					@Override
					public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
						return beanFactory.getBean(beanName, args);
					}
				};
				Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
				if (result == null) {
					throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
				}
				return result;
			}
		}

		@Override
		@Nullable
		public Object getIfAvailable() throws BeansException {
			try {
				if (this.optional) {
					return createOptionalDependency(this.descriptor, this.beanName);
				}
				else {
					DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
						@Override
						public boolean isRequired() {
							return false;
						}
					};
					return doResolveDependency(descriptorToUse, this.beanName, null, null);
				}
			}
			catch (ScopeNotActiveException ex) {
				// Ignore resolved bean in non-active scope
				return null;
			}
		}

		@Override
		public void ifAvailable(Consumer<Object> dependencyConsumer) throws BeansException {
			Object dependency = getIfAvailable();
			if (dependency != null) {
				try {
					dependencyConsumer.accept(dependency);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope, even on scoped proxy invocation
				}
			}
		}

		@Override
		@Nullable
		public Object getIfUnique() throws BeansException {
			DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
				@Override
				public boolean isRequired() {
					return false;
				}

				@Override
				@Nullable
				public Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) {
					return null;
				}
			};
			try {
				if (this.optional) {
					return createOptionalDependency(descriptorToUse, this.beanName);
				}
				else {
					return doResolveDependency(descriptorToUse, this.beanName, null, null);
				}
			}
			catch (ScopeNotActiveException ex) {
				// Ignore resolved bean in non-active scope
				return null;
			}
		}

		@Override
		public void ifUnique(Consumer<Object> dependencyConsumer) throws BeansException {
			Object dependency = getIfUnique();
			if (dependency != null) {
				try {
					dependencyConsumer.accept(dependency);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope, even on scoped proxy invocation
				}
			}
		}

		@Nullable
		protected Object getValue() throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName);
			}
			else {
				return doResolveDependency(this.descriptor, this.beanName, null, null);
			}
		}

		@Override
		public Stream<Object> stream() {
			return resolveStream(false);
		}

		@Override
		public Stream<Object> orderedStream() {
			return resolveStream(true);
		}

		@SuppressWarnings("unchecked")
		private Stream<Object> resolveStream(boolean ordered) {
			DependencyDescriptor descriptorToUse = new StreamDependencyDescriptor(this.descriptor, ordered);
			Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
			return (result instanceof Stream ? (Stream<Object>) result : Stream.of(result));
		}
	}


	/**
	 * <h3>🧩 Jsr330Factory —— JSR-330 (javax.inject) 兼容层</h3>
	 * <p>当注入点类型是 {@code javax.inject.Provider<T>} 时，返回 Jsr330Provider。<br/>
	 * 为什么单独拆成内部类？避免 DefaultListableBeanFactory 对 javax.inject 的硬依赖——
	 * 如果 classpath 上没有 javax.inject，这个内部类不会被加载，不会 ClassNotFoundException。<br/>
	 * 同时对 GraalVM native-image 友好（Graal 不会扫描到这个内嵌类）。</p>
	 * <hr/>
	 * Separate inner class for avoiding a hard dependency on the {@code javax.inject} API.
	 * Actual {@code javax.inject.Provider} implementation is nested here in order to make it
	 * invisible for Graal's introspection of DefaultListableBeanFactory's nested classes.
	 */
	private class Jsr330Factory implements Serializable {

		public Object createDependencyProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
			return new Jsr330Provider(descriptor, beanName);
		}

		private class Jsr330Provider extends DependencyObjectProvider implements Provider<Object> {

			public Jsr330Provider(DependencyDescriptor descriptor, @Nullable String beanName) {
				super(descriptor, beanName);
			}

			@Override
			@Nullable
			public Object get() throws BeansException {
				return getValue();
			}
		}
	}


	/**
	 * <h3>🧩 FactoryAwareOrderSourceProvider —— 排序时感知 @Bean 工厂方法上的 @Order</h3>
	 * <p>当注入 {@code List<T>} 需要排序时，排序器需要知道每个 Bean 的 @Order 值。<br/>
	 * 但 @Order 可能标在：① Bean 类上 ② @Bean 工厂方法上 ③ BD 的 targetType 上。<br/>
	 * 这个 OrderSourceProvider 把工厂方法和 targetType 作为排序来源提供给 OrderComparator，
	 * 让排序能正确识别所有标注位置上的 @Order 值。</p>
	 * <hr/>
	 * An {@link org.springframework.core.OrderComparator.OrderSourceProvider} implementation
	 * that is aware of the bean metadata of the instances to sort.
	 * <p>Lookup for the method factory of an instance to sort, if any, and let the
	 * comparator retrieve the {@link org.springframework.core.annotation.Order}
	 * value defined on it. This essentially allows for the following construct:
	 */
	private class FactoryAwareOrderSourceProvider implements OrderComparator.OrderSourceProvider {

		private final Map<Object, String> instancesToBeanNames;

		public FactoryAwareOrderSourceProvider(Map<Object, String> instancesToBeanNames) {
			this.instancesToBeanNames = instancesToBeanNames;
		}

		@Override
		@Nullable
		public Object getOrderSource(Object obj) {
			String beanName = this.instancesToBeanNames.get(obj);
			if (beanName == null) {
				return null;
			}
			try {
				RootBeanDefinition beanDefinition = (RootBeanDefinition) getMergedBeanDefinition(beanName);
				List<Object> sources = new ArrayList<>(2);
				Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
				if (factoryMethod != null) {
					sources.add(factoryMethod);
				}
				Class<?> targetType = beanDefinition.getTargetType();
				if (targetType != null && targetType != obj.getClass()) {
					sources.add(targetType);
				}
				return sources.toArray();
			}
			catch (NoSuchBeanDefinitionException ex) {
				return null;
			}
		}
	}

}
