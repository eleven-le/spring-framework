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

package org.springframework.beans.factory.config;

import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BeanFactory 的"能力扩展"——把工厂的创建/注入/初始化能力开放给"外来户"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.AutowireCapableBeanFactory}</li>
 * <li><b>中文名</b>：具备自动装配能力的 Bean 工厂 —— 超级工厂的"对外技术输出窗口"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包（注意！config 包 = 框架内部配置用！）</li>
 * <li><b>接口层级</b>：{@code BeanFactory} 的直系子接口，增加了 <b>5 个常量 + 13 个方法</b></li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个子接口？——"容器外对象"也想享受 Spring 福利！</h3>
 * <p>回顾 BeanFactory 的能力边界：它只管<b>容器内的 Bean</b>——你注册了 BD，我帮你创建、注入、初始化。<br/>
 * 但现实中存在大量<b>"容器外对象"</b>——它们不由 Spring 创建，Spring 管不着它们的生命周期：</p>
 * <ul>
 * <li><b>Servlet 容器创建的对象</b>：Filter、Servlet、Listener——由 Tomcat/Jetty 创建，不在 Spring 花名册上</li>
 * <li><b>JPA/Hibernate 实体</b>：由 ORM 框架通过反射 new 出来，Spring 碰不到</li>
 * <li><b>第三方框架的对象</b>：Quartz 的 Job、WebSocket 的 Handler——别的框架创建的</li>
 * <li><b>测试框架的对象</b>：JUnit 创建的测试实例，也想用 @Autowired</li>
 * <li><b>你自己 new 的对象</b>：手动 new 出来但想让 Spring 帮忙注入依赖</li>
 * </ul>
 * <p>这些对象的共同诉求：<b>"我不是 Spring 生的，但我也想用 @Autowired、@PostConstruct、BPP 回调这些能力！"</b><br/>
 * AutowireCapableBeanFactory 就是 Spring 为这些"外来户"打开的<b>特殊通道</b>——<br/>
 * 它把工厂完整的 createBean → autowire → initialize 流水线拆成<b>可独立调用的原子操作</b>，
 * 让外来对象可以"按需取用"Spring 的能力。</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>开闭原则（OCP）的"能力原子化"实践</b><br/>
 * Spring 没有要求"要用我的能力就必须完全由我管理"——而是把完整流程拆成独立原子步骤：
 * createBean（创建）、autowireBean（注入）、initializeBean（初始化）、applyBPP（后处理）、destroyBean（销毁）。<br/>
 * 外部框架可以只用其中一步，而不是全部。这就像一条完整的流水线，但每个工位都可以独立对外开放。<br/>
 * <b>业务借鉴</b>：当你的平台需要向第三方开放能力时，不要要求"要么全用我的，要么全不用"。
 * 把完整能力拆成原子 API，让第三方按需集成。比如你的风控平台，可以拆成：
 * 规则匹配（单独可用）→ 策略执行（单独可用）→ 结果回调（单独可用），第三方可以只接入"规则匹配"而不用其他。</li>
 *
 * <li><b>"不在编制内，也能享受服务"的包容性设计</b><br/>
 * Spring 没有说"你不是我创建的，我就不管你"。而是专门开了一个接口来服务"编外人员"。<br/>
 * 这种包容性设计让 Spring 成为了事实上的"应用骨架"——不管对象从哪来，都能接入 Spring 的能力体系。<br/>
 * <b>业务借鉴</b>：平台设计时，不要只服务"平台内的用户"。留一个"外部接入"的通道给"非标准"场景。
 * 比如你的订单系统，不只能处理 APP 下单，还应该能处理"外部导入的订单"——虽然不是你系统产生的，但能走你的后续流程。</li>
 *
 * <li><b>ApplicationContext 刻意不继承此接口——"不该暴露的能力就不暴露"</b><br/>
 * 注意：{@code ApplicationContext} <b>没有</b>继承 AutowireCapableBeanFactory！<br/>
 * 因为这些"手术刀级"的底层操作不应该暴露给普通应用代码。ApplicationContext 只通过
 * {@code getAutowireCapableBeanFactory()} 方法提供间接访问——这是<b>最小知识原则（迪米特法则）</b>的体现：
 * 普通代码不该知道这把手术刀的存在，只有真正需要的集成代码才去主动获取。<br/>
 * <b>业务借鉴</b>：你的服务接口中，"管理员级"的危险操作不应该和"普通用户级"操作混在一起。
 * 即使底层是同一个实现，也应该通过不同的接口暴露，让调用者根据需要显式获取。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * BeanFactory
 * ├── HierarchicalBeanFactory      （纵向：父子层级）
 * ├── ListableBeanFactory           （横向：批量枚举）
 * └── AutowireCapableBeanFactory    ← 👈 你在这里！（深度：创建/注入/初始化的原子化能力输出）
 *       └── ConfigurableListableBeanFactory（终极合体）
 *
 * ⚠️ 注意：ApplicationContext 不直接继承此接口！只通过 getAutowireCapableBeanFactory() 间接暴露！
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>5 个常量 + 13 个方法，划分为 <b>四大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>成员</th></tr>
 * <tr><td><b>🏗️ 第零战区：装配模式常量</b></td><td>定义 4+1 种自动装配策略</td><td>AUTOWIRE_NO/BY_NAME/BY_TYPE/CONSTRUCTOR/AUTODETECT + ORIGINAL_INSTANCE_SUFFIX</td></tr>
 * <tr><td><b>🔧 第一战区：面向外部的创建/装配</b></td><td>为"容器外对象"提供创建、注入、配置</td><td>createBean, autowireBean, configureBean</td></tr>
 * <tr><td><b>⚙️ 第二战区：细粒度生命周期控制</b></td><td>拆解完整流水线为独立工位</td><td>createBean(3参), autowire, autowireBeanProperties, applyBeanPropertyValues, initializeBean, applyBPP×2, destroyBean</td></tr>
 * <tr><td><b>🎯 第三战区：注入点解析</b></td><td>依赖解析的底层 API</td><td>resolveNamedBean, resolveBeanByName, resolveDependency×2</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AutowireCapableBeanFactory 的核心价值：<b>把 Spring 的"创建-注入-初始化"流水线对外开放为可独立调用的原子 API</b>。<br/>
 * 这让 Spring 从一个"封闭的对象工厂"变成了一个"开放的能力平台"——任何框架、任何对象都能接入 Spring 的 DI 能力，
 * 而不需要被 Spring 完全托管。这种"能力原子化 + 选择性暴露"的设计哲学，是 Spring 成为事实标准的关键原因之一。</p>
 *
 * <hr/>
 * Extension of the {@link org.springframework.beans.factory.BeanFactory}
 * interface to be implemented by bean factories that are capable of
 * autowiring, provided that they want to expose this functionality for
 * existing bean instances.
 *
 * <p>This subinterface of BeanFactory is not meant to be used in normal
 * application code: stick to {@link org.springframework.beans.factory.BeanFactory}
 * or {@link org.springframework.beans.factory.ListableBeanFactory} for
 * typical use cases.
 *
 * <p>Integration code for other frameworks can leverage this interface to
 * wire and populate existing bean instances that Spring does not control
 * the lifecycle of. This is particularly useful for WebWork Actions and
 * Tapestry Page objects, for example.
 *
 * <p>Note that this interface is not implemented by
 * {@link org.springframework.context.ApplicationContext} facades,
 * as it is hardly ever used by application code. That said, it is available
 * from an application context too, accessible through ApplicationContext's
 * {@link org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()}
 * method.
 *
 * <p>You may also implement the {@link org.springframework.beans.factory.BeanFactoryAware}
 * interface, which exposes the internal BeanFactory even when running in an
 * ApplicationContext, to get access to an AutowireCapableBeanFactory:
 * simply cast the passed-in BeanFactory to AutowireCapableBeanFactory.
 *
 * @author Juergen Hoeller
 * @since 04.12.2003
 * @see org.springframework.beans.factory.BeanFactoryAware
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory
 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
 */
