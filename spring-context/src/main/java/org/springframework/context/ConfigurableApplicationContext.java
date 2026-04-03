/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.context;

import java.io.Closeable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>ApplicationContext 的"控制面板"——只有启动/关闭代码才能碰的 SPI 接口！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.ConfigurableApplicationContext}</li>
 * <li><b>中文名</b>：可配置应用上下文 —— 容器的"管理后台/控制面板"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块</li>
 * <li><b>接口层级</b>：继承 {@code ApplicationContext}（只读门面）+ {@code Lifecycle}（启停协议）+ {@code Closeable}（资源释放）</li>
 * <li><b>方法数量</b>：<b>8 个常量 + 15 个方法</b>（涵盖配置、生命周期、工厂访问三大战区）</li>
 * <li><b>核心定位</b>：<b>SPI 接口</b>——面向框架开发者和容器启动/关闭代码，而非普通业务代码</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个接口？——"只读使用"和"管理配置"必须分离！</h3>
 * <p>ApplicationContext 是容器的"只读门面"——业务代码只能查 Bean、发事件、读资源。<br/>
 * 但容器的启动、配置、关闭需要一组"写操作"：设父容器、加后处理器、refresh、close……<br/>
 * 如果把这些"写操作"也放在 ApplicationContext 上，每个注入了 ApplicationContext 的 Service
 * 都能调 {@code refresh()} 重启容器——太危险了！</p>
 * <p>所以 Spring 用接口隔离（ISP）把它们拆到了 ConfigurableApplicationContext：</p>
 * <ul>
 * <li><b>ApplicationContext</b>：面向使用者——"你能查什么、发什么"（只读）</li>
 * <li><b>ConfigurableApplicationContext</b>：面向管理者——"你能配什么、启停什么"（读写）</li>
 * </ul>
 * <p>普通 Service 注入的是 ApplicationContext，碰不到 refresh/close。<br/>
 * 只有 main 方法、测试框架、Boot 启动器等"管理代码"才会向下转型使用 ConfigurableApplicationContext。</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"只读门面 vs 管理后台"的接口分层</b><br/>
 * BeanFactory 体系也有同样的拆法：BeanFactory（只读）→ ConfigurableBeanFactory（管理）。<br/>
 * <b>业务借鉴</b>：设计配置中心时，给业务代码暴露 {@code ConfigReader}（只读），
 * 给运维工具暴露 {@code ConfigManager extends ConfigReader}（读写）。
 * 用接口层级控制权限，而不是在每个方法里加 if 判断。</li>
 *
 * <li><b>"先配置、再启动、再使用、最后关闭"的生命周期协议</b><br/>
 * ConfigurableApplicationContext 的方法隐含了一个严格的时序约束：<br/>
 * {@code setXxx()} 系列 → {@code addXxx()} 系列 → {@code refresh()} → 使用期 → {@code close()}<br/>
 * refresh 之前是"配置窗口"，refresh 之后是"运行窗口"，close 之后是"死亡窗口"——<br/>
 * 在错误的窗口调用错误的方法会抛 {@code IllegalStateException}。<br/>
 * <b>业务借鉴</b>：设计有生命周期的组件（连接池、缓存、任务调度器）时，
 * 明确定义"配置期/运行期/关闭期"，在接口层面约束调用时序，而不是靠文档说"请不要在运行期改配置"。</li>
 *
 * <li><b>refresh() 的核心地位——"一键启动"的模板方法入口</b><br/>
 * refresh() 是整个 Spring 容器的"总开关"——内部执行 12 大步骤（从创建 BeanFactory 到发布 ContextRefreshedEvent）。<br/>
 * 它定义在接口上，实现在 {@code AbstractApplicationContext.refresh()} 中，是整个 Spring 最重要的方法之一。<br/>
 * <b>业务借鉴</b>：复杂系统的"一键初始化"入口应该是一个模板方法，内部拆分为可复写的步骤，
 * 子类按需定制某些步骤而不需要重写整个启动流程。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * ApplicationContext（只读门面：getBean/getEnvironment/publishEvent/getResource...）
 * ├── ApplicationEventPublisher（发事件）
 * ├── ListableBeanFactory（枚举 Bean）
 * ├── HierarchicalBeanFactory（父子层级）
 * ├── ResourcePatternResolver（资源加载）
 * └── MessageSource（国际化）
 *
 * ConfigurableApplicationContext  ← 👈 你在这里！在 ApplicationContext 之上增加"管理/配置"能力
 * ├── extends ApplicationContext（继承只读能力）
 * ├── extends Lifecycle（start/stop/isRunning）
 * ├── extends Closeable（close 资源释放）
 * │
 * └── AbstractApplicationContext（核心实现——refresh/close/publishEvent 都在这里）
 *       ├── GenericApplicationContext
 *       │     └── AnnotationConfigApplicationContext（注解驱动，SpringBoot 默认）
 *       └── AbstractRefreshableApplicationContext
 *             └── ClassPathXmlApplicationContext（XML 驱动，传统方式）
 * </pre>
 *
 * <h3>🏭 核心设计思想：SPI 分层 + 生命周期管控 + 配置窗口期</h3>
 * <ol>
 * <li><b>SPI 分层</b>：{@code ApplicationContext} 面向使用者，{@code ConfigurableApplicationContext} 面向框架/管理者</li>
 * <li><b>生命周期管控</b>：{@code refresh()} 启动 → {@code isActive()} 运行中 → {@code close()} 关闭，三态切换</li>
 * <li><b>配置窗口期</b>：{@code setXxx/addXxx} 系列方法必须在 {@code refresh()} 之前调用，refresh 之后配置窗口关闭</li>
 * </ol>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>15 个方法 + 8 个常量分为 <b>5 个战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>方法/常量</th><th>使命</th></tr>
 * <tr><td rowspan="8">📋 常量——知名 Bean 名称</td>
 *     <td>{@code CONFIG_LOCATION_DELIMITERS}</td><td>配置路径分隔符 ",; \t\n"</td></tr>
 * <tr><td>{@code CONVERSION_SERVICE_BEAN_NAME}</td><td>类型转换服务</td></tr>
 * <tr><td>{@code LOAD_TIME_WEAVER_BEAN_NAME}</td><td>加载时织入器</td></tr>
 * <tr><td>{@code ENVIRONMENT_BEAN_NAME}</td><td>环境对象</td></tr>
 * <tr><td>{@code SYSTEM_PROPERTIES_BEAN_NAME}</td><td>系统属性</td></tr>
 * <tr><td>{@code SYSTEM_ENVIRONMENT_BEAN_NAME}</td><td>系统环境变量</td></tr>
 * <tr><td>{@code APPLICATION_STARTUP_BEAN_NAME}</td><td>启动指标记录器</td></tr>
 * <tr><td>{@code SHUTDOWN_HOOK_THREAD_NAME}</td><td>关闭钩子线程名</td></tr>
 * <tr><td rowspan="5">⚙️ 配置期——refresh 前的配置窗口</td>
 *     <td>{@code setId(id)}</td><td>设置上下文唯一 ID</td></tr>
 * <tr><td>{@code setParent(parent)}</td><td>设置父上下文</td></tr>
 * <tr><td>{@code setEnvironment(env)}</td><td>设置环境对象</td></tr>
 * <tr><td>{@code setApplicationStartup(startup)}</td><td>设置启动指标记录器</td></tr>
 * <tr><td>{@code setClassLoader(cl)}</td><td>设置类加载器</td></tr>
 * <tr><td rowspan="3">➕ 注册期——添加处理器/监听者/协议解析器</td>
 *     <td>{@code addBeanFactoryPostProcessor(bfpp)}</td><td>添加工厂后处理器</td></tr>
 * <tr><td>{@code addApplicationListener(listener)}</td><td>添加事件监听者</td></tr>
 * <tr><td>{@code addProtocolResolver(resolver)}</td><td>添加资源协议解析器</td></tr>
 * <tr><td rowspan="3">🔄 生命周期——启停控制</td>
 *     <td>{@code refresh()}</td><td>🔥 核心！加载/刷新容器（12 大步骤）</td></tr>
 * <tr><td>{@code registerShutdownHook()}</td><td>注册 JVM 关闭钩子</td></tr>
 * <tr><td>{@code close()}</td><td>关闭容器，释放资源</td></tr>
 * <tr><td rowspan="3">🔍 运行期——状态查询</td>
 *     <td>{@code isActive()}</td><td>容器是否处于活跃状态</td></tr>
 * <tr><td>{@code getEnvironment()}</td><td>获取可配置环境（覆盖父接口返回类型）</td></tr>
 * <tr><td>{@code getBeanFactory()}</td><td>获取内部 BeanFactory 引用</td></tr>
 * </table>
 *
 * <h3>🧠 三、架构师透视·现实应用场景</h3>
 * <ul>
 * <li><b>SpringBoot 启动入口</b>：{@code SpringApplication.run()} 内部创建 ConfigurableApplicationContext，
 * 调用一系列 setXxx/addXxx 后执行 refresh()，最后 registerShutdownHook()</li>
 * <li><b>测试框架</b>：{@code @SpringBootTest} 通过 ConfigurableApplicationContext 控制测试容器的生命周期</li>
 * <li><b>优雅停机</b>：{@code registerShutdownHook()} 确保 JVM 关闭时容器能执行 close()，
 * 依次触发 {@code @PreDestroy}、{@code DisposableBean.destroy()}、{@code SmartLifecycle.stop()}</li>
 * <li><b>getBeanFactory() 的陷阱</b>：refresh 之前调用会抛 IllegalStateException，close 之后也是——
 * 只有 isActive()==true 期间才安全</li>
 * </ul>
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>ConfigurableApplicationContext 是 ApplicationContext 的"管理面"——<br/>
 * 如果说 ApplicationContext 是"飞机的客舱"（乘客只能看窗外、按呼叫铃），<br/>
 * 那 ConfigurableApplicationContext 就是"驾驶舱"（飞行员控制起飞、降落、引擎参数）。<br/>
 * 它用<b>接口隔离</b>确保"乘客"碰不到"驾驶杆"，<br/>
 * 用<b>生命周期时序约束</b>确保不会"在空中拆引擎"，<br/>
 * 而 {@code refresh()} 则是整个 Spring 宇宙的<b>"大爆炸按钮"</b>——按下去，一切就绪。</p>
 *
 * <hr/>
 * SPI interface to be implemented by most if not all application contexts.
 * Provides facilities to configure an application context in addition
 * to the application context client methods in the
 * {@link org.springframework.context.ApplicationContext} interface.
 *
 * <p>Configuration and lifecycle methods are encapsulated here to avoid
 * making them obvious to ApplicationContext client code. The present
 * methods should only be used by startup and shutdown code.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Sam Brannen
 * @since 03.11.2003
 */
