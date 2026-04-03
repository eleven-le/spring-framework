/*
 * Copyright 2002-2014 the original author or authors.
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

import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>从"Bean 容器"到"应用骨架"的质变——Spring 应用的"中央总线"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.ApplicationContext}</li>
 * <li><b>中文名</b>：应用上下文 —— Spring 应用的"中央神经系统"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块（注意！不是 spring-beans！从 beans 升级到了 context！）</li>
 * <li><b>接口层级</b>：同时继承 6 个接口——跨越了 BeanFactory 体系和全新的能力维度！</li>
 * </ul>
 *
 * <h3>💡 为什么要有 ApplicationContext？BeanFactory 不够吗？——从"零件仓库"到"完整工厂"的跃迁！</h3>
 * <p>BeanFactory 及其子接口解决的是<b>"Bean 管理"</b>问题——创建、注入、枚举、配置。<br/>
 * 但一个真正的应用需要的远不止 Bean 管理：</p>
 * <ul>
 * <li><b>你的 Bean 之间需要通信</b> → 事件发布/监听（ApplicationEventPublisher）</li>
 * <li><b>你的应用需要读配置文件/类路径资源</b> → 资源加载（ResourcePatternResolver）</li>
 * <li><b>你的应用需要国际化</b> → 消息解析（MessageSource）</li>
 * <li><b>你的应用需要感知运行环境</b> → 环境抽象（EnvironmentCapable）</li>
 * <li><b>你的 Web 应用有父子容器</b> → 层级关系（HierarchicalBeanFactory）</li>
 * </ul>
 * <p>BeanFactory 只是"零件仓库"——ApplicationContext 是"完整的工厂"，它在零件仓库的基础上，
 * 加装了通信系统（事件）、文件系统（资源）、翻译系统（i18n）、环境感知系统。<br/>
 * <b>ApplicationContext 不是 BeanFactory 的"加强版"，而是一个全新的抽象层次——它是"应用的骨架"！</b></p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"组合优于继承"的教科书实践——6 个正交能力的多接口组合</b><br/>
 * ApplicationContext 同时继承了 6 个接口，每个接口代表一种<b>正交的能力维度</b>：
 * <pre>
 * ApplicationContext extends
 *   EnvironmentCapable        → 环境感知（profile/property）
 *   ListableBeanFactory       → Bean 批量枚举
 *   HierarchicalBeanFactory   → 父子容器层级
 *   MessageSource             → 国际化消息解析
 *   ApplicationEventPublisher → 事件发布
 *   ResourcePatternResolver   → 资源模式匹配加载
 * </pre>
 * 这 6 种能力<b>互不依赖、各自可独立演化</b>——这就是"正交组合"的力量。<br/>
 * Spring 没有把这些能力塞进一个巨型接口——而是让每种能力有自己独立的接口定义和实现，
 * 最后在 ApplicationContext 这一层通过多继承"组装"起来。<br/>
 * <b>业务借鉴</b>：当你设计"中台/平台上下文"时，不要用一个大接口定义所有能力。<br/>
 * 而是把每种能力定义为独立接口（如 {@code NotificationCapable}、{@code AuditCapable}、{@code ConfigCapable}），
 * 然后在"平台上下文"中通过多继承组合。新增能力只需新增一个接口+实现，不影响已有代码。</li>
 *
 * <li><b>"持有而非继承"BeanFactory——ApplicationContext 和 BeanFactory 的真实关系</b><br/>
 * 虽然 ApplicationContext 继承了 ListableBeanFactory 和 HierarchicalBeanFactory，
 * 但它的实现类（如 AbstractApplicationContext）<b>并不自己实现这些方法</b>！<br/>
 * 而是内部持有一个 {@code DefaultListableBeanFactory} 实例，所有 BeanFactory 相关的方法都<b>委托</b>给它。<br/>
 * 这是<b>"组合+委托"模式</b>——接口层面是"继承"（满足 IS-A 语义），实现层面是"组合+委托"（满足 HAS-A 复用）。<br/>
 * <b>业务借鉴</b>：你的"平台上下文"可以在接口层面继承多种能力接口，但在实现层面把每种能力委托给专门的组件。
 * 比如 {@code PlatformContext} 继承了 {@code NotificationCapable}，但实际通知能力委托给 {@code NotificationService}。</li>
 *
 * <li><b>刻意不继承 AutowireCapableBeanFactory——"危险能力不暴露给普通用户"</b><br/>
 * 注意看继承列表——有 ListableBeanFactory、有 HierarchicalBeanFactory，但<b>没有 AutowireCapableBeanFactory</b>！<br/>
 * 因为 AutowireCapableBeanFactory 的方法（createBean/autowireBean/initializeBean）是"手术刀级"底层 API，
 * 普通应用代码不应该直接使用。Spring 只通过 {@code getAutowireCapableBeanFactory()} 方法提供间接获取——
 * 让真正需要的人主动索取，而不是所有人都无差别地看到这些危险 API。<br/>
 * <b>业务借鉴</b>：平台上下文的接口设计要有"分级暴露"意识。基础查询能力默认暴露，
 * 危险的管理/修改能力通过 {@code getXxxAdmin()} 方法间接暴露。</li>
 *
 * <li><b>ApplicationContext 自身只增加了 6 个方法——"组合大于新增"</b><br/>
 * ApplicationContext 集成了 6 个父接口几十个方法的能力，但自身只定义了 6 个新方法（id/name/displayName/startupDate/parent/getAutowireCapableBeanFactory）。<br/>
 * 这些方法都是"应用级元信息"——关于这个应用上下文本身的身份和状态。<br/>
 * <b>绝大部分能力来自组合，而非新增</b>——这就是组合式设计的优雅之处。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 *                    EnvironmentCapable
 *                          │
 * BeanFactory              │
 * ├── ListableBeanFactory ─┼── ApplicationContext ← 👈 你在这里！"应用骨架"！
 * ├── HierarchicalBeanFactory ─┘     │
 * │                                  │
 * MessageSource ─────────────────────┤
 * ApplicationEventPublisher ─────────┤
 * ResourcePatternResolver ───────────┘
 *                                    │
 *                           ConfigurableApplicationContext（可配置版——refresh/close 在这里）
 *                                    │
 *                           AbstractApplicationContext（抽象骨架——refresh() 12 步模板方法）
 *                                    │
 *                    ┌───────────────┼───────────────┐
 *        AnnotationConfigAC    ClassPathXmlAC    GenericWebAC ...
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>本接口自身只有 <b>6 个方法</b>，全部是"应用元信息"：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th></tr>
 * <tr><td>{@code getId()}</td><td>应用上下文唯一 ID</td></tr>
 * <tr><td>{@code getApplicationName()}</td><td>部署的应用名称</td></tr>
 * <tr><td>{@code getDisplayName()}</td><td>友好显示名</td></tr>
 * <tr><td>{@code getStartupDate()}</td><td>首次加载时间戳</td></tr>
 * <tr><td>{@code getParent()}</td><td>父上下文（比 HierarchicalBeanFactory.getParentBeanFactory 更精确——返回 ApplicationContext 类型）</td></tr>
 * <tr><td>{@code getAutowireCapableBeanFactory()}</td><td>"手术刀"的间接获取入口</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>ApplicationContext 的核心价值：<b>把 BeanFactory（Bean 管理）+ 事件 + 资源 + i18n + 环境，组合成一个统一的"应用骨架"</b>。<br/>
 * 它让 Spring 从一个"依赖注入框架"升级为一个"应用框架"——你的整个应用运行在这个骨架上，
 * Bean 管理只是其中一根骨头。<br/>
 * 设计上，它是"多接口正交组合 + 委托实现"的典范——每种能力独立定义、独立实现、在这一层聚合。<br/>
 * 这种设计让 Spring 可以在不改动 BeanFactory 体系的前提下，通过新增接口来扩展应用上下文的能力——<br/>
 * 比如 Spring Boot 的 {@code WebServerApplicationContext} 就是在 ApplicationContext 上再组合了"内嵌服务器"能力。</p>
 *
 * <hr/>
 * Central interface to provide configuration for an application.
 * This is read-only while the application is running, but may be
 * reloaded if the implementation supports this.
 *
 * <p>An ApplicationContext provides:
 * <ul>
 * <li>Bean factory methods for accessing application components.
 * Inherited from {@link org.springframework.beans.factory.ListableBeanFactory}.
 * <li>The ability to load file resources in a generic fashion.
 * Inherited from the {@link org.springframework.core.io.ResourceLoader} interface.
 * <li>The ability to publish events to registered listeners.
 * Inherited from the {@link ApplicationEventPublisher} interface.
 * <li>The ability to resolve messages, supporting internationalization.
 * Inherited from the {@link MessageSource} interface.
 * <li>Inheritance from a parent context. Definitions in a descendant context
 * will always take priority. This means, for example, that a single parent
 * context can be used by an entire web application, while each servlet has
 * its own child context that is independent of that of any other servlet.
 * </ul>
 *
 * <p>In addition to standard {@link org.springframework.beans.factory.BeanFactory}
 * lifecycle capabilities, ApplicationContext implementations detect and invoke
 * {@link ApplicationContextAware} beans as well as {@link ResourceLoaderAware},
 * {@link ApplicationEventPublisherAware} and {@link MessageSourceAware} beans.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see ConfigurableApplicationContext
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.core.io.ResourceLoader
 */
