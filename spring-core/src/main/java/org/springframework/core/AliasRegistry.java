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

package org.springframework.core;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>别名注册表接口——整个 Spring 继承体系的"绝对起点"，最原子的命名契约</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.AliasRegistry}</li>
 * <li><b>中文名</b>：别名注册表 —— "一个名字可以有多个叫法"的统一契约</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块（最底层！不依赖 spring-beans、spring-context 中的任何概念）</li>
 * <li><b>接口层级</b>：无父接口——是整个 Spring 核心继承链的<b>绝对零点</b></li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个接口？——"别名"是比"Bean"更底层的通用能力！</h3>
 * <p>Spring 架构师面对一个洞察：<b>名字的多态性（一个事物多个称呼）是一个独立于"Bean 管理"的基础需求</b>。</p>
 * <ul>
 * <li>在 XML 中，{@code <bean id="ds" name="dataSource,primaryDS"/>}——一个 Bean 三个名字</li>
 * <li>{@code <alias name="ds" alias="defaultDataSource"/>}——还能单独追加别名</li>
 * <li>在注解中，{@code @Bean({"orderService", "os"})}——同样产生别名</li>
 * </ul>
 * <p>如果把别名管理写死在 BeanFactory 或 BeanDefinitionRegistry 里，会导致：</p>
 * <ol>
 * <li><b>概念耦合</b>：别名是纯粹的"名字→名字"映射，和"Bean 是什么/怎么创建"毫无关系</li>
 * <li><b>无法共享</b>：BeanDefinitionRegistry 需要别名（注册图纸时指定别名），
 *     BeanFactory 也需要别名（getBean 时通过别名查找）。
 *     如果别名能力不抽到公共父接口，两边就得各自实现一套</li>
 * <li><b>模块依赖倒置</b>：AliasRegistry 放在 spring-core，连 "Bean" 的概念都不知道。
 *     这意味着它可以被 spring-core 内部的其他组件复用，比如用于 Environment 的 profile 别名等</li>
 * </ol>
 *
 * <h3>🧬 这个接口蕴含的设计精髓</h3>
 * <ol>
 * <li><b>"最小契约"原则</b><br/>
 * 整个接口只有 4 个方法：注册、移除、判断、查询——这是别名管理的最小完备集，不多不少。<br/>
 * Spring 架构师没有在这里加 {@code canonicalName()}（解析真名）或 {@code resolveAliases()}（占位符替换），
 * 那些是实现层面的增强，不属于契约本身。<br/>
 * <b>业务借鉴</b>：定义接口时，只放"消费者真正需要的操作"，实现层面的便利方法放在实现类中。</li>
 *
 * <li><b>"接口在最底层模块"的依赖方向</b><br/>
 * AliasRegistry 在 spring-core，BeanDefinitionRegistry 在 spring-beans，
 * 后者 extends 前者——依赖方向是 beans → core，永远不会反向。<br/>
 * <b>业务借鉴</b>：基础能力接口（如命名/标识/序列化）应该放在最底层模块，
 * 上层业务模块通过 extends 来组合这些基础能力，而不是在业务模块中重新定义。</li>
 *
 * <li><b>"两棵继承树的公共根"</b><br/>
 * Spring 有两棵并行的继承树：<br/>
 * ① <b>BeanFactory 树</b>：BeanFactory → ... → DefaultListableBeanFactory（管"运行时实例"）<br/>
 * ② <b>BeanDefinitionRegistry 树</b>：AliasRegistry → BeanDefinitionRegistry → DefaultListableBeanFactory（管"图纸"）<br/>
 * 两棵树在 DefaultListableBeanFactory 合体——而 AliasRegistry 是它们共同的最远祖先，
 * 保证了不管从哪棵树看，别名能力都是统一的。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位——两棵树的公共根</h3>
 * <pre>
 * AliasRegistry  ← 👈 你在这里！绝对零点
 * ├── (树1·实例管理) SimpleAliasRegistry
 * │     └── DefaultSingletonBeanRegistry (+ SingletonBeanRegistry)
 * │           └── FactoryBeanRegistrySupport
 * │                 └── AbstractBeanFactory (+ ConfigurableBeanFactory)
 * │                       └── ... → DefaultListableBeanFactory ←────┐
 * │                                                                 │ 合体！
 * └── (树2·图纸管理) BeanDefinitionRegistry                          │
 *       └── DefaultListableBeanFactory ←────────────────────────────┘
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·4 个方法的完美对称</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th><th>对称关系</th></tr>
 * <tr><td>{@code registerAlias(name, alias)}</td><td>注册别名</td><td>写入</td></tr>
 * <tr><td>{@code removeAlias(alias)}</td><td>移除别名</td><td>写入（逆操作）</td></tr>
 * <tr><td>{@code isAlias(name)}</td><td>判断是否是别名</td><td>读取（单条探测）</td></tr>
 * <tr><td>{@code getAliases(name)}</td><td>获取所有别名</td><td>读取（批量枚举）</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>AliasRegistry 用 4 个方法定义了"多名字→同一事物"的最小契约。
 * 它位于 spring-core（不依赖 spring-beans），是两棵继承树（BeanFactory 体系 + BeanDefinitionRegistry 体系）
 * 的公共根。正因为这个接口足够小、足够纯粹、足够底层，
 * 它才能成为整个 Spring 命名体系的"地心引力"——上层的一切命名能力都建立在它之上。</p>
 *
 * <hr/>
 * Common interface for managing aliases. Serves as a super-interface for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 */
