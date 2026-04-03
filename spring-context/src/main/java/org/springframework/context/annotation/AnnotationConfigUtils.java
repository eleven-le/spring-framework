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

package org.springframework.context.annotation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.event.DefaultEventListenerFactory;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>注解驱动的"军火库管理员"——在容器中默默埋下 6 大内置处理器的幕后英雄！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.annotation.AnnotationConfigUtils}</li>
 * <li><b>中文名</b>：注解配置工具类 —— 注解驱动基础设施的"预装工"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 annotation 包（注意！annotation 包 = 注解驱动编程模型的大本营！
 * 注解定义、解析引擎、Reader/Scanner、上下文入口，以及本类——注解基础设施的"军火补给站"，全在这里。
 * 一句话：<b>凡是和"用注解代替 XML"相关的，都在这个包里！</b>）</li>
 * <li><b>类性质</b>：<b>抽象工具类</b>（abstract + 全 static 方法 + 不可实例化），是框架内部的"隐藏后勤"</li>
 * </ul>
 *
 * <h3>💡 为什么需要这个工具类？——注解驱动需要"预装"一批基础设施处理器！</h3>
 * <p>当你写 {@code new AnnotationConfigApplicationContext(AppConfig.class)} 时，
 * Spring 能识别 @Configuration、@Bean、@Autowired、@PostConstruct、@EventListener 等注解——
 * 但这些"识别能力"不是凭空来的！每种注解都需要一个对应的<b>处理器（Processor）</b>来解析执行。</p>
 * <p>本类的核心方法 {@code registerAnnotationConfigProcessors()} 就是那个<b>在容器启动前就把
 * 这些处理器全部安排就位</b>的"幕后英雄"。</p>
 *
 * <h3>🧬 6 大内置处理器——本类注册的"核心员工"</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>#</th><th>Bean 名称（常量）</th><th>处理器类</th><th>负责解析的注解</th><th>身份</th></tr>
 * <tr><td>1</td><td>internalConfigurationAnnotationProcessor</td><td>{@link ConfigurationClassPostProcessor}</td>
 *     <td>@Configuration, @Bean, @Import, @ComponentScan, @PropertySource</td><td>BDRPP（图纸设计师）</td></tr>
 * <tr><td>2</td><td>internalAutowiredAnnotationProcessor</td><td>{@link org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor}</td>
 *     <td>@Autowired, @Value, @Inject</td><td>BPP（质检员）</td></tr>
 * <tr><td>3</td><td>internalCommonAnnotationProcessor</td><td>{@link CommonAnnotationBeanPostProcessor}</td>
 *     <td>@Resource, @PostConstruct, @PreDestroy</td><td>BPP（质检员）</td></tr>
 * <tr><td>4</td><td>internalEventListenerProcessor</td><td>{@link EventListenerMethodProcessor}</td>
 *     <td>@EventListener</td><td>BFPP + SmartInitializingSingleton</td></tr>
 * <tr><td>5</td><td>internalEventListenerFactory</td><td>{@link org.springframework.context.event.DefaultEventListenerFactory}</td>
 *     <td>（@EventListener 的工厂配套）</td><td>普通 Bean</td></tr>
 * <tr><td>6</td><td>internalPersistenceAnnotationProcessor</td><td>PersistenceAnnotationBeanPostProcessor</td>
 *     <td>@PersistenceContext, @PersistenceUnit（JPA）</td><td>BPP（仅 JPA 环境存在时注册）</td></tr>
 * </table>
 *
 * <h3>🧬 调用链路——谁在什么时候调用本类？</h3>
 * <pre>
 * new AnnotationConfigApplicationContext()
 *   └── new AnnotatedBeanDefinitionReader(this)         ← Reader 构造器
 *         └── AnnotationConfigUtils.registerAnnotationConfigProcessors(registry)  ← 👈 本类在此被调用！
 *               ├── 注册 ConfigurationClassPostProcessor（解析 @Configuration 的大脑）
 *               ├── 注册 AutowiredAnnotationBeanPostProcessor（执行 @Autowired 的双手）
 *               ├── 注册 CommonAnnotationBeanPostProcessor（执行 @Resource/@PostConstruct 的双手）
 *               ├── 注册 EventListenerMethodProcessor（处理 @EventListener）
 *               ├── 注册 DefaultEventListenerFactory（@EventListener 工厂）
 *               ├── 注册 PersistenceAnnotationBeanPostProcessor（JPA 环境，可选）
 *               └── 设置 AnnotationAwareOrderComparator + ContextAnnotationAutowireCandidateResolver
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"约定优于配置"的基础设施预装</b><br/>
 * 用户不需要手动声明这些处理器——Spring 在创建 Reader 时就默默注册好了。
 * 这就是"约定优于配置"的落地：<b>你只需写 @Autowired，不需要知道 AutowiredAnnotationBeanPostProcessor 的存在</b>。<br/>
 * <b>业务借鉴</b>：你的框架/SDK 也应该有"默认预装"机制——用户引入依赖就能用，
 * 不需要额外配置 20 个处理器。</li>
 *
 * <li><b>条件化注册——有 JPA 才注册 JPA 处理器</b><br/>
 * PersistenceAnnotationBeanPostProcessor 只在 classpath 存在 JPA API 时才注册
 * （通过 ClassUtils.isPresent 检查）。<br/>
 * 这是 Spring Boot @ConditionalOnClass 的原始形态——在 Spring Framework 层面就已经有了按需注册的思想。</li>
 *
 * <li><b>除了注册处理器，还配置了两个关键基础设施</b><br/>
 * ① {@code AnnotationAwareOrderComparator}：让 @Order 和 @Priority 注解生效的排序器<br/>
 * ② {@code ContextAnnotationAutowireCandidateResolver}：支持 @Lazy/@Qualifier 的候选解析器<br/>
 * 这些"静默配置"让整个注解驱动体系能正常运转。</li>
 * </ol>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AnnotationConfigUtils 的核心价值：<b>在容器启动的最早期（Reader 构造时），
 * 向 Registry 预装 6 大内置处理器 + 2 大基础设施组件</b>。<br/>
 * 它是"约定优于配置"的幕后功臣——你之所以能直接用 @Autowired/@Configuration/@EventListener 等注解，
 * 是因为本类在你无感知的情况下就把对应的处理器安排妥当了。</p>
 *
 * <hr/>
 * Utility class that allows for convenient registration of common
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} and
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor}
 * definitions for annotation-based configuration. Also registers a common
 * {@link org.springframework.beans.factory.support.AutowireCandidateResolver}.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 2.5
 * @see ContextAnnotationAutowireCandidateResolver
 * @see ConfigurationClassPostProcessor
 * @see CommonAnnotationBeanPostProcessor
 * @see org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
 * @see org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor
 */
