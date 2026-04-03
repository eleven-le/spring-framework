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

package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BeanFactory 的"横向扩展"——赋予工厂批量枚举的能力！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.ListableBeanFactory}</li>
 * <li><b>中文名</b>：可枚举 Bean 工厂 —— 超级工厂的"库存盘点系统"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块</li>
 * <li><b>接口层级</b>：{@code BeanFactory} 的直系子接口，增加了 <b>15 个方法</b>！</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个子接口？——从"精确提货"到"全厂盘点"的能力跃迁！</h3>
 * <p>回到 {@code BeanFactory}——它是"总服务窗口"，你来提货必须报名字或型号：{@code getBean("orderService")}。
 * 这是<b>精确的点查询</b>——你知道你要什么，工厂精确地给你。</p>
 * <p>但有一类需求 {@code BeanFactory} 完全满足不了——<b>"我不知道具体要哪个，我要所有符合条件的！"</b></p>
 * <ul>
 * <li>Spring AOP：需要找到<b>所有 Advisor 类型的 Bean</b>来构建拦截链</li>
 * <li>Spring MVC：需要找到<b>所有 HandlerMapping 类型的 Bean</b>来注册请求路由</li>
 * <li>Spring Event：需要找到<b>所有 ApplicationListener 类型的 Bean</b>来构建监听器列表</li>
 * <li>你自己的业务：需要找到<b>所有实现了 PaymentStrategy 接口的 Bean</b>来构建支付策略集</li>
 * </ul>
 * <p>这就是 ListableBeanFactory 诞生的根本原因：<b>把"批量枚举/发现"的能力从"单点查询"中剥离出来</b>。</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>接口隔离原则（ISP）的"成本驱动"拆分</b><br/>
 * 为什么不把这 15 个方法直接加到 BeanFactory 上？因为<b>批量枚举是昂贵的</b>！Spring 在 Javadoc 中明确说了：
 * "methods in this interface are not designed for frequent invocation. Implementations may be slow."<br/>
 * 如果放在 BeanFactory 上，所有使用者（包括只需要 getBean 的人）都要承担这个"沉重的契约"。<br/>
 * <b>业务借鉴</b>：当你设计服务接口时，把"轻量高频操作"和"重量低频操作"拆到不同接口。
 * 比如 {@code OrderQueryService}（单笔查询，毫秒级）和 {@code OrderReportService}（全量统计，秒级）应该是两个接口，
 * 因为它们的性能特征、调用频率、SLA 完全不同。一个接口塞进去，下游消费者根本分不清哪些能高频调哪些不能。</li>
 *
 * <li><b>"发现机制"是框架的核心竞争力</b><br/>
 * BeanFactory 的 getBean 是"我知道我要什么"——这是应用代码的思维。<br/>
 * ListableBeanFactory 的 getBeansOfType/getBeanNamesForAnnotation 是"我不知道有什么，帮我发现"——这是<b>框架的思维</b>。<br/>
 * Spring 之所以强大，核心原因之一就是它的"自动发现"能力：你不需要手动注册每个 Listener、每个 Advisor、每个 Controller，
 * Spring 通过 ListableBeanFactory 自动发现并组装它们。<br/>
 * <b>业务借鉴</b>：当你设计插件系统/策略系统/规则引擎时，一定要有"发现层"。
 * 比如支付策略不要硬编码 {@code if-else}，而是定义 {@code PaymentStrategy} 接口，
 * 然后用类似 {@code getBeansOfType(PaymentStrategy.class)} 的方式自动发现所有策略实现——新增策略只需加一个 Bean，零改动！</li>
 *
 * <li><b>"只看本厂"的数据边界哲学</b><br/>
 * ListableBeanFactory 有一个重要约定：<b>所有枚举方法只返回当前工厂的 Bean，不向父工厂递归！</b><br/>
 * 这和 HierarchicalBeanFactory 的双亲委派形成了有趣的对比——点查询可以委托，批量枚举不能委托。为什么？
 * 因为枚举操作的语义是"盘点"，盘点必须有明确边界，否则子容器的盘点会被父容器的数据污染，结果不可预期。<br/>
 * <b>业务借鉴</b>：数据查询 API 设计中，聚合/统计/列表类接口必须明确数据边界。
 * 比如"门店级报表"就只看本门店，不能自动汇总到区域——汇总是上层的事。边界不清是数据混乱的万恶之源。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * BeanFactory
 * ├── HierarchicalBeanFactory      （纵向：父子层级）
 * ├── ListableBeanFactory           ← 👈 你在这里！（横向：批量枚举）
 * │     └── ConfigurableListableBeanFactory（终极合体）
 * └── AutowireCapableBeanFactory    （能力：自动装配）
 * </pre>
 * <p>HierarchicalBeanFactory 让工厂有了"深度"（纵向层级），ListableBeanFactory 让工厂有了"广度"（横向扫描）。
 * 两者正交互补，最终在 {@code DefaultListableBeanFactory} 中合流。</p>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>15 个方法，划分为 <b>五大战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>使命</th><th>成员数</th></tr>
 * <tr><td><b>📋 第一战区：BD 档案室</b></td><td>查图纸（BeanDefinition）的存在性、总数、名单</td><td>3</td></tr>
 * <tr><td><b>🎫 第二战区：延迟提货券增强</b></td><td>带"是否允许提前初始化"开关的 ObjectProvider</td><td>2</td></tr>
 * <tr><td><b>🔎 第三战区：按类型批量查名</b></td><td>给我所有某型号机器的名字清单（不提货！）</td><td>4</td></tr>
 * <tr><td><b>📦 第四战区：按类型批量提货</b></td><td>给我所有某型号机器的实例（真的提货！名→实例 Map）</td><td>2</td></tr>
 * <tr><td><b>🏷️ 第五战区：按注解查找</b></td><td>按"标签"（注解）找 Bean——这是注解驱动时代的核心！</td><td>4</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>ListableBeanFactory 的核心价值：<b>把工厂从"被动响应"升级为"主动发现"</b>。<br/>
 * BeanFactory 是"你问我答"，ListableBeanFactory 是"我能帮你把所有符合条件的都翻出来"。<br/>
 * 这个能力升级让 Spring 从一个简单的 IoC 容器，进化为一个具备<b>自动组装、自动发现、自动编排</b>能力的框架引擎。<br/>
 * 你设计业务框架时，也应该思考：你的系统是只能"被动提货"，还是也能"主动发现"？</p>
 *
 * <hr/>
 * Extension of the {@link BeanFactory} interface to be implemented by bean factories
 * that can enumerate all their bean instances, rather than attempting bean lookup
 * by name one by one as requested by clients. BeanFactory implementations that
 * preload all their bean definitions (such as XML-based factories) may implement
 * this interface.
 *
 * <p>If this is a {@link HierarchicalBeanFactory}, the return values will <i>not</i>
 * take any BeanFactory hierarchy into account, but will relate only to the beans
 * defined in the current factory. Use the {@link BeanFactoryUtils} helper class
 * to consider beans in ancestor factories too.
 *
 * <p>The methods in this interface will just respect bean definitions of this factory.
 * They will ignore any singleton beans that have been registered by other means like
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}'s
 * {@code registerSingleton} method, with the exception of
 * {@code getBeanNamesForType} and {@code getBeansOfType} which will check
 * such manually registered singletons too. Of course, BeanFactory's {@code getBean}
 * does allow transparent access to such special beans as well. However, in typical
 * scenarios, all beans will be defined by external bean definitions anyway, so most
 * applications don't need to worry about this differentiation.
 *
 * <p><b>NOTE:</b> With the exception of {@code getBeanDefinitionCount}
 * and {@code containsBeanDefinition}, the methods in this interface
 * are not designed for frequent invocation. Implementations may be slow.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16 April 2001
 * @see HierarchicalBeanFactory
 * @see BeanFactoryUtils
 */
