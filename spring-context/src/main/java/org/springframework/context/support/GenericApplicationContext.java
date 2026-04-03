/*
 * Copyright 2002-2022 the original author or authors.
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
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>AbstractApplicationContext 的"现代派实装"——一次性工厂，内置大管家，不走回头路！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.support.GenericApplicationContext}</li>
 * <li><b>中文名</b>：通用应用上下文 —— 现代 Spring 的"标准工厂车间"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 support 包（注意！support 包 = 抽象骨架的具体实现！
 * context 包定义接口契约，support 包提供真正可用的实现类。就像 context 包是"建筑图纸"，
 * support 包是"施工队"——AbstractApplicationContext、GenericApplicationContext、
 * ClassPathXmlApplicationContext 全在这里）</li>
 * <li><b>类层级</b>：{@code AbstractApplicationContext} 的直系子类 + 实现 {@code BeanDefinitionRegistry}，
 * 增加了<b>内置 BeanFactory + BD 注册能力 + 一次性 refresh 保护</b></li>
 * </ul>
 *
 * <h3>💡 为什么需要 GenericApplicationContext？——"现代派"和"传统派"的分水岭！</h3>
 * <p>Spring 的容器实现有两大流派，它们在"BeanFactory 的创建时机"上存在本质分歧：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>维度</th><th>传统 XML 派 (AbstractRefreshableApplicationContext)</th><th>现代派 (GenericApplicationContext) ← 你在这里！</th></tr>
 * <tr><td>BeanFactory 创建时机</td><td>每次 refresh() 时销毁旧的、创建新的</td><td><b>构造方法中一次性创建，refresh() 只允许调一次</b></td></tr>
 * <tr><td>BD 注册方式</td><td>refresh() 过程中由 Reader 加载</td><td><b>refresh() 之前就可以注册 BD（实现了 BeanDefinitionRegistry）</b></td></tr>
 * <tr><td>是否可重复 refresh</td><td>✅ 可以（砸旧建新）</td><td><b>❌ 不行（CAS 保护，调两次直接报错）</b></td></tr>
 * <tr><td>典型子类</td><td>ClassPathXmlApplicationContext</td><td><b>AnnotationConfigApplicationContext（最常用！）</b></td></tr>
 * <tr><td>设计哲学</td><td>热部署/热加载（Java EE 时代）</td><td><b>不可变基础设施（云原生时代）</b></td></tr>
 * </table>
 * <p>GenericApplicationContext 的设计哲学：<b>"工厂从出生（new）那一刻起就自带大管家（DefaultListableBeanFactory），
 * 你可以在 refresh() 之前随便往里面塞图纸（BD），但 refresh() 只能开一次机，绝不回头！"</b></p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"组合持有"而非"按需创建"——工厂一出生就自带引擎</b><br/>
 * GenericApplicationContext 在构造方法中直接 {@code new DefaultListableBeanFactory()}，
 * 而不是等 refresh() 时才创建。这意味着你在 refresh() 之前就能往工厂里注册 BD——<br/>
 * 这对于编程式注册（{@code registerBeanDefinition()}、{@code registerBean()}）极其友好。<br/>
 * <b>业务借鉴</b>：如果你的对象在创建后需要"配置 → 启动"两阶段，
 * 让核心组件在构造阶段就绑定好（而不是启动阶段才创建），能大幅简化配置流程。
 * 比如你的消息队列消费者，在构造时就绑定 channel，启动前可以灵活配置消费策略。</li>
 *
 * <li><b>CAS 防重复刷新——"不可变基础设施"在代码层面的体现</b><br/>
 * {@code refreshed} 是一个 {@code AtomicBoolean}，在 {@code refreshBeanFactory()} 中用
 * CAS（compareAndSet）保证 refresh() 只能成功调用一次。<br/>
 * 这不是技术限制，而是<b>架构宣言</b>：现代应用不需要热刷新，配置变了就重建新实例！<br/>
 * <b>业务借鉴</b>：当你的系统中某个操作语义上就是"一次性"的（如订单支付、合同签署），
 * 用 CAS 或状态机在代码层面强制保证幂等，比文档约束可靠一万倍。</li>
 *
 * <li><b>实现 BeanDefinitionRegistry——让上下文本身成为"图纸柜台"</b><br/>
 * GenericApplicationContext 同时实现了 {@code BeanDefinitionRegistry}，
 * 所有注册方法都委托给内部的 DefaultListableBeanFactory。<br/>
 * 这样外部代码（如 {@code BeanDefinitionReader}、{@code ClassPathBeanDefinitionScanner}）
 * 可以直接把 GenericApplicationContext 当作注册中心使用，不需要先获取内部 BeanFactory。<br/>
 * <b>业务借鉴</b>：如果你的"管理器"内部持有一个"注册表"，
 * 可以让管理器直接实现注册表接口并委托给内部实例——调用者不需要知道内部有多少层。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * DefaultResourceLoader
 * └── AbstractApplicationContext                 （模板方法骨架：refresh() 12 步）
 *       ├── AbstractRefreshableApplicationContext （传统 XML 派：每次 refresh 砸旧建新）
 *       │     └── AbstractRefreshableConfigApplicationContext
 *       │           └── AbstractXmlApplicationContext
 *       │                 ├── ClassPathXmlApplicationContext
 *       │                 └── FileSystemXmlApplicationContext
 *       └── GenericApplicationContext             ← 👈 你在这里！（现代派：一次性 refresh）
 *             ├── AnnotationConfigApplicationContext   ← ⭐ 最常用的入口！你的 Spring Boot 最终到这里
 *             ├── GenericXmlApplicationContext
 *             ├── StaticApplicationContext
 *             └── GenericWebApplicationContext
 *
 * 同时实现：BeanDefinitionRegistry（让上下文自身具备 BD 注册能力）
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>4 个字段 + 4 组构造器 + N 个方法，划分为 <b>五大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>成员</th></tr>
 * <tr><td><b>🏗️ 第零战区：内部状态</b></td><td>持有大管家 + 刷新标志</td>
 * <td>beanFactory(DefaultListableBeanFactory) / resourceLoader / customClassLoader / refreshed(AtomicBoolean)</td></tr>
 * <tr><td><b>🔧 第一战区：构造与配置</b></td><td>创建工厂、设置父容器、配置开关</td>
 * <td>4 个构造器 / setParent / setAllowBeanDefinitionOverriding / setAllowCircularReferences / setResourceLoader</td></tr>
 * <tr><td><b>⚙️ 第二战区：模板方法实现</b></td><td>实现 AbstractApplicationContext 的抽象方法</td>
 * <td>refreshBeanFactory(CAS 防重刷) / closeBeanFactory / getBeanFactory / cancelRefresh</td></tr>
 * <tr><td><b>📋 第三战区：BD 注册代理</b></td><td>实现 BeanDefinitionRegistry，全部委托给内部 BeanFactory</td>
 * <td>registerBeanDefinition / removeBeanDefinition / getBeanDefinition / registerAlias / removeAlias / isAlias</td></tr>
 * <tr><td><b>🎯 第四战区：便捷注册</b></td><td>编程式注册 Bean 的快捷方法（5.0+ 新增）</td>
 * <td>registerBean 系列重载（Class/Supplier/BeanDefinitionCustomizer）</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>GenericApplicationContext 的核心价值：<b>用"一次性工厂 + 构造时绑定 BeanFactory + CAS 防重刷"
 * 的设计范式，取代了传统的"每次 refresh 砸旧建新"模式</b>。<br/>
 * 它是现代 Spring（Spring Boot / 注解驱动 / 云原生）的基石——
 * AnnotationConfigApplicationContext 就是它的直系子类。<br/>
 * 同时它通过实现 BeanDefinitionRegistry，让"上下文"和"注册中心"合二为一，
 * 大幅简化了编程式配置的使用姿势。</p>
 *
 * <hr/>
 * Generic ApplicationContext implementation that holds a single internal
 * {@link org.springframework.beans.factory.support.DefaultListableBeanFactory}
 * instance and does not assume a specific bean definition format. Implements
 * the {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * interface in order to allow for applying any bean definition readers to it.
 *
 * <p>Typical usage is to register a variety of bean definitions via the
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * interface and then call {@link #refresh()} to initialize those beans
 * with application context semantics (handling
 * {@link org.springframework.context.ApplicationContextAware}, auto-detecting
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * etc).
 *
 * <p>In contrast to other ApplicationContext implementations that create a new
 * internal BeanFactory instance for each refresh, the internal BeanFactory of
 * this context is available right from the start, to be able to register bean
 * definitions on it. {@link #refresh()} may only be called once.
 *
 * <p>Usage example:
 *
 * <pre class="code">
 * GenericApplicationContext ctx = new GenericApplicationContext();
 * XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(ctx);
 * xmlReader.loadBeanDefinitions(new ClassPathResource("applicationContext.xml"));
 * PropertiesBeanDefinitionReader propReader = new PropertiesBeanDefinitionReader(ctx);
 * propReader.loadBeanDefinitions(new ClassPathResource("otherBeans.properties"));
 * ctx.refresh();
 *
 * MyBean myBean = (MyBean) ctx.getBean("myBean");
 * ...</pre>
 *
 * For the typical case of XML bean definitions, simply use
 * {@link ClassPathXmlApplicationContext} or {@link FileSystemXmlApplicationContext},
 * which are easier to set up - but less flexible, since you can just use standard
 * resource locations for XML bean definitions, rather than mixing arbitrary bean
 * definition formats. The equivalent in a web environment is
 * {@link org.springframework.web.context.support.XmlWebApplicationContext}.
 *
 * <p>For custom application context implementations that are supposed to read
 * special bean definition formats in a refreshable manner, consider deriving
 * from the {@link AbstractRefreshableApplicationContext} base class.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Sam Brannen
 * @since 1.1.2
 * @see #registerBeanDefinition
 * @see #refresh()
 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
 * @see org.springframework.beans.factory.support.PropertiesBeanDefinitionReader
 */
