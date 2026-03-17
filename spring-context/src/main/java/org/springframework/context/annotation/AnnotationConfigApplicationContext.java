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

package org.springframework.context.annotation;

import java.util.Arrays;
import java.util.function.Supplier;

import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Standalone application context, accepting <em>component classes</em> as input &mdash;
 * in particular {@link Configuration @Configuration}-annotated classes, but also plain
 * {@link org.springframework.stereotype.Component @Component} types and JSR-330 compliant
 * classes using {@code javax.inject} annotations.
 *
 * <p>Allows for registering classes one by one using {@link #register(Class...)}
 * as well as for classpath scanning using {@link #scan(String...)}.
 *
 * <p>In case of multiple {@code @Configuration} classes, {@link Bean @Bean} methods
 * defined in later classes will override those defined in earlier classes. This can
 * be leveraged to deliberately override certain bean definitions via an extra
 * {@code @Configuration} class.
 *
 * <p>See {@link Configuration @Configuration}'s javadoc for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 3.0
 * @see #register
 * @see #scan
 * @see AnnotatedBeanDefinitionReader
 * @see ClassPathBeanDefinitionScanner
 * @see org.springframework.context.support.GenericXmlApplicationContext
 */
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

	private final AnnotatedBeanDefinitionReader reader;

	private final ClassPathBeanDefinitionScanner scanner;


	/**
	 * Create a new AnnotationConfigApplicationContext that needs to be populated
	 * through {@link #register} calls and then manually {@linkplain #refresh refreshed}.
	 *
	 * <br>
	 * <hr>
	 * <h5> 隐式操作：初始化底层大仓库</h5>
	 * <p>
	 * 在执行这个无参构造方法之前，Java 会先调用父类 {@link GenericApplicationContext}
	 * 的构造方法。在那里面，Spring 初始化了它最核心的底层工厂 ——
	 * {@code DefaultListableBeanFactory}。
	 * </p>
	 */
	public AnnotationConfigApplicationContext() {
		// 记录启动步骤（Spring 5.3 新增的特性，用于性能分析，可暂时忽略）
		StartupStep createAnnotatedBeanDefReader = getApplicationStartup().start("spring.context.annotated-bean-reader.create");

		/*
		 * 1. 初始化注解 Bean 定义读取器 (Reader)  “左膀（Reader，负责直接注册配置类）”
		 * ---------------------------------------------------------
		 * [作用] 读取被 @Component、@Configuration 等注解修饰的类，并将它们转换为 BeanDefinition。
		 *
		 * [内幕] 创建 reader 时，Spring 会隐式向容器注册几个非常核心的内部后置处理器 (PostProcessor)。
		 * 例如大名鼎鼎的 ConfigurationClassPostProcessor (专门解析 @Configuration 和 @Bean)。
		 *
		 * [比喻] 如果把 Spring 容器比作即将开工的建筑工地，你自己的业务 Bean 就是砖块。
		 * 那么 reader 的初始化，就是在砖块进场前，秘密招募“包工头”和“重型机械”等内部员工。
		 *
		 * [源码追踪] 追踪 new AnnotatedBeanDefinitionReader(this) 的底层，你会发现它不仅仅
		 * 实例化了自己，更核心的是调用了 AnnotationConfigUtils.registerAnnotationConfigProcessors(...)
		 * 工具方法来完成上述的“招募”动作，这个工具方法的名字已经出卖了它的意图：注册注解配置相关的处理器。
		 */
		this.reader = new AnnotatedBeanDefinitionReader(this);
		createAnnotatedBeanDefReader.end();

		/*
		 * 2. 初始化类路径 Bean 定义扫描器 (Scanner) —— “右臂（Scanner，负责包扫描）”
		 * ---------------------------------------------------------
		 * [核心职责] 充当 Spring 的“超级雷达”。
		 * 顾名思义，它负责去指定的包路径下，把所有带有 @Component、@Service、@Controller
		 * 等注解的类全部找出来，转换成 BeanDefinition（图纸），并最终注册到容器里。
		 *
		 * [参数解析] 为什么传入 this？(当前的 AnnotationConfigApplicationContext 实例)
		 * ① 原理解析：当前上下文本身实现了 BeanDefinitionRegistry（图纸注册表）接口。
		 * ② 设计意图：扫描器就像是在外围侦察的“侦察兵”，找到带有注解的类后，总得有个地方交回情报。
		 * 把 this 传给它，就是明确告诉它：“把你扫描到的所有图纸，都存到我这个大管家的仓库（ConcurrentHashMap）里！”
		 *
		 * 💡 [高频面试/源码避坑指南]：@ComponentScan 到底是谁扫的？
		 * ---------------------------------------------------------
		 * 这是一个极易混淆的盲点，也是展现底层技术深度的关键！
		 *
		 * ❌ 误区：平时写在配置类上的 @ComponentScan("com.xxx")，底层是由这里的this.scanner 执行扫描的。
		 * ✅ 真相：这里的 this.scanner 纯粹是留给“编程式”调用的。它主要是为了让你在 main 方法里手动执行 context.scan("com.xxx") 时使用的。
		 *
		 * 你写在配置类上的 @ComponentScan 注解，其真正的解析者是咱们之前聊过的【元老 1】 —— ConfigurationClassPostProcessor！它会在后续的生命周期中，
		 * 自己内部偷偷 new 一个全新的 ClassPathBeanDefinitionScanner 来完成扫描动作。
		 *
		 * Spring 在这里提前 new 出一个 scanner 放在上下文中，更多是为了提供丰富的 API 支持，
		 * 保证上下文对外提供 scan() 能力的完整性。
		 */
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	/**
	 * Create a new AnnotationConfigApplicationContext with the given DefaultListableBeanFactory.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 */
	public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	/**
	 * Create a new AnnotationConfigApplicationContext, deriving bean definitions
	 * from the given component classes and automatically refreshing the context.
	 * @param componentClasses one or more component classes &mdash; for example,
	 * {@link Configuration @Configuration} classes
	 */
	public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
		/*
		 * 1. 环境准备 (初始化基础组件)
		 * ---------------------------------------------------------
		 * [核心动作] 调用无参构造函数 this()。
		 *
		 * [原理解析] 在这里，Spring 默默完成了两项核心工作：一是创建了专门读取注解的
		 * Reader 和扫描包的 Scanner；二是向容器中注册了维持框架运转的基础设施 Bean
		 * （如处理 @Configuration 和 @Autowired 的内部后置处理器）。
		 *
		 * [形象比喻] 相当于在建筑工地正式开工前，先把“包工头”和“重型机械”安排进场。
		 */
		this();

		/*
		 * 2. 注册主配置类 (图纸入库)
		 * ---------------------------------------------------------
		 * [核心动作] 将传入的配置类（例如 AppConfig.class）解析为 BeanDefinition。
		 *
		 * [原理解析] Spring 容器目前虽然有了基础组件，但还不知道业务逻辑在哪里。
		 * 这一步就是把主配置类注册进大管家的仓库（DefaultListableBeanFactory）中。
		 *
		 * [形象比喻] 这个配置类就像是一张“总设计图纸”，它指引着 Spring 接下来去哪里
		 * 寻找其他的业务 Bean（比如通过 @ComponentScan 指示的扫描路径）。
		 */
		register(componentClasses);

		/*
		 * 3. 刷新与启动容器 (万物生长的引擎)
		 * ---------------------------------------------------------
		 * [核心动作] 执行 Spring 源码中最核心、最复杂、也最重要的方法 —— refresh()。
		 *
		 * [原理解析] 之前的步骤都只是在做“纸上谈兵”（处理 BeanDefinition 图纸）。
		 * 只有调用了 refresh()，Spring 才会真正开始根据图纸去实例化所有的单例 Bean，
		 * 完成依赖注入（DI），并织入切面逻辑（AOP）等高级功能。
		 *
		 * [高能预警] 这是整个 Spring 框架的“心脏跳动”时刻。只要彻底掌握了 refresh()
		 * 内部的执行主干，Spring 的底层原理基本就通透了！
		 */
		refresh();
	}

	/**
	 * Create a new AnnotationConfigApplicationContext, scanning for components
	 * in the given packages, registering bean definitions for those components,
	 * and automatically refreshing the context.
	 * @param basePackages the packages to scan for component classes
	 */
	public AnnotationConfigApplicationContext(String... basePackages) {
		this();
		scan(basePackages);
		refresh();
	}


	/**
	 * Propagate the given custom {@code Environment} to the underlying
	 * {@link AnnotatedBeanDefinitionReader} and {@link ClassPathBeanDefinitionScanner}.
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		super.setEnvironment(environment);
		this.reader.setEnvironment(environment);
		this.scanner.setEnvironment(environment);
	}

	/**
	 * Provide a custom {@link BeanNameGenerator} for use with {@link AnnotatedBeanDefinitionReader}
	 * and/or {@link ClassPathBeanDefinitionScanner}, if any.
	 * <p>Default is {@link AnnotationBeanNameGenerator}.
	 * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
	 * and/or {@link #scan(String...)}.
	 * @see AnnotatedBeanDefinitionReader#setBeanNameGenerator
	 * @see ClassPathBeanDefinitionScanner#setBeanNameGenerator
	 * @see AnnotationBeanNameGenerator
	 * @see FullyQualifiedAnnotationBeanNameGenerator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.reader.setBeanNameGenerator(beanNameGenerator);
		this.scanner.setBeanNameGenerator(beanNameGenerator);
		getBeanFactory().registerSingleton(
				AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
	}

	/**
	 * Set the {@link ScopeMetadataResolver} to use for registered component classes.
	 * <p>The default is an {@link AnnotationScopeMetadataResolver}.
	 * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
	 * and/or {@link #scan(String...)}.
	 */
	public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
		this.reader.setScopeMetadataResolver(scopeMetadataResolver);
		this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
	}


	//---------------------------------------------------------------------
	// Implementation of AnnotationConfigRegistry
	//---------------------------------------------------------------------

	/**
	 * <br>
	 * <h3>架构设计与源码解析：外观模式与委托机制</h3>
	 * <p>
	 * 在日常开发中，当我们使用纯注解方式启动 Spring 容器时，通常会写出这样的核心代码：
	 * </p>
	 * <pre>{@code
	 * AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
	 * context.register(AppConfig.class); // <--- 你正在看的方法！
	 * context.refresh();
	 * }</pre>
	 * <p>
	 * 这段代码虽然只有短短几行，但它完美地体现了面向对象设计中的<b>“外观模式 (Facade Pattern)”</b>。
	 * 容器本身不直接干活，而是提供一个极其简洁的 API，底层将复杂的解析任务<b>委托 (Delegate)</b>
	 * 给各个专业的内部组件（如 Reader 和 Scanner）。
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Register one or more component classes to be processed.
	 * <p>Note that {@link #refresh()} must be called in order for the context
	 * to fully process the new classes.
	 * @param componentClasses one or more component classes &mdash; for example,
	 * {@link Configuration @Configuration} classes
	 * @see #scan(String...)
	 * @see #refresh()
	 */
	@Override
	public void register(Class<?>... componentClasses) {
		/*
		 * 1. 防御性编程：断言校验 (Fail-Fast)
		 * ---------------------------------------------------------
		 * [原理解析] Assert 是 Spring 提供的内部断言工具类。在正式执行逻辑前，
		 * 先严格检查传入的 componentClasses (如 AppConfig.class) 是否为空。
		 *
		 * [设计意图] 如果为空，立刻抛出 IllegalArgumentException。这是一种经典的
		 * 快速失败 (Fail-Fast) 机制，防止程序带着错误或非法的状态继续向下执行，
		 * 从而避免在后续链路中引发更难排查的 Bug。
		 */
		Assert.notEmpty(componentClasses, "At least one component class must be specified");

		/*
		 * 2. 性能监控追踪 (Spring 5.3 核心新特性 ✨)
		 * ---------------------------------------------------------
		 * [背景知识] StartupStep 是 Spring 5.3 引入的 Application Startup 启动追踪机制。
		 * 随着微服务架构的普及，开发者对 Spring Boot / Spring 的启动速度要求越来越苛刻。
		 *
		 * [设计意图] 这段代码相当于给 Spring 的启动过程装上了“秒表”。它精准记录了“注册配置类”
		 * 这一步的耗时，并打上 tag 标签。配合 Java Flight Recorder (JFR) 等工具，
		 * 能够实现对启动性能瓶颈的毫秒级剖析。
		 */
		StartupStep registerComponentClass = getApplicationStartup().start("spring.context.component-classes.register")
				.tag("classes", () -> Arrays.toString(componentClasses));

		/*
		 * 3. 核心动作：委托“左膀”接管解析
		 * ---------------------------------------------------------
		 * [原理解析] 这是本方法中唯一真正执行业务逻辑的代码！AnnotationConfigApplicationContext
		 * 作为一个庞大的外观上下文，并不亲自解析配置类。它把这个复杂的脏活累活，
		 * 委托 (Delegate) 给了它的“左膀” —— this.reader (AnnotatedBeanDefinitionReader)。
		 *
		 * [串联上下文] 还记得我们最开始研究的那几位“核心大将”吗？
		 * 当 this.reader 被实例化的那一刻，Spring 就悄悄调用了底层的注册方法，
		 * 把那些“核心基础设施图纸”塞进了大管家的仓库里。现在，环境准备完毕，
		 * 终于轮到 Reader 来接管并解析我们传入的业务配置类了。
		 */
		this.reader.register(componentClasses);

		// 结束性能监控的“秒表”计时
		registerComponentClass.end();
	}

	/**
	 * Perform a scan within the specified base packages.
	 * <p>Note that {@link #refresh()} must be called in order for the context
	 * to fully process the new classes.
	 * @param basePackages the packages to scan for component classes
	 * @see #register(Class...)
	 * @see #refresh()
	 */
	@Override
	public void scan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		StartupStep scanPackages = getApplicationStartup().start("spring.context.base-packages.scan")
				.tag("packages", () -> Arrays.toString(basePackages));
		this.scanner.scan(basePackages);
		scanPackages.end();
	}


	//---------------------------------------------------------------------
	// Adapt superclass registerBean calls to AnnotatedBeanDefinitionReader
	//---------------------------------------------------------------------

	@Override
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
			@Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

		this.reader.registerBean(beanClass, beanName, supplier, customizers);
	}

}
