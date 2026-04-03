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

package org.springframework.beans.factory;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Spring 超级工厂的 "总纲领"、"宪法级" 接口！</h2>
 * <ul>
 * <li><b>全限定名</b>： {@code org.springframework.beans.factory.BeanFactory}</li>
 * <li><b>中文名</b>：Bean 工厂 —— 超级工厂的"总服务窗口"</li>
 * <li><b>所属车间 🏭</b>：Spring IoC 容器最底层核心车间 — {@code spring-beans} 模块</li>
 * <li><b>接口层级</b>：这是 <b>所有 IoC 容器的祖宗接口</b>，没有之一！</li>
 * </ul>
 *
 * <h3>🎯 终极历史使命</h3>
 * <p>BeanFactory 就是整座超级工厂对外开放的 <b>唯一总服务大厅</b>！<br/>
 * 不管你是谁——开发者、框架组件、还是别的 Bean——你想从工厂里拿货（获取 Bean），你想查档案（查类型），你想看花名册（查别名）……<b>你都必须经过这扇门！</b></p>
 * <p>它定义了与 Spring 容器交互的最小契约。往上看：{@code ApplicationContext}、{@code ListableBeanFactory}、{@code ConfigurableBeanFactory} 全是它的子接口，全是在这份"宪法"基础上做扩展。往下看：{@code DefaultListableBeanFactory} 是它最终极的实现——那座真正轰鸣运转的工厂车间。</p>
 *
 * <h3>🧬 继承体系一览</h3>
 * <pre>
 * BeanFactory  ← 👈 你在这里！"宪法"！一切的起点！
 * ├── HierarchicalBeanFactory      （支持父子工厂层级）
 * │     └── ConfigurableBeanFactory （可配置的工厂，内部管理用）
 * ├── ListableBeanFactory           （可枚举所有Bean的工厂）
 * └── AutowireCapableBeanFactory    （支持自动装配的工厂）
 * <b>最终大 Boss 实现类：DefaultListableBeanFactory</b>（同时实现以上所有！）
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>我把 BeanFactory 的 16 个成员（1 个常量 + 15 个方法）划分为 <b>四大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>成员</th></tr>
 * <tr><td><b>🔑 第零战区：暗号常量</b></td><td>工厂的"特殊暗号"</td><td>{@code FACTORY_BEAN_PREFIX}</td></tr>
 * <tr><td><b>📦 第一战区：核心提货</b></td><td>从工厂拿货（获取 Bean）</td><td>5个 {@code getBean()} 重载 + 2个 {@code getBeanProvider()}</td></tr>
 * <tr><td><b>🕵️ 第二战区：情报侦察</b></td><td>查户口、问身份</td><td>{@code containsBean}, {@code isSingleton}, {@code isPrototype}</td></tr>
 * <tr><td><b>🔬 第三战区：类型鉴定</b></td><td>验货、查型号</td><td>2个 {@code isTypeMatch} + 2个 {@code getType} + {@code getAliases}</td></tr>
 * </table>
 *

 * <h3>🧠 三、架构师透视·顶级设计思想</h3>
 * <ol>
 * <li><b>接口隔离原则（ISP）的教科书实践</b><br/>
 * {@code BeanFactory} 只定义了最基础的访问契约——提货、查户口、验型号。它没有塞进"注册 Bean"、"销毁 Bean"、"遍历 Bean" 等职责！这些分别由 {@code BeanDefinitionRegistry}、{@code ConfigurableBeanFactory}、{@code ListableBeanFactory} 来承担。<b>一个接口只干一票事，这就是 ISP 的极致！</b></li>
 * * <li><b>面向接口编程——"客户端只知道窗口，不知道车间"</b><br/>
 * 使用者只依赖本接口这个"总服务窗口"，完全不需要知道背后是 {@code DefaultListableBeanFactory} 还是 XmlBeanFactory 在运转。工厂的底层引擎可以随时替换，使用者的代码一行不改！这是<b>依赖倒置原则（DIP）</b>的经典体现。</li>
 * * <li><b>方法重载的渐进式设计</b><br/>
 * 5 个 {@code getBean()} 重载形成了一个优雅的 API 阶梯，从简单到复杂，从"能用"到"好用"，每个重载都精确覆盖一个使用场景，绝不冗余！：
 * <ul>
 * <li><b>① 最简版</b> {@code (String)}：按名字提货</li>
 * <li><b>② 安全版</b> {@code (String, Class)}：按名字 + 类型提货（斩断强转引发的 ClassCastException 风险）</li>
 * <li><b>③ 定制版</b> {@code (String, Object...)}：按名字 + 动态构造参数（专为 Prototype 定制）</li>
 * <li><b>④ 纯类型版</b> {@code (Class)}：无需名字，按型号提货</li>
 * <li><b>⑤ 终极版</b> {@code (Class, Object...)}：纯类型 + 动态构造参数</li>
 * </ul>
 * </li>
 * * <li><b>快速失败（Fail-Fast）哲学</b><br/>
 * 注意看——几乎所有拿货和查身份的方法都声明了 {@code throws BeansException}！Spring 在这里坚持快速失败：找不到就立刻报错，类型不对就立刻爆炸。<b>绝不返回 null 让你稀里糊涂地在深层业务代码里排查 {@code NullPointerException}！</b>（唯一例外是 {@code getType()} 可以返回 null，因为未初始化的 FactoryBean 确实可能无法确定类型）。</li>
 * * <li><b>{@code ObjectProvider}——"延迟解析 + 优雅降级"的现代设计</b><br/>
 * {@code getBeanProvider()} 是 Spring 5.1 的神来之笔。它用延迟求值的理念，把"硬依赖"变成了"软依赖"。这和 Java 8 的 {@code Optional} 哲学一脉相承，也与<b>策略模式</b>天然契合：允许你在运行时动态决定提哪个货，甚至没货也能优雅降级！</li>
 * </ol>
 *
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>BeanFactory 是整座 Spring 超级工厂的<b>"宪法第一条"——它用 15 个方法定义了与容器交互的最小完备契约</b>。<br/>
 * 通过极致的接口隔离、面向接口编程、快速失败哲学、和渐进式 API 设计，它既保证了使用者的简洁体验，又为下游子接口的扩展留足了空间。
 * 这就是为什么 Spring 历经二十年迭代，BeanFactory 的方法签名几乎没变——好的顶层抽象，就是经得起时间考验的！ 🏛 </p>
 *
 * <h3>🚀 下一步作战计划！悬念来了！</h3>
 * <ul>
 * <li><b>BeanFactory 是总服务窗口，但窗口后面那座真正轰鸣运转的车间到底长什么样？我们有两个方向可以深入——</li>
 * <li><b>🅰️ 选项 A：HierarchicalBeanFactory + ListableBeanFactory</b> —— 工厂的"扩建图纸"。BeanFactory 的两大直系子接口！一个赋予了工厂父子层级的能力（双亲委派！），一个赋予了工厂枚举所有 Bean 的能力。它们是如何在"宪法"基础上扩展出新权力的？</li>
 * <li><b>🅱️ 选项 B：DefaultListableBeanFactory</b> —— 直接杀进终极车间！。这是 BeanFactory 体系中最重量级的实现类，所有接口的能力在这里汇聚成一台真正能运转的超级机器！getBean() 到底是怎么一步步走到 doGetBean() → createBean() 的？三级缓存在这里如何运作？</li>
 * </ul>
 *
 * <hr/>
 * The root interface for accessing a Spring bean container.
 *
 * <p>This is the basic client view of a bean container;
 * further interfaces such as {@link ListableBeanFactory} and
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * are available for specific purposes.
 *
 * <p>This interface is implemented by objects that hold a number of bean definitions,
 * each uniquely identified by a String name. Depending on the bean definition,
 * the factory will return either an independent instance of a contained object
 * (the Prototype design pattern), or a single shared instance (a superior
 * alternative to the Singleton design pattern, in which the instance is a
 * singleton in the scope of the factory). Which type of instance will be returned
 * depends on the bean factory configuration: the API is the same. Since Spring
 * 2.0, further scopes are available depending on the concrete application
 * context (e.g. "request" and "session" scopes in a web environment).
 *
 * <p>The point of this approach is that the BeanFactory is a central registry
 * of application components, and centralizes configuration of application
 * components (no more do individual objects need to read properties files,
 * for example). See chapters 4 and 11 of "Expert One-on-One J2EE Design and
 * Development" for a discussion of the benefits of this approach.
 *
 * <p>Note that it is generally better to rely on Dependency Injection
 * ("push" configuration) to configure application objects through setters
 * or constructors, rather than use any form of "pull" configuration like a
 * BeanFactory lookup. Spring's Dependency Injection functionality is
 * implemented using this BeanFactory interface and its subinterfaces.
 *
 * <p>Normally a BeanFactory will load bean definitions stored in a configuration
 * source (such as an XML document), and use the {@code org.springframework.beans}
 * package to configure the beans. However, an implementation could simply return
 * Java objects it creates as necessary directly in Java code. There are no
 * constraints on how the definitions could be stored: LDAP, RDBMS, XML,
 * properties file, etc. Implementations are encouraged to support references
 * amongst beans (Dependency Injection).
 *
 * <p>In contrast to the methods in {@link ListableBeanFactory}, all of the
 * operations in this interface will also check parent factories if this is a
 * {@link HierarchicalBeanFactory}. If a bean is not found in this factory instance,
 * the immediate parent factory will be asked. Beans in this factory instance
 * are supposed to override beans of the same name in any parent factory.
 *
 * <p>Bean factory implementations should support the standard bean lifecycle interfaces
 * as far as possible. The full set of initialization methods and their standard order is:
 * <ol>
 * <li>BeanNameAware's {@code setBeanName}
 * <li>BeanClassLoaderAware's {@code setBeanClassLoader}
 * <li>BeanFactoryAware's {@code setBeanFactory}
 * <li>EnvironmentAware's {@code setEnvironment}
 * <li>EmbeddedValueResolverAware's {@code setEmbeddedValueResolver}
 * <li>ResourceLoaderAware's {@code setResourceLoader}
 * (only applicable when running in an application context)
 * <li>ApplicationEventPublisherAware's {@code setApplicationEventPublisher}
 * (only applicable when running in an application context)
 * <li>MessageSourceAware's {@code setMessageSource}
 * (only applicable when running in an application context)
 * <li>ApplicationContextAware's {@code setApplicationContext}
 * (only applicable when running in an application context)
 * <li>ServletContextAware's {@code setServletContext}
 * (only applicable when running in a web application context)
 * <li>{@code postProcessBeforeInitialization} methods of BeanPostProcessors
 * <li>InitializingBean's {@code afterPropertiesSet}
 * <li>a custom {@code init-method} definition
 * <li>{@code postProcessAfterInitialization} methods of BeanPostProcessors
 * </ol>
 *
 * <p>On shutdown of a bean factory, the following lifecycle methods apply:
 * <ol>
 * <li>{@code postProcessBeforeDestruction} methods of DestructionAwareBeanPostProcessors
 * <li>DisposableBean's {@code destroy}
 * <li>a custom {@code destroy-method} definition
 * </ol>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 13 April 2001
 * @see BeanNameAware#setBeanName
 * @see BeanClassLoaderAware#setBeanClassLoader
 * @see BeanFactoryAware#setBeanFactory
 * @see org.springframework.context.EnvironmentAware#setEnvironment
 * @see org.springframework.context.EmbeddedValueResolverAware#setEmbeddedValueResolver
 * @see org.springframework.context.ResourceLoaderAware#setResourceLoader
 * @see org.springframework.context.ApplicationEventPublisherAware#setApplicationEventPublisher
 * @see org.springframework.context.MessageSourceAware#setMessageSource
 * @see org.springframework.context.ApplicationContextAware#setApplicationContext
 * @see org.springframework.web.context.ServletContextAware#setServletContext
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization
 * @see InitializingBean#afterPropertiesSet
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getInitMethodName
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization
 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor#postProcessBeforeDestruction
 * @see DisposableBean#destroy
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getDestroyMethodName
 */