public abstract class AnnotationConfigUtils {

	/**
	 * The bean name of the internally managed Configuration annotation processor.
	 */
	public static final String CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalConfigurationAnnotationProcessor";

	/**
	 * The bean name of the internally managed BeanNameGenerator for use when processing
	 * {@link Configuration} classes. Set by {@link AnnotationConfigApplicationContext}
	 * and {@code AnnotationConfigWebApplicationContext} during bootstrap in order to make
	 * any custom name generation strategy available to the underlying
	 * {@link ConfigurationClassPostProcessor}.
	 * @since 3.1.1
	 */
	public static final String CONFIGURATION_BEAN_NAME_GENERATOR =
			"org.springframework.context.annotation.internalConfigurationBeanNameGenerator";

	/**
	 * The bean name of the internally managed Autowired annotation processor.
	 */
	public static final String AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalAutowiredAnnotationProcessor";

	/**
	 * The bean name of the internally managed Required annotation processor.
	 * @deprecated as of 5.1, since no Required processor is registered by default anymore
	 */
	@Deprecated
	public static final String REQUIRED_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalRequiredAnnotationProcessor";

	/**
	 * The bean name of the internally managed JSR-250 annotation processor.
	 */
	public static final String COMMON_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalCommonAnnotationProcessor";

	/**
	 * The bean name of the internally managed JPA annotation processor.
	 */
	public static final String PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalPersistenceAnnotationProcessor";

	private static final String PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME =
			"org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor";

	/**
	 * The bean name of the internally managed @EventListener annotation processor.
	 */
	public static final String EVENT_LISTENER_PROCESSOR_BEAN_NAME =
			"org.springframework.context.event.internalEventListenerProcessor";

