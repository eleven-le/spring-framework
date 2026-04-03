/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.AliasRegistry;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BeanDefinition 注册表——"图纸档案馆"的唯一入口协议！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.BeanDefinitionRegistry}</li>
 * <li><b>中文名</b>：Bean 定义注册表 —— 管理所有 Bean 图纸（BeanDefinition）的统一 CRUD 契约</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块</li>
 * <li><b>接口层级</b>：继承 {@code AliasRegistry}（因为注册图纸时同时要管理名字和别名）</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个接口？——"图纸管理"与"实例管理"是完全正交的两条线！</h3>
 * <p>Spring 容器的工作分为两大阶段：</p>
 * <ol>
 * <li><b>配置阶段（定义期）</b>：读取 XML/@Component/@Bean → 解析成 BeanDefinition → <b>注册到 Registry</b></li>
 * <li><b>运行阶段（运行期）</b>：getBean() → 从 Registry 取图纸 → 按图纸创建实例 → 返回</li>
 * </ol>
 * <p>如果把"图纸注册"和"实例获取"混在同一个接口里（比如全塞进 BeanFactory），会导致：</p>
 * <ul>
 * <li><b>职责混乱</b>：BeanFactory 的使用者（业务代码）只需要 getBean()，根本不应该看到 registerBeanDefinition()。
 *     把注册方法暴露给所有人，违反了<b>最小权限原则</b></li>
 * <li><b>读写者不分</b>：BeanDefinitionReader（XML 解析器、注解扫描器）是"图纸的写入者"，
 *     BeanFactory 是"图纸的消费者"。它们面向不同的接口：前者面向 BeanDefinitionRegistry，
 *     后者面向 BeanFactory——<b>CQRS 在接口层面的体现</b></li>
 * </ul>
 *
 * <h3>🧬 核心设计精髓</h3>
 * <ol>
 * <li><b>"唯一的注册入口"——原始 Javadoc 的重要声明</b><br/>
 * 原文说："This is the <b>only</b> interface in Spring's bean factory packages that encapsulates
 * <i>registration</i> of bean definitions."<br/>
 * 这不是随便说的——Spring 刻意把"图纸注册权"集中在这一个接口上，
 * 所有的 BeanDefinitionReader（XML/注解/Groovy）都只认这个接口。
 * 如果你想自定义图纸来源（比如从数据库加载 BeanDefinition），只需要实现这个接口即可。</li>
 *
 * <li><b>继承 AliasRegistry 的深层原因</b><br/>
 * 为什么 BeanDefinitionRegistry extends AliasRegistry？因为注册图纸时天然需要管理别名：<br/>
 * {@code <bean id="ds" name="dataSource,primaryDS"/>} 注册一张图纸的同时，
 * 要把 "dataSource" 和 "primaryDS" 作为 "ds" 的别名登记。<br/>
 * 继承 AliasRegistry 后，BeanDefinitionRegistry 的实现类自动获得别名管理能力，无需重复实现。</li>
 *
 * <li><b>"两棵树在 DefaultListableBeanFactory 合体"</b><br/>
 * DefaultListableBeanFactory 同时实现了 BeanFactory 体系（管实例）和 BeanDefinitionRegistry（管图纸），
 * 是两棵继承树的交汇点。但在接口层面，这两棵树是完全独立的——<br/>
 * 图纸的写入者（Reader/Scanner）只依赖 BeanDefinitionRegistry，<br/>
 * 图纸的消费者（getBean 流程）只依赖 BeanFactory，<br/>
 * 互不干扰，解耦彻底。</li>
 * </ol>
 *
 * <h3>🧬 业务借鉴——你的系统能偷师什么？</h3>
 * <ol>
 * <li><b>"元数据注册"与"运行时使用"分离</b><br/>
 * 在规则引擎场景中："规则定义的录入/修改"（类比 BeanDefinitionRegistry）和"规则的执行"（类比 BeanFactory）
 * 应该是两个独立的接口。规则编辑器面向 RuleRegistry，规则引擎面向 RuleExecutor，
 * 普通业务代码永远不应该看到"如何注册规则"的方法。</li>
 *
 * <li><b>"Reader 只认 Registry"的解耦模式</b><br/>
 * XmlBeanDefinitionReader 的构造器接收 BeanDefinitionRegistry（不是 BeanFactory）。
 * 这意味着 Reader 完全不知道"工厂怎么用图纸造实例"，它只负责"把图纸塞进档案馆"。<br/>
 * 业务借鉴：数据导入组件只依赖"数据仓库写入接口"，不依赖"数据查询/分析接口"。</li>
 *
 * <li><b>isBeanNameInUse() 的"写前检测"</b><br/>
 * 在注册图纸前，先检查名字是否已被占用（被别名或其他 Bean 使用）。<br/>
 * 业务借鉴：注册用户名前检查"用户名是否已存在"；创建路由规则前检查"路径是否已被其他规则占用"。
 * 写前检测比写后冲突回滚成本低得多。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位——第二棵树的主干</h3>
 * <pre>
 * AliasRegistry (spring-core, 绝对零点)
 * │
 * ├── (树1·实例管理) SimpleAliasRegistry → DefaultSingletonBeanRegistry → ... → DefaultListableBeanFactory
 * │
 * └── (树2·图纸管理) BeanDefinitionRegistry  ← 👈 你在这里！
 *       │
 *       ├── DefaultListableBeanFactory (核心实现：beanDefinitionMap + beanDefinitionNames)
 *       └── GenericApplicationContext (委托给内部的 DefaultListableBeanFactory)
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·7 个方法形成完整的"图纸档案馆 CRUD"</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th><th>对应操作</th></tr>
 * <tr><td>{@code registerBeanDefinition(name, bd)}</td><td>注册/覆盖图纸</td><td>Create / Update</td></tr>
 * <tr><td>{@code removeBeanDefinition(name)}</td><td>移除图纸</td><td>Delete</td></tr>
 * <tr><td>{@code getBeanDefinition(name)}</td><td>按名查图纸</td><td>Read（单条）</td></tr>
 * <tr><td>{@code containsBeanDefinition(name)}</td><td>图纸是否存在</td><td>Read（存在性探测）</td></tr>
 * <tr><td>{@code getBeanDefinitionNames()}</td><td>列出所有图纸名</td><td>Read（批量枚举）</td></tr>
 * <tr><td>{@code getBeanDefinitionCount()}</td><td>图纸总数</td><td>Read（度量）</td></tr>
 * <tr><td>{@code isBeanNameInUse(name)}</td><td>名字是否被占用</td><td>Read（写前检测）</td></tr>
 * </table>
 * <p><b>注意</b>：加上继承自 AliasRegistry 的 4 个方法（registerAlias/removeAlias/isAlias/getAliases），
 * BeanDefinitionRegistry 共暴露 <b>11 个方法</b>——涵盖了图纸 + 别名的全量管理能力。</p>
 *
 * <h3>🧠 三、与 SingletonBeanRegistry 的对称美</h3>
 * <p>Spring 中有两个独立的"注册表接口"，分别管理 Bean 生命周期的不同阶段：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>接口</th><th>管理对象</th><th>阶段</th><th>核心实现</th></tr>
 * <tr><td>{@code BeanDefinitionRegistry}</td><td>BeanDefinition（图纸）</td><td>配置期/定义期</td><td>DefaultListableBeanFactory.beanDefinitionMap</td></tr>
 * <tr><td>{@code SingletonBeanRegistry}</td><td>Singleton 实例（成品）</td><td>运行期</td><td>DefaultSingletonBeanRegistry.singletonObjects</td></tr>
 * </table>
 * <p>图纸先注册（BeanDefinitionRegistry），然后按图纸生产实例并缓存（SingletonBeanRegistry）。
 * 两个注册表各管各的，在 DefaultListableBeanFactory 中合体。</p>
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>BeanDefinitionRegistry 是 Spring 容器"配置阶段"的核心入口——所有的 XML 解析器、注解扫描器、
 * BDRPP（BeanDefinitionRegistryPostProcessor）都通过这个接口往容器里"塞图纸"。<br/>
 * 它与 BeanFactory 形成了完美的 CQRS 分离：Registry 负责写入图纸，BeanFactory 负责消费图纸。
 * 继承 AliasRegistry 让它天然具备别名管理能力，7+4=11 个方法涵盖了图纸+别名的全量 CRUD。<br/>
 * 在 DefaultListableBeanFactory 中，这个接口的实现就是两个核心字段：
 * {@code beanDefinitionMap}（ConcurrentHashMap，名字→图纸）和
 * {@code beanDefinitionNames}（ArrayList，保持注册顺序）。</p>
 *
 * <hr/>
 * Interface for registries that hold bean definitions, for example RootBeanDefinition
 * and ChildBeanDefinition instances. Typically implemented by BeanFactories that
 * internally work with the AbstractBeanDefinition hierarchy.
 *
 * <p>This is the only interface in Spring's bean factory packages that encapsulates
 * <i>registration</i> of bean definitions. The standard BeanFactory interfaces
 * only cover access to a <i>fully configured factory instance</i>.
 *
 * <p>Spring's bean definition readers expect to work on an implementation of this
 * interface. Known implementors within the Spring core are DefaultListableBeanFactory
 * and GenericApplicationContext.
 *
 * @author Juergen Hoeller
 * @since 26.11.2003
 * @see org.springframework.beans.factory.config.BeanDefinition
 * @see AbstractBeanDefinition
 * @see RootBeanDefinition
 * @see ChildBeanDefinition
 * @see DefaultListableBeanFactory
 * @see org.springframework.context.support.GenericApplicationContext
 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
 * @see PropertiesBeanDefinitionReader
 */