public interface BeanFactory {

	/*=======================================================================================================
	                                      🔑 第零战区：暗号常量
	  =======================================================================================================*/
	/**
	 * <h3>🔑 第零战区：暗号常量</h3>
	 * <p><b>🏭 这个 {@code "&"} 符号，就是工厂的【特殊暗号/密码前缀】！</b></p>
	 * <ul>
	 * <li>正常情况下：{@code getBean("myDataSource")} → 拿到 FactoryBean 生产出来的<b>【产品】</b></li>
	 * <li>加了暗号后：{@code getBean("&myDataSource")} → 拿到 FactoryBean<b>【本身】</b>，即那台"小型代工厂"！</li>
	 * </ul>
	 * <p><b>【硬核释义】</b><br/>
	 * FactoryBean 是 Spring 里的"工厂中的工厂"——它自己也是一个 Bean，但它的本职工作是生产别的 Bean。问题来了：当你说 getBean("myJndiObject")，你到底是想要它生产的产品，还是想要这台代工机器本身？Spring 用 {@code "&"} 前缀来区分——加了 {@code &} 就是要机器本身！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你去工厂提货窗口说："给我 myJndiObject"，仓管员给你的是这台机器造出来的零件。<br/>
	 * 但如果你说："给我 &myJndiObject"——仓管员懂了，你要的不是零件，你要的是那台造零件的机器本身！</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * 在古茗的业务里，假设你有一个 {@code RedisConnectionFactoryBean}，它负责生产 Redis 连接。<br/>
	 * {@code getBean("redisConnection")} 拿到的是连接对象，<br/>
	 * {@code getBean("&redisConnection")} 拿到的是那个工厂 Bean 本身——你可能需要它来动态修改连接配置。</p>
	 * <hr/>
	 * Used to dereference a {@link FactoryBean} instance and distinguish it from
	 * beans <i>created</i> by the FactoryBean. For example, if the bean named
	 * {@code myJndiObject} is a FactoryBean, getting {@code &myJndiObject}
	 * will return the factory, not the instance returned by the factory.
	 */
	String FACTORY_BEAN_PREFIX = "&";


