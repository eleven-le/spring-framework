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

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>注解配置上下文的"入口契约"——把 register + scan 两大能力抽象为接口标准！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.annotation.AnnotationConfigRegistry}</li>
 * <li><b>中文名</b>：注解配置注册表 —— 注解驱动容器的"两把钥匙"标准接口</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 annotation 包（注意！annotation 包 = 注解驱动编程模型的大本营！
 * 这里聚集了 Spring 注解体系的核心成员：注解定义（@Configuration、@Bean、@ComponentScan、@Import）、
 * 注解解析引擎（ConfigurationClassPostProcessor、ConfigurationClassParser）、
 * BD 读取/扫描器（AnnotatedBeanDefinitionReader、ClassPathBeanDefinitionScanner）、
 * 以及注解驱动上下文（AnnotationConfigApplicationContext）。
 * 一句话：<b>凡是和"用注解代替 XML"相关的，都在这个包里！</b>）</li>
 * <li><b>接口层级</b>：顶层独立接口，仅定义 <b>2 个方法</b>（register + scan）</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个接口？——"注解驱动的两条路"需要统一入口！</h3>
 * <p>在 Spring 的注解驱动体系中，Bean 的来源有<b>两条路径</b>：</p>
 * <ol>
 * <li><b>精确注册（register）</b>：手动指定一个或多个配置类，如 {@code context.register(AppConfig.class)}
 * —— 你明确告诉容器"就用这几个类"</li>
 * <li><b>包扫描（scan）</b>：指定一个基础包路径，如 {@code context.scan("com.example")}
 * —— 让容器自己去包路径下"扫荡"所有带 @Component 的类</li>
 * </ol>
 * <p>这两条路径在不同场景下各有优势，但它们都属于"告诉容器去哪找 Bean"这一核心动作。
 * AnnotationConfigRegistry 把这两个动作<b>提炼为接口契约</b>，让所有注解驱动的上下文实现类都遵循同一标准。</p>
 *
 * <h3>🧬 设计精髓——接口隔离原则（ISP）的教科书实践</h3>
 * <ol>
 * <li><b>只抽取"注解配置"相关的能力，不混入其他职责</b><br/>
 * Spring 的 ApplicationContext 继承了一大堆接口（BeanFactory、ResourceLoader、MessageSource、EventPublisher……），
 * 但"register + scan"这两个能力<b>只和注解驱动相关</b>，不是所有上下文都需要。<br/>
 * 所以 Spring 没有把它们塞进 ApplicationContext 或 ConfigurableApplicationContext，
 * 而是单独拆出 AnnotationConfigRegistry——<b>只有真正支持注解配置的上下文才实现这个接口</b>。<br/>
 * <b>业务借鉴</b>：当你的系统有多种"入口"（Web入口、RPC入口、MQ入口），不要把所有入口的方法定义在一个大接口里。
 * 每种入口拆出独立接口，各自的实现类按需组合。</li>
 *
 * <li><b>"两条路"的多态统一——不管你是 Standalone 还是 Web，注册方式一样</b><br/>
 * 注意这个接口有<b>两个实现者</b>：
 * <ul>
 * <li>{@link AnnotationConfigApplicationContext}：独立应用上下文（非 Web 环境、Spring Boot main 方法）</li>
 * <li>{@code AnnotationConfigWebApplicationContext}：Web 环境上下文（Servlet 容器、DispatcherServlet）</li>
 * </ul>
 * 虽然它们的继承链完全不同（一个继承 GenericApplicationContext，一个继承 AbstractRefreshableWebApplicationContext），
 * 但通过共同实现 AnnotationConfigRegistry，<b>上层代码可以用统一的 register/scan API 操作任意注解上下文</b>。<br/>
 * <b>业务借鉴</b>：当两个实现类的继承链不同但有共同能力时，抽一个小接口让它们"殊途同归"，
 * 而不是强行制造共同父类。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AnnotationConfigRegistry  ← 👈 你在这里！（独立的能力契约接口）
 * ├── AnnotationConfigApplicationContext      （独立应用：GenericApplicationContext + 本接口）
 * └── AnnotationConfigWebApplicationContext   （Web 应用：AbstractRefreshableWebApplicationContext + 本接口）
 *
 * 🔑 核心特征：
 * ① 不继承任何接口——它是一个"独立能力标准"，不属于 BeanFactory 或 ApplicationContext 体系
 * ② 只定义 2 个方法——极致精简，只聚焦"注解驱动的 Bean 来源注册"这一件事
 * ③ 连接两个不同继承链的实现——接口隔离 + 桥接的典范
 * </pre>
 *
 * <h3>🗂️ 二、方法清单·全局作战地图</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th><th>典型用法</th></tr>
 * <tr><td><b>register(Class...)</b></td><td>精确注册配置类/组件类</td><td>{@code ctx.register(AppConfig.class, DataConfig.class)}</td></tr>
 * <tr><td><b>scan(String...)</b></td><td>按包路径批量扫描组件</td><td>{@code ctx.scan("com.example.service", "com.example.dao")}</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AnnotationConfigRegistry 的核心价值：<b>用一个极简接口统一"注解配置"场景下的两种 Bean 来源注册方式</b>。<br/>
 * 它让 Standalone 和 Web 两种完全不同继承链的上下文，能够通过同一套 API 完成配置类注册和包扫描。<br/>
 * 这种"小接口、大桥梁"的设计思路，是接口隔离原则在 Spring 中最精炼的体现之一。</p>
 *
 * <hr/>
 * Common interface for annotation config application contexts,
 * defining {@link #register} and {@link #scan} methods.
 *
 * @author Juergen Hoeller
 * @since 4.1
 */
public interface AnnotationConfigRegistry {

	/**
	 * <h3>📌 方法 1：register —— 精确注册，"点名入册"</h3>
	 * <p><b>把一个或多个组件类（@Configuration、@Component 等）直接注册到容器中</b>。</p>
	 *
	 * <h4>💡 核心要点</h4>
	 * <ul>
	 * <li><b>幂等性保证</b>：重复注册同一个类不会产生副作用——Spring 会用类名生成 beanName，
	 * 后注册的会覆盖（或被忽略，取决于 allowBeanDefinitionOverriding 配置）</li>
	 * <li><b>调用时机</b>：必须在 {@code refresh()} 之前调用！refresh 之后容器已经"开工"了，
	 * 再注册新类不会被处理（除非手动再次 refresh，但 GenericApplicationContext 不允许二次 refresh）</li>
	 * <li><b>底层委托</b>：实际工作由 {@link AnnotatedBeanDefinitionReader#register(Class...)} 完成——
	 * Reader 会解析类上的注解元信息，生成 {@code AnnotatedGenericBeanDefinition}，
	 * 然后注册到 {@code BeanDefinitionRegistry}（即 DefaultListableBeanFactory）</li>
	 * </ul>
	 *
	 * <h4>🎯 典型使用场景</h4>
	 * <pre>{@code
	 * // 场景 1：Spring Boot 之前的纯注解启动
	 * AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
	 * ctx.register(AppConfig.class);     // 注册主配置类
	 * ctx.register(DataConfig.class);    // 再注册数据源配置类
	 * ctx.refresh();                      // 刷新容器，开始处理
	 *
	 * // 场景 2：直接通过构造器传入（内部也是调 register + refresh）
	 * new AnnotationConfigApplicationContext(AppConfig.class, DataConfig.class);
	 * }</pre>
	 *
	 * <hr/>
	 * Register one or more component classes to be processed.
	 * <p>Calls to {@code register} are idempotent; adding the same
	 * component class more than once has no additional effect.
	 * @param componentClasses one or more component classes,
	 * e.g. {@link Configuration @Configuration} classes
	 */
	void register(Class<?>... componentClasses);

	/**
	 * <h3>📌 方法 2：scan —— 包扫描，"地毯式搜索"</h3>
	 * <p><b>在指定的基础包路径下，扫描所有带有 @Component（及其衍生注解 @Service/@Repository/@Controller）的类，
	 * 自动注册为 BeanDefinition</b>。</p>
	 *
	 * <h4>💡 核心要点</h4>
	 * <ul>
	 * <li><b>扫描范围</b>：包括指定包及其所有子包——是递归扫描！</li>
	 * <li><b>底层委托</b>：实际工作由 {@link ClassPathBeanDefinitionScanner#scan(String...)} 完成——
	 * Scanner 利用 ASM 字节码技术读取类文件（不加载类到 JVM），性能极高</li>
	 * <li><b>与 @ComponentScan 的区别</b>：
	 * <ul>
	 * <li>这里的 scan() 是<b>编程式调用</b>——你在代码里手动调 {@code ctx.scan("com.example")}</li>
	 * <li>@ComponentScan 是<b>声明式配置</b>——写在配置类上，由 {@link ConfigurationClassPostProcessor}
	 * 在 refresh 过程中解析执行，它内部会 new 一个全新的 Scanner，和这里的 this.scanner 完全无关！</li>
	 * </ul></li>
	 * <li><b>调用时机</b>：同 register，必须在 refresh() 之前调用</li>
	 * </ul>
	 *
	 * <h4>🎯 典型使用场景</h4>
	 * <pre>{@code
	 * // 场景 1：编程式包扫描启动
	 * AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
	 * ctx.scan("com.example.service", "com.example.dao");
	 * ctx.refresh();
	 *
	 * // 场景 2：直接通过构造器传入包路径
	 * new AnnotationConfigApplicationContext("com.example.service", "com.example.dao");
	 * }</pre>
	 *
	 * <hr/>
	 * Perform a scan within the specified base packages.
	 * @param basePackages the packages to scan for component classes
	 */
	void scan(String... basePackages);

}
