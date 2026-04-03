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

package org.springframework.context.support;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.NativeDetector;
import org.springframework.core.ResolvableType;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>ApplicationContext 的"模板方法骨架"——refresh() 十二步的总指挥！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.support.AbstractApplicationContext}</li>
 * <li><b>中文名</b>：抽象应用上下文 —— Spring 容器启动流程的"总指挥部"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 support 包（注意！support 包 = 骨架实现区！
 * context 包定义接口契约（ApplicationContext/ConfigurableApplicationContext），
 * support 包提供抽象骨架和具体实现——AbstractApplicationContext 是所有具体上下文的公共祖先，
 * GenericApplicationContext、ClassPathXmlApplicationContext 等都在这个包里）</li>
 * <li><b>类层级</b>：继承 {@code DefaultResourceLoader}（资源加载能力）+ 实现 {@code ConfigurableApplicationContext}（可配置容器契约），
 * 用<b>模板方法模式</b>定义了 refresh() 十二步的完整流程骨架</li>
 * </ul>
 *
 * <h3>💡 为什么需要 AbstractApplicationContext？——从 BeanFactory 到 ApplicationContext 的"能力飞跃"！</h3>
 * <p>回顾 BeanFactory 体系：它只管"造 Bean"——创建、注入、初始化、销毁。<br/>
 * 但一个真正的应用容器还需要更多：</p>
 * <ul>
 * <li><b>启动流程编排</b>：谁先谁后？BFPP 先跑、BPP 再注册、最后才造单例——这套顺序不能乱！</li>
 * <li><b>事件广播系统</b>：ContextRefreshedEvent、ContextClosedEvent——Bean 之间需要松耦合的通信机制</li>
 * <li><b>国际化消息</b>：MessageSource——让应用能说多种语言</li>
 * <li><b>资源加载</b>：classpath:、file:、http:——统一的资源定位能力</li>
 * <li><b>生命周期管理</b>：start/stop/close——优雅启停</li>
 * <li><b>父子容器层级</b>：子容器能访问父容器的 Bean，反过来不行</li>
 * <li><b>环境抽象</b>：Environment——profile 切换、property 解析</li>
 * </ul>
 * <p>AbstractApplicationContext 就是把上述所有能力<b>整合在一起</b>的"总指挥部"——<br/>
 * 它不直接持有 BeanFactory（由子类决定），但通过 {@code getBeanFactory()} 抽象方法获取内部 BeanFactory，
 * 然后在 {@code refresh()} 中编排完整的启动流程。<br/>
 * <b>这就是模板方法模式的教科书级实践：骨架在父类，变化点留给子类！</b></p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>模板方法模式的巅峰实践——refresh() 定义流程，子类填充细节</b><br/>
 * refresh() 定义了 12 步骨架流程，其中 {@code refreshBeanFactory()}、{@code postProcessBeanFactory()}、
 * {@code onRefresh()}、{@code onClose()} 等是子类扩展点。<br/>
 * 传统 XML 派（AbstractRefreshableApplicationContext）在 refreshBeanFactory 中砸旧建新；<br/>
 * 现代注解派（GenericApplicationContext）在 refreshBeanFactory 中只做 CAS 防重刷。<br/>
 * 同一个 refresh() 骨架，两种完全不同的行为！<br/>
 * <b>业务借鉴</b>：当你的业务有"固定流程 + 可变步骤"的特征时（如订单处理流程：校验→扣库存→支付→通知，
 * 但不同渠道的校验逻辑不同），用模板方法模式是最优解。</li>
 *
 * <li><b>"组合优于继承"的完美示范——持有而非成为</b><br/>
 * AbstractApplicationContext 实现了 MessageSource、ResourcePatternResolver、ApplicationEventPublisher 等接口，
 * 但它<b>不亲自处理</b>——而是内部持有 messageSource、applicationEventMulticaster 等委托对象。<br/>
 * 调用 getMessage() 时，实际委托给内部的 MessageSource Bean；调用 publishEvent() 时，委托给 ApplicationEventMulticaster。<br/>
 * 这样每个能力都可以独立替换（自定义 MessageSource、自定义事件广播器），而不影响其他能力。<br/>
 * <b>业务借鉴</b>：你的"门面对象"可以实现多个接口来提供统一入口，但底层委托给不同的专家对象。
 * 比如你的 OrderFacade 实现了 OrderQuery + OrderCommand 接口，但分别委托给 OrderQueryService 和 OrderCommandService。</li>
 *
 * <li><b>synchronized + CAS 双保险——启动和关闭的线程安全</b><br/>
 * {@code startupShutdownMonitor} 用 synchronized 保证 refresh() 和 close() 不会并发执行；<br/>
 * {@code active}（AtomicBoolean）和 {@code closed}（AtomicBoolean）用 CAS 保证状态切换的原子性。<br/>
 * 双重保护：粗粒度的互斥锁 + 细粒度的原子状态标志。<br/>
 * <b>业务借鉴</b>：对于"启动/关闭"这种生命周期操作，不要只靠一种并发机制。
 * 用锁保证操作的互斥（不能同时启动和关闭），用原子变量保证状态的可见性（其他线程能立即看到状态变化）。</li>
 *
 * <li><b>"well-known name"约定——用魔法常量建立松耦合的组件发现</b><br/>
 * {@code messageSource}、{@code applicationEventMulticaster}、{@code lifecycleProcessor} 这三个
 * 基础设施 Bean 用固定的名称在容器中查找。如果你注册了同名的自定义 Bean，Spring 会用你的替代默认的。<br/>
 * 这就是"约定优于配置"——不需要显式声明"我的 MessageSource 是谁"，只要注册一个名为 "messageSource" 的 Bean 即可。<br/>
 * <b>业务借鉴</b>：系统中的基础设施组件（如日志、监控、缓存管理器），用约定名称发现比显式注入更灵活。
 * 允许用户通过注册同名组件来覆盖默认行为。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * DefaultResourceLoader                          （资源加载基础能力）
 * └── AbstractApplicationContext                  ← 👈 你在这里！（模板方法骨架：refresh() 十二步）
 *       │
 *       │ 实现接口：ConfigurableApplicationContext（可配置容器契约）
 *       │          → ApplicationContext → ListableBeanFactory + HierarchicalBeanFactory
 *       │          → MessageSource / ApplicationEventPublisher / ResourcePatternResolver
 *       │          → Lifecycle / Closeable
 *       │
 *       ├── AbstractRefreshableApplicationContext  （传统 XML 派：每次 refresh 砸旧建新 BeanFactory）
 *       │     └── ... → ClassPathXmlApplicationContext / FileSystemXmlApplicationContext
 *       │
 *       └── GenericApplicationContext              （现代注解派：一次性 refresh，构造时就有 BeanFactory）
 *             └── AnnotationConfigApplicationContext  ← ⭐ Spring Boot 的起点！
 *
 * ⚠️ 关键设计：AbstractApplicationContext 自身不持有 BeanFactory！
 *    通过抽象方法 getBeanFactory() 让子类决定如何提供——这就是模板方法模式的精髓。
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>3 个常量 + 15+ 个字段 + N 个方法，划分为 <b>八大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>核心成员</th></tr>
 * <tr><td><b>🏷️ 第零战区：Well-known 常量</b></td><td>定义基础设施 Bean 的约定名称</td>
 * <td>MESSAGE_SOURCE_BEAN_NAME / APPLICATION_EVENT_MULTICASTER_BEAN_NAME / LIFECYCLE_PROCESSOR_BEAN_NAME</td></tr>
 * <tr><td><b>🏗️ 第一战区：内部状态字段</b></td><td>持有容器的所有运行时状态</td>
 * <td>id/displayName/parent/environment/beanFactoryPostProcessors/active/closed/startupShutdownMonitor/
 * messageSource/applicationEventMulticaster/lifecycleProcessor/applicationListeners/earlyApplicationEvents</td></tr>
 * <tr><td><b>🚀 第二战区：refresh() 十二步</b></td><td>容器启动的完整编排流程（绝对核心！）</td>
 * <td>refresh() → prepareRefresh/obtainFreshBeanFactory/prepareBeanFactory/postProcessBeanFactory/
 * invokeBeanFactoryPostProcessors/registerBeanPostProcessors/initMessageSource/initApplicationEventMulticaster/
 * onRefresh/registerListeners/finishBeanFactoryInitialization/finishRefresh</td></tr>
 * <tr><td><b>🛑 第三战区：关闭与销毁</b></td><td>优雅关停容器</td>
 * <td>close/doClose/destroyBeans/onClose/registerShutdownHook</td></tr>
 * <tr><td><b>📢 第四战区：事件发布</b></td><td>ApplicationEventPublisher 的实现</td>
 * <td>publishEvent（3 个重载）→ 委托给 applicationEventMulticaster + 父容器冒泡</td></tr>
 * <tr><td><b>🔀 第五战区：BeanFactory 代理</b></td><td>把 BeanFactory/ListableBeanFactory 接口方法全部委托给内部 BeanFactory</td>
 * <td>getBean系列 / containsBean / getBeanNamesForType / getBeansOfType / ... （全部 assertBeanFactoryActive() 后委托）</td></tr>
 * <tr><td><b>🌍 第六战区：MessageSource 代理</b></td><td>国际化消息解析</td>
 * <td>getMessage（3 个重载）→ 委托给内部 messageSource</td></tr>
 * <tr><td><b>🔧 第七战区：抽象方法</b></td><td>留给子类实现的模板方法</td>
 * <td>refreshBeanFactory() / closeBeanFactory() / getBeanFactory()（三个抽象方法 = 两大流派的分水岭！）</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AbstractApplicationContext 的核心价值：<b>用模板方法模式定义了 Spring 容器启动（refresh）和关闭（close）
 * 的完整流程骨架，同时通过"组合 + 委托"整合了事件广播、国际化、资源加载、生命周期管理等横切能力</b>。<br/>
 * 它是 Spring 框架中<b>代码量最大、职责最重、调用链最长</b>的类之一——<br/>
 * 无论你用的是 XML 配置、注解配置、还是 Spring Boot，最终都殊途同归到这个 refresh() 方法。<br/>
 * 理解了 AbstractApplicationContext，就理解了 Spring 容器的"大动脉"——<br/>
 * 其他所有机制（BPP、BFPP、事件、AOP、事务）都是在 refresh() 的某一步中被激活的。</p>
 *
 * <hr/>
 * Abstract implementation of the {@link org.springframework.context.ApplicationContext}
 * interface. Doesn't mandate the type of storage used for configuration; simply
 * implements common context functionality. Uses the Template Method design pattern,
 * requiring concrete subclasses to implement abstract methods.
 *
 * <p>In contrast to a plain BeanFactory, an ApplicationContext is supposed
 * to detect special beans defined in its internal bean factory:
 * Therefore, this class automatically registers
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessors},
 * and {@link org.springframework.context.ApplicationListener ApplicationListeners}
 * which are defined as beans in the context.
 *
 * <p>A {@link org.springframework.context.MessageSource} may also be supplied
 * as a bean in the context, with the name "messageSource"; otherwise, message
 * resolution is delegated to the parent context. Furthermore, a multicaster
 * for application events can be supplied as an "applicationEventMulticaster" bean
 * of type {@link org.springframework.context.event.ApplicationEventMulticaster}
 * in the context; otherwise, a default multicaster of type
 * {@link org.springframework.context.event.SimpleApplicationEventMulticaster} will be used.
 *
 * <p>Implements resource loading by extending
 * {@link org.springframework.core.io.DefaultResourceLoader}.
 * Consequently treats non-URL resource paths as class path resources
 * (supporting full class path resource names that include the package path,
 * e.g. "mypackage/myresource.dat"), unless the {@link #getResourceByPath}
 * method is overridden in a subclass.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @author Sebastien Deleuze
 * @author Brian Clozel
 * @since January 21, 2001
 * @see #refreshBeanFactory
 * @see #getBeanFactory
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.MessageSource
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	/* =======================================================================================================
	          🏷️ 第零战区：Well-known 常量 —— 基础设施 Bean 的"约定门牌号"
	          容器会按这些固定名称在 BeanFactory 中查找基础设施组件，找到就用你的，找不到就用默认的。
	          这就是"约定优于配置"在 Spring 内核中的体现！
	   =======================================================================================================*/

	/**
	 * <h3>🏷️ 常量 1：MESSAGE_SOURCE_BEAN_NAME = "messageSource"</h3>
	 * <p><b>国际化消息源的约定 Bean 名称</b>。在 initMessageSource() 中按此名查找，
	 * 找到了就用你自定义的 MessageSource；找不到就创建一个 DelegatingMessageSource（空壳，委托给父容器）。</p>
	 * <hr/>
	 * The name of the {@link MessageSource} bean in the context.
	 * If none is supplied, message resolution is delegated to the parent.
	 * @see org.springframework.context.MessageSource
	 * @see org.springframework.context.support.ResourceBundleMessageSource
	 * @see org.springframework.context.support.ReloadableResourceBundleMessageSource
	 * @see #getMessage(MessageSourceResolvable, Locale)
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

	/**
	 * <h3>🏷️ 常量 2：APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster"</h3>
	 * <p><b>事件广播器的约定 Bean 名称</b>。在 initApplicationEventMulticaster() 中按此名查找，
	 * 找到了就用你自定义的广播器（如异步广播）；找不到就创建 SimpleApplicationEventMulticaster（同步广播）。<br/>
	 * 自定义广播器是实现"事件异步化"的常用手段：注册一个同名 Bean 并设置 taskExecutor 即可。</p>
	 * <hr/>
	 * The name of the {@link ApplicationEventMulticaster} bean in the context.
	 * If none is supplied, a {@link SimpleApplicationEventMulticaster} is used.
	 * @see org.springframework.context.event.ApplicationEventMulticaster
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 * @see #publishEvent(ApplicationEvent)
	 * @see #addApplicationListener(ApplicationListener)
	 */
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";

	/**
	 * <h3>🏷️ 常量 3：LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor"</h3>
	 * <p><b>生命周期处理器的约定 Bean 名称</b>。在 initLifecycleProcessor() 中按此名查找，
	 * 找到了就用你自定义的；找不到就创建 DefaultLifecycleProcessor。<br/>
	 * 负责管理 SmartLifecycle Bean 的 start/stop/onRefresh/onClose。</p>
	 * <hr/>
	 * The name of the {@link LifecycleProcessor} bean in the context.
	 * If none is supplied, a {@link DefaultLifecycleProcessor} is used.
	 * @since 3.0
	 * @see org.springframework.context.LifecycleProcessor
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 * @see #start()
	 * @see #stop()
	 */
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";


	/**
	 * Boolean flag controlled by a {@code spring.spel.ignore} system property that
	 * instructs Spring to ignore SpEL, i.e. to not initialize the SpEL infrastructure.
	 * <p>The default is "false".
	 */
	private static final boolean shouldIgnoreSpel = SpringProperties.getFlag("spring.spel.ignore");


	static {
		// Eagerly load the ContextClosedEvent class to avoid weird classloader issues
		// on application shutdown in WebLogic 8.1. (Reported by Dustin Woods.)
		ContextClosedEvent.class.getName();
	}


	/* =======================================================================================================
	          🏗️ 第一战区：内部状态字段 —— 容器运行时的"神经中枢"
	          分为四组：①身份信息 ②并发与生命周期控制 ③基础设施委托对象 ④事件系统
	   =======================================================================================================*/

	// ─────────────────── ① 身份信息与日志 ───────────────────

	/** 日志记录器，子类也能直接使用 */
	protected final Log logger = LogFactory.getLog(getClass());

	/** 容器的唯一 ID，默认是对象的 identityToString（如 "...@1a2b3c"），可通过 setId() 自定义 */
	private String id = ObjectUtils.identityToString(this);

	/** 容器的友好显示名称，用于日志和 toString()。默认同 id，可通过 setDisplayName() 自定义 */
	private String displayName = ObjectUtils.identityToString(this);

	/** 父容器引用。子容器能访问父容器的 Bean（getBean 时向上查找），但父容器看不到子容器的 Bean */
	@Nullable
	private ApplicationContext parent;

	/** 环境对象（持有 profile 激活列表 + PropertySource 属性源链），懒初始化，首次 getEnvironment() 时创建 */
	@Nullable
	private ConfigurableEnvironment environment;

	// ─────────────────── ② 并发与生命周期控制 ───────────────────

	/** 通过 addBeanFactoryPostProcessor() 手动添加的 BFPP 列表（区别于容器中以 Bean 形式注册的 BFPP！）
	 *  这些 BFPP 在 invokeBeanFactoryPostProcessors() 中优先执行 */
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	/** 容器启动时间戳（毫秒），在 prepareRefresh() 中记录 */
	private long startupDate;

	/** 🟢 容器是否处于"活跃"状态（refresh 成功后 → true，close 后 → false）。
	 *  所有 getBean/getBeanNamesForType 等代理方法都会先 assertBeanFactoryActive() 检查此标志 */
	private final AtomicBoolean active = new AtomicBoolean();

	/** 🔴 容器是否已关闭（close/doClose 时 CAS 设为 true）。
	 *  与 active 配合使用：active=false + closed=true → 已关闭；active=false + closed=false → 尚未 refresh */
	private final AtomicBoolean closed = new AtomicBoolean();

	/** 🔒 启动/关闭的全局互斥锁。refresh() 和 close() 都在此锁内执行，保证不会并发启停 */
	private final Object startupShutdownMonitor = new Object();

	/** JVM 关闭钩子线程。调用 registerShutdownHook() 后注册，JVM 关闭时自动触发 doClose() 优雅停机 */
	@Nullable
	private Thread shutdownHook;

	// ─────────────────── ③ 基础设施委托对象（"组合优于继承"的典范） ───────────────────

	/** 🔍 资源模式解析器（classpath*:、ant-style 通配符）。构造方法中创建，默认是 PathMatchingResourcePatternResolver */
	private final ResourcePatternResolver resourcePatternResolver;

	/** 🔄 生命周期处理器。在 initLifecycleProcessor() 中按 well-known name 查找或创建默认的 DefaultLifecycleProcessor。
	 *  负责驱动 SmartLifecycle Bean 的 start()/stop() */
	@Nullable
	private LifecycleProcessor lifecycleProcessor;

	/** 🌍 国际化消息源。在 initMessageSource() 中按 well-known name 查找或创建默认的 DelegatingMessageSource。
	 *  所有 getMessage() 调用都委托给它 */
	@Nullable
	private MessageSource messageSource;

	/** 📢 事件广播器。在 initApplicationEventMulticaster() 中按 well-known name 查找或创建默认的 SimpleApplicationEventMulticaster。
	 *  所有 publishEvent() 调用都委托给它 */
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;

	/** 📊 应用启动度量（Spring 5.3+），用于追踪启动过程中各步骤的耗时 */
	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	// ─────────────────── ④ 事件系统（早期事件门控机制） ───────────────────

	/** 通过 addApplicationListener() 手动注册的监听器集合（区别于容器中以 Bean 形式注册的监听器！） */
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	/** refresh() 之前的监听器快照。用于在重复 refresh 时（传统 XML 派）恢复到初始状态 */
	@Nullable
	private Set<ApplicationListener<?>> earlyApplicationListeners;

	/** 🚪 早期事件"门控"集合！在 prepareRefresh() 中创建（= 门开了），registerListeners() 中置 null（= 门关了）。
	 *  门开期间发布的事件暂存在此集合中；门关时一次性补发——这就是 earlyApplicationEvents 的精髓！
	 *  为什么需要？因为广播器在 initApplicationEventMulticaster()（第 8 步）才初始化，
	 *  而在此之前的 prepareRefresh/obtainFreshBeanFactory 阶段可能就有事件要发布 */
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;


	/**
	 * Create a new AbstractApplicationContext with no parent.
	 */
	public AbstractApplicationContext() {
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	/**
	 * Create a new AbstractApplicationContext with the given parent context.
	 * @param parent the parent context
	 */
	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}


	/* =======================================================================================================
	          🔧 第一战区（续）：ApplicationContext 接口实现 —— 身份/环境/事件发布/基础查询
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of ApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the unique id of this application context.
	 * <p>Default is the object id of the context instance, or the name
	 * of the context bean if the context is itself defined as a bean.
	 * @param id the unique id of the context
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getApplicationName() {
		return "";
	}

	/**
	 * Set a friendly name for this context.
	 * Typically done during initialization of concrete context implementations.
	 * <p>Default is the object id of the context instance.
	 */
	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	/**
	 * Return a friendly name for this context.
	 * @return a display name for this context (never {@code null})
	 */
	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Return the parent context, or {@code null} if there is no parent
	 * (that is, this context is the root of the context hierarchy).
	 */
	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	/**
	 * Set the {@code Environment} for this application context.
	 * <p>Default value is determined by {@link #createEnvironment()}. Replacing the
	 * default with this method is one option but configuration through {@link
	 * #getEnvironment()} should also be considered. In either case, such modifications
	 * should be performed <em>before</em> {@link #refresh()}.
	 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Return the {@code Environment} for this application context in configurable
	 * form, allowing for further customization.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardEnvironment}.
	 * <p>Subclasses may override this method in order to supply
	 * a custom {@link ConfigurableEnvironment} implementation.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

	/**
	 * Return this context's internal bean factory as AutowireCapableBeanFactory,
	 * if already available.
	 * @see #getBeanFactory()
	 */
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	/**
	 * Return the timestamp (ms) when this context was first loaded.
	 */
	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	/* =======================================================================================================
	          📢 第四战区：事件发布 —— ApplicationEventPublisher 的核心实现
	          三个 publishEvent 重载形成调用链：
	          publishEvent(ApplicationEvent) → publishEvent(Object, ResolvableType)
	          publishEvent(Object)           → publishEvent(Object, ResolvableType)
	          内部逻辑：① 包装为 ApplicationEvent → ② 早期事件门控判断 → ③ 广播器分发 → ④ 父容器冒泡
	   =======================================================================================================*/

	/**
	 * <h3>📢 publishEvent(ApplicationEvent) —— 发布强类型事件</h3>
	 * <p>直接委托给三参数的内部方法，eventType 传 null（让框架自动推断）。</p>
	 * <hr/>
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * @param event the event to publish (may be application-specific or a
	 * standard framework event)
	 */
	@Override
	public void publishEvent(ApplicationEvent event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 */
	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 * @param eventType the resolved event type, if known
	 * @since 4.2
	 */
	protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
		Assert.notNull(event, "Event must not be null");

		// Decorate event as an ApplicationEvent if necessary
		ApplicationEvent applicationEvent;
		if (event instanceof ApplicationEvent) {
			applicationEvent = (ApplicationEvent) event;
		}
		else {
			applicationEvent = new PayloadApplicationEvent<>(this, event);
			if (eventType == null) {
				eventType = ((PayloadApplicationEvent<?>) applicationEvent).getResolvableType();
			}
		}

		// Multicast right now if possible - or lazily once the multicaster is initialized
		if (this.earlyApplicationEvents != null) {
			this.earlyApplicationEvents.add(applicationEvent);
		}
		else {
			getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
		}

		// Publish event via parent context as well...
		if (this.parent != null) {
			if (this.parent instanceof AbstractApplicationContext) {
				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
			}
			else {
				this.parent.publishEvent(event);
			}
		}
	}

	/**
	 * Return the internal ApplicationEventMulticaster used by the context.
	 * @return the internal ApplicationEventMulticaster (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
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
	 * Return the internal LifecycleProcessor used by the context.
	 * @return the internal LifecycleProcessor (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	/**
	 * Return the ResourcePatternResolver to use for resolving location patterns
	 * into Resource instances. Default is a
	 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver},
	 * supporting Ant-style location patterns.
	 * <p>Can be overridden in subclasses, for extended resolution strategies,
	 * for example in a web environment.
	 * <p><b>Do not call this when needing to resolve a location pattern.</b>
	 * Call the context's {@code getResources} method instead, which
	 * will delegate to the ResourcePatternResolver.
	 * @return the ResourcePatternResolver for this context
	 * @see #getResources
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}


	/* =======================================================================================================
	          🔧 第一战区（续）：ConfigurableApplicationContext 接口实现 —— 容器的可配置能力
	          setParent（父子容器 + 环境合并）/ addBeanFactoryPostProcessor / addApplicationListener
	          这些方法都在 refresh() 之前调用，用于"组装"容器的配置
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of ConfigurableApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the parent of this application context.
	 * <p>The parent {@linkplain ApplicationContext#getEnvironment() environment} is
	 * {@linkplain ConfigurableEnvironment#merge(ConfigurableEnvironment) merged} with
	 * this (child) application context environment if the parent is non-{@code null} and
	 * its environment is an instance of {@link ConfigurableEnvironment}.
	 * @see ConfigurableEnvironment#merge(ConfigurableEnvironment)
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment) {
				getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
			}
		}
	}

	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}

	/**
	 * Return the list of BeanFactoryPostProcessors that will get applied
	 * to the internal BeanFactory.
	 */
	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		this.applicationListeners.add(listener);
	}

	/**
	 * Return the list of statically specified ApplicationListeners.
	 */
	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	/**
	 * <h3>架构巅峰：Spring 启动十二步（工厂量产总指挥） 🏭</h3>
	 * <p>
	 * 这就是传说中的<b>“Spring 启动十二步”</b>！无论你是用传统的 XML、纯注解，亦或是
	 * 目前最流行的 Spring Boot，底层最终都会殊途同归，来到这个方法。
	 * 它就像是交响乐的指挥棒，一挥动，整个 IoC 容器才真正活了过来。
	 * </p>
	 * <p>
	 * 既然我们前面用<b>“全自动 Bean 图纸加工厂”</b>的比喻把前期准备工作讲透了，
	 * 现在让我们继续用这个视角，来看看这家超级工厂是如何正式拉开帷幕、投入大规模量产的！
	 * 这 12 步看似复杂，但我们可以清晰地将其划分为 5 个大阶段。
	 * </p>
	 *
	 * @throws BeansException if the bean factory could not be initialized
	 * @throws IllegalStateException if already initialized and multiple refresh attempts are not supported
	 */
	@Override
	public void refresh() throws BeansException, IllegalStateException {
		/*
		 * 🛡️ 门卫大爷的铁腕：全局加锁
		 * [工厂广播] “全厂注意，现在开始核心启动流程！期间任何人不准乱动！”
		 * [原理解析] 防止多线程环境下，有人在工厂启动一半时试图关闭工厂或重复启动。Spring 一上来就加上了全局同步锁 (startupShutdownMonitor)，保证启动过程的绝对安全。
		 */
		synchronized (this.startupShutdownMonitor) {
			StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");

/* =======================================🏗️ 阶段一：工厂奠基与打扫场地 (第 1-4 步)=======================================
 * [核心目标] 把场地腾出来，准备好最基础的工具。*/
			// 1. 打扫场地：记录工厂启动时间，检查环境变量里必须存在的属性（如数据库密码配没配）。
			// Prepare this context for refreshing.
			prepareRefresh();

			// 2. 搬来核心仓库：极其关键！把大管家 DefaultListableBeanFactory（存图纸的底层仓库）拿出来并刷新。
			// Tell the subclass to refresh the internal bean factory.
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			// 3. 配置基础工具：给大管家配上类加载器、配置好 SpEL 表达式解析器，并悄悄塞进几个内部专用的零件（比如处理 ApplicationContextAware 的组件）。
			// Prepare the bean factory for use in this context.
			prepareBeanFactory(beanFactory);

			try {
				// 4. 子类扩展预留（钩子）：留给 Spring 子类实现的。例如 Web 容器可在此注册 Web 专属作用域。
				// Allows post-processing of the bean factory in context subclasses.
				postProcessBeanFactory(beanFactory);

				StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");

/* ======================================= 👑 阶段二：图纸大爆发！(第 5 步 —— 绝对核心 🔥)=======================================
 * [剧情高潮] 还记得我们最早挂在嘴边的【1号大将】(ConfigurationClassPostProcessor) 吗？ 它一直作为一张图纸躺在仓库里。就是在这第 5 步，它被正式唤醒了！它醒来后的第一件事，就是疯狂扫描你配置的包路径，把所有的 @Component、@Service、@Bean 全部找出来，统统扔进 doRegisterBean 流水线里！
 * [结果输出] 执行完这一步，工厂仓库里彻底堆满了所有业务 Bean 的图纸！*/
				// Invoke factory processors registered as beans in the context.
				invokeBeanFactoryPostProcessors(beanFactory);

/* ======================================= 👷‍♂️ 阶段三：招募流水线质检员 (第 6 步)=======================================
 * [剧情衔接] 图纸有了，马上要开始造对象了。但在造对象前，得先把质检员招募好。还记得【2号大将】(AutowiredAnnotationBeanPostProcessor 处理 @Autowired) 和【3号大将】(CommonAnnotationBeanPostProcessor 处理 @PostConstruct) 吗？它们在这一步被实例化，并且像守卫一样站到了流水线的两旁。
 * [注意] 这里只是“注册（站岗）”，并没有开始干活！它们在等后面真正的 Bean 实例化时扑上去。*/
				// Register bean processors that intercept bean creation.
				registerBeanPostProcessors(beanFactory);
				beanPostProcess.end();

/* ======================================= 📢 阶段四：搭建厂区广播系统 (第 7-10 步)=======================================
 * [核心目标] 完善工厂的配套基础设施。*/
				// 7. 初始化国际化组件（让工厂能听懂多国语言）。
				// Initialize message source for this context.
				initMessageSource();

				// 8. 初始化事件广播器（工厂的“大喇叭”安装完毕）。
				// Initialize event multicaster for this context.
				initApplicationEventMulticaster();

				// 9. 这又是一个神仙钩子方法！在 Spring Boot 中，正是这一步启动了内嵌的 Tomcat / Undertow 服务器！
				// Initialize other special beans in specific context subclasses.
				onRefresh();

				// 10. registerListeners()：把代码里所有实现了 ApplicationListener 的监听器（比如使用了 @EventListener 的大将 4/5 号）注册到广播器上。
				// Check for listener beans and register them.
				registerListeners();

/* ======================================= 🚀 阶段五：大规模量产！(第 11 步 —— 终极核心 🔥🔥)=======================================
 * [厂长按下总开关] 这是 Spring 源码中代码量最大、逻辑最复杂的地方！ 大管家会巡视仓库里所有非懒加载的单例图纸 (non-lazy-init singletons)，逐一投入生产。
 * 在这里，你的 UserService 会被 new 出来；站岗的【2号大将】会扑上去为它注入；UserDao (依赖注入 DI)；如果有事务注解，Spring 会在这里为它生成 CGLIB 代理对象 (AOP 动态代理)。*/
				// Instantiate all remaining (non-lazy-init) singletons.
				finishBeanFactoryInitialization(beanFactory);

/*
 * ======================================= 🎉 尾声：剪彩开业 (第 12 步)=======================================
 * [扫尾工作] 清理无用缓存图纸，并通过大喇叭广播 ContextRefreshedEvent 事件。 告诉全天下：“Spring 容器启动成功，可以开始接收业务请求啦！”
 * 呼~ 看到这里，你是不是有一种“任督二脉被打通”的爽快感？前面我们抠了那么久的底层细节，其实全都是在为这个 refresh() 里的第 5 步和第 11 步做铺垫！*/
				// Last step: publish corresponding event.
				finishRefresh();
			}

			catch (BeansException ex) {
				/* 🚨 突发事故：紧急熔断与销毁。
				 * 如果在上述 12 步中发生任何异常（比如 Bean 循环依赖无法解决、配置报错），立即销毁已经创建出来的残次品 Bean，避免占用内存，并重置启动标识，最后向上层抛出异常。*/
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				// Destroy already created singletons to avoid dangling resources.
				destroyBeans();

				// Reset 'active' flag.
				cancelRefresh(ex);

				// Propagate exception to caller.
				throw ex;
			}

			finally {
				/* 🧹 清扫战场：重置缓存。
				 * 既然单例对象都已经造完了，图纸的反射缓存信息基本就用不上了。清空它们，释放宝贵的内存。*/
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				resetCommonCaches();
				contextRefresh.end();
			}
		}
	}

	/**
	 * Prepare this context for refreshing, setting its startup date and
	 * active flag as well as performing any initialization of property sources.
	 */
	protected void prepareRefresh() {
		// Switch to active.
		this.startupDate = System.currentTimeMillis();
		this.closed.set(false);
		this.active.set(true);

		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Refreshing " + this);
			}
			else {
				logger.debug("Refreshing " + getDisplayName());
			}
		}

		// Initialize any placeholder property sources in the context environment.
		initPropertySources();

		// Validate that all properties marked as required are resolvable:
		// see ConfigurablePropertyResolver#setRequiredProperties
		getEnvironment().validateRequiredProperties();

		// Store pre-refresh ApplicationListeners...
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		}
		else {
			// Reset local application listeners to pre-refresh state.
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}

	/**
	 * <p>Replace any stub property sources with actual instances.
	 * @see org.springframework.core.env.PropertySource.StubPropertySource
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources
	 */
	protected void initPropertySources() {
		// For subclasses: do nothing by default.
	}

	/**
	 * <h3>架构陷阱与源码避坑：工厂的“双重人格” 🎭</h3>
	 * <p>
	 * 在深入 {@code refreshBeanFactory()} 之前，必须警惕 Spring 源码中最容易让人困惑的陷阱。
	 * 获取这个“Fresh”（新鲜）的 Bean 工厂时，Spring 底层有两条截然不同的流派，完全取决于你使用的上下文继承结构：
	 * </p>
	 *
	 * <h4>⚔️ 流派一：传统 XML 派 (AbstractRefreshableApplicationContext)</h4>
	 * <ul>
	 * <li><b>破釜沉舟：</b> 如果使用老式 XML 配置（如 {@code ClassPathXmlApplicationContext}），
	 * 调用此方法时，Spring 会销毁旧工厂里的所有 Bean，直接把旧工厂“砸了”。</li>
	 * <li><b>浴火重生：</b> 重新创建一个全新的 {@code DefaultListableBeanFactory}。</li>
	 * <li><b>重新装载：</b> 重新读取 XML 文件并将图纸加载进新工厂。这就是它叫 Refreshable (可重复刷新) 的原因。</li>
	 * </ul>
	 *
	 * <h4>🕰️ 架构演进溯源：Spring 设计的“时代温差”</h4>
	 * <p>
	 * <b>[Java EE 时代与热加载刚需]</b><br>
	 * 早期应用部署在重量级服务器（如 WebLogic、WebSphere）上，重启动辄数分钟。为了修改一个数据库 IP 而重启整个 JVM 是不可接受的。
	 * 因此，Spring 设计了“可重复刷新”的上下文。通过调用 {@code context.refresh()} 重新读取 XML，实现类似热部署的效果。
	 * 为什么非要砸了旧工厂？如果不这么做（底层调用 {@code destroyBeans()}），旧的单例 Bean 持有的数据库连接、网络套接字等资源就不会释放。
	 * 彻底清空内存，拿着全新的 XML 重新开始，是防止状态混淆引发玄学 Bug 最安全、最没有历史包袱的做法。
	 * </p>
	 * <p>
	 * <b>[云原生时代与不可变基础设施]</b><br>
	 * 了解过去，方懂现在。你目前正在看的 {@code AnnotationConfigApplicationContext} 继承自现代派，被设计为<b>“一次性消耗品”</b>。
	 * 在微服务与 Kubernetes 时代，应用启动极快，且提倡<b>“不可变基础设施”</b>。如果配置变了，现代做法是直接杀掉 Pod 重新启动新实例。
	 * 在生产环境中调 API 去“热刷新”上下文被认为是极度危险的反模式（Anti-pattern）。既然不需要重复刷新，工厂在 new 的那一刻直接建好即可，逻辑更精简、安全、高效。
	 * </p>
	 *
	 * <h4>🛡️ 流派二：现代注解派 (GenericApplicationContext) 👉 当前路线！</h4>
	 * <ul>
	 * <li><b>一次性工厂：</b> 现代派认为“好马不吃回头草”。在上下文被实例化的那一瞬间，底层的
	 * {@code DefaultListableBeanFactory} 早就被创建好了。</li>
	 * <li><b>防重锁校验：</b> 在这个流派里，绝不会去创建新工厂。它只检查 {@code refreshed} 标志位。
	 * 如果你敢调两次 {@code refresh()}，直接抛出 {@code IllegalStateException} (“工厂已刷新，不准重复！”)。</li>
	 * <li><b>打下烙印：</b> 仅仅给这个已经存在的底层工厂设置一个序列化 ID。</li>
	 * </ul>
	 *
	 * <blockquote>
	 * <b>💡 [{@link org.springframework.context.support.AbstractApplicationContext#obtainFreshBeanFactory()} 第 2 步核心总结]：移交仓库钥匙</b><br>
	 * XML 派的“砸工厂重建”是为了在不重启 JVM 的前提下安全重载配置；而当前的注解派则是云原生时代的轻量级利刃，讲究“一次构建，不可篡改”。<br>
	 * {@code obtainFreshBeanFactory()} 的核心意义就是：“检查大管家的仓库是否安全无误，然后把仓库钥匙正式交给主流程！”
	 * 拿到钥匙后，主流程就要开始对仓库进行疯狂的量产操作了。
	 * </blockquote>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Tell the subclass to refresh the internal bean factory.
	 * @return the fresh BeanFactory instance
	 * @see #refreshBeanFactory()
	 * @see #getBeanFactory()
	 */
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		/* 1. 刷新/校验底层工厂 (触发多态逻辑)
		 * [核心动作] 执行真正的“刷新或校验”工厂逻辑。
		 * [原理解析] 这里会触发上述的“双重人格”逻辑。对于当前的现代注解派， 仅仅是执行防重复刷新校验，并为工厂打上序列化 ID 标签。*/
		refreshBeanFactory();

		/* 2. 移交大管家钥匙
		 * [核心动作] 把准备好的大管家 (DefaultListableBeanFactory) 暴露返回。
		 * [原理解析] 返回给外层 refresh() 方法，让后面的 10 个核心步骤都能拿着这把钥匙（工厂实例引用）去干活。*/
		return getBeanFactory();
	}

	/**
	 * <h3>🚀 refresh 第 3 步：prepareBeanFactory —— 给大管家配上"标配装备"</h3>
	 * <p>在这一步中，容器给内部 BeanFactory 配上了一套"标准装备"，分为 5 大块：</p>
	 * <ol>
	 * <li><b>基础设施配置</b>：ClassLoader、SpEL 表达式解析器、PropertyEditor 注册器</li>
	 * <li><b>ApplicationContextAwareProcessor（关键！）</b>：注册一个 BPP，专门处理 6 种 Aware 接口回调
	 * （EnvironmentAware/EmbeddedValueResolverAware/ResourceLoaderAware/ApplicationEventPublisherAware/
	 * MessageSourceAware/ApplicationContextAware），同时 ignore 这 6 种接口的自动装配——
	 * 因为它们由 BPP 回调注入，不走普通的 DI 流程</li>
	 * <li><b>特殊类型的解析注册</b>：把 BeanFactory/ResourceLoader/ApplicationEventPublisher/ApplicationContext
	 * 注册为可解析的依赖——这就是为什么你能 @Autowired ApplicationContext 的原因！</li>
	 * <li><b>ApplicationListenerDetector</b>：注册 BPP 来自动检测和注册 ApplicationListener Bean</li>
	 * <li><b>环境 Bean 注册</b>：把 Environment、SystemProperties、SystemEnvironment 注册为单例 Bean</li>
	 * </ol>
	 * <hr/>
	 * Configure the factory's standard context characteristics,
	 * such as the context's ClassLoader and post-processors.
	 * @param beanFactory the BeanFactory to configure
	 */
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// Tell the internal bean factory to use the context's class loader etc.
		beanFactory.setBeanClassLoader(getClassLoader());
		if (!shouldIgnoreSpel) {
			beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		}
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		// Configure the bean factory with context callbacks.
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);

		// BeanFactory interface not registered as resolvable type in a plain factory.
		// MessageSource registered (and found for autowiring) as a bean.
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// Register early post-processor for detecting inner beans as ApplicationListeners.
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		// Detect a LoadTimeWeaver and prepare for weaving, if found.
		if (!NativeDetector.inNativeImage() && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching.
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// Register default environment beans.
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
		if (!beanFactory.containsLocalBean(APPLICATION_STARTUP_BEAN_NAME)) {
			beanFactory.registerSingleton(APPLICATION_STARTUP_BEAN_NAME, getApplicationStartup());
		}
	}

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. The initial definition resources will have been loaded but no
	 * post-processors will have run and no derived bean definitions will have been
	 * registered, and most importantly, no beans will have been instantiated yet.
	 * <p>This template method allows for registering special BeanPostProcessors
	 * etc in certain AbstractApplicationContext subclasses.
	 * @param beanFactory the bean factory used by the application context
	 */
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	}

	/**
	 * <h3>架构巅峰：图纸大爆发！(容器刷新第 5 步) 🔥</h3>
	 * <p>
	 * 来到了这场大戏的第一个真正的高潮！如果说前 4 步我们都在建厂房、拿执照、搬仓库，
	 * 那么这第 5 步 {@code invokeBeanFactoryPostProcessors} 就是向全厂下达的第一道核心生产指令！
	 * </p>
	 * <p>
	 * 这段代码虽然看起来不长，但它里面蕴含了极其庞大的工作量。还记得我们在最开始千辛万苦注册的
	 * <b>【元老 1】(ConfigurationClassPostProcessor)</b> 吗？
	 * 它在这里沉睡了那么久，终于要在这一步被正式唤醒并发威了！
	 * 咱们分两部分来拆解这段硬核代码：
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Instantiate and invoke all registered BeanFactoryPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before singleton instantiation.
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		/* ⚔️ 第一部分：委托执行，大将出征！(绝对核心)
		 * [架构设计] 委托模式 (Delegate Pattern)。 AbstractApplicationContext 作为厂长，他自己是不干脏活累活的。遇到这种需要把全厂所有 BeanFactoryPostProcessor（图纸修改派）找出来、排序、再依次执行的复杂逻辑，他直接委托给了一个专门的“执行官” —— PostProcessorRegistrationDelegate （后置处理器注册委托类）。
		 * [车间动作：唤醒元老 1] 点进这个委托方法，你会发现里面极其壮观。它会去仓库里把咱们之前注册的【元老 1】找出来并调用它。【元老 1】醒来后干了什么？
		 * ① 它拿起你写的 AppConfig.class 图纸。
		 * ② 看到上面写着 @ComponentScan("com.xxx")，它立刻化身超级吸尘器，冲进你的包路径，把所有带 @Component、@Service、@Controller 的 .class 文件全扫出来！
		 * ③ 扫出来后，它会调用咱们上一轮学的 doRegisterBean 流水线，把它们全部变成一张张新鲜的 BeanDefinition 图纸，疯狂地塞进大管家底层的 ConcurrentHashMap 仓库里！
		 * [阶段结论] 执行完这一行代码，咱们的仓库就不再只有几个基础设施图纸了，而是堆满了你写的成百上千个业务 Bean 的图纸！*/
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		/* 🕸️ 第二部分：黑科技埋伏 —— AOP 编织准备 (LoadTimeWeaver)
		 * [原理解析] 这段代码处理的是 Spring 中相对高级且底层的特性：LTW (类加载期织入)。 平时用 AOP 多半是运行期生成代理（CGLIB/JDK 动态代理）。而 LTW 是一种更狠的技术，它是在 JVM 刚刚把 .class 文件加载进内存的那一瞬间，直接修改字节码，把切面逻辑“塞”进去（通常配合 AspectJ 使用）.
		 * [条件一：原生镜像兼容] !NativeDetector.inNativeImage()  这是 Spring 5.3 专门为 GraalVM Native Image （原生镜像）做的兼容。原生镜像是提前编译好的二进制文件，不支持在运行时动态加载和修改字节码。所以如果是原生镜像环境，直接跳过。
		 * [条件二：LTW 激活检测]  beanFactory.containsBean(...) 如果【元老 1】刚才扫包时发现你用了 @EnableLoadTimeWeaving 注解，就会进入这个 if 分支。
		 * [车间大白话：临时装扮]  如果满足条件，工厂会给你配一个临时类加载器 (TempClassLoader)。为什么要临时的？因为你想修改类的字节码，总得先把它加载进来看看吧？如果用正式的类加载器看一眼，这个类就被永久定型了，没法改了。所以得用一个临时的去“偷瞄”一眼，改好字节码后，再交给正式的去加载。*/
		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
		if (!NativeDetector.inNativeImage() && beanFactory.getTempClassLoader() == null &&
				beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		/* 🎉 [容器刷新第 5 步总结]
		 * 现在你可以长舒一口气了！经过这第 5 步的洗礼，咱们工厂的图纸总算全部画齐了！ 无论是你手写的 @Bean，还是包扫描进来的 @Service，它们现在都已经作为完整的BeanDefinition 在大管家的仓库里列队完毕，只等被实例化！
		 * (注：这段代码看似简单，但它调用的 PostProcessorRegistrationDelegate 内部其实隐藏着几百行极其精妙的“接口排序与分组执行”逻辑，完美处理了 PriorityOrdered、 Ordered 接口的优先级。)*/
	}

	/**
	 * Instantiate and register all BeanPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before any instantiation of application beans.
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	/**
	 * Initialize the {@link MessageSource}.
	 * <p>Uses parent's {@code MessageSource} if none defined in this context.
	 * @see #MESSAGE_SOURCE_BEAN_NAME
	 */
	protected void initMessageSource() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// Make MessageSource aware of parent MessageSource.
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				if (hms.getParentMessageSource() == null) {
					// Only set parent context as parent MessageSource if no parent MessageSource
					// registered already.
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using MessageSource [" + this.messageSource + "]");
			}
		}
		else {
			// Use empty MessageSource to be able to accept getMessage calls.
			DelegatingMessageSource dms = new DelegatingMessageSource();
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
			}
		}
	}

	/**
	 * Initialize the {@link ApplicationEventMulticaster}.
	 * <p>Uses {@link SimpleApplicationEventMulticaster} if none defined in the context.
	 * @see #APPLICATION_EVENT_MULTICASTER_BEAN_NAME
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		else {
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " +
						"[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the {@link LifecycleProcessor}.
	 * <p>Uses {@link DefaultLifecycleProcessor} if none defined in the context.
	 * @since 3.0
	 * @see #LIFECYCLE_PROCESSOR_BEAN_NAME
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	protected void initLifecycleProcessor() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			this.lifecycleProcessor = beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
			}
		}
		else {
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			defaultProcessor.setBeanFactory(beanFactory);
			this.lifecycleProcessor = defaultProcessor;
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + LIFECYCLE_PROCESSOR_BEAN_NAME + "' bean, using " +
						"[" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Template method which can be overridden to add context-specific refresh work.
	 * Called on initialization of special beans, before instantiation of singletons.
	 * <p>This implementation is empty.
	 * @throws BeansException in case of errors
	 * @see #refresh()
	 */
	protected void onRefresh() throws BeansException {
		// For subclasses: do nothing by default.
	}

	/**
	 * Add beans that implement ApplicationListener as listeners.
	 * Doesn't affect other listeners, which can be added without being beans.
	 */
	protected void registerListeners() {
		// Register statically specified listeners first.
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let post-processors apply to them!
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		// Publish early application events now that we finally have a multicaster...
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	/**
	 * <h3>架构巅峰：决战前的最后 6 秒倒数 (容器刷新第 11 步收尾) 🚀</h3>
	 * <p>
	 * 经过前面漫长、严谨、甚至有些枯燥的“画图纸”（第 5 步）和“招募质检员”（第 6 步）的筹备工作，
	 * 现在，我们正式踏入了 Spring 启动流程的绝对高潮——<b>第 11 步：finishBeanFactoryInitialization！</b>
	 * 翻译过来就是：完成工厂初始化的最后收尾，并拉下全自动生产线的总电闸！
	 * </p>
	 * <p>
	 * 这段代码看似平平无奇，但它就像火箭发射前的最后 6 秒倒数。
	 * 每一行都在为最后那一脚“点火”做着极其关键的清场和确认工作。让我们戴上安全帽，
	 * 最后一次检查这 6 道点火工序：
	 * </p>
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Finish the initialization of this context's bean factory,
	 * initializing all remaining singleton beans.
	 */
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		/* 🗣️ 倒数第 6 秒：成立“通用翻译部” (ConversionService)
		 * Spring 的类型转换服务（ConversionService）
		 * [车间大白话] 厂长说：“等会儿造机器注入属性时，XML 或注解里写的可全是字符串(String)啊！ 比如 <property name="age" value="18"/>，但我对象里要的是个 Integer！”
		 * [原理解析] 所以，这里先去仓库里看看有没有配好的“通用翻译部”(ConversionService 转换器)。如果有，赶紧装配到大管家身上，等会儿造机器时随时调用它来做类型转换。*/
		// Initialize conversion service for this context.
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		/* 🕵️‍♂️ 倒数第 5 秒：设立“密码破译局” (EmbeddedValueResolver)
		 * [车间大白话] 代码里写了 @Value("${mysql.url}")，Spring 得知道去哪查这个值啊！ 如果之前没人注册过这种“破译员”，工厂就在这里紧急设立一个默认的“密码破译局”。
		 * [原理解析] 它的任务就是拿着 ${xxx} 占位符，去 Environment (环境大管家，我们在第 5 步看它加载了 properties) 里把真实的值翻译出来。这主要用于后续注解属性值的极速解析。*/
		// Register a default embedded value resolver if no BeanFactoryPostProcessor
		// (such as a PropertySourcesPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		/* 🧙‍♂️ 倒数第 4 秒：提前唤醒“底层魔法师” (LoadTimeWeaverAware 类加载期织入的 AOP)
		 * [车间大白话] 这类魔法师（AspectJ 相关的字节码修改器）极其特殊，必须在绝大多数普通机器(Bean)被加载进 JVM 之前就醒过来，否则他们就没法修改别人的字节码了！
		 * [原理解析] 处理 LTW (类加载期织入) 的 AOP。厂长特批，用 getBean() 强行把他们提前实例化，以便尽早注册他们的 ClassFileTransformer 进行字节码修改。 */
		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			// 提前实例化！
			getBean(weaverAwareName);
		}

		/* 🗑️ 倒数第 3 秒：砸毁“临时透视仪” (TempClassLoader)
		 * [车间大白话] 第 5 步开头为了不提前把类定型，工厂装了个“临时类加载器”去偷瞄字节码。 马上要动真格造真实的 Java 对象了。厂长一声令下：把临时透视仪砸了！全厂统一使用标准的正式类加载器！
		 * [原理解析] 释放临时类加载器，停止基于临时加载器的类型匹配，节约内存并彻底锁定类加载环境。*/
		// Stop using the temporary ClassLoader for type matching.
		beanFactory.setTempClassLoader(null);

		/* 🔒 倒数第 2 秒：彻底封锁“图纸档案馆” (freezeConfiguration 极其重要)
		 * [车间大白话] 这也是极其关键的性能优化！厂长给档案馆上了一把大锁，并对着全厂广播：“从这一秒开始，任何人都绝对不允许再去修改图纸(BeanDefinition)的任何一个标点符号！” 因为马上要批量造机器了，如果有人一边造机器一边改图纸，那就全乱套了。
		 * [原理解析] 冻结所有的 BeanDefinition 配置。Spring 内部会把这些图纸的关键信息缓存到一个极速的数组/集合里，不再预期有修改，等会儿 new 对象时读取图纸元数据的速度将飙升到极致！*/
		// Allow for caching all bean definition metadata, not expecting further changes.
		beanFactory.freezeConfiguration();

		/* 🚀 倒数第 1 秒：拉下总电闸！全面开工！ (终极大招 🔥)
		 * [车间大白话] 这是整个 Spring 框架中最震耳欲聋的一声巨响！ 所有的前置条件完美具备，厂长拉下了这根名为 preInstantiateSingletons 的总电闸。 整个工厂的流水线瞬间爆发出惊人的轰鸣声！大管家将拿着那几百张冻结的图纸，冲向单例池，一个接一个地把你的 @Service、@Controller、@Component 全部 new 出来，并打上 @Autowired 的补丁！
		 * [原理解析] 预实例化所有剩余的、非懒加载 (non-lazy-init) 的单例 Bean！ */
		// Instantiate all remaining (non-lazy-init) singletons.
		beanFactory.preInstantiateSingletons();