	/* =======================================================================================================
	                 📦 第一战区：核心提货系统（最重要的 7 个方法！） 这是整座工厂存在的终极理由——把货（Bean）交到你手上！
	   =======================================================================================================*/
	/**
	 * <h3>📦 方法 1：Object getBean(String name)</h3>
	 * <p><b>🏭【总服务窗口·最基础的提货单】</b><br/>
	 * 拿着 Bean 的名字（就是提货单号），去工厂提货！<br/>
	 * 返回 Object —— 意味着你拿到的是"没贴标签"的通用货物，需要自己强转类型。</p>
	 * <p><b>【硬核释义】</b><br/>
	 * 根据 Bean 的名称从容器中获取实例。如果是 Singleton，返回缓存中的共享实例；如果是 Prototype，每次都造一个新的。支持别名解析（alias → canonical name），如果当前工厂找不到，会往父工厂递归查找（双亲委派！）。返回值永远不会是 null——找不到直接抛异常，快速失败！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你拿着一张写着名字的提货单走到窗口："我要 userService！" <br/>
	 * 仓管员（BeanFactory）先去现货仓库（一级缓存 singletonObjects）翻一翻——有？直接给你！ <br/>
	 * 没有？去查生产图纸（BeanDefinition），然后启动流水线现场给你造一台！<br/>
	 * 这个窗口还认识小名/花名（alias），你说"给我 userSvc"，仓管员知道你要的就是 userService。本厂没有？打电话问总厂/母公司（parent factory）！</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗门店系统中，根据名字拿到商品中心服务<br/>
	 * {@code Object productService = beanFactory.getBean("productCenterService");}<br/>
	 * // 缺点：返回 Object，你得自己强转，不安全<br/>
	 * {@code ProductCenterService service = (ProductCenterService) productService;}</p>
	 * <hr/>
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * <p>This method allows a Spring BeanFactory to be used as a replacement for the
	 * Singleton or Prototype design pattern. Callers may retain references to
	 * returned objects in the case of Singleton beans.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to retrieve
	 * @return an instance of the bean.
	 * Note that the return value will never be {@code null} but possibly a stub for
	 * {@code null} returned from a factory method, to be checked via {@code equals(null)}.
	 * Consider using {@link #getBeanProvider(Class)} for resolving optional dependencies.
	 * @throws NoSuchBeanDefinitionException if there is no bean with the specified name
	 * @throws BeansException if the bean could not be obtained
	 */
	Object getBean(String name) throws BeansException;

