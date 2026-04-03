/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.context;

import javax.servlet.ServletContext;

import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>ApplicationContext 的"Web 延伸"——让容器感知 Servlet 世界！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.web.context.WebApplicationContext}</li>
 * <li><b>中文名</b>：Web 应用上下文 —— Spring 容器通往 Servlet 世界的"大使馆"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-web} 模块的 context 包（注意！context 包 = Web 层上下文抽象！
 * 这里不是 spring-context 模块！spring-web 的 context 包专门定义 Web 环境下的容器扩展接口，
 * 是 Servlet API 与 Spring IoC 的桥接层）</li>
 * <li><b>接口层级</b>：{@code ApplicationContext} 的直系子接口，增加了 <b>6 个常量 + 1 个方法</b></li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个子接口？——"Web 容器"和"普通容器"有什么本质区别？</h3>
 * <p>回顾 {@code ApplicationContext} 的能力边界：它管理 Bean 生命周期、发事件、读资源、解析国际化消息……<br/>
 * 但它对 Servlet 世界<b>一无所知</b>——不知道 ServletContext 在哪、不知道 request/session 是什么概念。<br/>
 * 而 Web 应用有一套特殊的诉求：</p>
 * <ul>
 * <li><b>需要访问 ServletContext</b>：获取 web.xml 配置参数、读 WAR 包内资源、与 Filter/Listener 交互</li>
 * <li><b>需要 Web 专属的作用域</b>：除了 singleton/prototype，还需要 request/session/application 三种 Web 作用域</li>
 * <li><b>需要与 Servlet 容器共享状态</b>：把根上下文绑定到 ServletContext 的 attribute 上，让 Filter/Servlet 能找到 Spring 容器</li>
 * <li><b>需要父子容器分层</b>：根上下文（Root）管 Service/DAO，每个 DispatcherServlet 有自己的子上下文管 Controller</li>
 * </ul>
 * <p>WebApplicationContext 就是 Spring 为这些 Web 特殊需求定义的<b>最小化扩展契约</b>——<br/>
 * 只加了一个方法 {@code getServletContext()} 和六个常量，就把 Spring 容器和 Servlet 世界桥接起来了！</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>接口隔离原则（ISP）的极致实践——只加必要的、不加多余的</b><br/>
 * WebApplicationContext 只增加了 1 个方法 + 6 个常量。它没有把 HttpServletRequest、HttpServletResponse、
 * DispatcherServlet 等 Web 细节塞进来。为什么？因为"容器级"的接口只需要知道 ServletContext（全局环境），
 * 请求级的东西由请求处理链自己解决。<br/>
 * <b>业务借鉴</b>：当你需要扩展一个通用接口来适配特殊场景时，只添加"这个层级真正需要的"东西。
 * 比如你的订单接口要扩展出"跨境订单"子接口，只加"关税计算"和"汇率"这种跨境特有的，
 * 不要把"物流跟踪"也塞进去（那是物流层的事）。</li>
 *
 * <li><b>"常量即契约"——用 well-known name 建立跨组件通信</b><br/>
 * {@code ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE}、{@code SERVLET_CONTEXT_BEAN_NAME} 等常量
 * 是 Spring 与 Servlet 容器之间的<b>"暗号"</b>。ContextLoaderListener 启动时把根上下文绑定到这个 key 上，
 * 任何 Filter/Servlet 都能通过这个 key 找到 Spring 容器。<br/>
 * <b>业务借鉴</b>：跨系统/跨模块的通信，定义一组"约定常量"比强耦合 API 更灵活。
 * 比如你的消息系统和订单系统之间，通过约定的 header key（如 "X-Order-Trace-Id"）传递追踪信息，
 * 而不是让两个系统直接依赖对方的接口。</li>
 *
 * <li><b>父子容器分层——"大管家管共享、小管家管私有"</b><br/>
 * 一个 Web 应用有<b>一个根上下文</b>（Root WebApplicationContext，管 Service/DAO/DataSource 等共享 Bean）
 * 和<b>N 个 Servlet 子上下文</b>（每个 DispatcherServlet 一个，管 Controller/ViewResolver 等 Web 专属 Bean）。<br/>
 * 子上下文能访问父上下文的 Bean，但反过来不行——这保证了 Service 层不依赖 Web 层！<br/>
 * <b>业务借鉴</b>：多租户系统中，"共享服务"放在全局容器，"租户专属配置"放在租户容器。
 * 租户容器能访问共享服务，但共享服务不知道具体租户——隔离性与复用性兼得。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * ApplicationContext                         （通用容器契约）
 * └── WebApplicationContext                  ← 👈 你在这里！（Web 延伸：+ServletContext +Web作用域）
 *       ├── ConfigurableWebApplicationContext（可配置版本：setServletContext/setConfigLocations/setNamespace）
 *       │     ├── AbstractRefreshableWebApplicationContext（XML 派的 Web 版：支持重复 refresh）
 *       │     │     └── XmlWebApplicationContext         （传统 web.xml + Spring XML 的经典组合）
 *       │     └── GenericWebApplicationContext            （现代派的 Web 版：一次性 refresh）
 *       │           └── AnnotationConfigServletWebServerApplicationContext（Spring Boot Web 的终极实现！）
 *       └── 【注意】StubWebApplicationContext（测试用桩）
 *
 * ⚠️ 关键路径：Spring Boot Web 应用 → AnnotationConfigServletWebServerApplicationContext → ... → WebApplicationContext
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>6 个常量 + 1 个方法，划分为 <b>三大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>成员</th></tr>
 * <tr><td><b>🏷️ 第零战区：跨容器通信暗号</b></td><td>定义 Spring 容器在 ServletContext 上的绑定 key</td>
 * <td>ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE</td></tr>
 * <tr><td><b>🌐 第一战区：Web 专属作用域 + 环境 Bean 名称</b></td><td>定义 request/session/application 三种 Web 作用域标识
 * 和 Servlet 环境中的三个 well-known Bean 名称</td>
 * <td>SCOPE_REQUEST/SESSION/APPLICATION + SERVLET_CONTEXT_BEAN_NAME/CONTEXT_PARAMETERS/CONTEXT_ATTRIBUTES</td></tr>
 * <tr><td><b>🔌 第二战区：Servlet 世界入口</b></td><td>获取 Servlet 上下文实例</td><td>getServletContext()</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>WebApplicationContext 的核心价值：<b>用最小的接口扩展（1 个方法 + 6 个常量）完成 Spring IoC 与 Servlet 世界的桥接</b>。<br/>
 * 它没有把 Web 的复杂性（HTTP 请求/响应/过滤器链/MVC 分发）泄漏到容器接口层，
 * 而是只暴露了"容器级"真正需要的东西——ServletContext 引用和 Web 作用域定义。<br/>
 * 这种"最小桥接"的设计让 Spring 的核心容器（spring-context）完全不需要依赖 Servlet API，
 * 保持了架构的干净分层。只有当你引入 spring-web 模块时，容器才"学会"感知 Web 世界。</p>
 *
 * <hr/>
 * Interface to provide configuration for a web application. This is read-only while
 * the application is running, but may be reloaded if the implementation supports this.
 *
 * <p>This interface adds a {@code getServletContext()} method to the generic
 * ApplicationContext interface, and defines a well-known application attribute name
 * that the root context must be bound to in the bootstrap process.
 *
 * <p>Like generic application contexts, web application contexts are hierarchical.
 * There is a single root context per application, while each servlet in the application
 * (including a dispatcher servlet in the MVC framework) has its own child context.
 *
 * <p>In addition to standard application context lifecycle capabilities,
 * WebApplicationContext implementations need to detect {@link ServletContextAware}
 * beans and invoke the {@code setServletContext} method accordingly.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since January 19, 2001
 * @see ServletContextAware#setServletContext
 */