public interface AutowireCapableBeanFactory extends BeanFactory {

	/* =======================================================================================================
	          🏗️ 第零战区：装配模式常量 —— 定义"怎么找零件、怎么装配"的 4+1 种策略
	   =======================================================================================================*/

	/**
	 * <h3>🏗️ 常量 0：AUTOWIRE_NO = 0 —— 不自动装配</h3>
	 * <p><b>不主动按名字/类型去找零件</b>，但 @Autowired 等注解驱动注入仍然生效！<br/>
	 * 现代 Spring 应用中，这是默认值——交给注解来精确控制注入点，而不是让框架自作主张按 setter 全量装配。</p>
	 * <hr/>
	 * Constant that indicates no externally defined autowiring. Note that
	 * BeanFactoryAware etc and annotation-driven injection will still be applied.
	 * @see #createBean
	 * @see #autowire
	 * @see #autowireBeanProperties
	 */
	int AUTOWIRE_NO = 0;

	/**
	 * <h3>🏗️ 常量 1：AUTOWIRE_BY_NAME = 1 —— 按名字装配</h3>
	 * <p><b>看 setter 方法名推导属性名，然后在容器中按这个名字找 Bean</b>。<br/>
	 * 比如 {@code setDataSource()} → 推导属性名 "dataSource" → 到容器里找名叫 "dataSource" 的 Bean。<br/>
	 * XML 时代常用，注解时代基本被 @Autowired 取代。</p>
	 * <hr/>
	 * Constant that indicates autowiring bean properties by name
	 * (applying to all bean property setters).
	 * @see #createBean
	 * @see #autowire
	 * @see #autowireBeanProperties
	 */
	int AUTOWIRE_BY_NAME = 1;

	/**
	 * <h3>🏗️ 常量 2：AUTOWIRE_BY_TYPE = 2 —— 按类型装配</h3>
	 * <p><b>看 setter 参数的类型，在容器中按类型匹配</b>。<br/>
	 * 比如 {@code setDataSource(DataSource ds)} → 按 DataSource 类型在容器中找唯一匹配的 Bean。<br/>
	 * 找到多个会报 NoUniqueBeanDefinitionException。</p>
	 * <hr/>
	 * Constant that indicates autowiring bean properties by type
	 * (applying to all bean property setters).
	 * @see #createBean
	 * @see #autowire
	 * @see #autowireBeanProperties
	 */
	int AUTOWIRE_BY_TYPE = 2;