public interface ConfigurableApplicationContext extends ApplicationContext, Lifecycle, Closeable {

	/**
	 * <h3>📋 常量 1：CONFIG_LOCATION_DELIMITERS —— 配置路径分隔符</h3>
	 * <p><b>🏭【多个配置文件路径之间的"分隔号"——逗号/分号/空格/换行都行】</b></p>
	 * <p>当你在 XML 时代用 {@code contextConfigLocation} 指定多个配置文件时：<br/>
	 * {@code "classpath:app.xml, classpath:db.xml"} —— 这里的逗号就由这个常量定义。<br/>
	 * 支持 {@code ,}、{@code ;}、空格、Tab、换行作为分隔符。注解时代基本用不到了。</p>
	 * <hr/>
	 * Any number of these characters are considered delimiters between
	 * multiple context config paths in a single String value.
	 * @see org.springframework.context.support.AbstractXmlApplicationContext#setConfigLocation
	 * @see org.springframework.web.context.ContextLoader#CONFIG_LOCATION_PARAM
	 * @see org.springframework.web.servlet.FrameworkServlet#setContextConfigLocation
	 */
	String CONFIG_LOCATION_DELIMITERS = ",; \t\n";

	/**
	 * <h3>📋 常量 2：CONVERSION_SERVICE_BEAN_NAME —— 类型转换服务的知名 Bean 名称</h3>
	 * <p><b>🏭【类型转换的"官方翻译员"——如果容器里有这个 Bean，所有属性绑定都走它】</b></p>
	 * <p>refresh 过程中，{@code AbstractApplicationContext.finishBeanFactoryInitialization()} 会检查
	 * 容器中是否有名为 {@code "conversionService"} 的 Bean，如果有就设置到 BeanFactory 上，
	 * 用于 {@code @Value}、属性注入、数据绑定等场景的类型转换。没有就用默认规则。</p>
	 * <hr/>
	 * Name of the ConversionService bean in the factory.
	 * If none is supplied, default conversion rules apply.
	 * @since 3.0
	 * @see org.springframework.core.convert.ConversionService
	 */
	String CONVERSION_SERVICE_BEAN_NAME = "conversionService";