public interface WebApplicationContext extends ApplicationContext {

	/* =======================================================================================================
	          🏷️ 第零战区：跨容器通信暗号 —— Spring 容器在 Servlet 世界的"门牌号"
	   =======================================================================================================*/

	/**
	 * <h3>🏷️ 常量 1：ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE —— 根上下文的 ServletContext 绑定 key</h3>
	 * <p><b>值 = "org.springframework.web.context.WebApplicationContext.ROOT"</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 这是 Spring 容器在 Servlet 世界中的<b>"门牌号"</b>！<br/>
	 * 当 {@code ContextLoaderListener} 启动成功后，会把根 WebApplicationContext 实例
	 * 绑定到 {@code ServletContext.setAttribute(ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context)}。<br/>
	 * 之后任何 Filter/Servlet 都能通过 {@code WebApplicationContextUtils.getWebApplicationContext(servletContext)}
	 * 找到 Spring 根容器。如果启动失败，这个 attribute 可能存储的是一个异常对象！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * Spring 工厂建好之后，在 Servlet 大楼的公告栏上贴了一张纸条：<br/>
	 * "门牌号：WebApplicationContext.ROOT → 工厂在这里！"<br/>
	 * 任何住在大楼里的 Filter/Servlet 看到这个门牌号就能找到工厂。<br/>
	 * 如果工厂建设失败了，纸条上写的就是"施工事故报告"（异常对象）！</blockquote>
	 * <hr/>
	 * Context attribute to bind root WebApplicationContext to on successful startup.
	 * <p>Note: If the startup of the root context fails, this attribute can contain
	 * an exception or error as value. Use WebApplicationContextUtils for convenient
	 * lookup of the root WebApplicationContext.
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#getWebApplicationContext
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#getRequiredWebApplicationContext
	 */
	String ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE = WebApplicationContext.class.getName() + ".ROOT";