	/**
	 * The bean name of the internally managed EventListenerFactory.
	 */
	public static final String EVENT_LISTENER_FACTORY_BEAN_NAME =
			"org.springframework.context.event.internalEventListenerFactory";

	private static final boolean jsr250Present;

	private static final boolean jpaPresent;

	static {
		ClassLoader classLoader = AnnotationConfigUtils.class.getClassLoader();
		jsr250Present = ClassUtils.isPresent("javax.annotation.Resource", classLoader);
		jpaPresent = ClassUtils.isPresent("javax.persistence.EntityManagerFactory", classLoader) &&
				ClassUtils.isPresent(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, classLoader);
	}


	/**
	 * Register all relevant annotation post processors in the given registry.
	 * @param registry the registry to operate on
	 */
	public static void registerAnnotationConfigProcessors(BeanDefinitionRegistry registry) {
		registerAnnotationConfigProcessors(registry, null);
	}

	/**
	 * Register all relevant annotation post processors in the given registry.
	 * @param registry the registry to operate on
	 * @param source the configuration source element (already extracted)
	 * that this registration was triggered from. May be {@code null}.
	 * @return a Set of BeanDefinitionHolders, containing all bean definitions
	 * that have actually been registered by this call
	 */
	public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		/*
		 * 1. 获取核心容器引擎
		 * ---------------------------------------------------------
		 * [作用] 尝试将传入的 BeanDefinitionRegistry（Bean 注册表接口）“解包”强转为 DefaultListableBeanFactory。
		 *
		 * [深层架构] DefaultListableBeanFactory 是 Spring IoC 体系中最核心、功能最全的底层实现类，
		 * 可以说是 Spring 真正干活的“发动机”。几乎所有的高级容器（如 AnnotationConfigApplicationContext）
		 * 底层都把工作委托给它。Spring 在这里需要拿到真正的发动机实体，才能为其配置高级零件。
		 */
		DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
		if (beanFactory != null) {
			/*
			 * 2. 装配“注解感知”的排序器 (Comparator)
			 * ---------------------------------------------------------
			 * [作用] 检查并为底层 Bean 工厂装配 AnnotationAwareOrderComparator（注解感知的顺序比较器）。
			 * [解决痛点] 假设项目中定义了多个实现同一接口的 Bean，并按集合注入（如 List<MyInterface>），
			 * 原生的 Bean 工厂并不知道该按什么顺序把它们放进 List 里。
			 *
			 * [底层支撑] 换上这个高级比较器后，工厂就拥有了识别 @Order、JSR-250 的 @Priority 注解
			 * 以及 Ordered 接口的能力，从而实现精准的优先级排序。
			 */
			if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
				beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
			}
			/*
			 * 3. 装配“注解自动装配”候选解析器 (Resolver)
			 * ---------------------------------------------------------
			 * [作用] 为 Bean 工厂装配 ContextAnnotationAutowireCandidateResolver（上下文注解自动装配候选解析器）。
			 * [解决痛点] 原生工厂在自动装配（Autowire）时，主要靠简单的 byType 或 byName。
			 * 引入注解开发后，注入逻辑变得极其复杂，需要一个更智能的大脑来决定“谁能被注入”。
			 *
			 * [三大超能力] 这个解析器是处理注解依赖注入的“幕后英雄”，赋予了工厂以下能力：
			 * ① @Qualifier 识别：当注入时遇到多个同类型的 Bean，它能识别 @Qualifier("specificName") 并筛选出唯一的候选者。
			 * ② @Lazy 代理机制：当它发现在注入点（如 @Autowired 所在的字段）标有 @Lazy 注解时，它不会立即去创建真实的 Bean，而是会返回一个代理对象（Proxy），实现真正的延迟加载。
			 * ③ @Value 解析：它是解析 @Value("${xxx}") 中配置属性和 SpEL 表达式的重要一环。
			 */
			if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
				beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
			}
		}