	/**
	 * <h3>🏗️ 常量 3：AUTOWIRE_CONSTRUCTOR = 3 —— 构造器装配</h3>
	 * <p><b>选择"最贪心"的构造器（参数最多且都能被满足的那个），按类型解析每个构造器参数</b>。<br/>
	 * 这是现代 Spring 推荐的方式——配合 {@code @RequiredArgsConstructor}（Lombok）实现构造器注入。</p>
	 * <hr/>
	 * Constant that indicates autowiring the greediest constructor that
	 * can be satisfied (involves resolving the appropriate constructor).
	 * @see #createBean
	 * @see #autowire
	 */
	int AUTOWIRE_CONSTRUCTOR = 3;

	/**
	 * <h3>🏗️ 常量 4：AUTOWIRE_AUTODETECT = 4 —— 自动探测（已废弃）</h3>
	 * <p>Spring 3.0 废弃。原本的逻辑是"有默认构造器就用 BY_TYPE，否则用 CONSTRUCTOR"。
	 * 现代 Spring 推荐明确选择注入方式，不再自动探测。</p>
	 * <hr/>
	 * Constant that indicates determining an appropriate autowire strategy
	 * through introspection of the bean class.
	 * @see #createBean
	 * @see #autowire
	 * @deprecated as of Spring 3.0: If you are using mixed autowiring strategies,
	 * prefer annotation-based autowiring for clearer demarcation of autowiring needs.
	 */
	@Deprecated
	int AUTOWIRE_AUTODETECT = 4;

	/**
	 * <h3>🏗️ 常量 5：ORIGINAL_INSTANCE_SUFFIX = ".ORIGINAL"</h3>
	 * <p><b>"防代理"暗号</b>——在 initializeBean 时，如果 beanName 以 ".ORIGINAL" 结尾，
	 * BPP 会跳过代理包装，强制返回原始实例。<br/>
	 * 用途：当你需要初始化一个对象但明确不想被 AOP 代理包装时。</p>
	 * <hr/>
	 * Suffix for the "original instance" convention when initializing an existing
	 * bean instance: to be appended to the fully-qualified bean class name,
	 * e.g. "com.mypackage.MyClass.ORIGINAL", in order to enforce the given instance
	 * to be returned, i.e. no proxies etc.
	 * @since 5.1
	 * @see #initializeBean(Object, String)
	 * @see #applyBeanPostProcessorsBeforeInitialization(Object, String)
	 * @see #applyBeanPostProcessorsAfterInitialization(Object, String)
	 */
	String ORIGINAL_INSTANCE_SUFFIX = ".ORIGINAL";


	/* =======================================================================================================
	         🔧 第一战区：面向外部的创建/装配 —— 为"容器外对象"提供便捷的完整流水线
	            这三个方法是"对外服务窗口"——简单、完整、开箱即用
	   =======================================================================================================*/

	/**
	 * <h3>🔧 方法 1：&lt;T&gt; T createBean(Class&lt;T&gt; beanClass)</h3>
	 * <p><b>🏭【全流程造一台新机器——不需要图纸（BD），直接给 Class 就能造！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 根据给定的 Class 从零创建一个 Bean 实例，走完完整生命周期：实例化 → 注解注入 → BPP 回调 → 初始化。<br/>
	 * 关键特点：<b>不需要在容器中注册 BeanDefinition！</b>你只需要给一个 Class，Spring 帮你造出来并注入好依赖。<br/>
	 * 注意：这里的注入是<b>注解驱动</b>的（@Autowired/@Resource），不会做传统的 by-name/by-type setter 注入。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你拿着一张机器蓝图（Class）走进工厂说："帮我造一台！你们的零件（其他 Bean）帮我装上！"<br/>
	 * 工厂不需要在花名册上先登记这台机器——直接开造，把标了 @Autowired 的零件装上，然后走完全部质检（BPP）流程。<br/>
	 * 但是！这台机器造完后<b>不在花名册上</b>——它是"编外产出"，不受容器托管！</blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // Quartz Job 不由 Spring 创建，但需要 Spring 注入依赖<br/>
	 * {@code MyJob job = autowireCapableBeanFactory.createBean(MyJob.class);}<br/>
	 * // job 里的 @Autowired 字段都已经注入好了！</p>
	 * <hr/>
	 * Fully create a new bean instance of the given class.
	 * <p>Performs full initialization of the bean, including all applicable
	 * {@link BeanPostProcessor BeanPostProcessors}.
	 * <p>Note: This is intended for creating a fresh instance, populating annotated
	 * fields and methods as well as applying all standard bean initialization callbacks.
	 * It does <i>not</i> imply traditional by-name or by-type autowiring of properties;
	 * use {@link #createBean(Class, int, boolean)} for those purposes.
	 * @param beanClass the class of the bean to create
	 * @return the new bean instance
	 * @throws BeansException if instantiation or wiring failed
	 */
	<T> T createBean(Class<T> beanClass) throws BeansException;