public interface AliasRegistry {

	/**
	 * <h3>📝 方法 1：registerAlias(name, alias) —— 给真名注册一个别名</h3>
	 * <p><b>🏭【往花名册上登记："以后叫 alias 的时候，其实找的是 name！"】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 建立一个 alias → name 的单向映射。之后通过 alias 查找时，最终会解析到 name。<br/>
	 * 实现类 {@code SimpleAliasRegistry} 在此基础上增加了三重防御：自引用检测、覆盖策略钩子、环路检测。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你告诉花名册管理员："以后有人来找 'ds'，其实就是找 'dataSource'！"<br/>
	 * 管理员登记在册。下次有人报 "ds"，管理员翻花名册，"哦，ds 就是 dataSource"，直接带你去找 dataSource。</blockquote>
	 * <hr/>
	 * Given a name, register an alias for it.
	 * @param name the canonical name
	 * @param alias the alias to be registered
	 * @throws IllegalStateException if the alias is already in use
	 * and may not be overridden
	 */
	void registerAlias(String name, String alias);

	/**
	 * <h3>🗑️ 方法 2：removeAlias(alias) —— 从花名册上撤销一个别名</h3>
	 * <p><b>🏭【把这个外号从花名册上划掉！不存在就直接报错！】</b></p>
	 * <p>Fail-Fast 设计：如果别名不存在，抛 IllegalStateException 而非静默忽略——
	 * 防止调用者误以为删除成功，实际上压根没有这个别名。</p>
	 * <hr/>
	 * Remove the specified alias from this registry.
	 * @param alias the alias to remove
	 * @throws IllegalStateException if no such alias was found
	 */
	void removeAlias(String alias);

	/**
	 * <h3>🔍 方法 3：isAlias(name) —— 这个名字是别名还是真名？</h3>
	 * <p><b>🏭【单条探测——翻一下花名册，看这个名字是不是某人的外号】</b></p>
	 * <p>如果 name 在别名映射表中作为 key 存在，说明它是某个真名的别名，返回 true。<br/>
	 * 注意：一个名字可以同时是"某人的别名"和"另一个别名的真名"（传递性别名链）。</p>
	 * <hr/>
	 * Determine whether the given name is defined as an alias
	 * (as opposed to the name of an actually registered component).
	 * @param name the name to check
	 * @return whether the given name is an alias
	 */
	boolean isAlias(String name);

	/**
	 * <h3>📋 方法 4：getAliases(name) —— 列出这个真名的所有外号</h3>
	 * <p><b>🏭【批量枚举——这个人一共有几个外号？全报出来！】</b></p>
	 * <p>返回所有直接和间接指向 name 的别名数组。<br/>
	 * 实现类 {@code SimpleAliasRegistry} 通过递归遍历 aliasMap 收集传递性别名：<br/>
	 * 如果 aliasMap 中有 "a"→"b"，"b"→"c"，则 getAliases("c") 返回 ["b", "a"]。</p>
	 * <hr/>
	 * Return the aliases for the given name, if defined.
	 * @param name the name to check for aliases
	 * @return the aliases, or an empty array if none
	 */
	String[] getAliases(String name);

}