	/**
	 * <h3>📋 常量 3：LOAD_TIME_WEAVER_BEAN_NAME —— 加载时织入器的知名 Bean 名称</h3>
	 * <p><b>🏭【字节码改造的"手术刀"——有它在，容器会用临时 ClassLoader 做类型匹配】</b></p>
	 * <p>当容器中存在名为 {@code "loadTimeWeaver"} 的 Bean 时，容器会启用临时 ClassLoader
	 * 来做类型匹配（避免过早加载类导致织入失败）。主要用于 JPA 的实体增强、AspectJ 加载时织入等场景。<br/>
	 * 大多数 SpringBoot 应用不需要关心这个。</p>
	 * <hr/>
	 * Name of the LoadTimeWeaver bean in the factory. If such a bean is supplied,
	 * the context will use a temporary ClassLoader for type matching, in order
	 * to allow the LoadTimeWeaver to process all actual bean classes.
	 * @since 2.5
	 * @see org.springframework.instrument.classloading.LoadTimeWeaver
	 */
	String LOAD_TIME_WEAVER_BEAN_NAME = "loadTimeWeaver";

	/**
	 * <h3>📋 常量 4：ENVIRONMENT_BEAN_NAME —— 环境对象的知名 Bean 名称</h3>
	 * <p><b>🏭【容器把自己的 Environment 也注册为 Bean——方便注入和依赖查找】</b></p>
	 * <p>refresh 时 {@code prepareBeanFactory()} 会把 Environment 对象以
	 * {@code "environment"} 名称注册为单例 Bean，这样你就可以 {@code @Autowired Environment env;} 拿到它。</p>
	 * <hr/>
	 * Name of the {@link Environment} bean in the factory.
	 * @since 3.1
	 */
	String ENVIRONMENT_BEAN_NAME = "environment";

	/**
	 * <h3>📋 常量 5：SYSTEM_PROPERTIES_BEAN_NAME —— JVM 系统属性的知名 Bean 名称</h3>
	 * <p><b>🏭【JVM 的 -D 参数也是一个 Bean！名字叫 "systemProperties"】</b></p>
	 * <p>refresh 时注册为单例，值是 {@code System.getProperties()} 返回的 Map。<br/>
	 * 你可以通过 {@code @Value("#{systemProperties['user.home']}")} 读取。</p>
	 * <hr/>
	 * Name of the System properties bean in the factory.
	 * @see java.lang.System#getProperties()
	 */
	String SYSTEM_PROPERTIES_BEAN_NAME = "systemProperties";