public interface BeanDefinitionRegistry extends AliasRegistry {

	/**
	 * <h3>📥 方法 1：registerBeanDefinition(beanName, beanDefinition) —— 往图纸档案馆里塞一张图纸</h3>
	 * <p><b>🏭【核心写入操作——所有 Bean 的一生，从这张图纸的登记开始！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 将一个 BeanDefinition（图纸）以 beanName 为 key 注册到档案馆中。<br/>
	 * 如果同名图纸已存在：检查 {@code allowBeanDefinitionOverriding}，允许则覆盖（warn 日志），不允许则抛异常。</p>
	 * <p><b>【典型调用者】</b></p>
	 * <ul>
	 * <li>{@code ClassPathBeanDefinitionScanner}：包扫描注册 @Component 图纸</li>
	 * <li>{@code ConfigurationClassBeanDefinitionReader}：注册 @Bean 方法产生的图纸</li>
	 * <li>{@code XmlBeanDefinitionReader}：注册 XML 中 {@code <bean>} 标签解析出的图纸</li>
	 * <li>{@code ImportBeanDefinitionRegistrar}：动态注册自定义图纸（如 MyBatis 的 MapperScannerConfigurer）</li>
	 * </ul>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 档案管理员接到一张新图纸，检查档案柜里有没有同名的：<br/>
	 * 没有 → 直接归档。有 → 看厂长允不允许覆盖。允许则新图纸替换旧图纸（旧的作废），不允许就报错。</blockquote>
	 * <hr/>
	 * Register a new bean definition with this registry.
	 * Must support RootBeanDefinition and ChildBeanDefinition.
	 * @param beanName the name of the bean instance to register
	 * @param beanDefinition definition of the bean instance to register
	 * @throws BeanDefinitionStoreException if the BeanDefinition is invalid
	 * @throws BeanDefinitionOverrideException if there is already a BeanDefinition
	 * for the specified bean name and we are not allowed to override it
	 * @see GenericBeanDefinition
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 */
	void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException;