public class GenericApplicationContext extends AbstractApplicationContext implements BeanDefinitionRegistry {

	/* =======================================================================================================
	          🏗️ 第零战区：内部状态 —— 大管家（BeanFactory）+ 资源加载器 + 防重刷标志
	   =======================================================================================================*/

	/** 🏭 大管家本尊！构造方法中直接 new 出来，refresh() 前就能往里面注册 BD。
	 *  这是现代派和传统派的核心区别——传统派在 refresh 时才创建，现代派一出生就有！ */
	private final DefaultListableBeanFactory beanFactory;

	/** 可选的自定义资源加载器，未设置时使用父类 DefaultResourceLoader 的默认行为 */
	@Nullable
	private ResourceLoader resourceLoader;

	/** 标记是否通过 setClassLoader() 设置了自定义类加载器（影响 getClassLoader() 的委托逻辑） */
	private boolean customClassLoader = false;

	/** 🛡️ 一次性防伪封条！CAS 保证 refresh() 只能成功调用一次，第二次直接报 IllegalStateException */
	private final AtomicBoolean refreshed = new AtomicBoolean();


	/**
	 * Create a new GenericApplicationContext.
	 * @see #registerBeanDefinition
	 * @see #refresh
	 */
	public GenericApplicationContext() {
		this.beanFactory = new DefaultListableBeanFactory();
	}