	/**
	 * <h3>📋 常量 6：SYSTEM_ENVIRONMENT_BEAN_NAME —— 操作系统环境变量的知名 Bean 名称</h3>
	 * <p><b>🏭【操作系统的 PATH、JAVA_HOME 等也是 Bean！名字叫 "systemEnvironment"】</b></p>
	 * <p>值是 {@code System.getenv()} 返回的 Map。与 systemProperties 并列注册。</p>
	 * <hr/>
	 * Name of the System environment bean in the factory.
	 * @see java.lang.System#getenv()
	 */
	String SYSTEM_ENVIRONMENT_BEAN_NAME = "systemEnvironment";

	/**
	 * <h3>📋 常量 7：APPLICATION_STARTUP_BEAN_NAME —— 启动指标记录器的知名 Bean 名称</h3>
	 * <p><b>🏭【容器启动耗时分析的"秒表"——记录每个步骤花了多长时间】</b></p>
	 * <p>5.3+ 新增，配合 {@code ApplicationStartup} 接口记录容器启动过程中各步骤的耗时，
	 * 用于性能分析（如 Spring Boot Actuator 的 startup endpoint）。默认是 no-op 实现。</p>
	 * <hr/>
	 * Name of the {@link ApplicationStartup} bean in the factory.
	 * @since 5.3
	 */
	String APPLICATION_STARTUP_BEAN_NAME = "applicationStartup";

	/**
	 * <h3>📋 常量 8：SHUTDOWN_HOOK_THREAD_NAME —— 关闭钩子线程名</h3>
	 * <p><b>🏭【JVM 关闭时那个执行 close() 的线程叫什么名字——"SpringContextShutdownHook"】</b></p>
	 * <p>当你调用 {@code registerShutdownHook()} 后，Spring 会向 JVM 注册一个关闭钩子线程，
	 * 线程名就是这个常量。方便在日志和线程 dump 中识别"这个线程是 Spring 的关闭钩子"。</p>
	 * <hr/>
	 * {@link Thread#getName() Name} of the {@linkplain #registerShutdownHook()
	 * shutdown hook} thread: {@value}.
	 * @since 5.2
	 * @see #registerShutdownHook()
	 */
	String SHUTDOWN_HOOK_THREAD_NAME = "SpringContextShutdownHook";


	/**
	 * <h3>⚙️ 方法 1：setId(String id) —— 设置上下文唯一 ID</h3>
	 * <p><b>🏭【给容器取名字——方便在多容器场景中区分"你是哪个"】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 为这个 ApplicationContext 设置一个唯一标识。默认是自动生成的（类名@hashCode 或路径）。<br/>
	 * 在父子容器、多 DispatcherServlet 等多容器场景中，ID 用于日志和 JMX 区分。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 一栋楼里有多个分厂，给每个分厂编个工号——"root-context"、"servlet-context-admin"——<br/>
	 * 这样看日志的时候一眼就知道这行日志来自哪个容器。</blockquote>
	 * <hr/>
	 * Set the unique id of this application context.
	 * @since 3.0
	 */
	void setId(String id);

	/**
	 * <h3>⚙️ 方法 2：setParent(ApplicationContext parent) —— 设置父上下文</h3>
	 * <p><b>🏭【认总公司——建立父子容器关系的唯一入口】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 设置本上下文的父上下文。设置后，本上下文可以通过双亲委派访问父上下文的 Bean。<br/>
	 * <b>重要约束</b>：父上下文应该只设置一次！不应该在运行期修改。<br/>
	 * 之所以不在构造方法里设置，是因为某些场景（如 Web 容器初始化）父容器在创建子容器时还不可用。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 新分厂成立时跟总部说："我以后归你管了！"<br/>
	 * 总部点头后，分厂就能用总部的公共资源（Bean）了。<br/>
	 * 但是！认了总部就不能再换——"认爹只能认一次！"</blockquote>
	 * <p><b>【内部联动】</b><br/>
	 * 设置 parent 后，{@code AbstractApplicationContext} 还会合并父容器的 Environment：<br/>
	 * 父容器的 PropertySource 会被添加到子容器的 Environment 中，优先级低于子容器自己的。</p>
	 * <hr/>
	 * Set the parent of this application context.
	 * <p>Note that the parent shouldn't be changed: It should only be set outside
	 * a constructor if it isn't available when an object of this class is created,
	 * for example in case of WebApplicationContext setup.
	 * @param parent the parent context
	 * @see org.springframework.web.context.ConfigurableWebApplicationContext
	 */
	void setParent(@Nullable ApplicationContext parent);

