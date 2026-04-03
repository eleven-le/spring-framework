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
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>现代 Spring 的"一键启动器"——注解驱动时代的容器入口！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.annotation.AnnotationConfigApplicationContext}</li>
 * <li><b>中文名</b>：基于注解配置的应用上下文 —— 注解驱动容器的"总开关"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 annotation 包（注意！annotation 包 = 注解驱动编程模型的大本营！
 * 这个包聚集了注解体系的所有核心成员：注解定义（@Configuration、@Bean、@ComponentScan、@Import）、
 * 注解解析引擎（ConfigurationClassPostProcessor、ConfigurationClassParser）、
 * BD 读取/扫描器（AnnotatedBeanDefinitionReader、ClassPathBeanDefinitionScanner）、
 * 以及本类——注解驱动上下文入口。
 * 一句话：<b>凡是和"用注解代替 XML"相关的，都在这个包里！</b>
 * 对比：context 包定义接口契约（ApplicationContext），support 包提供骨架实现（AbstractApplicationContext、GenericApplicationContext），
 * 而 annotation 包则是"注解驱动的最后一公里"——在骨架之上叠加注解解析能力，交付给开发者直接使用。）</li>
 * <li><b>类层级</b>：{@code GenericApplicationContext} 的直系子类 + 实现 {@link AnnotationConfigRegistry}，
 * 增加了<b>Reader（精确注册）+ Scanner（包扫描）两大武器</b></li>
 * </ul>
 *
 * <h3>💡 为什么需要 AnnotationConfigApplicationContext？——"注解驱动"需要一个专属入口！</h3>
 * <p>回顾 Spring 容器的进化史：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>时代</th><th>入口类</th><th>Bean 来源</th></tr>
 * <tr><td>XML 时代</td><td>ClassPathXmlApplicationContext</td><td>XML 文件中的 {@code <bean>} 标签</td></tr>
 * <tr><td>注解时代</td><td><b>AnnotationConfigApplicationContext</b> ← 👈 你在这里！</td><td>@Configuration 类 + @ComponentScan 包扫描</td></tr>
 * <tr><td>Spring Boot</td><td>SpringApplication.run()</td><td>底层仍然是本类（或其 Web 变体）</td></tr>
 * </table>
 * <p>GenericApplicationContext 只是一个"通用底座"——它持有 DefaultListableBeanFactory、支持 BeanDefinition 注册，
 * 但<b>不知道怎么从注解中提取 Bean 定义</b>。<br/>
 * AnnotationConfigApplicationContext 在这个底座上装上了两大武器：</p>
 * <ul>
 * <li><b>左膀 Reader（AnnotatedBeanDefinitionReader）</b>：接收具体的 Class 对象，解析注解元信息，生成 BD 并注册</li>
 * <li><b>右臂 Scanner（ClassPathBeanDefinitionScanner）</b>：接收包路径，用 ASM 扫描 class 文件，找到 @Component 组件并注册</li>
 * </ul>
 * <p>有了这两个武器，容器就能"看懂注解"了。这就是本类存在的根本价值。</p>
 *
 * <h3>🧬 设计精髓——外观模式 + 组合委托，你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>外观模式（Facade）——一个入口，极简 API</b><br/>
 * 开发者只需要 {@code new AnnotationConfigApplicationContext(AppConfig.class)} 一行代码，
 * 容器就完成了：创建工厂 → 注册内置处理器 → 注册配置类 → refresh 全链路。<br/>
 * 但底层实际涉及：GenericApplicationContext（工厂持有）、AnnotatedBeanDefinitionReader（BD 解析）、
 * AnnotationConfigUtils（内置处理器注册）、AbstractApplicationContext.refresh()（12 大步骤）等多个组件协作。<br/>
 * 本类作为 Facade，把这些复杂性全部隐藏在极简 API 之后。<br/>
 * <b>业务借鉴</b>：当你的系统有复杂的初始化链路时（连接池 + 缓存 + MQ + 配置中心），
 * 提供一个 Facade 类让调用者"一行启动"，底层再委托各专业组件。</li>
 *
 * <li><b>组合优于继承——Reader 和 Scanner 是"持有"而非"继承"</b><br/>
 * 本类没有继承 AnnotatedBeanDefinitionReader，也没有继承 ClassPathBeanDefinitionScanner，
 * 而是以<b>成员变量（组合）</b>的方式持有它们。<br/>
 * 这使得 Reader 和 Scanner 可以独立演化、独立替换，互不影响。
 * 如果用继承，Java 单继承的限制会让设计陷入死胡同。<br/>
 * <b>业务借鉴</b>：当一个类需要同时具备多种能力时，不要试图通过继承链叠加，
 * 而是把各种能力封装成独立组件，通过组合持有。</li>
 *
 * <li><b>构造器即启动协议——"三步走"标准流程</b><br/>
 * 核心构造器 {@code AnnotationConfigApplicationContext(Class<?>... componentClasses)} 内部执行了标准的三步：<br/>
 * ① {@code this()} → 初始化 Reader + Scanner + 注册内置处理器<br/>
 * ② {@code register(componentClasses)} → 把用户配置类注册为 BD<br/>
 * ③ {@code refresh()} → 启动容器的 12 大步骤<br/>
 * 这是 Spring 容器启动的<b>最小完整链路</b>——理解了这三步，就理解了 Spring 启动的核心骨架。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AbstractApplicationContext                     （模板骨架：refresh 12 大步骤）
 * └── GenericApplicationContext                   （通用底座：内置 DLBF + 一次性 refresh 保护）
 *       │    ↑ implements BeanDefinitionRegistry
 *       └── AnnotationConfigApplicationContext    ← 👈 你在这里！（注解入口：Reader + Scanner）
 *                ↑ implements AnnotationConfigRegistry
 *
 * 平行对比：
 * ┌─ AnnotationConfigApplicationContext（独立应用，继承 Generic，一次性 refresh）
 * └─ AnnotationConfigWebApplicationContext（Web 应用，继承 AbstractRefreshable，可多次 refresh）
 *    两者都实现 AnnotationConfigRegistry，共享 register/scan 契约
 * </pre>
 *
 * <h3>🗂️ 二、核心成员·全局作战地图</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>成员</th><th>类型</th><th>职责</th></tr>
 * <tr><td><b>reader</b></td><td>{@link AnnotatedBeanDefinitionReader}</td><td>"左膀"——精确注册配置类，解析 @Configuration/@Component 等注解生成 BD</td></tr>
 * <tr><td><b>scanner</b></td><td>{@link ClassPathBeanDefinitionScanner}</td><td>"右臂"——包路径扫描，用 ASM 找到所有 @Component 组件</td></tr>
 * </table>
 *
 * <h3>📌 方法速查</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>来源</th><th>使命</th></tr>
 * <tr><td>{@code register(Class...)}</td><td>AnnotationConfigRegistry</td><td>委托 reader 精确注册配置类</td></tr>
 * <tr><td>{@code scan(String...)}</td><td>AnnotationConfigRegistry</td><td>委托 scanner 扫描指定包</td></tr>
 * <tr><td>{@code setBeanNameGenerator(...)}</td><td>本类</td><td>自定义 Bean 命名策略，同步设置给 reader + scanner</td></tr>
 * <tr><td>{@code setScopeMetadataResolver(...)}</td><td>本类</td><td>自定义作用域解析策略</td></tr>
 * <tr><td>{@code setEnvironment(...)}</td><td>重写父类</td><td>环境配置三方同步（super + reader + scanner）</td></tr>
 * <tr><td>{@code registerBean(...)}</td><td>重写父类</td><td>适配 GenericApplicationContext 的 registerBean 到 reader</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AnnotationConfigApplicationContext 的核心价值：<b>在 GenericApplicationContext 的通用底座之上，
 * 装配了 Reader + Scanner 两大注解解析武器，成为注解驱动时代的标准容器入口</b>。<br/>
 * 它用外观模式封装了"初始化 → 注册 → 刷新"三步走的启动协议，
 * 让开发者只需一行代码就能启动一个功能完整的 Spring 容器。<br/>
 * 无论是独立应用还是 Spring Boot，底层都绕不开这个类。掌握它，就掌握了 Spring 启动的起点。</p>
 *
 * <hr/>
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

	/* =======================================================================================================
	          📦 核心成员：Reader + Scanner —— 注解驱动的"左膀右臂"
	   ======================================================================================================= */

	/**
	 * <h3>📌 左膀：注解 Bean 定义读取器</h3>
	 * <p><b>职责</b>：接收具体的 Class 对象（如 AppConfig.class），解析类上的注解元信息
	 * （@Configuration、@Component、@Scope、@Lazy、@Conditional 等），
	 * 生成 {@code AnnotatedGenericBeanDefinition}，注册到 BeanDefinitionRegistry。</p>
	 * <p><b>隐藏的大招</b>：Reader 在构造时会调用
	 * {@link AnnotationConfigUtils#registerAnnotationConfigProcessors(org.springframework.beans.factory.support.BeanDefinitionRegistry)}，
	 * 向容器注册 6 大内置处理器（ConfigurationClassPostProcessor、AutowiredAnnotationBeanPostProcessor 等）。
	 * 这意味着：<b>仅仅创建 Reader，Spring 的注解解析能力就已经就绪了！</b></p>
	 */
	private final AnnotatedBeanDefinitionReader reader;

	/**
	 * <h3>📌 右臂：类路径 Bean 定义扫描器</h3>
	 * <p><b>职责</b>：接收包路径字符串（如 "com.example"），利用 ASM 字节码技术扫描 class 文件，
	 * 找到所有带 @Component（及衍生注解）的类，生成 {@code ScannedGenericBeanDefinition} 并注册。</p>
	 * <p><b>高频面试考点</b>：这个 scanner 是留给<b>编程式调用</b> {@code ctx.scan("...")} 的！<br/>
	 * 你在配置类上写的 @ComponentScan，由 {@link ConfigurationClassPostProcessor} 在 refresh 中
	 * 内部 new 一个全新的 Scanner 来处理——和这里的 this.scanner 完全无关！</p>
	 */
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
		 * [作用] 读取被 @Component、@Configuration 等注解修饰的类，并将它们转换为 BeanDefinition。
		 * [内幕] 创建 reader 时，Spring 会隐式向容器注册几个非常核心的内部后置处理器 (PostProcessor)。例如大名鼎鼎的 ConfigurationClassPostProcessor (专门解析 @Configuration 和 @Bean)。
		 * [比喻] 如果把 Spring 容器比作即将开工的建筑工地，你自己的业务 Bean 就是砖块。那么 reader 的初始化，就是在砖块进场前，秘密招募“包工头”和“重型机械”等内部员工。
		 * [源码追踪] 追踪 new AnnotatedBeanDefinitionReader(this) 的底层，你会发现它不仅仅实例化了自己，更核心的是调用了 AnnotationConfigUtils.registerAnnotationConfigProcessors(...)工具方法来完成上述的“招募”动作，这个工具方法的名字已经出卖了它的意图：注册注解配置相关的处理器。
		 */
		this.reader = new AnnotatedBeanDefinitionReader(this);
		createAnnotatedBeanDefReader.end();

		/*
		 * 2. 初始化类路径 Bean 定义扫描器 (Scanner) —— “右臂（Scanner，负责包扫描）”
		 * [核心职责] 充当 Spring 的“超级雷达”。 顾名思义，它负责去指定的包路径下，把所有带有 @Component、@Service、@Controller 等注解的类全部找出来，转换成 BeanDefinition（图纸），并最终注册到容器里。
		 * [参数解析] 为什么传入 this？(当前的 AnnotationConfigApplicationContext 实例)
		 * ① 原理解析：当前上下文本身实现了 BeanDefinitionRegistry（图纸注册表）接口。
		 * ② 设计意图：扫描器就像是在外围侦察的“侦察兵”，找到带有注解的类后，总得有个地方交回情报。
		 * 把 this 传给它，就是明确告诉它：“把你扫描到的所有图纸，都存到我这个大管家的仓库（ConcurrentHashMap）里！”
		 *
		 * 💡 [高频面试/源码避坑指南]：@ComponentScan 到底是谁扫的？ 这是一个极易混淆的盲点，也是展现底层技术深度的关键！
		 * ❌ 误区：平时写在配置类上的 @ComponentScan("com.xxx")，底层是由这里的this.scanner 执行扫描的。
		 * ✅ 真相：这里的 this.scanner 纯粹是留给“编程式”调用的。它主要是为了让你在 main 方法里手动执行 context.scan("com.xxx") 时使用的。
		 * 你写在配置类上的 @ComponentScan 注解，其真正的解析者是咱们之前聊过的【元老 1】 —— ConfigurationClassPostProcessor！它会在后续的生命周期中，自己内部偷偷 new 一个全新的 ClassPathBeanDefinitionScanner 来完成扫描动作。
		 *
		 *  Spring 在这里提前 new 出一个 scanner 放在上下文中，更多是为了提供丰富的 API 支持，保证上下文对外提供 scan() 能力的完整性。
		 */
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	/**
	 * <h3>📌 构造器 2：自定义工厂版——"你自己带工厂来，我帮你装 Reader + Scanner"</h3>
	 * <p><b>使用场景</b>：当你需要对底层 BeanFactory 做特殊配置时（比如关闭 BD 覆盖、设置自定义 TypeConverter），
	 * 先手动创建一个 DefaultListableBeanFactory 做好配置，再传给本构造器。</p>
	 * <pre>{@code
	 * DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
	 * bf.setAllowBeanDefinitionOverriding(false); // 禁止 BD 覆盖
	 * AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(bf);
	 * ctx.register(AppConfig.class);
	 * ctx.refresh();
	 * }</pre>
	 * <p><b>注意</b>：调用 {@code super(beanFactory)} 会把你传入的工厂替换掉父类默认创建的工厂，
	 * 后续的 Reader 和 Scanner 都会往你的自定义工厂里注册 BD。</p>
	 *
	 * <hr/>
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
		 * [核心动作] 调用无参构造函数 this()。
		 * [原理解析] 在这里，Spring 默默完成了两项核心工作： 一是创建了专门读取注解的  Reader 和扫描包的 Scanner；二是向容器中注册了维持框架运转的基础设施 Bean （如处理 @Configuration 和 @Autowired 的内部后置处理器）。
		 * [形象比喻] 相当于在建筑工地正式开工前，先把“包工头”和“重型机械”安排进场。
		 */
		this();
		/*
		 * 2. 注册主配置类 (图纸入库)
		 * [核心动作] 将传入的配置类（例如 AppConfig.class）解析为 BeanDefinition。
		 * [原理解析] Spring 容器目前虽然有了基础组件，但还不知道业务逻辑在哪里。 这一步就是把主配置类注册进大管家的仓库（DefaultListableBeanFactory）中。
		 * [形象比喻] 这个配置类就像是一张“总设计图纸”，它指引着 Spring 接下来去哪里 寻找其他的业务 Bean（比如通过 @ComponentScan 指示的扫描路径）。
		 */
		register(componentClasses);
		/*
		 * 3. 刷新与启动容器 (万物生长的引擎)
		 * [核心动作] 执行 Spring 源码中最核心、最复杂、也最重要的方法 —— refresh()。
		 * [原理解析] 之前的步骤都只是在做“纸上谈兵”（处理 BeanDefinition 图纸）。 只有调用了 refresh()，Spring 才会真正开始根据图纸去实例化所有的单例 Bean，完成依赖注入（DI），并织入切面逻辑（AOP）等高级功能。
		 * [高能预警] 这是整个 Spring 框架的“心脏跳动”时刻。只要彻底掌握了 refresh() 内部的执行主干，Spring 的底层原理基本就通透了！
		 */
		refresh();
	}

	/**
	 * <h3>📌 构造器 4：包路径启动版——"给我包名，我自己去扫"</h3>
	 * <p>和构造器 3 的"三步走"一模一样，区别仅在第二步：用 scan 代替 register。</p>
	 * <p><b>底层链路</b>：this() → scan(basePackages) → refresh()</p>
	 * <p><b>使用场景</b>：当你不想写配置类，直接让 Spring 扫描整个包时：</p>
	 * <pre>{@code
	 * new AnnotationConfigApplicationContext("com.example.service", "com.example.dao");
	 * }</pre>
	 * <p><b>注意</b>：此时没有显式的 @Configuration 类，所有 Bean 都来自包扫描。
	 * 如果包下有 @Configuration 类，它们也会被扫描到，后续 refresh 中
	 * ConfigurationClassPostProcessor 会进一步处理其中的 @Bean 方法和 @Import 等。</p>
	 *
	 * <hr/>
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


	/* =======================================================================================================
	          ⚙️ 配置方法区：Environment / BeanNameGenerator / ScopeMetadataResolver
	          设计要点：任何配置变更都"三方同步"（super + reader + scanner），保证一致性！
	   ======================================================================================================= */

	/**
	 * <h3>⚙️ setEnvironment —— 环境配置三方同步</h3>
	 * <p><b>为什么要重写？</b>因为本类持有 reader 和 scanner 两个组件，它们内部也各自持有 Environment 引用。
	 * 如果只调 super.setEnvironment()，reader 和 scanner 还在用旧的 Environment——三者不一致会导致
	 * @Profile、@Conditional 等环境相关判断出现诡异 Bug。</p>
	 * <p><b>设计模式</b>：这是"观察者同步"的手动版——当核心状态变更时，主动通知所有持有该状态的组件。</p>
	 *
	 * <hr/>
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
	 * <h3>⚙️ setBeanNameGenerator —— 自定义 Bean 命名策略</h3>
	 * <p><b>默认策略</b>：{@link AnnotationBeanNameGenerator}——取类名首字母小写（如 {@code UserService → userService}）。</p>
	 * <p><b>常见替换场景</b>：多模块项目中不同模块有同名类（如 order 和 pay 模块都有 OrderService），
	 * 可以换成 {@link FullyQualifiedAnnotationBeanNameGenerator}，用全限定名作为 beanName 避免冲突。</p>
	 * <p><b>注意三点</b>：</p>
	 * <ol>
	 * <li>必须在 register/scan <b>之前</b>调用，否则先注册的 BD 还是用旧命名策略</li>
	 * <li>同时设置给 reader + scanner——保证两条路径产生的 beanName 策略一致</li>
	 * <li>还要把自定义生成器注册为单例 Bean——因为后续 ConfigurationClassPostProcessor
	 * 在处理 @ComponentScan 时，会从容器中查找这个命名生成器，用于内部新建 Scanner 的命名策略</li>
	 * </ol>
	 *
	 * <hr/>
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
		/*
		 * 关键细节：把命名生成器注册为单例 Bean！
		 * 为什么？因为后续 ConfigurationClassPostProcessor 处理 @ComponentScan 时，
		 * 会从容器中 getBean 获取这个命名生成器，传给它内部 new 出来的 Scanner。
		 * 如果不注册这个单例，CCPP 内部的 Scanner 就用默认策略，导致"编程式设置的命名策略"和
		 * "@ComponentScan 扫描出来的 BD 命名策略"不一致——又一个三方同步的细节！
		 */
		getBeanFactory().registerSingleton(
				AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
	}

	/**
	 * <h3>⚙️ setScopeMetadataResolver —— 自定义作用域解析策略</h3>
	 * <p><b>默认策略</b>：{@link AnnotationScopeMetadataResolver}——从 @Scope 注解读取作用域（singleton/prototype/request/session）。</p>
	 * <p><b>替换场景</b>：JSR-330 环境下可换成 {@link Jsr330ScopeMetadataResolver}，从 javax.inject.Scope 读取。</p>
	 * <p>同 setBeanNameGenerator，必须在 register/scan 之前调用，且同步设置给 reader + scanner。</p>
	 *
	 * <hr/>
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
		 * [原理解析] Assert 是 Spring 提供的内部断言工具类。在正式执行逻辑前， 先严格检查传入的 componentClasses (如 AppConfig.class) 是否为空。
		 * [设计意图] 如果为空，立刻抛出 IllegalArgumentException。这是一种经典的快速失败 (Fail-Fast) 机制，防止程序带着错误或非法的状态继续向下执行， 从而避免在后续链路中引发更难排查的 Bug。
		 */
		Assert.notEmpty(componentClasses, "At least one component class must be specified");

		/*
		 * 2. 性能监控追踪 (Spring 5.3 核心新特性 ✨)
		 * [背景知识] StartupStep 是 Spring 5.3 引入的 Application Startup 启动追踪机制。随着微服务架构的普及，开发者对 Spring Boot / Spring 的启动速度要求越来越苛刻。
		 * [设计意图] 这段代码相当于给 Spring 的启动过程装上了“秒表”。它精准记录了“注册配置类”这一步的耗时，并打上 tag 标签。配合 Java Flight Recorder (JFR) 等工具，能够实现对启动性能瓶颈的毫秒级剖析。
		 */
		StartupStep registerComponentClass = getApplicationStartup().start("spring.context.component-classes.register")
				.tag("classes", () -> Arrays.toString(componentClasses));

		/*
		 * 3. 核心动作：委托“左膀”接管解析
		 * [原理解析] 这是本方法中唯一真正执行业务逻辑的代码！AnnotationConfigApplicationContext 作为一个庞大的外观上下文，并不亲自解析配置类。它把这个复杂的脏活累活，委托 (Delegate)  给了它的“左膀” —— this.reader (AnnotatedBeanDefinitionReader)。
		 * [串联上下文] 还记得我们最开始研究的那几位“核心大将”吗？当 this.reader 被实例化的那一刻，Spring 就悄悄调用了底层的注册方法， 把那些“核心基础设施图纸”塞进了大管家的仓库里。现在，环境准备完毕，终于轮到 Reader 来接管并解析我们传入的业务配置类了。
		 */
		this.reader.register(componentClasses);
		// 结束性能监控的“秒表”计时
		registerComponentClass.end();
	}

	/**
	 * <h3>📌 scan —— 委托"右臂"执行包扫描</h3>
	 * <p>和 register 方法的套路完全一致：断言校验 → 启动计时 → 委托 scanner → 结束计时。</p>
	 * <p><b>底层链路</b>：{@code this.scanner.scan(basePackages)} →
	 * {@code ClassPathBeanDefinitionScanner.doScan(basePackages)} →
	 * 对每个包执行 ASM 扫描 → 生成 ScannedGenericBeanDefinition → 注册到 Registry</p>
	 *
	 * <h4>⚠️ 再次强调 @ComponentScan 的区别</h4>
	 * <p>这里的 scan 方法是<b>编程式调用</b>入口。写在配置类上的 @ComponentScan
	 * 是由 ConfigurationClassPostProcessor 在 refresh 过程中用自己 new 的 Scanner 来处理的。
	 * 两条路径互不干扰！</p>
	 *
	 * <hr/>
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


	/* =======================================================================================================
	          🔄 适配方法区：把父类 GenericApplicationContext 的 registerBean 桥接到 Reader
	   ======================================================================================================= */

	/**
	 * <h3>🔄 registerBean —— 适配器桥接：父类 API → Reader 实现</h3>
	 * <p>{@link GenericApplicationContext} 定义了编程式注册 Bean 的方法 {@code registerBean(name, class, supplier, customizers)}。
	 * 本类重写它，将调用<b>桥接到 AnnotatedBeanDefinitionReader</b>，
	 * 这样通过 reader 注册的 BD 会自动携带注解元信息（@Scope、@Lazy、@Primary 等），
	 * 而不是生成一个"裸"的 RootBeanDefinition。</p>
	 *
	 * <p><b>设计模式</b>：适配器模式（Adapter）——父类定义了接口签名，本类用自己的组件（reader）来适配实现。</p>
	 *
	 * <p><b>典型使用场景</b>：Spring 5.0+ 的函数式注册 API：</p>
	 * <pre>{@code
	 * ctx.registerBean("myService", MyService.class, () -> new MyService("custom-arg"));
	 * ctx.registerBean(MyService.class, bd -> bd.setLazyInit(true)); // customizer
	 * }</pre>
	 */
	@Override
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
			@Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

		this.reader.registerBean(beanClass, beanName, supplier, customizers);
	}

}