public interface ListableBeanFactory extends BeanFactory {

	/* =======================================================================================================
	              📋 第一战区：BD 档案室 —— 查图纸的存在性、总数、名单（只看 BeanDefinition，不看手动注册的 Singleton！）
	   =======================================================================================================*/

	/**
	 * <h3>📋 方法 1：boolean containsBeanDefinition(String beanName)</h3>
	 * <p><b>🏭【档案室查图纸——这张生产图纸（BD）在不在？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 检查本工厂是否有指定名称的 BeanDefinition（生产图纸）。两个关键限制：<br/>
	 * ① <b>不查父工厂</b>——只翻本厂档案柜<br/>
	 * ② <b>不看手动注册的 Singleton</b>——只认正式图纸（BD），不认通过 {@code registerSingleton()} 塞进来的"编外人员"</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你问档案室："生产图纸里有 orderService 这台机器的设计图吗？"<br/>
	 * 档案员只翻本厂的图纸柜——有！true。没有？false。<br/>
	 * 注意：就算仓库里有一台手工塞进来的 orderService（registerSingleton），档案员也不认——"图纸柜里没有就是没有！"<br/>
	 * 也不会打电话问总厂的档案室！</blockquote>
	 * <p><b>【与 containsBean 的对比】</b><br/>
	 * {@code BeanFactory.containsBean()} → 查"工厂里有没有这个 Bean"（BD + 手动注册 + 父工厂递归）<br/>
	 * {@code containsBeanDefinition()} → 查"本厂的图纸柜里有没有这张图纸"（仅 BD，仅本厂）<br/>
	 * 一个是"宽泛地查人"，一个是"精确地查档案"。</p>
	 * <hr/>
	 * Check if this bean factory contains a bean definition with the given name.
	 * <p>Does not consider any hierarchy this factory may participate in,
	 * and ignores any singleton beans that have been registered by
	 * other means than bean definitions.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 */
	boolean containsBeanDefinition(String beanName);