	/**
	 * <h3>⚙️ 方法 3：setEnvironment(ConfigurableEnvironment) —— 设置环境对象</h3>
	 * <p><b>🏭【换一套"环境仪表盘"——自定义 Profile 和 PropertySource】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 替换容器的 Environment 对象。必须在 {@code refresh()} 之前调用。<br/>
	 * 默认 Environment 由容器自动创建，但你可以自定义一个（比如添加加密 PropertySource）。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 工厂默认有一套环境监测仪（温度、湿度）。<br/>
	 * 但你觉得不够用，想换一套更高级的（带加密配置解析的）——在开工前换掉就行。</blockquote>
	 * <hr/>
	 * Set the {@code Environment} for this application context.
	 * @param environment the new environment
	 * @since 3.1
	 */
	void setEnvironment(ConfigurableEnvironment environment);

	/**
	 * <h3>🔍 方法 4：getEnvironment() —— 获取可配置环境（协变返回类型）</h3>
	 * <p><b>🏭【拿到环境仪表盘的"管理员版"——不只能看，还能改】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 覆盖父接口 {@code ApplicationContext.getEnvironment()} 的返回类型：<br/>
	 * 父接口返回 {@code Environment}（只读），这里返回 {@code ConfigurableEnvironment}（可读写）。<br/>
	 * 这样管理代码可以进一步定制 Environment（添加 PropertySource、设置 ActiveProfile 等）。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 普通工人看仪表盘只能看数值（Environment），<br/>
	 * 管理员看仪表盘不但能看，还能调参数（ConfigurableEnvironment）！</blockquote>
	 * <hr/>
	 * Return the {@code Environment} for this application context in configurable
	 * form, allowing for further customization.
	 * @since 3.1
	 */
	@Override
	ConfigurableEnvironment getEnvironment();

	/**
	 * <h3>⚙️ 方法 5：setApplicationStartup(ApplicationStartup) —— 设置启动指标记录器</h3>
	 * <p><b>🏭【给容器启动过程装上"秒表"——记录每一步耗时】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 设置启动指标记录器，用于在 refresh 过程中记录各步骤的耗时。<br/>
	 * 默认是 {@code ApplicationStartup.DEFAULT}（no-op，不记录）。<br/>
	 * SpringBoot 可以设置为 {@code BufferingApplicationStartup} 来收集启动指标，
	 * 通过 Actuator 的 {@code /startup} 端点查看。</p>
	 * <hr/>
	 * Set the {@link ApplicationStartup} for this application context.
	 * <p>This allows the application context to record metrics
	 * during startup.
	 * @param applicationStartup the new context event factory
	 * @since 5.3
	 */
	void setApplicationStartup(ApplicationStartup applicationStartup);

	/**
	 * <h3>🔍 方法 6：getApplicationStartup() —— 获取启动指标记录器</h3>
	 * <p><b>🏭【看看秒表——获取当前用的是哪个启动指标记录器】</b></p>
	 * <hr/>
	 * Return the {@link ApplicationStartup} for this application context.
	 * @since 5.3
	 */
	ApplicationStartup getApplicationStartup();

