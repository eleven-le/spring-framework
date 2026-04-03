/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>AnnotationConfigApplicationContext 的"左膀"——把具体的 Class 变成 BeanDefinition 的翻译官！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.annotation.AnnotatedBeanDefinitionReader}</li>
 * <li><b>中文名</b>：注解 Bean 定义读取器 —— 精确注册配置类的"翻译官"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 annotation 包（注意！annotation 包 = 注解驱动编程模型的大本营！
 * 注解定义、解析引擎、Reader/Scanner、注解上下文全在这里。
 * 一句话：<b>凡是和"用注解代替 XML"相关的，都在这个包里！</b>）</li>
 * <li><b>类性质</b>：普通类（非接口、非抽象类），是一个<b>工具型适配器</b></li>
 * </ul>
 *
 * <h3>💡 为什么需要 AnnotatedBeanDefinitionReader？——Class 和 BeanDefinition 之间需要"翻译"！</h3>
 * <p>Spring 容器不直接操作 Class 对象——它的世界里一切都是 BeanDefinition（图纸）。
 * 当你写 {@code ctx.register(AppConfig.class)} 时，容器需要有人把这个 Class 翻译成 BD：</p>
 * <ol>
 * <li>解析类上的 @Scope → 设置 BD 的 scope（singleton/prototype）</li>
 * <li>解析 @Lazy → 设置 BD 的 lazyInit</li>
 * <li>解析 @Primary → 设置 BD 的 primary 标记</li>
 * <li>解析 @DependsOn → 设置 BD 的 dependsOn</li>
 * <li>生成 beanName（通过 BeanNameGenerator）</li>
 * <li>包装成 AnnotatedGenericBeanDefinition → 注册到 Registry</li>
 * </ol>
 * <p>这就是 Reader 的核心价值——它是 <b>Class → AnnotatedGenericBeanDefinition → Registry</b> 这条链路的执行者。</p>
 *
 * <h3>🧬 Reader vs Scanner——左膀 vs 右臂的分工</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比维度</th><th>Reader（本类）</th><th>Scanner（ClassPathBeanDefinitionScanner）</th></tr>
 * <tr><td>输入</td><td>具体的 Class 对象</td><td>包路径字符串</td></tr>
 * <tr><td>发现方式</td><td>你告诉我是谁（精确指定）</td><td>我自己去找（ASM 扫描）</td></tr>
 * <tr><td>BD 类型</td><td>AnnotatedGenericBeanDefinition</td><td>ScannedGenericBeanDefinition</td></tr>
 * <tr><td>使用场景</td><td>{@code ctx.register(AppConfig.class)}</td><td>{@code ctx.scan("com.example")}</td></tr>
 * <tr><td>类加载</td><td>需要加载类到 JVM</td><td>不需要（ASM 直接读 .class 文件）</td></tr>
 * </table>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>构造时的"隐藏大招"——注册 6 大内置处理器</b><br/>
 * Reader 的构造器会调用 {@link AnnotationConfigUtils#registerAnnotationConfigProcessors(BeanDefinitionRegistry)}，
 * 向容器隐式注册 6 大核心处理器：
 * <ul>
 * <li>ConfigurationClassPostProcessor —— 解析 @Configuration/@Bean/@Import/@ComponentScan</li>
 * <li>AutowiredAnnotationBeanPostProcessor —— 处理 @Autowired/@Value</li>
 * <li>CommonAnnotationBeanPostProcessor —— 处理 @Resource/@PostConstruct/@PreDestroy</li>
 * <li>EventListenerMethodProcessor —— 处理 @EventListener</li>
 * <li>DefaultEventListenerFactory —— @EventListener 的工厂</li>
 * <li>PersistenceAnnotationBeanPostProcessor —— JPA @PersistenceContext（如果有 JPA）</li>
 * </ul>
 * 这意味着：<b>仅仅 new 一个 Reader，Spring 的注解解析基础设施就全部就绪了！</b><br/>
 * <b>业务借鉴</b>：当你的框架需要"默认就能工作"时，可以在核心组件的构造期隐式注册必要的基础设施，
 * 让用户无需手动配置就能获得完整能力。</li>
 *
 * <li><b>@Conditional 条件过滤——注册前先问"该不该注册"</b><br/>
 * Reader 内部持有 {@code ConditionEvaluator}，在注册每个类之前，先检查类上的 @Conditional 注解。
 * 如果条件不满足，这个类直接跳过，不会生成 BD。<br/>
 * 这是 Spring Boot 自动配置的基础——@ConditionalOnClass、@ConditionalOnProperty 等都依赖此机制。</li>
 * </ol>
 *
 * <h3>🧬 架构定位</h3>
 * <pre>
 * AnnotationConfigApplicationContext
 * ├── reader = AnnotatedBeanDefinitionReader   ← 👈 你在这里！（左膀：精确注册）
 * │     ├── 持有 BeanDefinitionRegistry（注册目标）
 * │     ├── 持有 BeanNameGenerator（命名策略）
 * │     ├── 持有 ScopeMetadataResolver（作用域解析）
 * │     └── 持有 ConditionEvaluator（@Conditional 条件过滤）
 * └── scanner = ClassPathBeanDefinitionScanner （右臂：包扫描）
 * </pre>
 *
 * <h3>📌 核心方法速查</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th></tr>
 * <tr><td>{@code register(Class<?>...)}</td><td>批量注册：遍历每个类调 registerBean</td></tr>
 * <tr><td>{@code registerBean(Class, ...)}</td><td>单个注册：@Conditional检查 → 解析注解 → 生成BD → 注册到Registry</td></tr>
 * <tr><td>{@code doRegisterBean(...)}</td><td>核心实现：真正的 Class → BD → Registry 翻译链路</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AnnotatedBeanDefinitionReader 的核心价值：<b>把 Class 对象翻译为 AnnotatedGenericBeanDefinition 并注册到容器</b>。<br/>
 * 它是注解驱动容器的"精确注册"通道（对应 Scanner 的"批量扫描"通道）。
 * 更关键的是，它在构造时隐式注册了 6 大内置处理器，让 Spring 的注解解析能力"开箱即用"。</p>
 *
 * <hr/>
 * Convenient adapter for programmatic registration of bean classes.
 *
 * <p>This is an alternative to {@link ClassPathBeanDefinitionScanner}, applying
 * the same resolution of annotations but for explicitly registered classes only.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @since 3.0
 * @see AnnotationConfigApplicationContext#register
 */
public class AnnotatedBeanDefinitionReader {

	private final BeanDefinitionRegistry registry;

	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private ConditionEvaluator conditionEvaluator;


	/**
	 * Create a new {@code AnnotatedBeanDefinitionReader} for the given registry.
	 * <p>If the registry is {@link EnvironmentCapable}, e.g. is an {@code ApplicationContext},
	 * the {@link Environment} will be inherited, otherwise a new
	 * {@link StandardEnvironment} will be created and used.
	 * @param registry the {@code BeanFactory} to load bean definitions into,
	 * in the form of a {@code BeanDefinitionRegistry}
	 * @see #AnnotatedBeanDefinitionReader(BeanDefinitionRegistry, Environment)
	 * @see #setEnvironment(Environment)
	 */
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
		this(registry, getOrCreateEnvironment(registry));
	}

	/**
	 *
	 * <p> 1. AnnotatedBeanDefinitionReader 的构造函数最终调用了这里  </p>
	 * <br>
	 * <hr>
	 *
	 * Create a new {@code AnnotatedBeanDefinitionReader} for the given registry,
	 * using the given {@link Environment}.
	 * @param registry the {@code BeanFactory} to load bean definitions into,
	 * in the form of a {@code BeanDefinitionRegistry}
	 * @param environment the {@code Environment} to use when evaluating bean definition
	 * profiles.
	 * @since 3.1
	 */
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		Assert.notNull(environment, "Environment must not be null");
		this.registry = registry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);

		/*
		 * 这个工具方法的名字已经出卖了它的意图：注册注解配置相关的处理器。
		 *
		 * 【核心点】向容器中注册解析注解所需的所有内部后置处理器！
		 * 看看它到底往 registry（也就是我们之前提到的 DefaultListableBeanFactory 大仓库）里塞了什么：
		 */
		AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
	}


	/**
	 * Get the BeanDefinitionRegistry that this reader operates on.
	 */
	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	/**
	 * Set the {@code Environment} to use when evaluating whether
	 * {@link Conditional @Conditional}-annotated component classes should be registered.
	 * <p>The default is a {@link StandardEnvironment}.
	 * @see #registerBean(Class, String, Class...)
	 */
	public void setEnvironment(Environment environment) {
		this.conditionEvaluator = new ConditionEvaluator(this.registry, environment, null);
	}

	/**
	 * Set the {@code BeanNameGenerator} to use for detected bean classes.
	 * <p>The default is a {@link AnnotationBeanNameGenerator}.
	 */
	public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator =
				(beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
	}

	/**
	 * Set the {@code ScopeMetadataResolver} to use for registered component classes.
	 * <p>The default is an {@link AnnotationScopeMetadataResolver}.
	 */
	public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
		this.scopeMetadataResolver =
				(scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
	}


	/**
	 * Register one or more component classes to be processed.
	 * <p>Calls to {@code register} are idempotent; adding the same
	 * component class more than once has no additional effect.
	 * @param componentClasses one or more component classes,
	 * e.g. {@link Configuration @Configuration} classes
	 */
	public void register(Class<?>... componentClasses) {
		for (Class<?> componentClass : componentClasses) {
			registerBean(componentClass);
		}
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations.
	 * @param beanClass the class of the bean
	 */
	public void registerBean(Class<?> beanClass) {
		doRegisterBean(beanClass, null, null, null, null);
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations.
	 * @param beanClass the class of the bean
	 * @param name an explicit name for the bean
	 * (or {@code null} for generating a default bean name)
	 * @since 5.2
	 */
	public void registerBean(Class<?> beanClass, @Nullable String name) {
		doRegisterBean(beanClass, name, null, null, null);
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations.
	 * @param beanClass the class of the bean
	 * @param qualifiers specific qualifier annotations to consider,
	 * in addition to qualifiers at the bean class level
	 */
	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> beanClass, Class<? extends Annotation>... qualifiers) {
		doRegisterBean(beanClass, null, qualifiers, null, null);
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations.
	 * @param beanClass the class of the bean
	 * @param name an explicit name for the bean
	 * (or {@code null} for generating a default bean name)
	 * @param qualifiers specific qualifier annotations to consider,
	 * in addition to qualifiers at the bean class level
	 */
	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> beanClass, @Nullable String name,
			Class<? extends Annotation>... qualifiers) {

		doRegisterBean(beanClass, name, qualifiers, null, null);
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations, using the given supplier for obtaining a new
	 * instance (possibly declared as a lambda expression or method reference).
	 * @param beanClass the class of the bean
	 * @param supplier a callback for creating an instance of the bean
	 * (may be {@code null})
	 * @since 5.0
	 */
	public <T> void registerBean(Class<T> beanClass, @Nullable Supplier<T> supplier) {
		doRegisterBean(beanClass, null, null, supplier, null);
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations, using the given supplier for obtaining a new
	 * instance (possibly declared as a lambda expression or method reference).
	 * @param beanClass the class of the bean
	 * @param name an explicit name for the bean
	 * (or {@code null} for generating a default bean name)
	 * @param supplier a callback for creating an instance of the bean
	 * (may be {@code null})
	 * @since 5.0
	 */
	public <T> void registerBean(Class<T> beanClass, @Nullable String name, @Nullable Supplier<T> supplier) {
		doRegisterBean(beanClass, name, null, supplier, null);
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations.
	 * @param beanClass the class of the bean
	 * @param name an explicit name for the bean
	 * (or {@code null} for generating a default bean name)
	 * @param supplier a callback for creating an instance of the bean
	 * (may be {@code null})
	 * @param customizers one or more callbacks for customizing the factory's
	 * {@link BeanDefinition}, e.g. setting a lazy-init or primary flag
	 * @since 5.2
	 */
	public <T> void registerBean(Class<T> beanClass, @Nullable String name, @Nullable Supplier<T> supplier,
			BeanDefinitionCustomizer... customizers) {

		doRegisterBean(beanClass, name, null, supplier, customizers);
	}

	/**
	 * <h3>架构深度解析：全自动 Bean 图纸加工流水线 🏭</h3>
	 * <p>
	 * 请闭上眼睛想象：你现在是 Spring 宇宙中一家“全自动 Bean 图纸加工厂”的厂长。
	 * 而 {@code doRegisterBean} 方法，正是你车间里最核心的那条<b>“全自动流水线”</b>。
	 * 当你把 Java 类扔进这条流水线时，它会经历一系列奇妙的加工，最终变成一张合格的图纸被存入核心仓库。
	 * </p>
	 *
	 * <h4>📦 进料口的“原材料清单”（入参解析）</h4>
	 * <p>流水线的入口处站着质检员，他手里拿着一张表，核对以下物料：</p>
	 * <ul>
	 * <li><b>{@code beanClass} (核心原材料 - 必填)</b>：要加工的“原木”（如 {@code AppConfig.class}）。没有它，整个车间就停工了。</li>
	 * <li><b>{@code name} (定制铭牌 - 选填)</b>：组件的专属名字。如果不传（null），车间主任会按规矩自动生成一个。</li>
	 * <li><b>{@code qualifiers} (外挂便利贴 - 选填)</b>：应对无法修改源码的第三方类，将 {@code @Primary} 等当作“便利贴”递进去，流水线会强行贴在图纸上。</li>
	 * <li><b>{@code supplier} (高级 3D 打印机 - Spring 5 大招)</b>：传入 Lambda 表达式（如 {@code () -> new UserService()}），相当于给图纸绑了一台高效 3D 打印机，后续要货时一键打印，性能起飞！</li>
	 * <li><b>{@code customizers} (VIP 魔改特权 - 选填)</b>：派几个“特派员”（回调函数）进去，在图纸出厂前的最后一秒进行任意涂改与干预。</li>
	 * </ul>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations.
	 * @param beanClass the class of the bean
	 * @param name an explicit name for the bean
	 * @param qualifiers specific qualifier annotations to consider, if any,
	 * in addition to qualifiers at the bean class level
	 * @param supplier a callback for creating an instance of the bean
	 * (may be {@code null})
	 * @param customizers one or more callbacks for customizing the factory's
	 * {@link BeanDefinition}, e.g. setting a lazy-init or primary flag
	 * @since 5.0
	 */
	private <T> void doRegisterBean(Class<T> beanClass, @Nullable String name,
			@Nullable Class<? extends Annotation>[] qualifiers, @Nullable Supplier<T> supplier,
			@Nullable BeanDefinitionCustomizer[] customizers) {
		/*
		 * 🎬 1. X光扫描与安检门 (图纸初始化与条件拦截)
		 * [扫描建模] 机器先用反射技术对 beanClass 进行 360 度扫描，转化为名为  AnnotatedGenericBeanDefinition 的数字化图纸，保留类上所有的注解信息。
		 * [无情安检] 图纸来到 conditionEvaluator（条件评估器）面前，安检员死死盯住 @Conditional 等条件注解（比如 @ConditionalOnMissingBean）。 如果不符合条件？警报拉响，直接 return，图纸当场销毁，提前下班！
		 */
		AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);
		if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
			return;
		}
		/*
		 * 🎬 2. 挂载 3D 打印机 (绑定实例提供者)
		 * [高级装配] 如果你在进料口递交了高级的 Supplier（入参 4），机器就会在这里把它组装到图纸上。彻底规避后续繁琐的反射实例化过程。
		 */
		abd.setInstanceSupplier(supplier);
		/*
		 * 🎬 3. 分配宿舍与印制身份证 (解析作用域与生成 BeanName)
		 * [查户口 Scope] 车间主任读取 @Scope 注解，决定这个 Bean它是住“全局单例豪华套房”（singleton） 还是“每次新建的快捷酒店”（prototype）。
		 * [发证件 Name] 如果入参没给名字，自带的 beanNameGenerator 就会生成一个默认名字 （通常是类名首字母小写）。
		 */
		ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
		abd.setScope(scopeMetadata.getScopeName());
		String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));
		/*
		 * 🎬 4. 全自动“纹身”识别 (处理通用注解)
		 * [精密盖章] 识别仪自动读取代码里的通用注解，并给图纸盖章：
		 * ① 看到 @Lazy ➡️ 盖上“不着急，延后处理”章。
		 * ② 看到 @Primary ➡️ 盖上“VIP 优先注入”章。
		 * ③ 看到 @DependsOn ➡️ 写上“必须先实例化某某”。
		 */
		AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
		/*
		 * 🎬 5. 人工特权干预 (处理便利贴与特派员)
		 * [极致扩展] 流水线在此暂停！开始处理入参 3 和入参 5。把你强行加的额外属性（qualifiers）以及特派员的涂改逻辑（customizers）全部融合进图纸里， 完美展现了 Spring 底层极高的可定制性。
		 */
		if (qualifiers != null) {
			for (Class<? extends Annotation> qualifier : qualifiers) {
				if (Primary.class == qualifier) {
					abd.setPrimary(true);
				}
				else if (Lazy.class == qualifier) {
					abd.setLazyInit(true);
				}
				else {
					abd.addQualifier(new AutowireCandidateQualifier(qualifier));
				}
			}
		}
		if (customizers != null) {
			for (BeanDefinitionCustomizer customizer : customizers) {
				customizer.customize(abd);
			}
		}
		/*
		 * 🎬 6. 打包装箱，正式入库！ (注册到大管家)
		 * [打包装箱] 图纸画好了！拿来 BeanDefinitionHolder 纸箱，把图纸和名字一起装进去。
		 * [防呆设计] 如果发现这是 Web 专属的 Request 作用域图纸，为防被单例对象错误引用，车间会施展魔法，给你套一个 CGLIB 代理的“保护壳”(ScopedProxyMode)。
		 * [入库上锁] 最后一步，纸箱被正式搬进大管家 (registry) 的恒温仓库 (底层的 ConcurrentHashMap) 中。大功告成！
		 */
		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
	}


	/**
	 * Get the Environment from the given registry if possible, otherwise return a new
	 * StandardEnvironment.
	 */
	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry instanceof EnvironmentCapable) {
			return ((EnvironmentCapable) registry).getEnvironment();
		}
		return new StandardEnvironment();
	}

}