/* ===================================💥 [大决战预告：深渊的凝视]==============================================
 * 厂长，手握核弹起爆器，准备好了吗？ 这段 finishBeanFactoryInitialization 方法，其实就是大决战前的最后一次深呼吸。
 * 它本身并没有写一行具体怎么 new 对象的代码，它把所有的悬念和极其恐怖的工作量，全部扔进了最后那一行：preInstantiateSingletons()。
 * 接下来，没有任何退路！那是全网所有 Spring 开发者梦寐以求、也是最容易迷失的终极迷宫。 里面藏着大名鼎鼎的 getBean()、doGetBean()、createBean()、doCreateBean()，以及解决循环依赖的“三级缓存”！
 */
	}

	/**
	 * Finish the refresh of this context, invoking the LifecycleProcessor's
	 * onRefresh() method and publishing the
	 * {@link org.springframework.context.event.ContextRefreshedEvent}.
	 */
	@SuppressWarnings("deprecation")
	protected void finishRefresh() {
		// Clear context-level resource caches (such as ASM metadata from scanning).
		clearResourceCaches();

		// Initialize lifecycle processor for this context.
		initLifecycleProcessor();

		// Propagate refresh to lifecycle processor first.
		getLifecycleProcessor().onRefresh();

		// Publish the final event.
		publishEvent(new ContextRefreshedEvent(this));

		// Participate in LiveBeansView MBean, if active.
		if (!NativeDetector.inNativeImage()) {
			LiveBeansView.registerApplicationContext(this);
		}
	}

	/**
	 * Cancel this context's refresh attempt, resetting the {@code active} flag
	 * after an exception got thrown.
	 * @param ex the exception that led to the cancellation
	 */
	protected void cancelRefresh(BeansException ex) {
		this.active.set(false);
	}

	/**
	 * Reset Spring's common reflection metadata caches, in particular the
	 * {@link ReflectionUtils}, {@link AnnotationUtils}, {@link ResolvableType}
	 * and {@link CachedIntrospectionResults} caches.
	 * @since 4.2
	 * @see ReflectionUtils#clearCache()
	 * @see AnnotationUtils#clearCache()
	 * @see ResolvableType#clearCache()
	 * @see CachedIntrospectionResults#clearClassLoader(ClassLoader)
	 */
	protected void resetCommonCaches() {
		ReflectionUtils.clearCache();
		AnnotationUtils.clearCache();
		ResolvableType.clearCache();
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}


	/* =======================================================================================================
	          🛑 第三战区：关闭与销毁 —— 优雅停机的完整编排
	          close() → doClose() 是关闭的核心链路，内部按序执行：
	          ① 发布 ContextClosedEvent → ② 停止 Lifecycle Bean → ③ 销毁所有单例 →
	          ④ 关闭 BeanFactory → ⑤ onClose 子类钩子 → ⑥ 清缓存 → ⑦ 置为 inactive
	   =======================================================================================================*/

	/**
	 * Register a shutdown hook {@linkplain Thread#getName() named}
	 * {@code SpringContextShutdownHook} with the JVM runtime, closing this
	 * context on JVM shutdown unless it has already been closed at that time.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * @see Runtime#addShutdownHook
	 * @see ConfigurableApplicationContext#SHUTDOWN_HOOK_THREAD_NAME
	 * @see #close()
	 * @see #doClose()
	 */
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}

	/**
	 * Callback for destruction of this instance, originally attached
	 * to a {@code DisposableBean} implementation (not anymore in 5.0).
	 * <p>The {@link #close()} method is the native way to shut down
	 * an ApplicationContext, which this method simply delegates to.
	 * @deprecated as of Spring Framework 5.0, in favor of {@link #close()}
	 */
	@Deprecated
	public void destroy() {
		close();
	}

	/**
	 * Close this application context, destroying all beans in its bean factory.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * Also removes a JVM shutdown hook, if registered, as it's not needed anymore.
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
			doClose();
			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				}
				catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

	/**
	 * <h3>🛑 doClose() —— 容器关闭的"拆弹 7 步"（与 refresh 12 步对称的终止流程）</h3>
	 * <p><b>【硬核释义】</b><br/>
	 * 这是容器关闭的核心执行方法，close() 和 JVM shutdown hook 都会调到这里。<br/>
	 * 用 CAS（closed.compareAndSet）保证只执行一次，按序执行 7 步拆弹：</p>
	 * <ol>
	 * <li><b>发布 ContextClosedEvent</b>——通知所有监听器"容器要关了"（@TransactionalEventListener BEFORE_CLOSE 在这里触发）</li>
	 * <li><b>停止 Lifecycle Bean</b>——LifecycleProcessor.onClose() 按 phase 倒序停止 SmartLifecycle Bean</li>
	 * <li><b>销毁所有单例</b>——destroyBeans() → destroySingletons()，触发 @PreDestroy、DisposableBean.destroy()</li>
	 * <li><b>关闭 BeanFactory</b>——closeBeanFactory()，清除序列化 ID（现代派）或置 null（传统派）</li>
	 * <li><b>子类钩子 onClose()</b>——Spring Boot 在这里关闭内嵌 Tomcat/Undertow</li>
	 * <li><b>清除反射缓存</b>——防止类加载器泄漏</li>
	 * <li><b>置为 inactive</b>——active.set(false)，此后所有 getBean 调用都会报错</li>
	 * </ol>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 工厂要关门了！按顺序执行"拆弹"流程：<br/>
	 * ① 广播通知："全员撤离！"（ContextClosedEvent）<br/>
	 * ② 先关掉所有还在运转的机器（Lifecycle Bean 按 phase 倒序停机）<br/>
	 * ③ 逐台拆解所有单例机器（触发销毁回调，释放连接池/线程池等资源）<br/>
	 * ④ 封存仓库（关闭 BeanFactory）<br/>
	 * ⑤ 子工厂做最后清理（如 Spring Boot 关 Tomcat）<br/>
	 * ⑥ 清扫缓存、防止内存泄漏<br/>
	 * ⑦ 挂上"已歇业"牌子（active=false）<br/>
	 * <b>注意：每一步都有 try-catch 容错！单个步骤失败不会阻止后续步骤执行，确保资源尽量被释放！</b></blockquote>
	 * <hr/>
	 * Actually performs context closing: publishes a ContextClosedEvent and
	 * destroys the singletons in the bean factory of this application context.
	 * <p>Called by both {@code close()} and a JVM shutdown hook, if any.
	 * @see org.springframework.context.event.ContextClosedEvent
	 * @see #destroyBeans()
	 * @see #close()
	 * @see #registerShutdownHook()
	 */
	@SuppressWarnings("deprecation")
	protected void doClose() {
		/*
		 * 🛡️ CAS 幂等保护：active=true 且 closed 从 false→true 才执行，保证只关一次。
		 * 即使 close() 和 shutdownHook 并发触发，也只有一个线程能进入。
		 */
		// Check whether an actual close attempt is necessary...
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}

			// 🧹 从 LiveBeansView MBean 中注销（JMX 管理）
			if (!NativeDetector.inNativeImage()) {
				LiveBeansView.unregisterApplicationContext(this);
			}

			try {
				// ① 发布 ContextClosedEvent——最后的广播，让监听器做清理工作
				// Publish shutdown event.
				publishEvent(new ContextClosedEvent(this));
			}
			catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			// ② 停止所有 Lifecycle Bean（按 phase 倒序排空）
			// Stop all Lifecycle beans, to avoid delays during individual destruction.
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				}
				catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			// ③ 销毁所有缓存的单例 Bean（触发 @PreDestroy / DisposableBean.destroy / destroy-method）
			// Destroy all cached singletons in the context's BeanFactory.
			destroyBeans();

			// ④ 关闭 BeanFactory 本身（现代派清序列化 ID，传统派置 null）
			// Close the state of this context itself.
			closeBeanFactory();

			// ⑤ 子类钩子——Spring Boot 在这里关闭内嵌 Web 服务器
			// Let subclasses do some final clean-up if they wish...
			onClose();

			// ⑥ 清除反射/注解/ResolvableType 等缓存，防止类加载器泄漏
			// Reset common introspection caches to avoid class reference leaks.
			resetCommonCaches();

			// 恢复监听器到 refresh 之前的状态（传统 XML 派支持重复 refresh 时需要）
			// Reset local application listeners to pre-refresh state.
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			// ⑦ 挂上"已歇业"牌子——此后所有 getBean 调用都会触发 assertBeanFactoryActive() 报错
			// Switch to inactive.
			this.active.set(false);
		}
	}

	/**
	 * Template method for destroying all beans that this context manages.
	 * The default implementation destroy all cached singletons in this context,
	 * invoking {@code DisposableBean.destroy()} and/or the specified
	 * "destroy-method".
	 * <p>Can be overridden to add context-specific bean destruction steps
	 * right before or right after standard singleton destruction,
	 * while the context's BeanFactory is still active.
	 * @see #getBeanFactory()
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#destroySingletons()
	 */
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	/**
	 * Template method which can be overridden to add context-specific shutdown work.
	 * The default implementation is empty.
	 * <p>Called at the end of {@link #doClose}'s shutdown procedure, after
	 * this context's BeanFactory has been closed. If custom shutdown logic
	 * needs to execute while the BeanFactory is still active, override
	 * the {@link #destroyBeans()} method instead.
	 */
	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	/**
	 * Assert that this context's BeanFactory is currently active,
	 * throwing an {@link IllegalStateException} if it isn't.
	 * <p>Invoked by all {@link BeanFactory} delegation methods that depend
	 * on an active context, i.e. in particular all bean accessor methods.
	 * <p>The default implementation checks the {@link #isActive() 'active'} status
	 * of this context overall. May be overridden for more specific checks, or for a
	 * no-op if {@link #getBeanFactory()} itself throws an exception in such a case.
	 */
	protected void assertBeanFactoryActive() {
		if (!this.active.get()) {
			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already");
			}
			else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
			}
		}
	}


	/* =======================================================================================================
	          🔀 第五战区：BeanFactory 方法代理 —— 所有 getBean/containsBean 等操作都委托给内部 BeanFactory
	          注意：每个方法都先调 assertBeanFactoryActive() 做活性检查！
	          容器未 refresh 或已 close 时调用任何 getBean 都会直接报 IllegalStateException
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name, allowFactoryBeanInit);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType, allowFactoryBeanInit);
	}


	/* =======================================================================================================
	          🔀 第五战区（续）：HierarchicalBeanFactory 代理 —— 父子容器层级查找
	          getParentBeanFactory() 返回父容器（可能是另一个 ApplicationContext）
	          containsLocalBean() 只在当前容器内查找，不向父容器回溯
	          getInternalParentBeanFactory() 内部方法，尝试获取父容器的"真正 BeanFactory"
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	/**
	 * <h3>内部方法：获取父容器的"真正 BeanFactory"</h3>
	 * <p>如果父容器也是 ConfigurableApplicationContext，返回它内部的 BeanFactory（更底层、更高效）；<br/>
	 * 否则返回父容器本身（它自己就是 BeanFactory）。<br/>
	 * 这个方法用于设置内部 BeanFactory 的 parentBeanFactory——让 getBean 的父子查找链路走最短路径。</p>
	 * <hr/>
	 * Return the internal bean factory of the parent context if it implements
	 * ConfigurableApplicationContext; else, return the parent context itself.
	 * @see org.springframework.context.ConfigurableApplicationContext#getBeanFactory
	 */
	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		return (getParent() instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) getParent()).getBeanFactory() : getParent());
	}


	/* =======================================================================================================
	          🌍 第六战区：MessageSource 方法代理 —— 国际化消息解析全部委托给内部 messageSource
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of MessageSource interface
	//---------------------------------------------------------------------

	@Override
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	/**
	 * Return the internal MessageSource used by the context.
	 * @return the internal MessageSource (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}

	/**
	 * Return the internal message source of the parent context if it is an
	 * AbstractApplicationContext too; else, return the parent context itself.
	 */
	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext ?
				((AbstractApplicationContext) getParent()).messageSource : getParent());
	}


	/* =======================================================================================================
	          🔍 ResourcePatternResolver 代理 —— classpath*: 通配符资源加载
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of ResourcePatternResolver interface
	//---------------------------------------------------------------------

	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	/* =======================================================================================================
	          🔄 Lifecycle 代理 —— 容器的启动/停止生命周期
	          start() 和 stop() 委托给 LifecycleProcessor，并分别发布 ContextStartedEvent/ContextStoppedEvent
	          注意区分：start/stop 是"运行态"切换（可反复调用），close 是"终止"（不可逆）
	   =======================================================================================================*/

	//---------------------------------------------------------------------
	// Implementation of Lifecycle interface
	//---------------------------------------------------------------------

	@Override
	public void start() {
		getLifecycleProcessor().start();
		publishEvent(new ContextStartedEvent(this));
	}

	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		publishEvent(new ContextStoppedEvent(this));
	}

	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	/* =======================================================================================================
	          🔧 第七战区：抽象方法 —— 两大流派的分水岭！
	          这三个抽象方法决定了"BeanFactory 从哪来、怎么刷新、怎么关闭"——
	          传统 XML 派和现代注解派在这里走向完全不同的道路！
	   =======================================================================================================*/

	/**
	 * <h3>🔧 抽象方法 1：refreshBeanFactory() —— 两大流派的核心分歧点！</h3>
	 * <p><b>传统 XML 派（AbstractRefreshableApplicationContext）</b>：销毁旧工厂 → 创建新 DefaultListableBeanFactory → 重新加载 XML 配置。<br/>
	 * <b>现代注解派（GenericApplicationContext）</b>：CAS 防重刷（只允许调一次）→ 设置序列化 ID，完毕。<br/>
	 * 同一个方法签名，两种截然不同的行为——这就是模板方法 + 多态的威力！</p>
	 * <hr/>
	 * Subclasses must implement this method to perform the actual configuration load.
	 * The method is invoked by {@link #refresh()} before any other initialization work.
	 * <p>A subclass will either create a new bean factory and hold a reference to it,
	 * or return a single BeanFactory instance that it holds. In the latter case, it will
	 * usually throw an IllegalStateException if refreshing the context more than once.
	 * @throws BeansException if initialization of the bean factory failed
	 * @throws IllegalStateException if already initialized and multiple refresh
	 * attempts are not supported
	 */
	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	/**
	 * <h3>🔧 抽象方法 2：closeBeanFactory() —— 释放内部 BeanFactory</h3>
	 * <p><b>传统 XML 派</b>：把内部 BeanFactory 引用置 null，彻底释放。<br/>
	 * <b>现代注解派（GenericApplicationContext）</b>：只清除序列化 ID，不释放 BeanFactory（因为它是 final 字段）。</p>
	 * <hr/>
	 * Subclasses must implement this method to release their internal bean factory.
	 * This method gets invoked by {@link #close()} after all other shutdown work.
	 * <p>Should never throw an exception but rather log shutdown failures.
	 */
	protected abstract void closeBeanFactory();

	/**
	 * <h3>🔧 抽象方法 3：getBeanFactory() —— 获取内部 BeanFactory 实例</h3>
	 * <p>所有 getBean/getBeanNamesForType 等代理方法最终都调到这里获取内部 BeanFactory。<br/>
	 * <b>传统 XML 派</b>：从 volatile 字段中读取（可能为 null，需要检查）。<br/>
	 * <b>现代注解派</b>：直接返回 final 字段（永不为 null，性能极高）。<br/>
	 * 这个方法被频繁调用，子类实现必须高效——不能有锁、不能有重计算！</p>
	 * <hr/>
	 * Subclasses must return their internal bean factory here. They should implement the
	 * lookup efficiently, so that it can be called repeatedly without a performance penalty.
	 * <p>Note: Subclasses should check whether the context is still active before
	 * returning the internal bean factory. The internal factory should generally be
	 * considered unavailable once the context has been closed.
	 * @return this application context's internal bean factory (never {@code null})
	 * @throws IllegalStateException if the context does not hold an internal bean factory yet
	 * (usually if {@link #refresh()} has never been called) or if the context has been
	 * closed already
	 * @see #refreshBeanFactory()
	 * @see #closeBeanFactory()
	 */
	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;


	/**
	 * Return information about this context.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(", started on ").append(new Date(getStartupDate()));
		ApplicationContext parent = getParent();
		if (parent != null) {
			sb.append(", parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