	/**
	 * <h3>➕ 方法 7：addBeanFactoryPostProcessor(BFPP) —— 添加工厂后处理器</h3>
	 * <p><b>🏭【在开工前塞一个"改造工程师"——它能在 Bean 实例化前修改 BeanDefinition！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 手动注册一个 {@code BeanFactoryPostProcessor}，它将在 refresh 的
	 * {@code invokeBeanFactoryPostProcessors()} 阶段被调用——<br/>
	 * 这时所有 BeanDefinition 已加载但还没有任何 Bean 被实例化，BFPP 可以修改 BD 元数据。<br/>
	 * <b>必须在 refresh() 之前调用！</b></p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 工厂开工前，你安排了一个"改造工程师"（BFPP）站在生产线入口：<br/>
	 * "所有产品蓝图（BeanDefinition）经过我手里时，我可以改参数——<br/>
	 * 比如把某个 Bean 的 scope 从 singleton 改成 prototype，或者修改属性值。"<br/>
	 * 但是！工厂一旦开工（refresh 完成），你就不能再塞人了。</blockquote>
	 * <p><b>【与容器自动检测的区别】</b><br/>
	 * 容器会自动检测 BeanFactory 中类型为 BFPP 的 Bean 并调用——那是"自动发现"。<br/>
	 * 这个方法是"手动追加"——用于在 refresh 前编程式注册额外的 BFPP（如 SpringBoot 的各种 Customizer）。</p>
	 * <hr/>
	 * Add a new BeanFactoryPostProcessor that will get applied to the internal
	 * bean factory of this application context on refresh, before any of the
	 * bean definitions get evaluated. To be invoked during context configuration.
	 * @param postProcessor the factory processor to register
	 */
	void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor);

	/**
	 * <h3>➕ 方法 8：addApplicationListener(ApplicationListener) —— 添加事件监听者</h3>
	 * <p><b>🏭【提前在广播站挂号——确保 refresh 事件也能听到！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 手动注册一个事件监听者。行为取决于容器状态：
	 * <ul>
	 * <li>容器未 active（refresh 前）：监听者被暂存，refresh 时统一注册到 Multicaster</li>
	 * <li>容器已 active（refresh 后）：直接注册到当前 Multicaster，立即生效</li>
	 * </ul>
	 * 前者保证了你在 refresh 前注册的监听者能收到 {@code ContextRefreshedEvent}。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 工厂还没开工时，你提前到广播站说："我要听开工典礼的广播（ContextRefreshedEvent）！"<br/>
	 * 广播站先记下你的名字，等开工典礼时一定通知你。<br/>
	 * 如果工厂已经开工了，你现在来挂号，那后续的广播你都能听到，但开工典礼已经过了，你错过了。</blockquote>
	 * <hr/>
	 * Add a new ApplicationListener that will be notified on context events
	 * such as context refresh and context shutdown.
	 * <p>Note that any ApplicationListener registered here will be applied
	 * on refresh if the context is not active yet, or on the fly with the
	 * current event multicaster in case of a context that is already active.
	 * @param listener the ApplicationListener to register
	 * @see org.springframework.context.event.ContextRefreshedEvent
	 * @see org.springframework.context.event.ContextClosedEvent
	 */
	void addApplicationListener(ApplicationListener<?> listener);

	/**
	 * <h3>⚙️ 方法 9：setClassLoader(ClassLoader) —— 设置类加载器</h3>
	 * <p><b>🏭【指定"去哪个仓库找原材料"——自定义类和资源的加载路径】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 设置容器加载类路径资源和 Bean 类的 ClassLoader。会传递给内部 BeanFactory。<br/>
	 * 大多数场景不需要调用——默认使用线程上下文 ClassLoader。<br/>
	 * 主要用于特殊类加载环境（如 OSGi、自定义热部署等）。</p>
	 * <hr/>
	 * Specify the ClassLoader to load class path resources and bean classes with.
	 * <p>This context class loader will be passed to the internal bean factory.
	 * @since 5.2.7
	 * @see org.springframework.core.io.DefaultResourceLoader#DefaultResourceLoader(ClassLoader)
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#setBeanClassLoader
	 */
	void setClassLoader(ClassLoader classLoader);

	/**
	 * <h3>➕ 方法 10：addProtocolResolver(ProtocolResolver) —— 添加资源协议解析器</h3>
	 * <p><b>🏭【教容器认识新的"快递公司"——自定义资源协议】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 注册一个自定义的资源协议解析器。调用后，该解析器会<b>优先于</b>默认解析规则执行。<br/>
	 * 比如你可以添加一个 {@code "s3://"} 协议解析器，让 {@code context.getResource("s3://bucket/key")} 直接从 S3 加载。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 容器默认只认识 "classpath:"、"file:"、"http:" 这几家快递公司。<br/>
	 * 你注册一个新的协议解析器，就相当于教容器认识一家新快递公司（如 "s3:"、"oss:"）——<br/>
	 * 以后用 {@code getResource("s3://...")} 就能直接取到资源。</blockquote>
	 * <hr/>
	 * Register the given protocol resolver with this application context,
	 * allowing for additional resource protocols to be handled.
	 * <p>Any such resolver will be invoked ahead of this context's standard
	 * resolution rules. It may therefore also override any default rules.
	 * @since 4.3
	 */
	void addProtocolResolver(ProtocolResolver resolver);

	/**
	 * <h3>🔥 方法 11：refresh() —— 容器的"大爆炸按钮"！</h3>
	 * <p><b>🏭【一键启动——整个 Spring 容器最重要的方法，没有之一！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 加载或刷新容器的全部配置——从 Java 配置、XML、properties 或其他格式中读取 BeanDefinition，
	 * 然后执行完整的容器初始化流程。<br/>
	 * 内部实现在 {@code AbstractApplicationContext.refresh()} 中，包含 <b>12 大步骤</b>：</p>
	 * <ol>
	 * <li>{@code prepareRefresh()} —— 准备工作（设标志位、校验必需属性、初始化 earlyEvents）</li>
	 * <li>{@code obtainFreshBeanFactory()} —— 获取/创建内部 BeanFactory</li>
	 * <li>{@code prepareBeanFactory(beanFactory)} —— 配置 BeanFactory（注册 ClassLoader、Aware 处理器、环境 Bean 等）</li>
	 * <li>{@code postProcessBeanFactory(beanFactory)} —— 子类扩展点（Web 容器在此注册 Scope 等）</li>
	 * <li>{@code invokeBeanFactoryPostProcessors(beanFactory)} —— 🔥 调用 BFPP/BDRPP（这里触发配置类解析！）</li>
	 * <li>{@code registerBeanPostProcessors(beanFactory)} —— 注册 BPP（实例化并排序所有 BeanPostProcessor）</li>
	 * <li>{@code initMessageSource()} —— 初始化国际化 MessageSource</li>
	 * <li>{@code initApplicationEventMulticaster()} —— 初始化事件广播器</li>
	 * <li>{@code onRefresh()} —— 子类扩展点（SpringBoot 在此启动内嵌 Tomcat！）</li>
	 * <li>{@code registerListeners()} —— 注册事件监听者 + 发布 earlyEvents</li>
	 * <li>{@code finishBeanFactoryInitialization(beanFactory)} —— 🔥 实例化所有非懒加载单例 Bean！</li>
	 * <li>{@code finishRefresh()} —— 发布 ContextRefreshedEvent，启动 SmartLifecycle</li>
	 * </ol>
	 * <p><b>【原子性保证】</b><br/>
	 * 如果 refresh 中途失败，已创建的单例会被销毁，避免资源泄漏——"要么全部就绪，要么全部回滚"。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 这就是工厂的"总开关"！按下去之后：<br/>
	 * 📋 读蓝图（加载 BeanDefinition）→ 🔧 改蓝图（BFPP 改造）→ 👷 安排监工（注册 BPP）<br/>
	 * → 📢 架设广播站 → 🏗️ 开工建造所有产品（实例化 Bean）→ 🎉 发广播"开工典礼完毕！"<br/>
	 * 如果中间任何一步炸了——已经建好的产品全部销毁，不留半成品！</blockquote>
	 * <p><b>【注意事项】</b></p>
	 * <ul>
	 * <li>大多数 ApplicationContext 实现<b>不支持多次 refresh</b>（GenericApplicationContext 只能 refresh 一次）</li>
	 * <li>{@code AbstractRefreshableApplicationContext} 支持多次 refresh（每次重建 BeanFactory）</li>
	 * <li>所有 setXxx/addXxx 配置必须在 refresh 前完成——refresh 之后配置窗口关闭</li>
	 * </ul>
	 * <hr/>
	 * Load or refresh the persistent representation of the configuration, which
	 * might be from Java-based configuration, an XML file, a properties file, a
	 * relational database schema, or some other format.
	 * <p>As this is a startup method, it should destroy already created singletons
	 * if it fails, to avoid dangling resources. In other words, after invocation
	 * of this method, either all or no singletons at all should be instantiated.
	 * @throws BeansException if the bean factory could not be initialized
	 * @throws IllegalStateException if already initialized and multiple refresh
	 * attempts are not supported
	 */
	void refresh() throws BeansException, IllegalStateException;

	/**
	 * <h3>🔄 方法 12：registerShutdownHook() —— 注册 JVM 关闭钩子</h3>
	 * <p><b>🏭【给 JVM 装一个"断电自动保存"——确保关机时容器能优雅关闭！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 向 JVM Runtime 注册一个 shutdown hook 线程，JVM 关闭时自动调用 {@code close()}。<br/>
	 * 可以多次调用，但每个 context 实例最多只注册一个 hook（幂等）。<br/>
	 * hook 线程名为 {@code "SpringContextShutdownHook"}。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你在工厂总电闸上装了一个"断电传感器"：<br/>
	 * 一旦有人拉总闸（JVM 关闭），传感器自动触发"安全关停流程"（close）——<br/>
	 * 通知所有工人下班（@PreDestroy）、关闭设备（DisposableBean.destroy）、保存现场（flush 缓存）。<br/>
	 * 不管你装几次传感器，只有一个会生效（幂等）。</blockquote>
	 * <p><b>【何时需要？】</b></p>
	 * <ul>
	 * <li>SpringBoot 应用：{@code SpringApplication.run()} 内部自动调用，不需要手动调</li>
	 * <li>独立 Spring 应用（非 Boot）：{@code ctx.registerShutdownHook();} 或手动 {@code ctx.close();}</li>
	 * <li>如果不注册也不手动 close，容器中的 {@code @PreDestroy} 等回调<b>不会执行</b>！</li>
	 * </ul>
	 * <hr/>
	 * Register a shutdown hook with the JVM runtime, closing this context
	 * on JVM shutdown unless it has already been closed at that time.
	 * <p>This method can be called multiple times. Only one shutdown hook
	 * (at max) will be registered for each context instance.
	 * <p>As of Spring Framework 5.2, the {@linkplain Thread#getName() name} of
	 * the shutdown hook thread should be {@link #SHUTDOWN_HOOK_THREAD_NAME}.
	 * @see java.lang.Runtime#addShutdownHook
	 * @see #close()
	 */
	void registerShutdownHook();

	/**
	 * <h3>🔄 方法 13：close() —— 关闭容器，释放所有资源</h3>
	 * <p><b>🏭【工厂关门大吉——销毁所有 Bean、释放所有资源！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 关闭容器，释放所有资源和锁，销毁所有缓存的单例 Bean。<br/>
	 * 内部执行 {@code doClose()} 9 步拆弹流程：
	 * <ol>
	 * <li>发布 {@code ContextClosedEvent}</li>
	 * <li>调用 {@code SmartLifecycle.stop()}（phase 倒序）</li>
	 * <li>销毁所有单例 Bean（{@code @PreDestroy} → {@code DisposableBean.destroy()} → {@code destroy-method}）</li>
	 * <li>关闭内部 BeanFactory</li>
	 * <li>设置 active=false</li>
	 * </ol>
	 * <b>关键特性</b>：
	 * <ul>
	 * <li>幂等——多次调用不会有副作用，已关闭的容器再调 close 直接跳过</li>
	 * <li>不级联——不会调用父容器的 close，父子容器的生命周期独立</li>
	 * <li>继承自 {@code Closeable}——支持 try-with-resources 用法</li>
	 * </ul></p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 工厂关门流程：<br/>
	 * 1. 广播"工厂要关门了！"（ContextClosedEvent）<br/>
	 * 2. 通知所有设备按顺序停机（SmartLifecycle.stop）<br/>
	 * 3. 销毁所有产品和库存（destroy 单例 Bean）<br/>
	 * 4. 锁门、关灯<br/>
	 * 注意：关了分厂不影响总公司——父容器还活着！</blockquote>
	 * <hr/>
	 * Close this application context, releasing all resources and locks that the
	 * implementation might hold. This includes destroying all cached singleton beans.
	 * <p>Note: Does <i>not</i> invoke {@code close} on a parent context;
	 * parent contexts have their own, independent lifecycle.
	 * <p>This method can be called multiple times without side effects: Subsequent
	 * {@code close} calls on an already closed context will be ignored.
	 */
	@Override
	void close();

	/**
	 * <h3>🔍 方法 14：isActive() —— 容器是否处于活跃状态</h3>
	 * <p><b>🏭【工厂现在开着门吗？——三态判断的核心方法】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 判断容器是否处于活跃状态：至少 refresh 过一次 <b>且</b> 尚未被 close。<br/>
	 * 容器的三态生命周期：
	 * <ul>
	 * <li><b>未启动</b>（created, 未 refresh）：isActive = false</li>
	 * <li><b>运行中</b>（refresh 完成，未 close）：isActive = true ← 只有这个状态才能安全使用！</li>
	 * <li><b>已关闭</b>（close 之后）：isActive = false</li>
	 * </ul>
	 * 在非 active 状态下调用 {@code getBean()}、{@code getBeanFactory()} 等方法会抛 {@code IllegalStateException}。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "这工厂现在营业吗？"<br/>
	 * 还没开工典礼（未 refresh）→ "没呢！" → false<br/>
	 * 正常营业中 → "营业中！" → true<br/>
	 * 已经关门了（close）→ "关了！" → false</blockquote>
	 * <hr/>
	 * Determine whether this application context is active, that is,
	 * whether it has been refreshed at least once and has not been closed yet.
	 * @return whether the context is still active
	 * @see #refresh()
	 * @see #close()
	 * @see #getBeanFactory()
	 */
	boolean isActive();

	/**
	 * <h3>🔍 方法 15：getBeanFactory() —— 获取内部 BeanFactory</h3>
	 * <p><b>🏭【打开引擎盖——直接访问底层的 BeanFactory！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回 ApplicationContext 内部持有的 {@code ConfigurableListableBeanFactory} 引用。<br/>
	 * 通过它可以访问 BeanFactory 级别的底层功能（如注册 BeanDefinition、查看单例缓存等）。<br/>
	 * <b>时序约束</b>：只有在 {@code isActive()==true} 期间（即 refresh 之后、close 之前）才可调用，
	 * 否则抛 {@code IllegalStateException}。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * ApplicationContext 是一辆车，BeanFactory 是车的引擎。<br/>
	 * 这个方法就是"打开引擎盖"——让你直接操作引擎（注册 Bean、查看内部状态等）。<br/>
	 * 但注意：<br/>
	 * ❌ 不要通过它来做"后处理"——那时 Bean 已经实例化了，应该用 BFPP 在更早的阶段介入<br/>
	 * ❌ 车没启动（未 refresh）或已熄火（close 后）时打不开引擎盖——会报错</blockquote>
	 * <p><b>【典型用法】</b></p>
	 * <pre>
	 * ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
	 * // 手动注册一个单例（不经过 BeanDefinition）
	 * beanFactory.registerSingleton("myService", new MyService());
	 * // 查看已注册的 BeanDefinition 数量
	 * int count = beanFactory.getBeanDefinitionCount();
	 * </pre>
	 * <p><b>【与 ApplicationContext 的关系】</b><br/>
	 * ApplicationContext 本身也实现了 BeanFactory（通过委托模式），<br/>
	 * 但 {@code getBeanFactory()} 让你拿到的是"原始引擎"（ConfigurableListableBeanFactory），<br/>
	 * 拥有比 ApplicationContext 暴露的更多底层能力。</p>
	 * <hr/>
	 * Return the internal bean factory of this application context.
	 * Can be used to access specific functionality of the underlying factory.
	 * <p>Note: Do not use this to post-process the bean factory; singletons
	 * will already have been instantiated before. Use a BeanFactoryPostProcessor
	 * to intercept the BeanFactory setup process before beans get touched.
	 * <p>Generally, this internal factory will only be accessible while the context
	 * is active, that is, in-between {@link #refresh()} and {@link #close()}.
	 * The {@link #isActive()} flag can be used to check whether the context
	 * is in an appropriate state.
	 * @return the underlying bean factory
	 * @throws IllegalStateException if the context does not hold an internal
	 * bean factory (usually if {@link #refresh()} hasn't been called yet or
	 * if {@link #close()} has already been called)
	 * @see #isActive()
	 * @see #refresh()
	 * @see #close()
	 * @see #addBeanFactoryPostProcessor
	 */
	ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;

}