	/**
	 * <h3>📋 方法 2：int getBeanDefinitionCount()</h3>
	 * <p><b>🏭【档案室盘点——本厂一共有多少张生产图纸？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回本工厂注册的 BeanDefinition 总数。同样不含父工厂、不含手动注册的 Singleton。<br/>
	 * 这是一个轻量级的计数操作，可以用于监控和诊断。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "本厂图纸柜里一共有多少张设计图？" → "357 张！"<br/>
	 * 这是 Spring 启动后快速掌握容器规模的好方法——一个应用到底注册了多少个 Bean？</blockquote>
	 * <hr/>
	 * Return the number of beans defined in the factory.
	 * <p>Does not consider any hierarchy this factory may participate in,
	 * and ignores any singleton beans that have been registered by
	 * other means than bean definitions.
	 * @return the number of beans defined in the factory
	 */
	int getBeanDefinitionCount();

	/**
	 * <h3>📋 方法 3：String[] getBeanDefinitionNames()</h3>
	 * <p><b>🏭【档案室花名册——列出所有图纸的名字！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回本工厂所有 BeanDefinition 的名称数组。这是"全量枚举"的起点——
	 * 很多框架内部操作（如 {@code ConfigurationClassPostProcessor} 扫描 @Configuration）的第一步就是调这个方法，
	 * 拿到所有名字后再逐个筛选。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "把图纸柜里所有图纸的名字念给我听！" → ["userService", "orderService", "dataSource", ...]<br/>
	 * 这是"盘点"的第一步——先拿到清单，再筛选你要的。</blockquote>
	 * <hr/>
	 * Return the names of all beans defined in this factory.
	 * <p>Does not consider any hierarchy this factory may participate in,
	 * and ignores any singleton beans that have been registered by
	 * other means than bean definitions.
	 * @return the names of all beans defined in this factory,
	 * or an empty array if none defined
	 */
	String[] getBeanDefinitionNames();