	/**
	 * <h3>🗑️ 方法 2：removeBeanDefinition(beanName) —— 从档案馆中撤销一张图纸</h3>
	 * <p><b>🏭【销毁图纸——从此档案馆里再也找不到这台机器的设计方案】</b></p>
	 * <p>移除指定名称的 BeanDefinition。如果不存在则抛 NoSuchBeanDefinitionException（Fail-Fast）。<br/>
	 * 注意：移除图纸不会影响已经创建好的单例实例（那是 SingletonBeanRegistry 管的事）。<br/>
	 * 此方法在实际开发中很少使用，但在测试场景和热部署场景中有价值。</p>
	 * <hr/>
	 * Remove the BeanDefinition for the given name.
	 * @param beanName the name of the bean instance to register
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 */
	void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>📜 方法 3：getBeanDefinition(beanName) —— 按名查图纸</h3>
	 * <p><b>🏭【调图纸——"把 userService 的设计方案给我拿来！"】</b></p>
	 * <p>返回指定名称的 BeanDefinition，永远不返回 null——找不到就抛异常。<br/>
	 * 这个方法是 {@code AbstractBeanFactory.getBeanDefinition()}（抽象钩子）的具象化实现入口，
	 * 也是 {@code getMergedLocalBeanDefinition()} 的数据来源。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 流水线要开工造 userService 了，先去档案馆调它的图纸。有图纸才能造，没图纸就报错。</blockquote>
	 * <hr/>
	 * Return the BeanDefinition for the given bean name.
	 * @param beanName name of the bean to find a definition for
	 * @return the BeanDefinition for the given name (never {@code null})
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 */
	BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * <h3>🔍 方法 4：containsBeanDefinition(beanName) —— 档案馆里有没有这张图纸？</h3>
	 * <p><b>🏭【存在性探测——只看图纸档案，不看成品仓库，不问父容器】</b></p>
	 * <p>纯粹的本地图纸存在性检查。与 {@code BeanFactory.containsBean()} 的关键区别：<br/>
	 * containsBean 会查图纸+单例+父容器，而 containsBeanDefinition 只查本地图纸。</p>
	 * <hr/>
	 * Check if this registry contains a bean definition with the given name.
	 * @param beanName the name of the bean to look for
	 * @return if this registry contains a bean definition with the given name
	 */
	boolean containsBeanDefinition(String beanName);