	/**
	 * <h3>📦 方法 2：&lt;T&gt; T getBean(String name, Class&lt;T&gt; requiredType)</h3>
	 * <p><b>🏭【带型号校验的提货单】</b><br/>
	 * 不仅要名字对，还要求提出来的货必须是指定型号（类型）！<br/>
	 * 如果型号不匹配，直接拒绝发货！不会让你拿错货回去才发现炸了！</p>
	 * <p><b>【硬核释义】</b><br/>
	 * 在 {@code getBean(String)} 基础上增加了类型安全校验。如果容器中名为 name 的 Bean 无法赋值给 requiredType，直接抛 {@code BeanNotOfRequiredTypeException}——把 {@code ClassCastException} 的炸弹拦截在工厂内部，不让它流入业务代码！泛型 {@code <T>} 让返回值直接就是目标类型，无需强转。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你拿着提货单说："我要 orderService，而且它必须是 OrderService 型号的！"<br/>
	 * 仓管员提出来，先拿卡尺量一量（instanceof 检查）——型号对！发货！<br/>
	 * 型号不对？当场拦下来，大喊："这货不是你要的型号！" 绝不让你带着错误的零件回车间，避免后面整条流水线爆炸！💥</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：类型安全地获取价格引擎服务<br/>
	 * {@code PriceEngineService priceEngine = beanFactory.getBean("priceEngine", PriceEngineService.class);}<br/>
	 * // 编译期就确定类型，运行期做二次校验，双重保险！</p>
	 * <hr/>
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * <p>Behaves the same as {@link #getBean(String)}, but provides a measure of type
	 * safety by throwing a BeanNotOfRequiredTypeException if the bean is not of the
	 * required type. This means that ClassCastException can't be thrown on casting
	 * the result correctly, as can happen with {@link #getBean(String)}.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to retrieve
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return an instance of the bean.
	 * Note that the return value will never be {@code null}. In case of a stub for
	 * {@code null} from a factory method having been resolved for the requested bean, a
	 * {@code BeanNotOfRequiredTypeException} against the NullBean stub will be raised.
	 * Consider using {@link #getBeanProvider(Class)} for resolving optional dependencies.
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanNotOfRequiredTypeException if the bean is not of the required type
	 * @throws BeansException if the bean could not be created
	 */
	<T> T getBean(String name, Class<T> requiredType) throws BeansException;

	/**
	 * <h3>📦 方法 3：Object getBean(String name, Object... args)</h3>
	 * <p><b>🏭【带定制参数的特殊订单】</b><br/>
	 * 提货时顺便递上一组"定制参数"，让工厂按你的要求来造！<br/>
	 * <b>⚠️ 注意：只对新造的有效！如果仓库里已经有现货（singleton已创建），这些参数就没用了！</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 允许在获取 Bean 时传入显式的构造器参数或工厂方法参数，覆盖 BeanDefinition 中的默认配置。关键限制：只在创建新实例时有效——如果 Bean 是 Singleton 且已被创建，这些参数会被忽略。如果对一个 Singleton Bean 传了 args，会抛 {@code BeanDefinitionStoreException}（你不能给已经造好的机器重新注入零件！）。Since 2.5。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你走到窗口说："给我造一台 reportGenerator，但是我要用这个特殊马达和那个定制齿轮（args）来造！"<br/>
	 * 如果这是 Prototype（每次新造），工厂说："没问题，按你的参数来！"<br/>
	 * 如果是 Singleton 且已经造好了？工厂说："大哥，这台机器早就造完了摆在仓库里了，你现在递零件过来没用了！" 💥</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：按区域动态创建不同配置的库存校验器（Prototype scope）<br/>
	 * {@code Object checker = beanFactory.getBean("inventoryChecker", "华东大区", 5000);}</p>
	 * <hr/>
	 *
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * <p>Allows for specifying explicit constructor arguments / factory method arguments,
	 * overriding the specified default arguments (if any) in the bean definition.
	 * @param name the name of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanDefinitionStoreException if arguments have been given but
	 * the affected bean isn't a prototype
	 * @throws BeansException if the bean could not be created
	 * @since 2.5
	 */
	Object getBean(String name, Object... args) throws BeansException;