/*
 * 💡 [架构启示]：组合优于继承与 OCP 原则
 * ---------------------------------------------------------
 * 从这短短十几行代码中，我们可以学到 Spring 优秀的可扩展性设计原则：
 * 1. 纯粹的核心：核心容器（DefaultListableBeanFactory）本身被设计得很纯粹， 它不包含任何对特定注解（如 @Order, @Qualifier）的硬编码。
 * 2. 策略与组合：相反，它是通过组合（Composition）的方式，留出了 Comparator  和 Resolver 的扩展点。
 * 3. 开闭原则 (OCP)：当我们要开启注解编程模式时，只需要把带有“注解解析能力”的 策略对象塞进核心容器即可。底层引擎无需修改源代码，完美契合开闭原则 (OCP)。
 */


		/*
		 * [数据结构选择] Spring 选择 LinkedHashSet 是为了保证这些后置处理器在集合中的顺序与插入顺序一致，这对于后续可能的按序处理非常重要。
         *
         * [初始容量设为 8 的原因] 往下看代码，Spring 在这里最多只会注册 6 个核心处理器。Java 中 HashSet 默认的负载因子是 0.75。
         * 如果容量是 8，那么它的扩容阈值就是 8 * 0.75 = 6。这就意味着，装载这 6 个核心组件刚好不会触发内部数组的扩容（Resize）动作，
         * 同时又避免了使用默认容量 16 带来的内存浪费。这是非常严谨的底层代码性能优化技巧。
		 */
		Set<BeanDefinitionHolder> beanDefs = new LinkedHashSet<>(8);

		/*
		 * 【1号大将】：注册 ConfigurationClassPostProcessor, 它是 Spring 中极其重要的 BeanFactoryPostProcessor！
		 *  职责：它是整个纯注解驱动的心脏！负责解析带有 @Configuration 的类，并且处理 @Bean、@ComponentScan、@Import 等注解。没有它，你写的配置类就是一张废纸。
		 *  身份：它实现了 BeanDefinitionRegistryPostProcessor（是 BeanFactoryPostProcessor 的子接口），这意味着它会在所有普通的 Bean 实例化之前执行。
		 *
		 *  [核心防重机制] 保证全局唯一
		 *  [运行机制] 在 Spring 容器启动的过程中，registerAnnotationConfigProcessors 这个方法是有可能被多次调用的（比如你同时传入了多个配置类，或者使用了多次扫描）。
		 *  [设计意图] 通过 containsBeanDefinition 进行前置校验，确保这些属于框架基础设施的“大将”们，在整个 IoC 容器中只会被注册一次，绝对不会被重复创建或覆盖，保证了核心引擎的稳定。
		 */
		if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			/*
			 * 定义与追溯：基础设施的专属身份
			 * [RootBeanDefinition] 在 Spring 中，Bean 的图纸分为很多种。对于框架内部的基础设施类，
			 * Spring 一律使用 RootBeanDefinition。它代表这是一个完整、独立、没有父类的顶级 Bean 定义。
			 *
			 * [setSource(source)] 保留“案发现场”。这里的 source 通常是你传入的原始配置类。
			 * 把源头记录在定义里，将来如果发生异常，或者你在使用 IDEA 的 Spring 插件时，
			 * 工具就能精确告诉你：这个内置处理器是由哪个配置类引发注册的，极大地方便了调试。
			 */
			RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
			def.setSource(source);
			/*
			 * 注册与打包：完成闭环
			 * [底层动作] registerPostProcessor 是一个辅助方法。它真正在底层调用了 registry.registerBeanDefinition(...)，
			 * 把这张“图纸”放进了 DefaultListableBeanFactory 核心的 ConcurrentHashMap 缓存中。
			 *
			 * [数据封装] 注册完成后，它会将 BeanDefinition（图纸）、BeanName（名字）打包成一个 BeanDefinitionHolder（持有者，“Bean 定义信息载体” 或者 “Bean 定义包装器”）对象，
			 * 并加入到我们一开始创建的 beanDefs 集合中。最终 return beanDefs;，让调用方清楚地知道这次到底成功注册了哪些组件。
			 */
			beanDefs.add(registerPostProcessor(registry, def, CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		/*
		 *【2号大将】：注册 AutowiredAnnotationBeanPostProcessor, 专门处理 @Autowired, @Value, @Inject 注解的依赖注入
		 * 职责：负责在 Bean 的生命周期中，把带有 @Autowired 的属性或方法自动注入进来（也就是常说的 DI 依赖注入）。
		 * 身份：它实现了 BeanPostProcessor，会在 Bean 实例化的过程中介入。
		 */
		if (!registry.containsBeanDefinition(AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		// Check for JSR-250 support, and if present add the CommonAnnotationBeanPostProcessor.
		/*
		 * 【3号大将】：注册 CommonAnnotationBeanPostProcessor,  专门处理 JSR-250 规范的注解，比如 @Resource, @PostConstruct, @PreDestroy
		 * 职责：如果你喜欢用 @Resource 来替代 @Autowired，或者用 @PostConstruct 来做初始化逻辑，底层就是靠它来解析和执行的。
		 */
		if (jsr250Present && !registry.containsBeanDefinition(COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, COMMON_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		// Check for JPA support, and if present add the PersistenceAnnotationBeanPostProcessor.
		if (jpaPresent && !registry.containsBeanDefinition(PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition();
			try {
				def.setBeanClass(ClassUtils.forName(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME,
						AnnotationConfigUtils.class.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(
						"Cannot load optional framework class: " + PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, ex);
			}
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		// 【4号、5号大将】：注册与事件监听相关的处理器 (处理 @EventListener)
		if (!registry.containsBeanDefinition(EVENT_LISTENER_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_PROCESSOR_BEAN_NAME));
		}

		if (!registry.containsBeanDefinition(EVENT_LISTENER_FACTORY_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_FACTORY_BEAN_NAME));
		}

		return beanDefs;
	}

	/**
	 * <br>
	 * <h3>标准流水线动作：注册框架内部组件</h3>
	 * <p>
	 * 这段只有短短三行的代码，可以说是 Spring 注册框架内部组件的<b>“标准流水线动作”</b>。
	 * 它完成了从身份确认、正式入库到返回凭证的完整闭环。
	 * </p>
	 *
	 * @param registry   核心大管家（通常是 DefaultListableBeanFactory）
	 * @param definition 准备入库的内部组件“图纸”
	 * @param beanName   组件在容器中的名字
	 * @return 包含图纸和名字的完整凭证（包装盒）
	 */
	private static BeanDefinitionHolder registerPostProcessor(
			BeanDefinitionRegistry registry, RootBeanDefinition definition, String beanName) {
		/*
		 * 1. 打上“皇家御用”的思想钢印 (设置基础设施角色)
		 * ---------------------------------------------------------
		 * [原理解析] 在 Spring 的世界里，Bean 是有阶级（角色）之分的：
		 * ① ROLE_APPLICATION (0)：用户自己写的业务 Bean（如 UserService）。默认角色。
		 * ② ROLE_SUPPORT (1)：辅助类，通常是某些复杂配置的一部分。
		 * ③ ROLE_INFRASTRUCTURE (2)：基础设施！代表这是维持框架运转的“幕后黑手”。
		 *
		 * [核心目的] 打上这个标记后，Spring 在执行 AOP 自动代理或组件扫描时，一旦识别到
		 * ROLE_INFRASTRUCTURE 就会直接跳过。就好比告诉安检人员：“这些是系统管理员，不用检查，直接放行！”
		 * 从而完美避免了框架内部组件被误操作。
		 */
		definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		/*
		 * 2. 图纸入库，正式存档 (注册 BeanDefinition)
		 * ---------------------------------------------------------
		 * [原理解析] 整个流程中最核心的一步！不管是前面提到的哪位“大将”，它们的图纸在这里
		 * 被正式交给了 registry（通常是咱们最开始提到的核心大管家 DefaultListableBeanFactory）。
		 *
		 * [底层动作] 大管家接到图纸后，会把它放进内部名为 beanDefinitionMap 的 ConcurrentHashMap （并发哈希表） 中。
		 * 从这一刻起，Spring 容器才真正知道了这个组件的存在。
		 */
		registry.registerBeanDefinition(beanName, definition);
		/*
		 * 3. 打包发货，返回凭证 (封装 Holder)
		 * ---------------------------------------------------------
		 * [原理解析] 呼应了 Holder 的包装盒作用。图纸虽然入库了，但外层的调用方还需要知道
		 * “我到底成功注册了什么”。
		 *
		 * [底层动作] 系统把刚刚入库的图纸 (definition) 和名字 (beanName) 装进
		 * BeanDefinitionHolder 这个“包装盒”里，作为一个完整的凭证返回回去。
		 */
		return new BeanDefinitionHolder(definition, beanName);
	}

	@Nullable
	private static DefaultListableBeanFactory unwrapDefaultListableBeanFactory(BeanDefinitionRegistry registry) {
		if (registry instanceof DefaultListableBeanFactory) {
			return (DefaultListableBeanFactory) registry;
		}
		else if (registry instanceof GenericApplicationContext) {
			return ((GenericApplicationContext) registry).getDefaultListableBeanFactory();
		}
		else {
			return null;
		}
	}

	public static void processCommonDefinitionAnnotations(AnnotatedBeanDefinition abd) {
		processCommonDefinitionAnnotations(abd, abd.getMetadata());
	}

	static void processCommonDefinitionAnnotations(AnnotatedBeanDefinition abd, AnnotatedTypeMetadata metadata) {
		AnnotationAttributes lazy = attributesFor(metadata, Lazy.class);
		if (lazy != null) {
			abd.setLazyInit(lazy.getBoolean("value"));
		}
		else if (abd.getMetadata() != metadata) {
			lazy = attributesFor(abd.getMetadata(), Lazy.class);
			if (lazy != null) {
				abd.setLazyInit(lazy.getBoolean("value"));
			}
		}

		if (metadata.isAnnotated(Primary.class.getName())) {
			abd.setPrimary(true);
		}
		AnnotationAttributes dependsOn = attributesFor(metadata, DependsOn.class);
		if (dependsOn != null) {
			abd.setDependsOn(dependsOn.getStringArray("value"));
		}

		AnnotationAttributes role = attributesFor(metadata, Role.class);
		if (role != null) {
			abd.setRole(role.getNumber("value").intValue());
		}
		AnnotationAttributes description = attributesFor(metadata, Description.class);
		if (description != null) {
			abd.setDescription(description.getString("value"));
		}
	}

	static BeanDefinitionHolder applyScopedProxyMode(
			ScopeMetadata metadata, BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {

		ScopedProxyMode scopedProxyMode = metadata.getScopedProxyMode();
		if (scopedProxyMode.equals(ScopedProxyMode.NO)) {
			return definition;
		}
		boolean proxyTargetClass = scopedProxyMode.equals(ScopedProxyMode.TARGET_CLASS);
		return ScopedProxyCreator.createScopedProxy(definition, registry, proxyTargetClass);
	}

	@Nullable
	static AnnotationAttributes attributesFor(AnnotatedTypeMetadata metadata, Class<?> annotationClass) {
		return attributesFor(metadata, annotationClass.getName());
	}

	@Nullable
	static AnnotationAttributes attributesFor(AnnotatedTypeMetadata metadata, String annotationClassName) {
		return AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(annotationClassName));
	}

	static Set<AnnotationAttributes> attributesForRepeatable(AnnotationMetadata metadata,
			Class<?> containerClass, Class<?> annotationClass) {

		return attributesForRepeatable(metadata, containerClass.getName(), annotationClass.getName());
	}

	@SuppressWarnings("unchecked")
	static Set<AnnotationAttributes> attributesForRepeatable(
			AnnotationMetadata metadata, String containerClassName, String annotationClassName) {

		Set<AnnotationAttributes> result = new LinkedHashSet<>();

		// Direct annotation present?
		addAttributesIfNotNull(result, metadata.getAnnotationAttributes(annotationClassName));

		// Container annotation present?
		Map<String, Object> container = metadata.getAnnotationAttributes(containerClassName);
		if (container != null && container.containsKey("value")) {
			for (Map<String, Object> containedAttributes : (Map<String, Object>[]) container.get("value")) {
				addAttributesIfNotNull(result, containedAttributes);
			}
		}

		// Return merged result
		return Collections.unmodifiableSet(result);
	}

	private static void addAttributesIfNotNull(
			Set<AnnotationAttributes> result, @Nullable Map<String, Object> attributes) {

		if (attributes != null) {
			result.add(AnnotationAttributes.fromMap(attributes));
		}
	}

}