	/**
	 * Create a new GenericApplicationContext with the given DefaultListableBeanFactory.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 * @see #registerBeanDefinition
	 * @see #refresh
	 */
	public GenericApplicationContext(DefaultListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		this.beanFactory = beanFactory;
	}

	/**
	 * Create a new GenericApplicationContext with the given parent.
	 * @param parent the parent application context
	 * @see #registerBeanDefinition
	 * @see #refresh
	 */
	public GenericApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}

	/**
	 * Create a new GenericApplicationContext with the given DefaultListableBeanFactory.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 * @param parent the parent application context
	 * @see #registerBeanDefinition
	 * @see #refresh
	 */
	public GenericApplicationContext(DefaultListableBeanFactory beanFactory, ApplicationContext parent) {
		this(beanFactory);
		setParent(parent);
	}


	/**
	 * Set the parent of this application context, also setting
	 * the parent of the internal BeanFactory accordingly.
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#setParentBeanFactory
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		super.setParent(parent);
		this.beanFactory.setParentBeanFactory(getInternalParentBeanFactory());
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		super.setApplicationStartup(applicationStartup);
		this.beanFactory.setApplicationStartup(applicationStartup);
	}

	/**
	 * Set whether it should be allowed to override bean definitions by registering
	 * a different definition with the same name, automatically replacing the former.
	 * If not, an exception will be thrown. Default is "true".
	 * @since 3.0
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.beanFactory.setAllowBeanDefinitionOverriding(allowBeanDefinitionOverriding);
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * @since 3.0
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowCircularReferences
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.beanFactory.setAllowCircularReferences(allowCircularReferences);
	}

	/**
	 * Set a ResourceLoader to use for this context. If set, the context will
	 * delegate all {@code getResource} calls to the given ResourceLoader.
	 * If not set, default resource loading will apply.
	 * <p>The main reason to specify a custom ResourceLoader is to resolve
	 * resource paths (without URL prefix) in a specific fashion.
	 * The default behavior is to resolve such paths as class path locations.
	 * To resolve resource paths as file system locations, specify a
	 * FileSystemResourceLoader here.
	 * <p>You can also pass in a full ResourcePatternResolver, which will
	 * be autodetected by the context and used for {@code getResources}
	 * calls as well. Else, default resource pattern matching will apply.
	 * @see #getResource
	 * @see org.springframework.core.io.DefaultResourceLoader
	 * @see org.springframework.core.io.FileSystemResourceLoader
	 * @see org.springframework.core.io.support.ResourcePatternResolver
	 * @see #getResources
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}


	//---------------------------------------------------------------------
	// ResourceLoader / ResourcePatternResolver override if necessary
	//---------------------------------------------------------------------

	/**
	 * This implementation delegates to this context's {@code ResourceLoader} if set,
	 * falling back to the default superclass behavior otherwise.
	 * <p>As of Spring Framework 5.3.22, this method also honors registered
	 * {@linkplain #getProtocolResolvers() protocol resolvers} when a custom
	 * {@code ResourceLoader} has been set.
	 * @see #setResourceLoader(ResourceLoader)
	 * @see #addProtocolResolver(ProtocolResolver)
	 */
	@Override
	public Resource getResource(String location) {
		if (this.resourceLoader != null) {
			for (ProtocolResolver protocolResolver : getProtocolResolvers()) {
				Resource resource = protocolResolver.resolve(location, this);
				if (resource != null) {
					return resource;
				}
			}
			return this.resourceLoader.getResource(location);
		}
		return super.getResource(location);
	}

	/**
	 * This implementation delegates to this context's ResourceLoader if it
	 * implements the ResourcePatternResolver interface, falling back to the
	 * default superclass behavior otherwise.
	 * @see #setResourceLoader
	 */
	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		if (this.resourceLoader instanceof ResourcePatternResolver) {
			return ((ResourcePatternResolver) this.resourceLoader).getResources(locationPattern);
		}
		return super.getResources(locationPattern);
	}

	@Override
	public void setClassLoader(@Nullable ClassLoader classLoader) {
		super.setClassLoader(classLoader);
		this.customClassLoader = true;
	}

	@Override
	@Nullable
	public ClassLoader getClassLoader() {
		if (this.resourceLoader != null && !this.customClassLoader) {
			return this.resourceLoader.getClassLoader();
		}
		return super.getClassLoader();
	}


	//---------------------------------------------------------------------
	// Implementations of AbstractApplicationContext's template methods
	//---------------------------------------------------------------------

	/**
	 * <br>
	 * <h3>架构深度解析：现代注解派的防重复刷新机制 🛡️</h3>
	 * <p>
	 * 这段代码揭开了<b>“现代注解派（{@link GenericApplicationContext}）”</b>防重复刷新机制的真面目。
	 * 它完美地印证了我们在云原生时代的架构理念：“工厂是一次性的，绝不回头！”
	 * 让我们用<b>“并发编程”</b>和<b>“架构设计”</b>的双重視角，把这两行代码彻底嚼碎：
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Do nothing: We hold a single internal BeanFactory and rely on callers
	 * to register beans through our public methods (or the BeanFactory's).
	 * @see #registerBeanDefinition
	 */
	@Override
	protected final void refreshBeanFactory() throws IllegalStateException {
		/*
		 * 🎬 1. 并发编程的艺术：CAS 无锁机制
		 * ---------------------------------------------------------
		 * [原理解析] this.refreshed 是一个 AtomicBoolean（原子布尔变量），初始值是 false。
		 * compareAndSet(false, true) 是 Java 并发包 (J.U.C) 里极其经典的 CAS
		 * (Compare-And-Swap，比较并交换) 操作，其底层是一条 CPU 硬件级别的原子指令，性能极高。
		 *
		 * [车间大白话] 这就好比工厂大门上的“一次性防伪封条”。主线程试图启动工厂时，先检查封条
		 * 是否完好 (false)。如果是，瞬间撕毁封条 (变 true) 并大摇大摆走进去。如果有不懂事的
		 * 其他线程也跑来调 refresh()，一看封条已被撕毁 (CAS 返回 false)，加上前面的 ! 取反，
		 * 就会立刻触发下方的异常报错。
		 *
		 * 🚨 [架构铁腕] Fail-Fast (快速失败)
		 * ---------------------------------------------------------
		 * 抛出 IllegalStateException 就是 Spring 明确的架构宣言。翻译过来就是：
		 * “咱们这个现代化的新工厂不支持反复折腾！refresh 只能调一次，若想重载配置，请把整个工厂
		 * 炸了重新 new 一个！” 这种快速失败设计，能在系统启动的最早期暴露出非法的调用逻辑，
		 * 避免带着错误的状态在后面积累出更大的灾难。
		 */
		if (!this.refreshed.compareAndSet(false, true)) {
			throw new IllegalStateException(
					"GenericApplicationContext does not support multiple refresh attempts: just call 'refresh' once");
		}

		/*
		 * 🎬 2. 给仓库发营业执照：设置序列化 ID
		 * ---------------------------------------------------------
		 * [原理解析] 最后这一行，是给底层的大管家 (DefaultListableBeanFactory)
		 * 发一个全局唯一的身份证号 (Serialization ID)。
		 *
		 * [设计意图] 虽然在微服务中少见，但在早期的 Java EE 场景或某些分布式集群环境中，
		 * Spring 的上下文可能被“序列化 (Serialization)”存入磁盘或通过网络传输。
		 * 有了这个唯一 ID，在反序列化时，Spring 就能精准认出这是哪个工厂，从而恢复状态。
		 */
		this.beanFactory.setSerializationId(getId());

		/*
		 * 🎉 [第 2 步收官总结]
		 * ---------------------------------------------------------
		 * 至此，refresh() 十二步中的第 2 步 obtainFreshBeanFactory() 已经向你交出了所有底牌。
		 * * 大管家（BeanFactory）不仅已经被安全地取了出来，还被贴上了“不可重复触碰”的防伪封条，
		 * 拿到了合法的营业执照。现在的它，正摩拳擦掌，准备迎接接下来的狂风暴雨！
		 */
	}

	@Override
	protected void cancelRefresh(BeansException ex) {
		this.beanFactory.setSerializationId(null);
		super.cancelRefresh(ex);
	}

	/**
	 * Not much to do: We hold a single internal BeanFactory that will never
	 * get released.
	 */
	@Override
	protected final void closeBeanFactory() {
		this.beanFactory.setSerializationId(null);
	}

	/**
	 * Return the single internal BeanFactory held by this context
	 * (as ConfigurableListableBeanFactory).
	 */
	@Override
	public final ConfigurableListableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * Return the underlying bean factory of this context,
	 * available for registering bean definitions.
	 * <p><b>NOTE:</b> You need to call {@link #refresh()} to initialize the
	 * bean factory and its contained beans with application context semantics
	 * (autodetecting BeanFactoryPostProcessors, etc).
	 * @return the internal bean factory (as DefaultListableBeanFactory)
	 */
	public final DefaultListableBeanFactory getDefaultListableBeanFactory() {
		return this.beanFactory;
	}

	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		assertBeanFactoryActive();
		return this.beanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanDefinitionRegistry
	//---------------------------------------------------------------------

	@Override
	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException {

		this.beanFactory.registerBeanDefinition(beanName, beanDefinition);
	}

	@Override
	public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		this.beanFactory.removeBeanDefinition(beanName);
	}

	@Override
	public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		return this.beanFactory.getBeanDefinition(beanName);
	}

	@Override
	public boolean isBeanNameInUse(String beanName) {
		return this.beanFactory.isBeanNameInUse(beanName);
	}

	@Override
	public void registerAlias(String beanName, String alias) {
		this.beanFactory.registerAlias(beanName, alias);
	}

	@Override
	public void removeAlias(String alias) {
		this.beanFactory.removeAlias(alias);
	}

	@Override
	public boolean isAlias(String beanName) {
		return this.beanFactory.isAlias(beanName);
	}


	//---------------------------------------------------------------------
	// Convenient methods for registering individual beans
	//---------------------------------------------------------------------

	/**
	 * Register a bean from the given bean class, optionally providing explicit
	 * constructor arguments for consideration in the autowiring process.
	 * @param beanClass the class of the bean
	 * @param constructorArgs custom argument values to be fed into Spring's
	 * constructor resolution algorithm, resolving either all arguments or just
	 * specific ones, with the rest to be resolved through regular autowiring
	 * (may be {@code null} or empty)
	 * @since 5.2 (since 5.0 on the AnnotationConfigApplicationContext subclass)
	 */
	public <T> void registerBean(Class<T> beanClass, Object... constructorArgs) {
		registerBean(null, beanClass, constructorArgs);
	}

	/**
	 * Register a bean from the given bean class, optionally providing explicit
	 * constructor arguments for consideration in the autowiring process.
	 * @param beanName the name of the bean (may be {@code null})
	 * @param beanClass the class of the bean
	 * @param constructorArgs custom argument values to be fed into Spring's
	 * constructor resolution algorithm, resolving either all arguments or just
	 * specific ones, with the rest to be resolved through regular autowiring
	 * (may be {@code null} or empty)
	 * @since 5.2 (since 5.0 on the AnnotationConfigApplicationContext subclass)
	 */
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass, Object... constructorArgs) {
		registerBean(beanName, beanClass, (Supplier<T>) null,
				bd -> {
					for (Object arg : constructorArgs) {
						bd.getConstructorArgumentValues().addGenericArgumentValue(arg);
					}
				});
	}

	/**
	 * Register a bean from the given bean class, optionally customizing its
	 * bean definition metadata (typically declared as a lambda expression).
	 * @param beanClass the class of the bean (resolving a public constructor
	 * to be autowired, possibly simply the default constructor)
	 * @param customizers one or more callbacks for customizing the factory's
	 * {@link BeanDefinition}, e.g. setting a lazy-init or primary flag
	 * @since 5.0
	 * @see #registerBean(String, Class, Supplier, BeanDefinitionCustomizer...)
	 */
	public final <T> void registerBean(Class<T> beanClass, BeanDefinitionCustomizer... customizers) {
		registerBean(null, beanClass, null, customizers);
	}

	/**
	 * Register a bean from the given bean class, optionally customizing its
	 * bean definition metadata (typically declared as a lambda expression).
	 * @param beanName the name of the bean (may be {@code null})
	 * @param beanClass the class of the bean (resolving a public constructor
	 * to be autowired, possibly simply the default constructor)
	 * @param customizers one or more callbacks for customizing the factory's
	 * {@link BeanDefinition}, e.g. setting a lazy-init or primary flag
	 * @since 5.0
	 * @see #registerBean(String, Class, Supplier, BeanDefinitionCustomizer...)
	 */
	public final <T> void registerBean(
			@Nullable String beanName, Class<T> beanClass, BeanDefinitionCustomizer... customizers) {

		registerBean(beanName, beanClass, null, customizers);
	}

	/**
	 * Register a bean from the given bean class, using the given supplier for
	 * obtaining a new instance (typically declared as a lambda expression or
	 * method reference), optionally customizing its bean definition metadata
	 * (again typically declared as a lambda expression).
	 * @param beanClass the class of the bean
	 * @param supplier a callback for creating an instance of the bean
	 * @param customizers one or more callbacks for customizing the factory's
	 * {@link BeanDefinition}, e.g. setting a lazy-init or primary flag
	 * @since 5.0
	 * @see #registerBean(String, Class, Supplier, BeanDefinitionCustomizer...)
	 */
	public final <T> void registerBean(
			Class<T> beanClass, Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

		registerBean(null, beanClass, supplier, customizers);
	}

	/**
	 * Register a bean from the given bean class, using the given supplier for
	 * obtaining a new instance (typically declared as a lambda expression or
	 * method reference), optionally customizing its bean definition metadata
	 * (again typically declared as a lambda expression).
	 * <p>This method can be overridden to adapt the registration mechanism for
	 * all {@code registerBean} methods (since they all delegate to this one).
	 * @param beanName the name of the bean (may be {@code null})
	 * @param beanClass the class of the bean
	 * @param supplier a callback for creating an instance of the bean (in case
	 * of {@code null}, resolving a public constructor to be autowired instead)
	 * @param customizers one or more callbacks for customizing the factory's
	 * {@link BeanDefinition}, e.g. setting a lazy-init or primary flag
	 * @since 5.0
	 */
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
			@Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

		ClassDerivedBeanDefinition beanDefinition = new ClassDerivedBeanDefinition(beanClass);
		if (supplier != null) {
			beanDefinition.setInstanceSupplier(supplier);
		}
		for (BeanDefinitionCustomizer customizer : customizers) {
			customizer.customize(beanDefinition);
		}

		String nameToUse = (beanName != null ? beanName : beanClass.getName());
		registerBeanDefinition(nameToUse, beanDefinition);
	}


	/**
	 * {@link RootBeanDefinition} marker subclass for {@code #registerBean} based
	 * registrations with flexible autowiring for public constructors.
	 */
	@SuppressWarnings("serial")
	private static class ClassDerivedBeanDefinition extends RootBeanDefinition {

		public ClassDerivedBeanDefinition(Class<?> beanClass) {
			super(beanClass);
		}

		public ClassDerivedBeanDefinition(ClassDerivedBeanDefinition original) {
			super(original);
		}

		@Override
		@Nullable
		public Constructor<?>[] getPreferredConstructors() {
			Class<?> clazz = getBeanClass();
			Constructor<?> primaryCtor = BeanUtils.findPrimaryConstructor(clazz);
			if (primaryCtor != null) {
				return new Constructor<?>[] {primaryCtor};
			}
			Constructor<?>[] publicCtors = clazz.getConstructors();
			if (publicCtors.length > 0) {
				return publicCtors;
			}
			return null;
		}

		@Override
		public RootBeanDefinition cloneBeanDefinition() {
			return new ClassDerivedBeanDefinition(this);
		}
	}

}