	/**
	 * <h3>📦 方法 4：&lt;T&gt; T getBean(Class&lt;T&gt; requiredType)</h3>
	 * <p><b>🏭【纯按型号提货——不用名字，直接报型号！】</b><br/>
	 * 工厂自己去翻花名册，找到唯一匹配这个型号的机器给你<br/>
	 * <b>⚠️ 如果有多台同型号机器？工厂懵了，直接报错！</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 按类型获取 Bean——不需要知道 Bean 的名字！容器会在所有注册的 BeanDefinition 中查找与 requiredType 匹配的唯一 Bean。如果找到 0 个，抛 {@code NoSuchBeanDefinitionException}；找到多个，抛 {@code NoUniqueBeanDefinitionException}。Since 3.0。内部实现会进入 {@code ListableBeanFactory} 的 by-type 查找领域。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你走到窗口说："我不管叫什么名字，给我一台 CacheService 类型的机器就行！"<br/>
	 * 仓管员翻遍花名册——只有一台？太好了，直接给你！<br/>
	 * 有两台同型号的？仓管员崩溃了："你到底要哪台啊！！！" 💥（NoUniqueBeanDefinitionException）<br/>
	 * 一台都没有？"这型号咱厂不生产啊兄弟！" ❌</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：只有一个 ProductSearchService 实现时，按类型获取最方便<br/>
	 * {@code ProductSearchService searchService = beanFactory.getBean(ProductSearchService.class);}</p>
	 * <hr/>
	 * Return the bean instance that uniquely matches the given object type, if any.
	 * <p>This method goes into {@link ListableBeanFactory} by-type lookup territory
	 * but may also be translated into a conventional by-name lookup based on the name
	 * of the given type. For more extensive retrieval operations across sets of beans,
	 * use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return an instance of the single bean matching the required type
	 * @throws NoSuchBeanDefinitionException if no bean of the given type was found
	 * @throws NoUniqueBeanDefinitionException if more than one bean of the given type was found
	 * @throws BeansException if the bean could not be created
	 * @since 3.0
	 * @see ListableBeanFactory
	 */
	<T> T getBean(Class<T> requiredType) throws BeansException;

	/**
	 * <h3>📦 方法 5：&lt;T&gt; T getBean(Class&lt;T&gt; requiredType, Object... args)</h3>
	 * <p><b>🏭【按型号 + 带定制参数的高级订单】</b><br/>
	 * 方法 4 和方法 3 的合体！按类型查找 + 传入构造参数。</p>
	 * <p><b>【硬核释义】</b><br/>
	 * 结合了按类型查找和显式参数传入。同样，参数只在创建新实例时有效。Since 4.1。是 getBean(Class) 和 getBean(String, Object...) 的"合体技"。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "我要一台 ReportGenerator 型号的机器，而且用我指定的这些零件来造！"</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：Prototype 的导出器，每次指定导出区域<br/>
	 * {@code ExportService exporter = beanFactory.getBean(ExportService.class, "浙江省", "PDF");}</p>
	 * <hr/>
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * <p>Allows for specifying explicit constructor arguments / factory method arguments,
	 * overriding the specified default arguments (if any) in the bean definition.
	 * <p>This method goes into {@link ListableBeanFactory} by-type lookup territory
	 * but may also be translated into a conventional by-name lookup based on the name
	 * of the given type. For more extensive retrieval operations across sets of beans,
	 * use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanDefinitionStoreException if arguments have been given but
	 * the affected bean isn't a prototype
	 * @throws BeansException if the bean could not be created
	 * @since 4.1
	 */
	<T> T getBean(Class<T> requiredType, Object... args) throws BeansException;