	/**
	 * <h3>🔧 方法 2：void autowireBean(Object existingBean)</h3>
	 * <p><b>🏭【给已有的机器补装零件——你造的机器，我帮你装上 Spring 的零件！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 对一个<b>已经存在的对象</b>（你 new 出来的、反序列化出来的、别的框架创建的）执行注解驱动的依赖注入。<br/>
	 * 只做"注入"这一步——不会走 initializeBean、不会走 BPP 的 postProcessBeforeInitialization/After。<br/>
	 * 与 createBean 的区别：createBean = 从零造 + 装 + 质检；autowireBean = 只装（注入），不造不质检。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你自己在家里造了一台机器（new MyService()），但缺几个零件（依赖）。<br/>
	 * 你把机器推到工厂说："帮我把标了 @Autowired 的零件装上就行，别的不用管！"<br/>
	 * 工厂只帮你装零件（注入），不会帮你做质检（BPP）、不会帮你开机调试（init）。</blockquote>
	 * <hr/>
	 * Populate the given bean instance through applying after-instantiation callbacks
	 * and bean property post-processing (e.g. for annotation-driven injection).
	 * <p>Note: This is essentially intended for (re-)populating annotated fields and
	 * methods, either for new instances or for deserialized instances. It does
	 * <i>not</i> imply traditional by-name or by-type autowiring of properties;
	 * use {@link #autowireBeanProperties} for those purposes.
	 * @param existingBean the existing bean instance
	 * @throws BeansException if wiring failed
	 */
	void autowireBean(Object existingBean) throws BeansException;

	/**
	 * <h3>🔧 方法 3：Object configureBean(Object existingBean, String beanName)</h3>
	 * <p><b>🏭【按图纸给已有机器做全套改装——装零件 + 刷固件 + 质检，全走一遍！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 对已有对象执行<b>完整的配置流程</b>：装配属性 → 应用属性值 → 调用 Aware 回调 → 所有 BPP 回调。<br/>
	 * 这是 initializeBean 的超集！但<b>需要容器中有对应名称的 BeanDefinition</b>——因为要根据 BD 中的配置来装配。<br/>
	 * 返回值可能是原始对象，也可能是被 BPP 代理包装过的对象。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你把一台半成品机器推进工厂说："按 orderService 这张图纸的标准帮我改装！全套流程都走一遍！"<br/>
	 * 工厂按图纸装零件 → 刷固件（Aware 回调）→ 全面质检（BPP）→ 可能还给你套了个外壳（AOP 代理）<br/>
	 * ⚠️ 前提：图纸柜里必须有 "orderService" 这张图纸（BD），否则报错！</blockquote>
	 * <hr/>
	 * Configure the given raw bean: autowiring bean properties, applying
	 * bean property values, applying factory callbacks such as {@code setBeanName}
	 * and {@code setBeanFactory}, and also applying all bean post processors
	 * (including ones which might wrap the given raw bean).
	 * <p>This is effectively a superset of what {@link #initializeBean} provides,
	 * fully applying the configuration specified by the corresponding bean definition.
	 * <b>Note: This method requires a bean definition for the given name!</b>
	 * @param existingBean the existing bean instance
	 * @param beanName the name of the bean, to be passed to it if necessary
	 * (a bean definition of that name has to be available)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if there is no bean definition with the given name
	 * @throws BeansException if the initialization failed
	 * @see #initializeBean
	 */
	Object configureBean(Object existingBean, String beanName) throws BeansException;


	/* =======================================================================================================
	         ⚙️ 第二战区：细粒度生命周期控制 —— 把完整流水线拆成独立工位，精确控制每一步
	            这些方法是"手术刀级" API —— 用于需要精确控制 Bean 生命周期某一步的高级场景
	   =======================================================================================================*/

	/**
	 * <h3>⚙️ 方法 4：createBean(Class, int autowireMode, boolean dependencyCheck)</h3>
	 * <p><b>🏭【指定装配策略的全流程造机——"用什么方式找零件"由你说了算！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 与方法 1 的区别：方法 1 只支持注解驱动注入，这个版本让你显式指定装配策略（BY_NAME/BY_TYPE/CONSTRUCTOR）。<br/>
	 * {@code dependencyCheck=true} 时，会检查所有属性是否都被注入了，未注入的会报错。<br/>
	 * 走完全部生命周期（包括 BPP），是 autowire() + initializeBean() 的合体。</p>
	 * <hr/>
	 * Fully create a new bean instance of the given class with the specified
	 * autowire strategy. All constants defined in this interface are supported here.
	 * <p>Performs full initialization of the bean, including all applicable
	 * {@link BeanPostProcessor BeanPostProcessors}. This is effectively a superset
	 * of what {@link #autowire} provides, adding {@link #initializeBean} behavior.
	 * @param beanClass the class of the bean to create
	 * @param autowireMode by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for objects
	 * (not applicable to autowiring a constructor, thus ignored there)
	 * @return the new bean instance
	 * @throws BeansException if instantiation or wiring failed
	 * @see #AUTOWIRE_NO
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_CONSTRUCTOR
	 */
	Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException;