public interface ApplicationContext extends EnvironmentCapable, ListableBeanFactory, HierarchicalBeanFactory,
		MessageSource, ApplicationEventPublisher, ResourcePatternResolver {

	/**
	 * <h3>🪪 方法 1：String getId()</h3>
	 * <p><b>应用上下文的唯一 ID</b>。可通过 {@code ConfigurableApplicationContext.setId()} 设置。<br/>
	 * 默认实现通常是类名+hashCode 或 ObjectUtils.identityToString。用于日志和 JMX 区分多容器。</p>
	 * <hr/>
	 * Return the unique id of this application context.
	 * @return the unique id of the context, or {@code null} if none
	 */
	@Nullable
	String getId();

	/**
	 * <h3>🪪 方法 2：String getApplicationName()</h3>
	 * <p><b>部署的应用名称</b>。Web 环境下通常是 ServletContext 的 contextPath。默认返回空字符串。</p>
	 * <hr/>
	 * Return a name for the deployed application that this context belongs to.
	 * @return a name for the deployed application, or the empty String by default
	 */
	String getApplicationName();

	/**
	 * <h3>🪪 方法 3：String getDisplayName()</h3>
	 * <p><b>友好显示名</b>。用于 toString()、日志、管理界面。</p>
	 * <hr/>
	 * Return a friendly name for this context.
	 * @return a display name for this context (never {@code null})
	 */
	String getDisplayName();

	/**
	 * <h3>🪪 方法 4：long getStartupDate()</h3>
	 * <p><b>首次加载的时间戳（毫秒）</b>。在 refresh() 开始时记录。用于计算启动耗时、监控告警。</p>
	 * <hr/>
	 * Return the timestamp when this context was first loaded.
	 * @return the timestamp (ms) when this context was first loaded
	 */
	long getStartupDate();

	/**
	 * <h3>🪪 方法 5：ApplicationContext getParent()</h3>
	 * <p><b>🏭【获取父上下文——比 getParentBeanFactory 更精确！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * HierarchicalBeanFactory.getParentBeanFactory() 返回的是 BeanFactory 类型。<br/>
	 * 这个方法返回的是 <b>ApplicationContext 类型</b>——你能拿到父上下文的全部能力（事件、资源、i18n），
	 * 而不仅仅是 Bean 管理能力。<br/>
	 * Spring MVC 中：DispatcherServlet 的子上下文通过这个方法可以拿到 Root 上下文的引用。</p>
	 * <hr/>
	 * Return the parent context, or {@code null} if there is no parent
	 * and this is the root of the context hierarchy.
	 * @return the parent context, or {@code null} if there is no parent
	 */
	@Nullable
	ApplicationContext getParent();

	/**
	 * <h3>🔓 方法 6：AutowireCapableBeanFactory getAutowireCapableBeanFactory()</h3>
	 * <p><b>🏭【"手术刀"的间接获取入口——刻意不直接继承，需要的人自己来拿！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * ApplicationContext 刻意<b>不继承</b> AutowireCapableBeanFactory——因为那些底层 API（createBean/autowireBean/initializeBean）
	 * 不应该暴露给普通应用代码。但确实有集成场景需要这些能力（如 Quartz Job 注入、测试框架注入），
	 * 所以提供了这个"间接获取"方法——<b>你主动要，我才给你</b>。<br/>
	 * 内部实现：直接返回 AbstractApplicationContext 持有的 DefaultListableBeanFactory 实例
	 * （它同时实现了 AutowireCapableBeanFactory）。<br/>
	 * 关闭后调用会抛 IllegalStateException。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * ApplicationContext 是"对外服务大厅"——普通客户在大厅就能完成所有业务。<br/>
	 * 但大厅里有一个"特殊服务柜台"（getAutowireCapableBeanFactory），
	 * 你走过去说："我需要那把手术刀（底层装配能力）"——柜台工作人员确认你有需要后，把手术刀给你。<br/>
	 * <b>而不是把手术刀摆在大厅地板上让所有人都能随手拿到！</b></blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 给不由 Spring 创建的对象注入依赖<br/>
	 * {@code AutowireCapableBeanFactory acbf = applicationContext.getAutowireCapableBeanFactory();}<br/>
	 * {@code acbf.autowireBean(myExternalObject);}</p>
	 * <hr/>
	 * Expose AutowireCapableBeanFactory functionality for this context.
	 * <p>This is not typically used by application code, except for the purpose of
	 * initializing bean instances that live outside the application context,
	 * applying the Spring bean lifecycle (fully or partly) to them.
	 * <p>Alternatively, the internal BeanFactory exposed by the
	 * {@link ConfigurableApplicationContext} interface offers access to the
	 * {@link AutowireCapableBeanFactory} interface too. The present method mainly
	 * serves as a convenient, specific facility on the ApplicationContext interface.
	 * <p><b>NOTE: As of 4.2, this method will consistently throw IllegalStateException
	 * after the application context has been closed.</b> In current Spring Framework
	 * versions, only refreshable application contexts behave that way; as of 4.2,
	 * all application context implementations will be required to comply.
	 * @return the AutowireCapableBeanFactory for this context
	 * @throws IllegalStateException if the context does not support the
	 * {@link AutowireCapableBeanFactory} interface, or does not hold an
	 * autowire-capable bean factory yet (e.g. if {@code refresh()} has
	 * never been called), or if the context has been closed already
	 * @see ConfigurableApplicationContext#refresh()
	 * @see ConfigurableApplicationContext#getBeanFactory()
	 */
	AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException;

}