	/**
	 * <h3>📦 方法 6：&lt;T&gt; ObjectProvider&lt;T&gt; getBeanProvider(Class&lt;T&gt; requiredType)</h3>
	 * <p><b>🏭【延迟提货券 / VIP 预约单】</b><br/>
	 * 不是立刻提货！而是先拿到一张"提货凭证"（ObjectProvider）<br/>
	 * 什么时候真正要用了，再凭证提货！——这就是懒加载的精髓！</p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回一个 {@code ObjectProvider<T>} 对象——它是 Bean 的延迟解析代理。你拿到的不是 Bean 本身，而是一个"提货凭证"。通过它你可以：<br/>
	 * - {@code getIfAvailable()} → 有就拿，没有也不报错（返回 null）<br/>
	 * - {@code getIfUnique()} → 有且唯一才拿<br/>
	 * - {@code stream()} / {@code orderedStream()} → 拿到所有匹配的，按顺序遍历<br/>
	 * 这是 Spring 5.1 引入的优雅依赖解析方式，完美解决可选依赖和延迟初始化问题！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 以前你要货就是直接提，没有就当场炸给你看。现在高级了——你先领一张 VIP 提货券（ObjectProvider），回去该干嘛干嘛。<br/>
	 * 等你真正需要的时候，拿券来提："有货吗？" → {@code getIfAvailable()}<br/>
	 * "有几台？只有一台的话给我" → {@code getIfUnique()}<br/>
	 * "把所有同型号的按优先级排队给我看看" → {@code orderedStream()}<br/>
	 * 这样即使工厂暂时没这款货，你的流水线也不会因为缺一个零件就整体停摆！🛡️</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：某些门店可能没有会员系统，用 ObjectProvider 优雅处理<br/>
	 * {@code @Autowired}<br/>
	 * {@code private ObjectProvider<MemberService> memberServiceProvider;}<br/>
	 * {@code public void handleOrder(Order order) { } } <br/>
	 * // 有会员服务就用，没有也不影响下单<br/>
	 * {@code memberServiceProvider.ifAvailable(ms -> ms.addPoints(order));}</p>
	 * <hr/>
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * <p>For matching a generic type, consider {@link #getBeanProvider(ResolvableType)}.
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return a corresponding provider handle
	 * @since 5.1
	 * @see #getBeanProvider(ResolvableType)
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

	/**
	 * <h3>📦 方法 7：&lt;T&gt; ObjectProvider&lt;T&gt; getBeanProvider(ResolvableType requiredType)</h3>
	 * <p><b>🏭【支持泛型精确匹配的高级提货券】</b><br/>
	 * 同上，但支持泛型！比如你要 {@code List<String>} 类型的 Bean，用 Class 做不到精确匹配<br/>
	 * {@code ResolvableType} 能精确表达 {@code Repository<Order>} 这样的带泛型的类型！</p>
	 * <p><b>【硬核释义】</b><br/>
	 * 与上一个方法的区别在于参数类型从 {@code Class<T>} 升级为 {@code ResolvableType}。{@code ResolvableType} 是 Spring 4.0 引入的泛型解析利器，能精确表达 {@code Repository<Order>}、{@code Map<String, List<Product>>} 这样的复杂泛型类型。泛型匹配规则是严格的 Java 赋值兼容，不会做宽松的 unchecked 匹配。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 普通提货券只认"大类型"：Repository 型号的给我一台。<br/>
	 * 高级提货券能精确到泛型参数："我要的不是随便一台 Repository，我要的是 Repository&lt;Order&gt;！别把 Repository&lt;User&gt; 给我混进来！" 🔍</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：精确获取泛型类型的 Bean<br/>
	 * {@code ResolvableType type = ResolvableType.forClassWithGenerics(CacheManager.class, ProductSKU.class);}<br/>
	 * {@code ObjectProvider<CacheManager<ProductSKU>> provider = beanFactory.getBeanProvider(type);}</p>
	 * <hr/>
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options. This variant allows
	 * for specifying a generic type to match, similar to reflective injection points
	 * with generic type declarations in method/constructor parameters.
	 * <p>Note that collections of beans are not supported here, in contrast to reflective
	 * injection points. For programmatically retrieving a list of beans matching a
	 * specific type, specify the actual bean type as an argument here and subsequently
	 * use {@link ObjectProvider#orderedStream()} or its lazy streaming/iteration options.
	 * <p>Also, generics matching is strict here, as per the Java assignment rules.
	 * For lenient fallback matching with unchecked semantics (similar to the 'unchecked'
	 * Java compiler warning), consider calling {@link #getBeanProvider(Class)} with the
	 * raw type as a second step if no full generic match is
	 * {@link ObjectProvider#getIfAvailable() available} with this variant.
	 * @return a corresponding provider handle
	 * @param requiredType type the bean must match; can be a generic type declaration
	 * @since 5.1
	 * @see ObjectProvider#iterator()
	 * @see ObjectProvider#stream()
	 * @see ObjectProvider#orderedStream()
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

	/* ==================================================================================================================================================
	 															🕵️ 第二战区：情报侦察系统   (不提货！只是来查户口、问身份的！)
	   ==================================================================================================================================================*/
	/**
	 * <h3>🕵️ 方法 8：boolean containsBean(String name)</h3>
	 * <p><b>🏭【查户口——这人在不在我们厂的花名册上？】</b><br/>
	 * <b>注意：只要名字存在就返回 true，不管这个 Bean 是不是真的能造出来！</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 检查工厂中是否存在指定名称的 Bean——包括 BeanDefinition 中注册的和外部手动注册的 Singleton 实例。支持别名解析，支持父子工厂层级查找。关键注意点：返回 true 不代表 {@code getBean()} 一定能成功！可能 BeanDefinition 是 abstract 的，或者创建过程会失败。这是一个宽松的存在性检查。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你打电话问工厂："你们有一个叫 paymentService 的员工吗？"<br/>
	 * 仓管员翻了翻花名册："有这个名字！" → true<br/>
	 * 但他没说这个员工是不是请假了、是不是实习生（abstract）、能不能干活——他只告诉你名字在不在册上！</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：检查当前环境是否注册了某个可选模块<br/>
	 * {@code if (beanFactory.containsBean("loyaltyPointsService")) { } }<br/>
	 * // 积分模块已部署，启用积分抵扣逻辑</p>
	 * <hr/>
	 * Does this bean factory contain a bean definition or externally registered singleton
	 * instance with the given name?
	 * <p>If the given name is an alias, it will be translated back to the corresponding
	 * canonical bean name.
	 * <p>If this factory is hierarchical, will ask any parent factory if the bean cannot
	 * be found in this factory instance.
	 * <p>If a bean definition or singleton instance matching the given name is found,
	 * this method will return {@code true} whether the named bean definition is concrete
	 * or abstract, lazy or eager, in scope or not. Therefore, note that a {@code true}
	 * return value from this method does not necessarily indicate that {@link #getBean}
	 * will be able to obtain an instance for the same name.
	 * @param name the name of the bean to query
	 * @return whether a bean with the given name is present
	 */
	boolean containsBean(String name);