	/**
	 * <h3>⚙️ 方法 5：Object autowire(Class, int autowireMode, boolean dependencyCheck)</h3>
	 * <p><b>🏭【只造+装，不质检！—— 实例化 + 装配，但不走 BPP 和初始化！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 和方法 4（createBean 三参版）非常像，但有一个关键区别：<br/>
	 * <b>方法 4 = 实例化 + 装配 + BPP + 初始化（全流程）</b><br/>
	 * <b>方法 5 = 实例化 + 装配（半流程！不走 BPP、不走 initializeBean！）</b><br/>
	 * 也就是说：机器造出来了，零件也装好了，但<b>没做质检（BPP），没做开机调试（init）</b>！<br/>
	 * 唯一的例外：{@code InstantiationAwareBeanPostProcessor} 的回调仍然生效——因为它作用在"实例化"阶段。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 方法 4 是"从造到交付的全流程"——造机器、装零件、质检、开机调试，全部走完才交货。<br/>
	 * 方法 5 只走前半段——造出来、装好零件就停了："你自己拿去质检和调试吧！"<br/>
	 * 为什么要拆开？<b>因为有时候你需要在"装配后、质检前"插入自定义逻辑！</b><br/>
	 * 这就是"原子化"的价值——你可以在任意两步之间插入自己的处理。</blockquote>
	 * <p><b>【与方法 4 的对比表】</b></p>
	 * <table border="1" cellpadding="3">
	 * <tr><th>阶段</th><th>方法 4 createBean(3参)</th><th>方法 5 autowire</th></tr>
	 * <tr><td>实例化</td><td>✅</td><td>✅</td></tr>
	 * <tr><td>属性装配</td><td>✅</td><td>✅</td></tr>
	 * <tr><td>BPP 回调</td><td>✅</td><td>❌（需要你自己调 initializeBean）</td></tr>
	 * <tr><td>初始化回调</td><td>✅</td><td>❌（需要你自己调 initializeBean）</td></tr>
	 * </table>
	 * <hr/>
	 * Instantiate a new bean instance of the given class with the specified autowire
	 * strategy. All constants defined in this interface are supported here.
	 * Can also be invoked with {@code AUTOWIRE_NO} in order to just apply
	 * before-instantiation callbacks (e.g. for annotation-driven injection).
	 * <p>Does <i>not</i> apply standard {@link BeanPostProcessor BeanPostProcessors}
	 * callbacks or perform any further initialization of the bean. This interface
	 * offers distinct, fine-grained operations for those purposes, for example
	 * {@link #initializeBean}. However, {@link InstantiationAwareBeanPostProcessor}
	 * callbacks are applied, if applicable to the construction of the instance.
	 * @param beanClass the class of the bean to instantiate
	 * @param autowireMode by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for object
	 * references in the bean instance (not applicable to autowiring a constructor,
	 * thus ignored there)
	 * @return the new bean instance
	 * @throws BeansException if instantiation or wiring failed
	 * @see #AUTOWIRE_NO
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_CONSTRUCTOR
	 * @see #AUTOWIRE_AUTODETECT
	 * @see #initializeBean
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException;

	/**
	 * <h3>⚙️ 方法 6：void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)</h3>
	 * <p><b>🏭【只装零件，不造机器！——对已有对象执行传统 setter 装配！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 和方法 2（autowireBean）容易混淆！关键区别：<br/>
	 * <b>方法 2 autowireBean</b> = 注解驱动注入（@Autowired/@Resource），不做传统 setter 装配<br/>
	 * <b>方法 6 autowireBeanProperties</b> = 传统 setter 装配（BY_NAME/BY_TYPE），可以用 AUTOWIRE_NO 来只执行注解注入<br/>
	 * 两者都<b>不走 BPP、不走初始化</b>——只做"注入"这一步。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * autowireBean："帮我把机器上标了 @Autowired 的插口都接上线！"（注解驱动）<br/>
	 * autowireBeanProperties："帮我把机器上所有 setter 方法对应的零件都按名字/类型装上！"（传统 setter 驱动）<br/>
	 * 两个方法做的事情表面相似——都是"给已有机器装零件"——但装配的<b>驱动方式</b>不同：<br/>
	 * 一个看注解，一个看 setter 方法签名。</blockquote>
	 * <p><b>【与 autowireBean 的对比表】</b></p>
	 * <table border="1" cellpadding="3">
	 * <tr><th>维度</th><th>方法 2 autowireBean</th><th>方法 6 autowireBeanProperties</th></tr>
	 * <tr><td>注入驱动方式</td><td>注解驱动（@Autowired/@Resource）</td><td>传统 setter 驱动（BY_NAME/BY_TYPE）</td></tr>
	 * <tr><td>能否指定装配模式</td><td>❌ 固定注解模式</td><td>✅ 可选 BY_NAME/BY_TYPE/NO</td></tr>
	 * <tr><td>依赖检查</td><td>❌</td><td>✅ dependencyCheck 参数</td></tr>
	 * <tr><td>BPP/初始化</td><td>❌</td><td>❌</td></tr>
	 * </table>
	 * <hr/>
	 * Autowire the bean properties of the given bean instance by name or type.
	 * Can also be invoked with {@code AUTOWIRE_NO} in order to just apply
	 * after-instantiation callbacks (e.g. for annotation-driven injection).
	 * <p>Does <i>not</i> apply standard {@link BeanPostProcessor BeanPostProcessors}
	 * callbacks or perform any further initialization of the bean. This interface
	 * offers distinct, fine-grained operations for those purposes, for example
	 * {@link #initializeBean}. However, {@link InstantiationAwareBeanPostProcessor}
	 * callbacks are applied, if applicable to the configuration of the instance.
	 * @param existingBean the existing bean instance
	 * @param autowireMode by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for object
	 * references in the bean instance
	 * @throws BeansException if wiring failed
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_NO
	 */
	void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException;