	/* =======================================================================================================
	          🌐 第一战区：Web 专属作用域 + 环境 Bean 名称 —— 超越 singleton/prototype 的 Web 生命周期
	   =======================================================================================================*/

	/**
	 * <h3>🌐 常量 2：SCOPE_REQUEST = "request" —— 请求级作用域</h3>
	 * <p><b>每个 HTTP 请求创建一个独立的 Bean 实例，请求结束后销毁</b>。<br/>
	 * 典型场景：购物车临时状态、请求级缓存、请求上下文信息对象。<br/>
	 * 底层实现：由 {@code RequestScope} 类管理，Bean 存储在 {@code HttpServletRequest.setAttribute()} 中。</p>
	 * <hr/>
	 * Scope identifier for request scope: "request".
	 * Supported in addition to the standard scopes "singleton" and "prototype".
	 */
	String SCOPE_REQUEST = "request";

	/**
	 * <h3>🌐 常量 3：SCOPE_SESSION = "session" —— 会话级作用域</h3>
	 * <p><b>每个 HTTP Session 创建一个独立的 Bean 实例，Session 失效后销毁</b>。<br/>
	 * 典型场景：用户登录状态、用户偏好设置、多步骤表单的中间状态。<br/>
	 * 底层实现：由 {@code SessionScope} 类管理，Bean 存储在 {@code HttpSession.setAttribute()} 中。<br/>
	 * ⚠️ 注意：Session 作用域的 Bean 会随着 Session 序列化/反序列化（集群场景），确保你的 Bean 实现了 Serializable！</p>
	 * <hr/>
	 * Scope identifier for session scope: "session".
	 * Supported in addition to the standard scopes "singleton" and "prototype".
	 */
	String SCOPE_SESSION = "session";

	/**
	 * <h3>🌐 常量 4：SCOPE_APPLICATION = "application" —— 应用级作用域</h3>
	 * <p><b>整个 ServletContext 生命周期共享一个 Bean 实例</b>。<br/>
	 * 和 singleton 的区别：singleton 是每个 Spring ApplicationContext 一个实例，
	 * 而 application 作用域是每个 ServletContext 一个实例。<br/>
	 * 在大多数场景下二者等效，但在父子容器场景中会有差异：
	 * 多个 ApplicationContext（根 + 多个 Servlet 子容器）共享同一个 ServletContext，
	 * 所以 application 作用域的 Bean 在所有容器中都是同一个实例。</p>
	 * <hr/>
	 * Scope identifier for the global web application scope: "application".
	 * Supported in addition to the standard scopes "singleton" and "prototype".
	 */
	String SCOPE_APPLICATION = "application";