	/**
	 * <h3>🕵️ 方法 9：boolean isSingleton(String name)</h3>
	 * <p><b>🏭【问身份——这台机器是"独苗"（全厂就一台）还是可以批量生产的？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 判断指定 Bean 是否是 Singleton 作用域——即 {@code getBean()} 每次都返回同一个实例。注意：返回 false 不代表它是 Prototype！它可能是 request、session 等自定义作用域。找不到 Bean 会抛异常（快速失败！）。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你问工厂："这台 dataSource 是全厂独一份的镇厂之宝吗？"<br/>
	 * "是！全厂就这一台，谁来提货都是拿的同一台！" → true（Singleton）<br/>
	 * "不是！" → false（但可能是 Prototype 也可能是 Scoped，你得进一步问！）</blockquote>
	 * <hr/>
	 * Is this bean a shared singleton? That is, will {@link #getBean} always
	 * return the same instance?
	 * <p>Note: This method returning {@code false} does not clearly indicate
	 * independent instances. It indicates non-singleton instances, which may correspond
	 * to a scoped bean as well. Use the {@link #isPrototype} operation to explicitly
	 * check for independent instances.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @return whether this bean corresponds to a singleton instance
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #isPrototype
	 */
	boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>🕵️ 方法 10：boolean isPrototype(String name)</h3>
	 * <p><b>🏭【问身份——这台机器是"流水线批量款"（每次都造新的）吗？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 判断指定 Bean 是否是 Prototype 作用域——即 {@code getBean()} 每次都返回全新独立实例。同理，返回 false 不代表就是 Singleton，可能是 Scoped Bean。与 {@code isSingleton()} 形成互补但不互斥的关系！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "这台 orderValidator 是每次来提货都现场新造一台的吗？"<br/>
	 * "是！每次都是崭新出厂的，互不干扰！" → true<br/>
	 * "不是！可能是共享的，也可能是按 session 分配的！"</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：根据 scope 决定是否需要做线程安全处理<br/>
	 * {@code if (beanFactory.isSingleton("shoppingCart")) { }} // Singleton 的购物车？要加锁！多线程共享一个实例！<br/>
	 * {@code else if (beanFactory.isPrototype("shoppingCart")) { }} // Prototype 的购物车？每人一个，不用担心并发！</p>
	 * <hr/>
	 * Is this bean a prototype? That is, will {@link #getBean} always return
	 * independent instances?
	 * <p>Note: This method returning {@code false} does not clearly indicate
	 * a singleton object. It indicates non-independent instances, which may correspond
	 * to a scoped bean as well. Use the {@link #isSingleton} operation to explicitly
	 * check for a shared singleton instance.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @return whether this bean will always deliver independent instances
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.0.3
	 * @see #getBean
	 * @see #isSingleton
	 */
	boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code ResolvableType})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 4.2
	 * @see #getBean
	 * @see #getType
	 */
	/* ==================================================================================================================================================
	  													🔬 第三战区：类型鉴定与档案查询 (不提货，不查户口，专门来验型号、翻档案的！)
	   ==================================================================================================================================================*/
	/**
	 * <h3>🔬 方法 11 & 12：boolean isTypeMatch(String name, ResolvableType/Class<?> typeToMatch)</h3>
	 * <p><b>🏭【质检——这台机器是不是我要的那个型号？】</b><br/>
	 * 两个重载：一个接收 ResolvableType（精确泛型匹配），一个接收 Class（基础类型匹配）</p>
	 * <p><b>【硬核释义】</b><br/>
	 * 检查指定名称的 Bean 是否与给定类型兼容（assignable）——即 {@code getBean(name)} 的返回值能否赋值给 typeToMatch。不会触发 Bean 的创建！ 这是一个轻量级的元数据检查。对于 FactoryBean，检查的是它生产的产品的类型，不是 FactoryBean 本身。支持别名解析和父子工厂查找。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你不提货，只是站在窗口问："你们那台叫 cacheManager 的机器，是 RedisCacheManager 型号吗？"<br/>
	 * 仓管员看了看图纸上标注的型号："是的！" → true<br/>
	 * "不是，或者我看不出来" → false<br/>
	 * 注意：仓管员不会因为你问一句就真的把机器造出来！ 这只是查图纸！📋</blockquote>
	 * <hr/>
	 */
	boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>🔬 方法 12：boolean isTypeMatch(String name, Class&lt;?&gt; typeToMatch)</h3>
	 * <p><b>🏭【质检——基础型号匹配】</b><br/>同上，但参数类型是基础的 {@code Class<?>}。</p>
	 * <hr/>
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code Class})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.0.1
	 * @see #getBean
	 * @see #getType
	 */
	boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>🔬 方法 13：Class&lt;?&gt; getType(String name)</h3>
	 * <p><b>🏭【查型号档案——这台机器到底是什么型号的？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回指定 Bean 的 Class 类型——即 {@code getBean()} 会返回什么类型的对象。对于 FactoryBean，返回的是它生产的产品的类型（通过 {@code FactoryBean.getObjectType()}），不是 FactoryBean 自身的类型！<br/>可能返回 null——当类型无法确定时（比如 FactoryBean 没初始化且你不允许初始化）。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你问："那台 dataSource 到底是什么型号的？"<br/>
	 * 仓管员翻图纸："是 HikariDataSource！" → 返回 HikariDataSource.class</blockquote>
	 * <hr/>
	 *
	 * Determine the type of the bean with the given name. More specifically,
	 * determine the type of object that {@link #getBean} would return for the given name.
	 * <p>For a {@link FactoryBean}, return the type of object that the FactoryBean creates,
	 * as exposed by {@link FactoryBean#getObjectType()}. This may lead to the initialization
	 * of a previously uninitialized {@code FactoryBean} (see {@link #getType(String, boolean)}).
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @return the type of the bean, or {@code null} if not determinable
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 1.1.2
	 * @see #getBean
	 * @see #isTypeMatch
	 */
	@Nullable
	Class<?> getType(String name) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>🔬 方法 14：Class&lt;?&gt; getType(String name, boolean allowFactoryBeanInit)</h3>
	 * <p><b>🏭【查型号档案·豪华版——允许为了查型号而启动代工厂（FactoryBean）】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 两参数版本中的 allowFactoryBeanInit：当 FactoryBean 还没初始化、无法通过静态方法得知产品类型时，是否允许提前初始化这个 FactoryBean 来获取类型信息？<br/>
	 * true = "为了查型号，可以先把代工厂点火启动"；false = "代工厂还没启动就别动它"。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 遇到代工厂（FactoryBean）的情况：<br/>
	 * "那台 myJndiObject 是什么型号的？"<br/>
	 * 仓管员翻图纸："这是一台代工厂，它生产的产品是 DataSource 型号的！"<br/>
	 * 如果代工厂还没启动，仓管员不知道它生产什么？<br/>
	 * allowFactoryBeanInit=true → "行，先把代工厂启动一下问问它！"<br/>
	 * allowFactoryBeanInit=false → "算了，不知道就不知道吧" → 返回 null</blockquote>
	 * <hr/>
	 * Determine the type of the bean with the given name. More specifically,
	 * determine the type of object that {@link #getBean} would return for the given name.
	 * <p>For a {@link FactoryBean}, return the type of object that the FactoryBean creates,
	 * as exposed by {@link FactoryBean#getObjectType()}. Depending on the
	 * {@code allowFactoryBeanInit} flag, this may lead to the initialization of a previously
	 * uninitialized {@code FactoryBean} if no early type information is available.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 * just for the purpose of determining its object type
	 * @return the type of the bean, or {@code null} if not determinable
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #isTypeMatch
	 */
	@Nullable
	Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>🔬 方法 15：String[] getAliases(String name)</h3>
	 * <p><b>🏭【查花名册——这个人有几个外号/别名？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回指定 Bean 的所有别名（alias）数组。如果传入的 name 本身就是别名，则返回原始名称（canonical name）+ 其他所有别名，且原始名称排在数组第一位。没有别名则返回空数组。支持父子工厂查找。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你问："那个叫 txManager 的家伙，还有没有其他外号？"<br/>
	 * 仓管员翻花名册："有！他的大名是 transactionManager，还有个外号叫 tm！"<br/>
	 * → 返回 ["transactionManager", "tm"]（大名排第一）</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 古茗：配置迁移时，检查老名字是否指向新的 Bean<br/>
	 * {@code String[] aliases = beanFactory.getAliases("productService");}<br/>
	 * // 看看 "productSvc", "goodsService" 这些老名字是不是都指向了同一个 Bean</p>
	 * <hr/>
	 * Return the aliases for the given bean name, if any.
	 * <p>All of those aliases point to the same bean when used in a {@link #getBean} call.
	 * <p>If the given name is an alias, the corresponding original bean name
	 * and other aliases (if any) will be returned, with the original bean name
	 * being the first element in the array.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the bean name to check for aliases
	 * @return the aliases, or an empty array if none
	 * @see #getBean
	 */
	String[] getAliases(String name);

/*
┌─────────────────────────────────┬──────────────────────────────────────────────┬─────────────────────────────────────────────────────────────────────┐
  │              接口               │                 为什么拆出来                 │                              业务借鉴                               │
  ├─────────────────────────────────┼──────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ HierarchicalBeanFactory         │ "共享 vs 隔离"的矛盾需要层级调和             │ 多租户/多门店系统用"层级配置"替代"全量复制"                         │
  ├─────────────────────────────────┼──────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ ListableBeanFactory             │ "精确提货"和"批量枚举"成本差异巨大，必须隔离 │ 高频轻量操作与低频重量操作拆分到不同接口；插件/策略的"自动发现机制" │
  ├─────────────────────────────────┼──────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ AutowireCapableBeanFactory      │ "容器外对象"也需要享受 Spring 能力           │ 平台能力原子化开放——第三方可按需集成，不必全量接入                  │
  ├─────────────────────────────────┼──────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ ConfigurableBeanFactory         │ "使用工厂"和"配置工厂"必须读写分离           │ 消费者 API 与管理员 API 必须是不同接口（CQRS 接口级实践）           │
  ├─────────────────────────────────┼──────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ ConfigurableListableBeanFactory │ ISP 拆分后，框架内部协调者需要"聚合点"       │ 对外 ISP 拆分，对内 Facade 聚合；阶段门控（freeze）模式             │
  ├─────────────────────────────────┼──────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────┤
  │ ApplicationContext              │ Bean 管理只是应用骨架的一根骨头              │ 多能力正交组合 + 委托实现；危险 API 间接暴露（分级暴露）            │
  └─────────────────────────────────┴──────────────────────────────────────────────┴─────────────────────────────────────────────────────────────────────┘        */
}
