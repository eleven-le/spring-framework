/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>别名注册表——整个继承链的"地基第一层"，最朴素也最不可或缺的基础设施</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.SimpleAliasRegistry}</li>
 * <li><b>中文名</b>：简单别名注册表 —— 一张"花名册↔真名"的翻译表</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块（注意！不是 spring-beans，是更底层的 core！）</li>
 * <li><b>类层级</b>：实现 {@code AliasRegistry} 接口，是 {@code DefaultSingletonBeanRegistry} 的直接父类</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个类？——"名字管理"是独立于"Bean 管理"的原子能力！</h3>
 * <p>Spring 架构师面对的问题：一个 Bean 可以有多个名字。比如 XML 中 {@code <bean id="ds" name="dataSource,primaryDS"/>}，
 * 用户可能用 "ds"、"dataSource"、"primaryDS" 中的任何一个来查找同一个 Bean。</p>
 * <p>如果把别名解析逻辑直接写在 BeanFactory 里，会产生两个问题：</p>
 * <ol>
 * <li><b>职责污染</b>：BeanFactory 的核心职责是管理 Bean 生命周期，别名翻译是纯粹的字符串映射，
 *     和"创建/注入/销毁"毫无关系</li>
 * <li><b>无法复用</b>：不仅 BeanFactory 需要别名，BeanDefinitionRegistry 也需要别名。
 *     如果写死在 BeanFactory 里，BeanDefinitionRegistry 就没法共享这个能力</li>
 * </ol>
 * <p>拆出 SimpleAliasRegistry 后的妙处：</p>
 * <ul>
 * <li><b>放在 spring-core</b>（而非 spring-beans）—— 连"Bean"的概念都不依赖！
 *     它是纯粹的"名字→真名"映射表，理论上任何需要别名的场景都能复用</li>
 * <li><b>作为继承链地基</b>：{@code SimpleAliasRegistry} → {@code DefaultSingletonBeanRegistry}
 *     → {@code FactoryBeanRegistrySupport} → {@code AbstractBeanFactory}。
 *     每一层只加一种能力，别名是最底层、最通用的能力</li>
 * <li><b>ConcurrentHashMap + synchronized 双保险</b>：读操作（canonicalName）走 ConcurrentHashMap 无锁读，
 *     写操作（registerAlias）用 synchronized 保证原子性——读多写少的经典并发模型</li>
 * </ul>
 *
 * <h3>🧬 业务借鉴——你的系统能偷师什么？</h3>
 * <ol>
 * <li><b>"多入口统一收敛"模式</b><br/>
 * 电商系统中，同一个商品可能有 SKU编号、条形码、内部货号 三种标识。与其在每个服务里各自维护映射，
 * 不如抽出一个 {@code ProductAliasRegistry}，所有服务统一通过它把"外部名"翻译成"内部规范名"。<br/>
 * Spring 的 {@code canonicalName()} 方法就是这个模式的教科书实现——do-while 循环递归解析别名链。</li>
 *
 * <li><b>"环路检测"的防御性编程</b><br/>
 * {@code checkForAliasCircle()} 在注册别名时检测 A→B→C→A 的循环引用——这个思路在任何图/树/链式结构中都适用。<br/>
 * 业务借鉴：组织架构中上下级关系、分类树的父子关系、路由规则的转发链，都应该在写入时做环路检测，
 * 而不是等运行时 StackOverflow 了再去排查。</li>
 *
 * <li><b>"允许覆盖"的策略开关</b><br/>
 * {@code allowAliasOverriding()} 是一个 protected 钩子方法，子类可以覆盖它来禁止别名覆盖。<br/>
 * 业务借鉴：配置管理中"是否允许低优先级配置覆盖高优先级配置"，用同样的模板方法钩子来控制策略。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AliasRegistry (接口)
 * └── SimpleAliasRegistry  ← 👈 你在这里！继承链的地基第一层
 *       └── DefaultSingletonBeanRegistry (+ SingletonBeanRegistry)
 *             └── FactoryBeanRegistrySupport
 *                   └── AbstractBeanFactory (implements ConfigurableBeanFactory)
 *                         └── AbstractAutowireCapableBeanFactory
 *                               └── DefaultListableBeanFactory
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·核心数据结构 + 7 个方法</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>成员</th><th>使命</th><th>关键设计</th></tr>
 * <tr><td>{@code aliasMap}</td><td>别名→真名的映射表</td><td>ConcurrentHashMap(16)，读无锁、写 synchronized</td></tr>
 * <tr><td>{@code registerAlias()}</td><td>注册/覆盖别名</td><td>同名自删 + 环路检测 + 覆盖策略钩子</td></tr>
 * <tr><td>{@code removeAlias()}</td><td>移除别名</td><td>不存在则抛异常（Fail-Fast）</td></tr>
 * <tr><td>{@code isAlias()}</td><td>判断是否是别名</td><td>直接查 key 是否存在</td></tr>
 * <tr><td>{@code getAliases()}</td><td>获取所有别名</td><td>递归收集传递性别名（A→B→C，查 C 返回 [B,A]）</td></tr>
 * <tr><td>{@code resolveAliases()}</td><td>占位符解析</td><td>用 StringValueResolver 替换别名和真名中的 ${} 占位符</td></tr>
 * <tr><td>{@code canonicalName()}</td><td>解析真名</td><td>do-while 循环递归翻译别名链，直到没有更深层的别名</td></tr>
 * </table>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>SimpleAliasRegistry 用一个 ConcurrentHashMap 和不到 200 行代码，为整个 Spring 容器提供了"多名字→单真名"的统一基础设施。
 * 它位于 spring-core（不依赖 spring-beans），是继承链中最底层、最通用的能力砖。<br/>
 * 上层的 DefaultSingletonBeanRegistry、AbstractBeanFactory、DefaultListableBeanFactory 全部通过继承获得了别名解析能力，
 * 而不需要各自重复实现——这就是"能力分层叠加"设计思想的完美起点。</p>
 *
 * <hr/>
 * Simple implementation of the {@link AliasRegistry} interface.
 *
 * <p>Serves as base class for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 *
 * @author Juergen Hoeller
 * @author Qimiao Chen
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * <b>🗂️ 核心数据结构：别名→真名 映射表</b><br/>
	 * key = 别名（alias），value = 真名（canonical name）。<br/>
	 * 用 ConcurrentHashMap 保证读操作无锁高性能，写操作通过 synchronized(this.aliasMap) 保证原子性。<br/>
	 * 别名可以是传递性的：A→B→C，即 A 是 B 的别名，B 是 C 的别名，canonicalName("A") 最终返回 "C"。
	 */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);


	/**
	 * <h3>📝 方法 1：registerAlias(name, alias) —— 注册别名，含三重防御</h3>
	 * <p><b>🏭【给机器起外号——但不许起重复的、不许起成环的！】</b></p>
	 * <p><b>【三重防御机制】</b></p>
	 * <ol>
	 * <li><b>自引用检测</b>：如果 alias == name（自己是自己的别名），静默移除并忽略——没有意义</li>
	 * <li><b>覆盖策略钩子</b>：如果别名已经被其他真名占用，检查 {@code allowAliasOverriding()} 是否允许覆盖。
	 *     默认允许（返回 true），子类可覆盖此方法禁止覆盖</li>
	 * <li><b>环路检测</b>：{@code checkForAliasCircle(name, alias)} 防止 A→B→C→A 的循环引用，
	 *     避免 {@code canonicalName()} 陷入死循环</li>
	 * </ol>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你要给"userService"起个外号叫"us"。花名册管理员先检查：<br/>
	 * ① "us"和"userService"是不是同一个名字？（自引用检测）<br/>
	 * ② "us"这个外号已经被别人用了吗？允不允许抢过来？（覆盖策略）<br/>
	 * ③ 会不会形成 us→userService→us 的死循环？（环路检测）<br/>
	 * 三关都过了，才正式登记在花名册上。</blockquote>
	 */
	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		synchronized (this.aliasMap) {
			if (alias.equals(name)) {
				this.aliasMap.remove(alias);
				if (logger.isDebugEnabled()) {
					logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
				}
			}
			else {
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null) {
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register
						return;
					}
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Overriding alias '" + alias + "' definition for registered name '" +
								registeredName + "' with new target name '" + name + "'");
					}
				}
				checkForAliasCircle(name, alias);
				this.aliasMap.put(alias, name);
				if (logger.isTraceEnabled()) {
					logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
				}
			}
		}
	}

	/**
	 * <h3>🔓 钩子方法：allowAliasOverriding() —— 是否允许别名覆盖？</h3>
	 * <p>模板方法钩子。默认返回 true（允许覆盖），子类可以覆盖返回 false 来禁止同一别名被多次注册。<br/>
	 * 例如 {@code DefaultListableBeanFactory} 中的 {@code allowBeanDefinitionOverriding} 就借鉴了这个模式。</p>
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}

	/**
	 * <h3>🔗 方法：hasAlias(name, alias) —— 递归检测传递性别名</h3>
	 * <p>判断 name 是否直接或间接拥有指定 alias。支持传递性检测：<br/>
	 * 如果 aliasMap 中 alias→X，X→name，则 hasAlias(name, alias) 返回 true。<br/>
	 * 这个递归检测被 {@code checkForAliasCircle()} 复用来做环路检测。</p>
	 */
	public boolean hasAlias(String name, String alias) {
		String registeredName = this.aliasMap.get(alias);
		return ObjectUtils.nullSafeEquals(registeredName, name) ||
				(registeredName != null && hasAlias(name, registeredName));
	}

	/**
	 * <h3>🗑️ 方法 2：removeAlias(alias) —— 移除别名，Fail-Fast</h3>
	 * <p>从花名册中移除指定别名。如果别名不存在，直接抛 IllegalStateException——快速失败，绝不静默忽略。</p>
	 */
	@Override
	public void removeAlias(String alias) {
		synchronized (this.aliasMap) {
			String name = this.aliasMap.remove(alias);
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	/**
	 * <h3>🔍 方法 3：isAlias(name) —— 这个名字是别名还是真名？</h3>
	 * <p>如果 name 作为 key 存在于 aliasMap 中，说明它是某个真名的别名。<br/>
	 * 注意：这里只检查"是不是别名"，不检查"是不是真名"——一个名字可以同时是别名和另一个名字的真名（传递性）。</p>
	 */
	@Override
	public boolean isAlias(String name) {
		return this.aliasMap.containsKey(name);
	}

	/**
	 * <h3>📋 方法 4：getAliases(name) —— 获取指定真名的所有别名（递归传递性收集）</h3>
	 * <p>通过 {@code retrieveAliases()} 递归遍历 aliasMap，收集所有直接和间接指向 name 的别名。<br/>
	 * 例如：aliasMap 中有 "a"→"b", "b"→"c"，则 getAliases("c") 返回 ["b", "a"]。</p>
	 */
	@Override
	public String[] getAliases(String name) {
		List<String> result = new ArrayList<>();
		synchronized (this.aliasMap) {
			retrieveAliases(name, result);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Transitively retrieve all aliases for the given name.
	 * @param name the target name to find aliases for
	 * @param result the resulting aliases list
	 */
	private void retrieveAliases(String name, List<String> result) {
		this.aliasMap.forEach((alias, registeredName) -> {
			if (registeredName.equals(name)) {
				result.add(alias);
				retrieveAliases(alias, result);
			}
		});
	}

	/**
	 * <h3>🔄 方法 5：resolveAliases(valueResolver) —— 占位符批量替换</h3>
	 * <p><b>🏭【花名册上的 ${} 占位符全部翻译成真实值！】</b></p>
	 * <p>遍历 aliasMap 中所有的 alias 和 name，用 StringValueResolver 解析其中的占位符。<br/>
	 * 例如：别名 "${prefix}.service" → 解析为 "order.service"。<br/>
	 * 解析后还会做冲突检测和环路检测，确保替换后的映射关系仍然合法。</p>
	 * <hr/>
	 * Resolve all alias target names and aliases registered in this
	 * registry, applying the given {@link StringValueResolver} to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * @param valueResolver the StringValueResolver to apply
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		synchronized (this.aliasMap) {
			Map<String, String> aliasCopy = new HashMap<>(this.aliasMap);
			aliasCopy.forEach((alias, registeredName) -> {
				String resolvedAlias = valueResolver.resolveStringValue(alias);
				String resolvedName = valueResolver.resolveStringValue(registeredName);
				if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
					this.aliasMap.remove(alias);
				}
				else if (!resolvedAlias.equals(alias)) {
					String existingName = this.aliasMap.get(resolvedAlias);
					if (existingName != null) {
						if (existingName.equals(resolvedName)) {
							// Pointing to existing alias - just remove placeholder
							this.aliasMap.remove(alias);
							return;
						}
						throw new IllegalStateException(
								"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
								"') for name '" + resolvedName + "': It is already registered for name '" +
								registeredName + "'.");
					}
					checkForAliasCircle(resolvedName, resolvedAlias);
					this.aliasMap.remove(alias);
					this.aliasMap.put(resolvedAlias, resolvedName);
				}
				else if (!registeredName.equals(resolvedName)) {
					this.aliasMap.put(alias, resolvedName);
				}
			});
		}
	}

	/**
	 * <h3>🚨 方法 6：checkForAliasCircle(name, alias) —— 环路检测，写入时防御！</h3>
	 * <p><b>🏭【绝不允许花名册形成 A→B→C→A 的死循环！】</b></p>
	 * <p>在注册别名之前调用。利用 {@code hasAlias(alias, name)} 反向检测：<br/>
	 * 如果 alias 已经（直接或间接）指向 name，那么再注册 name→alias 就会形成环路。<br/>
	 * 发现环路立即抛 IllegalStateException——<b>写入时防御，而不是读取时才发现 StackOverflow</b>。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你要注册 "A 的别名是 B"。管理员先反向查："B 是不是已经（直接或间接）指向 A 了？"<br/>
	 * 如果是，那 A→B→...→A 就成了死循环，直接拒绝登记！</blockquote>
	 * <hr/>
	 * Check whether the given name points back to the given alias as an alias
	 * in the other direction already, catching a circular reference upfront
	 * and throwing a corresponding IllegalStateException.
	 * @param name the candidate name
	 * @param alias the candidate alias
	 * @see #registerAlias
	 * @see #hasAlias
	 */
	protected void checkForAliasCircle(String name, String alias) {
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * <h3>🎯 方法 7：canonicalName(name) —— 别名链递归解析，找到最终真名！</h3>
	 * <p><b>🏭【不管你报的是外号的外号的外号，我都能翻译到最终的真名！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * do-while 循环递归解析别名链：如果 name 在 aliasMap 中有映射，就用映射值替换，
	 * 继续查映射值是否还有映射……直到找不到更深层的别名为止。<br/>
	 * 例如：aliasMap 中 "a"→"b", "b"→"c"，则 canonicalName("a") 返回 "c"。</p>
	 * <p><b>【为什么用 do-while 而不是递归？】</b><br/>
	 * do-while 循环是尾递归的迭代化写法，避免了深层别名链导致的栈溢出风险——
	 * 虽然正常情况下别名链不会很深，但作为基础设施类必须做最保守的防御。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你报了一个名字 "a"。管理员翻花名册："a 其实是 b 的外号"。<br/>
	 * 再翻："b 其实是 c 的外号"。再翻："c 没有更深的外号了，c 就是真名！"<br/>
	 * 最终返回 "c"。这就是 AbstractBeanFactory.transformedBeanName() 的底层引擎。</blockquote>
	 * <hr/>
	 * Determine the raw name, resolving aliases to canonical names.
	 * @param name the user-specified name
	 * @return the transformed name
	 */
	public String canonicalName(String name) {
		String canonicalName = name;
		// Handle aliasing...
		String resolvedName;
		do {
			resolvedName = this.aliasMap.get(canonicalName);
			if (resolvedName != null) {
				canonicalName = resolvedName;
			}
		}
		while (resolvedName != null);
		return canonicalName;
	}

}