	/**
	 * <h3>⚙️ 方法 6b：void applyBeanPropertyValues(Object existingBean, String beanName)</h3>
	 * <p><b>🏭【按图纸贴属性值——不是自动装配，是按 BD 里写死的值赋值！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 容易和 autowireBeanProperties 混淆！关键区别：<br/>
	 * <b>autowireBeanProperties</b> = 自动装配（从容器中按名字/类型找 Bean 注入）<br/>
	 * <b>applyBeanPropertyValues</b> = 把 BeanDefinition 中<b>显式定义的属性值</b>（如 XML 中 {@code <property name="timeout" value="3000"/>}）
	 * 贴到已有对象上。不做自动装配！<br/>
	 * 需要容器中有对应名称的 BD，因为属性值来自 BD。不走 BPP/初始化。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * autowireBeanProperties："去仓库找同型号的零件帮我装上！"（自动装配）<br/>
	 * applyBeanPropertyValues："别去找了，图纸上写了用什么值，直接照着贴就行！"（按 BD 的显式配置赋值）<br/>
	 * 比如图纸上写了 timeout=3000，就直接调 setTimeout(3000)，不需要去容器里找什么 Bean。</blockquote>
	 * <hr/>
	 * Apply the property values of the bean definition with the given name to
	 * the given bean instance. The bean definition can either define a fully
	 * self-contained bean, reusing its property values, or just property values
	 * meant to be used for existing bean instances.
	 * <p>This method does <i>not</i> autowire bean properties; it just applies
	 * explicitly defined property values. Use the {@link #autowireBeanProperties}
	 * method to autowire an existing bean instance.
	 * <b>Note: This method requires a bean definition for the given name!</b>
	 * <p>Does <i>not</i> apply standard {@link BeanPostProcessor BeanPostProcessors}
	 * callbacks or perform any further initialization of the bean. This interface
	 * offers distinct, fine-grained operations for those purposes, for example
	 * {@link #initializeBean}. However, {@link InstantiationAwareBeanPostProcessor}
	 * callbacks are applied, if applicable to the configuration of the instance.
	 * @param existingBean the existing bean instance
	 * @param beanName the name of the bean definition in the bean factory
	 * (a bean definition of that name has to be available)
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if there is no bean definition with the given name
	 * @throws BeansException if applying the property values failed
	 * @see #autowireBeanProperties
	 */
	void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException;

	/**
	 * <h3>⚙️ 方法 7：Object initializeBean(Object existingBean, String beanName)</h3>
	 * <p><b>🏭【给已装好零件的机器开机调试——走 Aware 回调 + BPP 全流程！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 对已经创建好、注入好的 Bean 执行"初始化"阶段的全部操作：<br/>
	 * ① Aware 接口回调（setBeanName, setBeanFactory...）<br/>
	 * ② BPP.postProcessBeforeInitialization（前置处理，如 @PostConstruct）<br/>
	 * ③ InitializingBean.afterPropertiesSet / init-method<br/>
	 * ④ BPP.postProcessAfterInitialization（后置处理，如 AOP 代理创建）<br/>
	 * <b>返回值可能不是原始对象！</b>BPP 可能会返回代理对象。<br/>
	 * 不需要容器中有 BD——beanName 只是传给 BPP 做参考的，不会去查图纸。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 机器已经造好了，零件也装好了，现在要"开机调试"：<br/>
	 * ① 告诉机器它叫什么名字、它在哪个工厂（Aware 回调）<br/>
	 * ② 质检员巡检（BPP 前置处理）<br/>
	 * ③ 按操作手册做首次启动调试（afterPropertiesSet / init-method）<br/>
	 * ④ 质检员最终检验——可能给机器套上一层安全外壳（AOP 代理）<br/>
	 * <b>注意：拿回来的可能不是原来那台机器，而是套了外壳的新机器（代理对象）！</b></blockquote>
	 * <hr/>
	 * Initialize the given raw bean, applying factory callbacks
	 * such as {@code setBeanName} and {@code setBeanFactory},
	 * also applying all bean post processors (including ones which
	 * might wrap the given raw bean).
	 * <p>Note that no bean definition of the given name has to exist
	 * in the bean factory. The passed-in bean name will simply be used
	 * for callbacks but not checked against the registered bean definitions.
	 * @param existingBean the existing bean instance
	 * @param beanName the name of the bean, to be passed to it if necessary
	 * (only passed to {@link BeanPostProcessor BeanPostProcessors};
	 * can follow the {@link #ORIGINAL_INSTANCE_SUFFIX} convention in order to
	 * enforce the given instance to be returned, i.e. no proxies etc)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws BeansException if the initialization failed
	 * @see #ORIGINAL_INSTANCE_SUFFIX
	 */
	Object initializeBean(Object existingBean, String beanName) throws BeansException;