	/* =======================================================================================================
	              🎫 第二战区：延迟提货券增强 —— 比 BeanFactory 的 getBeanProvider 多了一个"初始化开关"
	   =======================================================================================================*/

	/**
	 * <h3>🎫 方法 4 & 5：getBeanProvider(Class/ResolvableType, boolean allowEagerInit)</h3>
	 * <p><b>🏭【带初始化开关的延迟提货券】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 在 BeanFactory 的 getBeanProvider 基础上，增加了 {@code allowEagerInit} 开关：<br/>
	 * - {@code true}：为了查类型，可以提前初始化 lazy-init 的 Bean 和 FactoryBean<br/>
	 * - {@code false}：绝不触发初始化，只看已有元数据能推断出的类型<br/>
	 * 这个开关的价值：<b>在"发现"阶段控制副作用</b>——你只是想看看有什么，不想因为"看一眼"就把整个工厂启动了！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你领了一张 VIP 提货券，准备查看仓库里有多少同型号的机器。<br/>
	 * allowEagerInit=false → "只看标签和图纸上写的型号，别为了查型号就把机器点火启动！"<br/>
	 * allowEagerInit=true → "为了确认型号，可以把机器启动一下看看它产出什么！"</blockquote>
	 * <hr/>
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @param allowEagerInit whether stream-based access may initialize <i>lazy-init
	 * singletons</i> and <i>objects created by FactoryBeans</i> (or by factory methods
	 * with a "factory-bean" reference) for the type check
	 * @return a corresponding provider handle
	 * @since 5.3
	 * @see #getBeanProvider(ResolvableType, boolean)
	 * @see #getBeanProvider(Class)
	 * @see #getBeansOfType(Class, boolean, boolean)
	 * @see #getBeanNamesForType(Class, boolean, boolean)
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit);

	/**
	 * <h3>🎫 方法 5：getBeanProvider(ResolvableType, boolean) —— 泛型精确匹配版</h3>
	 * <p>同上，但支持 {@code ResolvableType} 泛型精确匹配。详见方法 4 的说明。</p>
	 * <hr/>
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * @param requiredType type the bean must match; can be a generic type declaration.
	 * Note that collection types are not supported here, in contrast to reflective
	 * injection points. For programmatically retrieving a list of beans matching a
	 * specific type, specify the actual bean type as an argument here and subsequently
	 * use {@link ObjectProvider#orderedStream()} or its lazy streaming/iteration options.
	 * @param allowEagerInit whether stream-based access may initialize <i>lazy-init
	 * singletons</i> and <i>objects created by FactoryBeans</i> (or by factory methods
	 * with a "factory-bean" reference) for the type check
	 * @return a corresponding provider handle
	 * @since 5.3
	 * @see #getBeanProvider(ResolvableType)
	 * @see ObjectProvider#iterator()
	 * @see ObjectProvider#stream()
	 * @see ObjectProvider#orderedStream()
	 * @see #getBeanNamesForType(ResolvableType, boolean, boolean)
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit);

	/* =======================================================================================================
	              🔎 第三战区：按类型批量查名 —— 给我所有某型号机器的名字清单（不提货！只查清单！）
	                       4 个方法 = 2 种类型参数(Class/ResolvableType) × 2 种精细度(简版/带开关)
	   =======================================================================================================*/