	/**
	 * <h3>📋 方法 5：getBeanDefinitionNames() —— 列出所有图纸名</h3>
	 * <p><b>🏭【花名册——档案馆里所有图纸的名字，按注册顺序排列】</b></p>
	 * <p>在 {@code DefaultListableBeanFactory} 中，这个方法返回 {@code beanDefinitionNames}（ArrayList）的快照。<br/>
	 * 注册顺序很重要——{@code preInstantiateSingletons()} 就是按这个顺序依次实例化单例的。</p>
	 * <hr/>
	 * Return the names of all beans defined in this registry.
	 * @return the names of all beans defined in this registry,
	 * or an empty array if none defined
	 */
	String[] getBeanDefinitionNames();

	/**
	 * <h3>🔢 方法 6：getBeanDefinitionCount() —— 图纸总数</h3>
	 * <p>返回档案馆中 BeanDefinition 的总数。用于监控/诊断/日志。</p>
	 * <hr/>
	 * Return the number of beans defined in the registry.
	 * @return the number of beans defined in the registry
	 */
	int getBeanDefinitionCount();

	/**
	 * <h3>🚦 方法 7：isBeanNameInUse(beanName) —— 这个名字是否已被占用？</h3>
	 * <p><b>🏭【写前检测——注册新图纸前先检查名字冲突！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 检查 beanName 是否已被本注册表中的任何 Bean 定义或别名占用。<br/>
	 * 在 {@code AbstractBeanFactory} 中实现为：{@code isAlias(name) || containsLocalBean(name) || hasDependentBean(name)}<br/>
	 * 三重检测——别名占用、本地 Bean 占用、依赖关系占用。</p>
	 * <p><b>【典型使用场景】</b><br/>
	 * {@code ConfigurationClassBeanDefinitionReader} 在注册 @Bean 方法产生的 BeanDefinition 时，
	 * 会先调用此方法检查名字是否冲突，避免意外覆盖。</p>
	 * <hr/>
	 * Determine whether the given bean name is already in use within this registry,
	 * i.e. whether there is a local bean or alias registered under this name.
	 * @param beanName the name to check
	 * @return whether the given bean name is already in use
	 */
	boolean isBeanNameInUse(String beanName);

}