	/**
	 * <h3>⚙️ 方法 8：Object applyBeanPostProcessorsBeforeInitialization(Object, String)</h3>
	 * <p><b>🏭【单独调用 BPP 前置处理——initializeBean 里第②步的独立拆出版！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 遍历所有注册的 BeanPostProcessor，逐个调用 {@code postProcessBeforeInitialization(bean, beanName)}。<br/>
	 * 这是 initializeBean 内部第②步的独立 API——你可以只调这一步而不走 Aware 回调和 init-method。<br/>
	 * <b>@PostConstruct 就是在这一步被触发的！</b>因为 CommonAnnotationBeanPostProcessor 会在 Before 阶段
	 * 扫描并调用 @PostConstruct 方法。<br/>
	 * 返回值可能被替换（任何一个 BPP 都可以返回一个不同的对象来代替原始 Bean）。<br/>
	 * 如果 beanName 以 ".ORIGINAL" 结尾，BPP 会跳过处理，强制返回原始实例。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * initializeBean 是"完整开机调试"（Aware → BPP前 → init → BPP后）。<br/>
	 * 这个方法只做其中一步："让所有质检员轮流做<b>前置检查</b>"。<br/>
	 * 第一个质检员检查完，把机器传给第二个，第二个传给第三个……<br/>
	 * 每个质检员都可能偷偷换掉机器（返回代理），所以拿回来的可能不是原来那台！</blockquote>
	 * <p><b>【与 initializeBean 的关系——拆解图】</b></p>
	 * <pre>
	 * initializeBean 内部做了 4 件事：
	 *   ① invokeAwareMethods          （Aware 回调）
	 *   ② applyBPPBeforeInit          ← 👈 就是这个方法！@PostConstruct 在这里触发
	 *   ③ invokeInitMethods           （afterPropertiesSet + init-method）
	 *   ④ applyBPPAfterInit           ← 👈 就是下面那个方法！AOP 代理在这里创建
	 * </pre>
	 * <hr/>
	 * Apply {@link BeanPostProcessor BeanPostProcessors} to the given existing bean
	 * instance, invoking their {@code postProcessBeforeInitialization} methods.
	 * The returned bean instance may be a wrapper around the original.
	 * @param existingBean the existing bean instance
	 * @param beanName the name of the bean, to be passed to it if necessary
	 * (only passed to {@link BeanPostProcessor BeanPostProcessors};
	 * can follow the {@link #ORIGINAL_INSTANCE_SUFFIX} convention in order to
	 * enforce the given instance to be returned, i.e. no proxies etc)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws BeansException if any post-processing failed
	 * @see BeanPostProcessor#postProcessBeforeInitialization
	 * @see #ORIGINAL_INSTANCE_SUFFIX
	 */
	Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException;

	/**
	 * <h3>⚙️ 方法 9：Object applyBeanPostProcessorsAfterInitialization(Object, String)</h3>
	 * <p><b>🏭【单独调用 BPP 后置处理——AOP 代理就是在这一步创建的！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 遍历所有注册的 BeanPostProcessor，逐个调用 {@code postProcessAfterInitialization(bean, beanName)}。<br/>
	 * 这是 initializeBean 内部第④步的独立 API。<br/>
	 * <b>AOP 代理创建就发生在这一步！</b>{@code AbstractAutoProxyCreator.postProcessAfterInitialization()}
	 * 会在这里判断 Bean 是否需要代理，如果需要则创建 JDK 动态代理或 CGLIB 代理并返回代理对象替代原始 Bean。<br/>
	 * 这就是为什么 @Transactional/@Async/@Cacheable 标注的 Bean 最终拿到的是代理对象——就是这一步被换掉的！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 机器已经造好、装好、调试好了（前置质检+init都过了），现在做<b>最终质检</b>。<br/>
	 * 最后一个质检员（AbstractAutoProxyCreator）看了看这台机器："嗯，它标了 @Transactional，需要套安全外壳！"<br/>
	 * 于是造了一个外壳（Proxy），把原始机器装进去，返回外壳。<br/>
	 * <b>从此以后，所有人拿到的都是这个外壳（代理对象），不是原始机器！</b><br/>
	 * 你调用 proxy.doSomething() → 外壳先做事务开启 → 转发给内部原始机器执行 → 外壳做事务提交/回滚。</blockquote>
	 * <hr/>
	 * Apply {@link BeanPostProcessor BeanPostProcessors} to the given existing bean
	 * instance, invoking their {@code postProcessAfterInitialization} methods.
	 * The returned bean instance may be a wrapper around the original.
	 * @param existingBean the existing bean instance
	 * @param beanName the name of the bean, to be passed to it if necessary
	 * (only passed to {@link BeanPostProcessor BeanPostProcessors};
	 * can follow the {@link #ORIGINAL_INSTANCE_SUFFIX} convention in order to
	 * enforce the given instance to be returned, i.e. no proxies etc)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws BeansException if any post-processing failed
	 * @see BeanPostProcessor#postProcessAfterInitialization
	 * @see #ORIGINAL_INSTANCE_SUFFIX
	 */
	Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException;

	/**
	 * <h3>⚙️ 方法 10：void destroyBean(Object existingBean)</h3>
	 * <p><b>🏭【拆机报废——按规矩走销毁流程！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 销毁一个 Bean 实例，触发 DisposableBean.destroy() + @PreDestroy + DestructionAwareBPP 回调。<br/>
	 * 通常用于销毁 createBean() 创建的"编外"实例——它们不在容器托管中，不会被容器自动销毁。<br/>
	 * 销毁过程中的异常会被捕获并记录日志，不会向上抛出。</p>
	 * <hr/>
	 * Destroy the given bean instance (typically coming from {@link #createBean}),
	 * applying the {@link org.springframework.beans.factory.DisposableBean} contract as well as
	 * registered {@link DestructionAwareBeanPostProcessor DestructionAwareBeanPostProcessors}.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * @param existingBean the bean instance to destroy
	 */
	void destroyBean(Object existingBean);


	/* =======================================================================================================
	         🎯 第三战区：注入点解析 —— 依赖解析的底层 API（DI 的最终落地点！）
	            @Autowired 注解驱动的注入最终都会走到这里！这是 DI 机制的"最后一公里"
	   =======================================================================================================*/