	/**
	 * <h3>🔎 方法 6：String[] getBeanNamesForType(ResolvableType type)</h3>
	 * <p><b>🏭【按型号批量翻花名册——给我所有这个型号的机器名字！（泛型精确版·简版）】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 这是 ListableBeanFactory 最核心的"发现"方法之一！返回所有匹配指定类型的 Bean 名称数组。<br/>
	 * <b>关键行为：</b>
	 * <ul>
	 * <li>匹配包含子类——你查 {@code DataSource}，{@code HikariDataSource} 也会被找到</li>
	 * <li>FactoryBean 会被初始化——为了确定它生产的产品类型。如果产品不匹配，FactoryBean 本身也会被检查</li>
	 * <li><b>不查父工厂！</b>只看本厂。要查父工厂请用 {@code BeanFactoryUtils.beanNamesForTypeIncludingAncestors()}</li>
	 * <li><b>包含手动注册的 Singleton！</b>（这是与其他方法的重要区别——containsBeanDefinition 不认手动注册的，但这个方法认！）</li>
	 * <li>返回顺序：按 Bean 定义的注册顺序</li>
	 * </ul>
	 * 简版 = 等价于 {@code getBeanNamesForType(type, true, true)}，即包含所有 scope + 允许提前初始化。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你走进工厂说："把所有 PaymentStrategy 型号的机器名字都报给我！"<br/>
	 * 仓管员翻遍花名册 + 已入库的现货："alipayStrategy, wechatPayStrategy, unionPayStrategy！"<br/>
	 * 你拿到名字清单后，可以自己决定要不要逐个提货。<b>注意：仓管员只报名字，不搬货！</b></blockquote>
	 * <p><b>【Spring 内部谁在用？】</b><br/>
	 * 这个方法是 Spring 自动发现机制的基石！比如：<br/>
	 * - {@code PostProcessorRegistrationDelegate}：用它找到所有 BeanPostProcessor 类型的 Bean 名字<br/>
	 * - {@code EventListenerMethodProcessor}：用它找到所有标注了 @EventListener 的 Bean 名字<br/>
	 * - {@code AnnotationAwareAspectJAutoProxyCreator}：用它找到所有 Advisor/Aspect 类型的 Bean 名字</p>
	 * <hr/>
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
	 * will get initialized. If the object created by the FactoryBean doesn't match,
	 * the raw FactoryBean itself will be matched against the type.
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * <p>This version of {@code getBeanNamesForType} matches all kinds of beans,
	 * be it singletons, prototypes, or FactoryBeans. In most implementations, the
	 * result will be the same as for {@code getBeanNamesForType(type, true, true)}.
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * @param type the generically typed class or interface to match
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @since 4.2
	 * @see #isTypeMatch(String, ResolvableType)
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, ResolvableType)
	 */
	String[] getBeanNamesForType(ResolvableType type);