	/**
	 * <h3>🌐 常量 5：SERVLET_CONTEXT_BEAN_NAME = "servletContext" —— ServletContext 在 Spring 中的 Bean 名称</h3>
	 * <p><b>Spring 会把 Servlet 容器的 ServletContext 对象注册为一个名为 "servletContext" 的单例 Bean</b>。<br/>
	 * 这样你就能在 Spring 管理的 Bean 中通过 {@code @Autowired ServletContext} 或
	 * {@code @Resource(name="servletContext")} 直接注入 ServletContext。<br/>
	 * 底层注册时机：{@code AbstractRefreshableWebApplicationContext.postProcessBeanFactory()} 中调用
	 * {@code beanFactory.registerSingleton("servletContext", servletContext)}。</p>
	 * <hr/>
	 * Name of the ServletContext environment bean in the factory.
	 * @see javax.servlet.ServletContext
	 */
	String SERVLET_CONTEXT_BEAN_NAME = "servletContext";

	/**
	 * <h3>🌐 常量 6：CONTEXT_PARAMETERS_BEAN_NAME = "contextParameters" —— Servlet 初始化参数的 Bean 名称</h3>
	 * <p><b>web.xml 中 {@code <context-param>} 配置的所有参数，被 Spring 包装成一个 Map 注册到容器中</b>。<br/>
	 * 注意：如果同名参数同时出现在 ServletContext 和 ServletConfig 中，ServletConfig 的值会覆盖 ServletContext 的值。</p>
	 * <hr/>
	 * Name of the ServletContext init-params environment bean in the factory.
	 * <p>Note: Possibly merged with ServletConfig parameters.
	 * ServletConfig parameters override ServletContext parameters of the same name.
	 * @see javax.servlet.ServletContext#getInitParameterNames()
	 * @see javax.servlet.ServletContext#getInitParameter(String)
	 * @see javax.servlet.ServletConfig#getInitParameterNames()
	 * @see javax.servlet.ServletConfig#getInitParameter(String)
	 */
	String CONTEXT_PARAMETERS_BEAN_NAME = "contextParameters";

	/**
	 * <h3>🌐 常量 7：CONTEXT_ATTRIBUTES_BEAN_NAME = "contextAttributes" —— Servlet 属性集的 Bean 名称</h3>
	 * <p><b>ServletContext 上所有 attribute 被 Spring 包装成一个 Map 注册到容器中</b>。<br/>
	 * 这让 Spring Bean 能方便地通过 DI 获取 Servlet 层面的全局共享数据。</p>
	 * <hr/>
	 * Name of the ServletContext attributes environment bean in the factory.
	 * @see javax.servlet.ServletContext#getAttributeNames()
	 * @see javax.servlet.ServletContext#getAttribute(String)
	 */
	String CONTEXT_ATTRIBUTES_BEAN_NAME = "contextAttributes";


	/* =======================================================================================================
	          🔌 第二战区：Servlet 世界入口 —— 唯一的方法，却是最关键的桥梁
	   =======================================================================================================*/

	/**
	 * <h3>🔌 方法 1：ServletContext getServletContext()</h3>
	 * <p><b>🏭【获取 Servlet 世界的总管家——整个接口唯一的方法，也是最核心的桥梁！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回当前 Web 应用的 {@code ServletContext} 实例——这是 Servlet 规范中代表整个 Web 应用的全局对象。<br/>
	 * 通过它你可以：获取 web.xml 配置参数、获取真实文件路径、读取 WAR 包内资源、与其他 Servlet/Filter 共享 attribute。<br/>
	 * 可能返回 null（在非 Web 环境或容器尚未完全初始化时）。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * Spring 工厂说："我管 Bean 的生死，但 Servlet 大楼的钥匙不在我手上。"<br/>
	 * getServletContext() 就是去 Servlet 大楼物业（Tomcat/Jetty）那里拿钥匙——<br/>
	 * 有了这把钥匙，Spring 就能读取大楼的配置（web.xml 参数）、访问大楼的公共区域（attribute）、
	 * 查看大楼的真实地址（getRealPath）。</blockquote>
	 * <hr/>
	 * Return the standard Servlet API ServletContext for this application.
	 */
	@Nullable
	ServletContext getServletContext();

}