	/**
	 * <h3>🎯 方法 11：NamedBeanHolder&lt;T&gt; resolveNamedBean(Class&lt;T&gt;)</h3>
	 * <p><b>🏭【按类型解析并返回名字+实例——getBean(Class) 的增强版】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 和 getBean(Class) 一样按类型查找唯一匹配的 Bean，但返回值不仅包含实例，还包含 Bean 的名称！<br/>
	 * 返回 {@code NamedBeanHolder<T>}，里面有 beanName + beanInstance。<br/>
	 * 用途：当你不仅需要实例，还需要知道"这个实例对应的 Bean 名字是什么"时。</p>
	 * <hr/>
	 * Resolve the bean instance that uniquely matches the given object type, if any,
	 * including its bean name.
	 * <p>This is effectively a variant of {@link #getBean(Class)} which preserves the
	 * bean name of the matching instance.
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return the bean name plus bean instance
	 * @throws NoSuchBeanDefinitionException if no matching bean was found
	 * @throws NoUniqueBeanDefinitionException if more than one matching bean was found
	 * @throws BeansException if the bean could not be created
	 * @since 4.3.3
	 * @see #getBean(Class)
	 */
	<T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException;

	/**
	 * <h3>🎯 方法 12：Object resolveBeanByName(String name, DependencyDescriptor)</h3>
	 * <p><b>🏭【按名字解析——带注入点上下文信息的 getBean】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * getBean(String, Class) 的变体——多了一个 DependencyDescriptor 参数，携带了"谁在请求、注入点在哪"的上下文。<br/>
	 * 让工厂方法可以通过 {@code InjectionPoint} 参数感知到是哪个注入点在请求它。</p>
	 * <hr/>
	 * Resolve a bean instance for the given bean name, providing a dependency descriptor
	 * for exposure to target factory methods.
	 * <p>This is effectively a variant of {@link #getBean(String, Class)} which supports
	 * factory methods with an {@link org.springframework.beans.factory.InjectionPoint}
	 * argument.
	 * @param name the name of the bean to look up
	 * @param descriptor the dependency descriptor for the requesting injection point
	 * @return the corresponding bean instance
	 * @throws NoSuchBeanDefinitionException if there is no bean with the specified name
	 * @throws BeansException if the bean could not be created
	 * @since 5.1.5
	 * @see #getBean(String, Class)
	 */
	Object resolveBeanByName(String name, DependencyDescriptor descriptor) throws BeansException;

	/**
	 * <h3>🎯 方法 13 & 14：Object resolveDependency(DependencyDescriptor, ...)</h3>
	 * <p><b>🏭【DI 的最后一公里——@Autowired 最终就是调到这里！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * <b>这是 Spring DI 的终极解析方法！</b>当 AutowiredAnnotationBeanPostProcessor 遇到一个 @Autowired 注入点时，
	 * 最终会调用这个方法来解析依赖。<br/>
	 * {@code DependencyDescriptor} 描述了注入点的所有信息：字段/方法/构造器参数、类型、泛型、@Qualifier 等。<br/>
	 * 四参数版本额外提供：{@code autowiredBeanNames}（输出参数，收集被注入的 Bean 名称）、
	 * {@code typeConverter}（类型转换器）。<br/>
	 * 可以返回 null（表示依赖可选且未找到）。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * @Autowired 的完整调用链：<br/>
	 * ① BPP 扫描到 @Autowired 注入点 → 构建 DependencyDescriptor（描述要什么）<br/>
	 * ② 调用 resolveDependency(descriptor, "requestingBeanName") <br/>
	 * ③ 工厂根据 descriptor 里的类型/泛型/@Qualifier 信息去匹配 Bean<br/>
	 * ④ 找到了返回实例，找不到根据 required 属性决定报错还是返回 null<br/>
	 * <b>这就是"@Autowired 背后到底发生了什么"的终极答案！</b></blockquote>
	 * <hr/>
	 * Resolve the specified dependency against the beans defined in this factory.
	 * @param descriptor the descriptor for the dependency (field/method/constructor)
	 * @param requestingBeanName the name of the bean which declares the given dependency
	 * @return the resolved object, or {@code null} if none found
	 * @throws NoSuchBeanDefinitionException if no matching bean was found
	 * @throws NoUniqueBeanDefinitionException if more than one matching bean was found
	 * @throws BeansException if dependency resolution failed for any other reason
	 * @since 2.5
	 * @see #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)
	 */
	@Nullable
	Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException;

	/**
	 * <h3>🎯 方法 14：resolveDependency —— 四参数完整版</h3>
	 * <p>同方法 13，多了 {@code autowiredBeanNames}（输出参数，记录依赖了哪些 Bean）和
	 * {@code typeConverter}（自定义类型转换器）。详见方法 13。</p>
	 * <hr/>
	 * Resolve the specified dependency against the beans defined in this factory.
	 * @param descriptor the descriptor for the dependency (field/method/constructor)
	 * @param requestingBeanName the name of the bean which declares the given dependency
	 * @param autowiredBeanNames a Set that all names of autowired beans (used for
	 * resolving the given dependency) are supposed to be added to
	 * @param typeConverter the TypeConverter to use for populating arrays and collections
	 * @return the resolved object, or {@code null} if none found
	 * @throws NoSuchBeanDefinitionException if no matching bean was found
	 * @throws NoUniqueBeanDefinitionException if more than one matching bean was found
	 * @throws BeansException if dependency resolution failed for any other reason
	 * @since 2.5
	 * @see DependencyDescriptor
	 */
	@Nullable
	Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException;

}