	/**
	 * <h3>🔎 方法 7：getBeanNamesForType(ResolvableType, boolean includeNonSingletons, boolean allowEagerInit)</h3>
	 * <p><b>🏭【按型号批量翻花名册——带精细控制开关的版本！】</b></p>
	 * <p><b>【两个开关的含义】</b><br/>
	 * <ul>
	 * <li>{@code includeNonSingletons}：是否包含 Prototype/Scoped Bean？false = 只看 Singleton</li>
	 * <li>{@code allowEagerInit}：是否允许为了查类型而提前初始化 lazy-init Bean / FactoryBean？</li>
	 * </ul>
	 * <b>典型组合：</b><br/>
	 * - {@code (true, true)}：最宽松，等价于简版。常用于框架启动扫描阶段<br/>
	 * - {@code (true, false)}：安全模式——查类型但不触发初始化。用于 BFPP 阶段（此时 Bean 还不该被创建！）<br/>
	 * - {@code (false, true)}：只要 Singleton。用于预热/预加载场景</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "给我所有 EventListener 型号的机器名字！但是——"<br/>
	 * includeNonSingletons=false → "只要长期驻厂的（Singleton），临时工（Prototype）不要！"<br/>
	 * allowEagerInit=false → "别为了查型号就把还在睡觉的机器（lazy-init）叫醒！看图纸就行！"</blockquote>
	 * <hr/>
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
	 * which means that FactoryBeans will get initialized. If the object created by the
	 * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
	 * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
	 * (which doesn't require initialization of each FactoryBean).
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * @param type the generically typed class or interface to match
	 * @param includeNonSingletons whether to include prototype or scoped beans too
	 * or just singletons (also applies to FactoryBeans)
	 * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and
	 * <i>objects created by FactoryBeans</i> (or by factory methods with a
	 * "factory-bean" reference) for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references.
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @since 5.2
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, ResolvableType, boolean, boolean)
	 */
	String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit);

	/**
	 * <h3>🔎 方法 8：getBeanNamesForType(Class) —— Class 版简版，同方法 6</h3>
	 * <p>与方法 6 相同逻辑，只是参数从 ResolvableType 换成 Class——不需要泛型精确匹配时用这个更方便。<br/>
	 * 传 {@code null} 可以获取<b>所有</b> Bean 名称！</p>
	 * <hr/>
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
	 * will get initialized. If the object created by the FactoryBean doesn't match,
	 * the raw FactoryBean itself will be matched against the type.
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * <p>This version of {@code getBeanNamesForType} matches all kinds of beans,
	 * be it singletons, prototypes, or FactoryBeans. In most implementations, the
	 * result will be the same as for {@code getBeanNamesForType(type, true, true)}.
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * @param type the class or interface to match, or {@code null} for all bean names
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class)
	 */
	String[] getBeanNamesForType(@Nullable Class<?> type);

	/**
	 * <h3>🔎 方法 9：getBeanNamesForType(Class, boolean, boolean) —— Class 版带开关，同方法 7</h3>
	 * <p>与方法 7 相同逻辑，参数从 ResolvableType 换成 Class。两个开关的含义完全一致。</p>
	 * <hr/>
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
	 * which means that FactoryBeans will get initialized. If the object created by the
	 * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
	 * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
	 * (which doesn't require initialization of each FactoryBean).
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * @param type the class or interface to match, or {@code null} for all bean names
	 * @param includeNonSingletons whether to include prototype or scoped beans too
	 * or just singletons (also applies to FactoryBeans)
	 * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and
	 * <i>objects created by FactoryBeans</i> (or by factory methods with a
	 * "factory-bean" reference) for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references.
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
	 */
	String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit);

	/* =======================================================================================================
	              📦 第四战区：按类型批量提货 —— 不仅要名字，直接把实例搬出来！返回 Map<名字, 实例>
	   =======================================================================================================*/

	/**
	 * <h3>📦 方法 10：Map&lt;String, T&gt; getBeansOfType(Class&lt;T&gt; type)</h3>
	 * <p><b>🏭【按型号批量提货——把所有同型号的机器连人带货一起搬出来！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 与第三战区的 getBeanNamesForType 不同——那个只返回名字清单，这个直接返回<b>名字 → 实例</b>的 Map！<br/>
	 * 也就是说，调用这个方法会<b>真正触发 Bean 的创建</b>（如果还没创建的话）。<br/>
	 * 返回的 Map 是有序的（LinkedHashMap），按 Bean 定义的注册顺序排列。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 第三战区：你说"报名字！"——仓管员只念名字给你听。<br/>
	 * 第四战区：你说"搬出来！"——仓管员直接把所有同型号的机器都推到你面前：<br/>
	 * {@code {"alipayStrategy" → AlipayStrategy实例, "wechatPayStrategy" → WechatPayStrategy实例}}<br/>
	 * <b>注意：搬货比报名字重得多！因为机器可能还没造，要现场造！</b></blockquote>
	 * <p><b>【使用场景——策略模式自动发现】</b><br/>
	 * // 自动发现所有支付策略，构建策略 Map<br/>
	 * {@code Map<String, PaymentStrategy> strategies = beanFactory.getBeansOfType(PaymentStrategy.class);}<br/>
	 * // 根据订单的支付方式，从 Map 中取出对应策略执行——新增支付方式只需加一个 Bean，零改动！</p>
	 * <hr/>
	 * Return the bean instances that match the given object type (including
	 * subclasses), judging from either bean definitions or the value of
	 * {@code getObjectType} in the case of FactoryBeans.
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
	 * will get initialized. If the object created by the FactoryBean doesn't match,
	 * the raw FactoryBean itself will be matched against the type.
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * <p>This version of getBeansOfType matches all kinds of beans, be it
	 * singletons, prototypes, or FactoryBeans. In most implementations, the
	 * result will be the same as for {@code getBeansOfType(type, true, true)}.
	 * <p>The Map returned by this method should always return bean names and
	 * corresponding bean instances <i>in the order of definition</i> in the
	 * backend configuration, as far as possible.
	 * @param type the class or interface to match, or {@code null} for all concrete beans
	 * @return a Map with the matching beans, containing the bean names as
	 * keys and the corresponding bean instances as values
	 * @throws BeansException if a bean could not be created
	 * @since 1.1.2
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class)
	 */
	<T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException;

	/**
	 * <h3>📦 方法 11：getBeansOfType(Class, boolean, boolean) —— 带精细控制开关的批量提货</h3>
	 * <p>同方法 10，增加 {@code includeNonSingletons} 和 {@code allowEagerInit} 两个开关，含义同第三战区。</p>
	 * <hr/>
	 * Return the bean instances that match the given object type (including
	 * subclasses), judging from either bean definitions or the value of
	 * {@code getObjectType} in the case of FactoryBeans.
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
	 * which means that FactoryBeans will get initialized. If the object created by the
	 * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
	 * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
	 * (which doesn't require initialization of each FactoryBean).
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * <p>The Map returned by this method should always return bean names and
	 * corresponding bean instances <i>in the order of definition</i> in the
	 * backend configuration, as far as possible.
	 * @param type the class or interface to match, or {@code null} for all concrete beans
	 * @param includeNonSingletons whether to include prototype or scoped beans too
	 * or just singletons (also applies to FactoryBeans)
	 * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and
	 * <i>objects created by FactoryBeans</i> (or by factory methods with a
	 * "factory-bean" reference) for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references.
	 * @return a Map with the matching beans, containing the bean names as
	 * keys and the corresponding bean instances as values
	 * @throws BeansException if a bean could not be created
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
	 */
	<T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException;

	/* =======================================================================================================
	          🏷️ 第五战区：按注解查找 —— 注解驱动时代的核心发现机制！
	             前面的战区按"类型"（Class/ResolvableType）发现，这里按"标签"（Annotation）发现！
	   =======================================================================================================*/

	/**
	 * <h3>🏷️ 方法 12：String[] getBeanNamesForAnnotation(Class&lt;? extends Annotation&gt;)</h3>
	 * <p><b>🏭【按标签翻花名册——所有贴了这个标签的机器，报名字！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回所有标注了指定注解的 Bean 名称。检查范围包括类级别、接口级别、工厂方法级别的注解。<br/>
	 * <b>不会创建 Bean 实例！</b>只是扫描元数据。但 FactoryBean 会被初始化以确定类型。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "所有身上贴了 @Service 标签的机器，把名字报给我！" → ["userService", "orderService", ...]<br/>
	 * "所有贴了 @Scheduled 标签的机器呢？" → ["reportJob", "cleanupJob", ...]<br/>
	 * 注意：只报名字，不搬货！</blockquote>
	 * <p><b>【Spring 内部谁在用？】</b><br/>
	 * {@code EventListenerMethodProcessor} 用这种模式找到所有需要处理事件的 Bean，
	 * 然后逐个扫描它们的方法上是否有 @EventListener 注解。这就是"发现→筛选→注册"三步曲的第一步！</p>
	 * <hr/>
	 * Find all names of beans which are annotated with the supplied {@link Annotation}
	 * type, without creating corresponding bean instances yet.
	 * <p>Note that this method considers objects created by FactoryBeans, which means
	 * that FactoryBeans will get initialized in order to determine their object type.
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @return the names of all matching beans
	 * @since 4.0
	 * @see #findAnnotationOnBean
	 */
	String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType);

	/**
	 * <h3>🏷️ 方法 13：Map&lt;String, Object&gt; getBeansWithAnnotation(Class&lt;? extends Annotation&gt;)</h3>
	 * <p><b>🏭【按标签批量提货——所有贴了这个标签的机器，连人带货搬出来！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 方法 12 只返回名字，这个方法返回 {@code Map<名字, 实例>}——直接拿到 Bean 实例！<br/>
	 * 注意返回值类型是 {@code Map<String, Object>}（不是泛型 T），因为按注解查找无法推断具体类型。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "所有贴了 @RestController 标签的机器，搬出来让我看看！"<br/>
	 * → {@code {"userController" → UserController实例, "orderController" → OrderController实例}}</blockquote>
	 * <hr/>
	 * Find all beans which are annotated with the supplied {@link Annotation} type,
	 * returning a Map of bean names with corresponding bean instances.
	 * <p>Note that this method considers objects created by FactoryBeans, which means
	 * that FactoryBeans will get initialized in order to determine their object type.
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @return a Map with the matching beans, containing the bean names as
	 * keys and the corresponding bean instances as values
	 * @throws BeansException if a bean could not be created
	 * @since 3.0
	 * @see #findAnnotationOnBean
	 */
	Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException;

	/**
	 * <h3>🏷️ 方法 14 & 15：findAnnotationOnBean —— 精确查看某台机器身上的特定标签</h3>
	 * <p><b>🏭【单台机器验标签——这台机器身上有没有某个特定标签？有的话，标签上写了什么？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 前面方法是"给我所有贴了某标签的机器"——先按标签筛，再看机器。<br/>
	 * 这个方法反过来——<b>先指定机器（beanName），再查它身上有没有某个标签</b>。<br/>
	 * 查找范围：类本身 → 接口 → 父类 → 工厂方法（逐级搜索，不放过任何角落！）<br/>
	 * 找到了返回注解实例（可以读取注解属性值），找不到返回 null。<br/>
	 * 方法 15（三参数版）多了 {@code allowFactoryBeanInit} 开关。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "那台叫 orderService 的机器，身上有没有贴 @Transactional 标签？"<br/>
	 * 质检员先看机器外壳（类本身）→ 再看操作手册（接口）→ 再看设计图纸（父类）→ 再看生产工单（工厂方法）<br/>
	 * "找到了！标签上写着 propagation=REQUIRED, rollbackFor=Exception.class！"<br/>
	 * → 返回 @Transactional 注解实例，你可以读取里面的属性值</blockquote>
	 * <hr/>
	 * Find an {@link Annotation} of {@code annotationType} on the specified bean,
	 * traversing its interfaces and superclasses if no annotation can be found on
	 * the given class itself, as well as checking the bean's factory method (if any).
	 * @param beanName the name of the bean to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @return the annotation of the given type if found, or {@code null} otherwise
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 3.0
	 * @see #getBeanNamesForAnnotation
	 * @see #getBeansWithAnnotation
	 * @see #getType(String)
	 */
	@Nullable
	<A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException;

	/**
	 * <h3>🏷️ 方法 15：findAnnotationOnBean —— 带 FactoryBean 初始化开关的版本</h3>
	 * <p>同方法 14，多了 {@code allowFactoryBeanInit} 参数控制是否为查注解而初始化 FactoryBean。</p>
	 * <hr/>
	 * Find an {@link Annotation} of {@code annotationType} on the specified bean,
	 * traversing its interfaces and superclasses if no annotation can be found on
	 * the given class itself, as well as checking the bean's factory method (if any).
	 * @param beanName the name of the bean to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 * just for the purpose of determining its object type
	 * @return the annotation of the given type if found, or {@code null} otherwise
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.3.14
	 * @see #getBeanNamesForAnnotation
	 * @see #getBeansWithAnnotation
	 * @see #getType(String, boolean)
	 */
	@Nullable
	<A extends Annotation> A findAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException;

}
