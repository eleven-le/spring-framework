/*
 * Copyright 2002-2024 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>默认单例注册表——三级缓存的发源地，循环依赖的终极战场！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.DefaultSingletonBeanRegistry}</li>
 * <li><b>中文名</b>：默认单例 Bean 注册表 —— 继承链的"第二层"，管理所有单例的存取 + 三级缓存 + 依赖关系 + 销毁顺序</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块</li>
 * <li><b>类层级</b>：{@code SimpleAliasRegistry} 的子类，实现 {@code SingletonBeanRegistry} 接口</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个类？——"单例生命周期管理"独立于"Bean 定义"和"Bean 创建"！</h3>
 * <p>Spring 架构师在设计继承链时面对一个关键洞察：<b>单例管理是一个独立的关切领域，
 * 它不需要知道 BeanDefinition 是什么，也不需要知道 createBean 怎么工作</b>。</p>
 * <p>如果把三级缓存、循环依赖检测、销毁顺序编排这些逻辑混在 AbstractBeanFactory 里，会导致：</p>
 * <ol>
 * <li><b>职责膨胀</b>：AbstractBeanFactory 已经够复杂了（doGetBean、合并 BD、父子委派），
 *     再加上缓存管理和依赖图维护，一个类上千行，不可维护</li>
 * <li><b>无法独立使用</b>：原始 Javadoc 明确说"Can alternatively also be used as a nested helper to delegate to"——
 *     你可以单独 new 一个 DefaultSingletonBeanRegistry 当纯粹的"单例容器"使用，根本不需要 BeanFactory 的能力</li>
 * <li><b>违反单一职责</b>：缓存管理（空间换时间）、循环检测（状态机）、销毁编排（拓扑排序）
 *     这三个子领域各有各的复杂性，混在一起会让每个子领域都难以理解和测试</li>
 * </ol>
 *
 * <h3>🧬 这个类的三大核心职责</h3>
 * <ol>
 * <li><b>三级缓存——循环依赖的终极解决方案</b><br/>
 * {@code singletonObjects}（一级/成品仓库）、{@code earlySingletonObjects}（二级/半成品展示柜）、
 * {@code singletonFactories}（三级/图纸代工厂）。<br/>
 * 通过"提前暴露未完成的引用"打破循环等待的僵局，同时用三级缓存延迟 AOP 代理的创建时机——
 * 只有在真正发生循环依赖时才提前触发代理，保护正常流程的纯洁性。</li>
 *
 * <li><b>依赖关系图——优雅销毁的拓扑基础</b><br/>
 * {@code dependentBeanMap}（谁依赖我）和 {@code dependenciesForBeanMap}（我依赖谁）构成了双向依赖图。<br/>
 * 销毁时必须先销毁"依赖我的人"，再销毁"我自己"——这就是拓扑排序的逆序遍历。<br/>
 * {@code containedBeanMap} 则处理内部 Bean 的包含关系（inner bean）。</li>
 *
 * <li><b>创建状态机——并发安全与防死循环</b><br/>
 * {@code singletonsCurrentlyInCreation}（正在创建的单例集合）+ {@code beforeSingletonCreation/afterSingletonCreation}
 * 构成了一个简洁的状态机。CAS-like 的 add/remove 操作既能检测循环依赖，又能防止并发重复创建。</li>
 * </ol>
 *
 * <h3>🧬 业务借鉴——你的系统能偷师什么？</h3>
 * <ol>
 * <li><b>"三级缓存"解决循环依赖的通用范式</b><br/>
 * 在微服务场景中，服务 A 启动时需要服务 B 的地址，服务 B 启动时需要服务 A 的地址。
 * 可以借鉴三级缓存思路：先注册一个"占位地址"（类比二级缓存），等实例就绪后再升级为真实地址（类比一级缓存）。<br/>
 * Nacos/Eureka 的自注册机制本质上就是这种"先占位、后完善"的思路。</li>
 *
 * <li><b>"依赖图 + 逆序销毁"的优雅停机模式</b><br/>
 * 微服务关停时，必须先摘流量（依赖我的服务先切走），再关闭服务本身，最后断开下游连接。
 * 这和 Spring 的 destroyBean 逻辑一模一样：先递归销毁 dependentBeans，再销毁自己，最后清理 containedBeans。<br/>
 * 业务借鉴：在你的系统中维护一张"谁依赖谁"的图，关停时按图的逆序操作。</li>
 *
 * <li><b>"创建状态标记"的防重入模式</b><br/>
 * {@code singletonsCurrentlyInCreation} 用 Set 标记"正在创建中"的 Bean，检测到重入就抛异常。
 * 业务借鉴：分布式锁场景中，可以用 Redis SET NX 标记"正在处理中的订单"，
 * 如果同一订单再次进来，直接拒绝而不是重复处理。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AliasRegistry (接口)
 * └── SimpleAliasRegistry          ← 第一层：别名管理（name→alias 映射）
 *       └── DefaultSingletonBeanRegistry  ← 👈 你在这里！第二层：单例管理（三级缓存+依赖图+销毁）
 *             │   implements SingletonBeanRegistry
 *             └── FactoryBeanRegistrySupport  ← 第三层：FactoryBean 产物缓存
 *                   └── AbstractBeanFactory   ← 第四层：完整的 getBean 流程编排
 * </pre>
 *
 * <h3>🗂️ 三、核心数据结构——12 个字段，构成超级工厂的"中枢神经系统"</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>字段</th><th>类型</th><th>使命</th></tr>
 * <tr><td>{@code singletonObjects}</td><td>ConcurrentHashMap(256)</td><td>一级缓存：完整生命周期的成品 Bean</td></tr>
 * <tr><td>{@code earlySingletonObjects}</td><td>ConcurrentHashMap(16)</td><td>二级缓存：已实例化但未填充属性的早期引用</td></tr>
 * <tr><td>{@code singletonFactories}</td><td>HashMap(16)</td><td>三级缓存：ObjectFactory，按需触发 AOP 代理</td></tr>
 * <tr><td>{@code registeredSingletons}</td><td>LinkedHashSet(256)</td><td>按注册顺序记录所有单例名</td></tr>
 * <tr><td>{@code singletonsCurrentlyInCreation}</td><td>ConcurrentHashMap-Set</td><td>正在创建中的单例标记（检测循环依赖）</td></tr>
 * <tr><td>{@code disposableBeans}</td><td>LinkedHashMap</td><td>需要销毁回调的 Bean 注册表</td></tr>
 * <tr><td>{@code dependentBeanMap}</td><td>ConcurrentHashMap</td><td>谁依赖我（用于逆序销毁）</td></tr>
 * <tr><td>{@code dependenciesForBeanMap}</td><td>ConcurrentHashMap</td><td>我依赖谁（用于依赖查询）</td></tr>
 * <tr><td>{@code containedBeanMap}</td><td>ConcurrentHashMap</td><td>内部 Bean 包含关系</td></tr>
 * </table>
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>DefaultSingletonBeanRegistry 是 Spring IoC 容器中<b>数据密度最高</b>的一层——12 个核心字段、
 * 三级缓存、双向依赖图、创建状态机，全部集中在这 700 多行代码里。
 * 它不懂 BeanDefinition，不懂 createBean，但它掌管着所有单例的"生（缓存注册）、住（三级缓存流转）、
 * 异（循环依赖化解）、灭（依赖感知销毁）"——是超级工厂的绝对中枢。</p>
 *
 * <hr/>
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** Maximum number of suppressed exceptions to preserve. */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/** Cache of singleton objects: bean name to bean instance. */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** Cache of singleton factories: bean name to ObjectFactory. */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/** Cache of early singleton objects: bean name to bean instance. */
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/** Set of registered singletons, containing the bean names in registration order. */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/** Names of beans that are currently in creation. */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Names of beans currently excluded from in creation checks. */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Collection of suppressed Exceptions, available for associating related causes. */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/** Flag that indicates whether we're currently within destroySingletons. */
	private boolean singletonsCurrentlyInDestruction = false;

	/** Disposable bean instances: bean name to disposable instance. */
	private final Map<String, DisposableBean> disposableBeans = new LinkedHashMap<>();

	/** Map between containing bean names: bean name to Set of bean names that the bean contains. */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** Map between dependent bean names: bean name to Set of dependent bean names. */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** Map between depending bean names: bean name to Set of bean names for the bean's dependencies. */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		// 【车间大白话】大管家接到提货指令，默认允许去“半成品仓库”和“代工厂”提货（allowEarlyReference = true）。
		return getSingleton(beanName, true);
	}

	/**
	 * <h3>💥 厂长亲临前线！三级缓存破解“套娃死结”实况 🏭</h3>
	 * <p>报告厂长！大管家为了防止两条流水线因为互相等待零件而彻底卡死（套娃式循环依赖），硬生生开辟了三个不同级别的仓库来进行极限拉扯！这三个战区层层设防，精妙绝伦！</p>
	 *
	 * <ul>
	 * <li><b>一级缓存 <code>singletonObjects</code>：</b> 【成品现货大仓库】（经历完整生命周期的完美机器）</li>
	 * <li><b>二级缓存 <code>earlySingletonObjects</code>：</b> 【半成品展示柜】（刚焊好铁壳子，属性还没装的早期引用）</li>
	 * <li><b>三级缓存 <code>singletonFactories</code>：</b> 【图纸与专属代工厂】（存放 ObjectFactory，随时准备提前触发 AOP 代理的秘密武器）</li>
	 * </ul>
	 *
	 * <blockquote>
	 * <b>【大管家的生死时速】</b><br/>
	 * 超级工厂的最强防线：三级缓存（Three-Level Cache）。这是大管家为了彻底解决“流水线连环卡死（循环依赖）”问题而设计的核心调度中枢。
	 * 当机器 A 的组装需要机器 B，而机器 B 的组装又需要机器 A 时，如果不加干预，整条流水线就会因为互相等待而彻底瘫痪。
	 * 这段 getSingleton 代码，就是大管家如何在三个不同等级的仓库中闪转腾挪，巧妙化解僵局的实况。
	 *
	 * “前面流水线报废了！机器A等机器B，机器B等机器A！快！跟我按顺序冲进一、二、三号仓库！就算是个只有铁壳子的半成品，或者要当场叫醒代工师傅给它穿机甲（AOP），也必须马上拿出去救场，把流水线给我跑通！”
	 * </blockquote>
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>beanName</code> (入参) -> 【救火清单上的机器名】：</b> 流水线上正急缺的那个零件名称。</li>
	 * <li><b><code>allowEarlyReference</code> (入参) -> 【厂长特批令】：</b> 是否允许为了解开死结，提前把没组装完的“半成品铁壳子”拿出去用。</li>
	 * <li><b><code>return</code> (出参) -> 【救场神兵】：</b> 抱出来的可能是完美成品、早期铁壳子，也可能查无此物。</li>
	 * </ul>
	 * <hr/>
	 *
	 *
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
/* ========================================================================= 🏭 第一战区：突击一级缓存（成品现货大仓库） ========================================================================= */

		/* [架构师视角] 去一级缓存 singletonObjects 中查。这里存放的是经历了完整生命周期（实例化、属性填充、初始化）的绝对成熟 Bean。
		 * [动作拆解] 大管家急火火地先看一眼“成品现货大仓库”，查查这台机器是不是早就完全造好、随时可以出厂了。 */
		// Quick check for existing instance without full singleton lock
		Object singletonObject = this.singletonObjects.get(beanName);

		/*
		 * 🚨 [高能预警] 循环依赖的生命体征探测！
		 * [架构师视角] 如果一级缓存没有，且 isSingletonCurrentlyInCreation(beanName) 为 true，说明当前 Bean 正在另一个栈帧中处于创建状态，发生了循环依赖！
		 * [动作拆解] 现货仓库没货！大管家猛抬头看了一眼大屏幕，发现这台机器的状态是“正在流水线上造着呢”——坏了，前方的流水线肯定是因为缺零件互相卡住了，死锁预警！
		 */
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
/* ========================================================================= 🛠️ 第二战区：搜寻二级缓存（半成品展示柜）=========================================================================*/
			/* [架构师视角] 去二级缓存 earlySingletonObjects 查。这里存放的是刚实例化（只分配了内存地址），还没填充属性的早期对象引用。
			 * [动作拆解] 既然卡死了，大管家赶紧跑去“半成品展示柜”找找。看看这机器虽然没造完，是不是已经把“铁壳子（引用）”焊出来放在这儿了。 */
			singletonObject = this.earlySingletonObjects.get(beanName);

			/* [架构师视角] 如果二级缓存没货，并且允许获取早期引用（allowEarlyReference = true），准备进入加锁重地。
			 * [动作拆解] 半成品展示柜里也没有！但大管家手里有厂长您特批的“允许提早拿半成品走”的条子，准备动用终极手段了。  */
			if (singletonObject == null && allowEarlyReference) {
/* ========================================================================= 🚨 第三战区：加锁闭门，启用三级缓存（图纸与专属代工厂）========================================================================= */
				/* 🚨 [致命瓶颈] 全局单例锁护航机制
				 * [架构师视角] 由于要操作底层的三级缓存并进行对象状态升级，必须加锁保证并发安全。锁的是一级缓存对象，作为全局唯一的单例互斥锁。
				 * [动作拆解] 前方高能！大管家把所有仓库的大门反锁（synchronized），挂上“闲人免进”的牌子，准备内部调配核心资源，防止其他流水线来捣乱。*/
				synchronized (this.singletonObjects) {
					/* [架构师视角] 双重检查锁（Double-Check）的常规操作。加锁后再次确认一级缓存，防止等待锁的期间被其他并发线程造出来了。
					 * [动作拆解] 进门后再确认一眼“成品大仓库”，万一刚才锁门的功夫，这机器刚好造完了呢？ */
					// Consistent creation of early reference within full singleton lock
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						// 【原理解析】再次确认二级缓存。【车间大白话】再看一眼“半成品展示柜”。
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							/* 🌟 [终极破局点] 三级缓存与提早代理暴露
							 * [架构师视角] 去三级缓存 singletonFactories 查。这里存的是一个 ObjectFactory（Lambda表达式），它持有实例化后的原始对象，并能在必要时提前触发 SmartInstantiationAwareBeanPostProcessor 生成 AOP 代理。
							 * [动作拆解] 都没货！大管家直奔最隐秘的“图纸与专属代工厂（三级缓存）”。这里不放实体机器，只放了一张图纸和一个随时待命的代工师傅。*/
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								/* [架构师视角] 调用 ObjectFactory.getObject()。这一步极其关键！如果该 Bean 需要 AOP，【核心质检员】会在这里提前把动态代理对象造出来；如果不需要，就原样返回原始对象。
								 * [动作拆解] 大管家一巴掌拍醒代工师傅：“别睡了！流水线卡死了！快把这台机器的铁壳子给我打出来！如果有质检员要给它穿机甲（AOP代理），现在就立刻给我穿上！” */
								singletonObject = singletonFactory.getObject();
								/* [架构师视角] 缓存升级！将早期引用放入二级缓存 earlySingletonObjects。
								 * [动作拆解] 铁壳子（或披着机甲的半成品）一打出来，大管家立刻把它摆进“半成品展示柜（二级缓存）”。下次别的流水线再缺这台机器，直接来展示柜拿，不用再惊动代工师傅了。*/
								this.earlySingletonObjects.put(beanName, singletonObject);
								/* [架构师视角] 从三级缓存 singletonFactories 中移除该工厂。因为早期引用已经生成，工厂使命结束。
								 * [动作拆解] 代工师傅完工，大管家直接把“临时代工厂（三级缓存）”拆除。任务完成，资源回收！ */
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		// [动作拆解] 大管家抱着这台可能刚造好的成品，也可能只是个半成品的铁壳子，一脚踹开大门，狂奔出去解救那条卡死的流水线！
		return singletonObject;

/* 📊 【战略复盘：超级工厂顶级架构思想】
 * 报告厂长，这段代码完美展示了 Spring 解决循环依赖的降维打击策略：
 *
 * 1. 空间换时间与状态分级 (State Gradients & Caching)：
 * 通过三个 Map 清晰定义了机器的生命周期状态（成品、半成品引用、半成品生产工厂）。在遇到死锁时，通过提前暴露未完全初始化的对象内存地址，打破了互相等待的僵局。
 *
 * 2. 延迟代理的极致追求 (Delayed AOP Execution)：
 * 为什么要有三级缓存而不是仅仅两级？因为 Spring 的架构洁癖要求：所有的 AOP 代理都应该在对象完全初始化后（正常的 BeanPostProcessor 阶段）再优雅地创建。
 * 只有在遇到真正的循环依赖导致流水线卡死时，才会通过三级缓存的 ObjectFactory 钩子，被迫“提前”把【核心质检员】拉过来生成代理对象。三级缓存可以说是为了保护 Spring 生命周期主流程的纯洁性而做出的最华丽的妥协！*/
	}

	/**
	 * <h3>💥 厂长亲临前线！机密档案：getSingleton 终极单例制造流水线 🏭</h3>
	 * <p>【它在做什么】报告厂长！这里是超级工厂最核心的“造物总闸”！它接收机器名字和一个微型加工厂，想尽一切办法（查仓库、加锁、防死循环）给您返回一个绝对唯一的单例机器实例。</p>
	 *
	 * <blockquote>
	 * <b>【大管为什么这么做 & 解决什么痛点】</b><br/>
	 * 1. <b>并发抢夺：</b> 几百个经销商（多线程）同时来要同一台大机器（如数据库连接池），绝不能重复造！-> <b>【加互斥锁】</b><br/>
	 * 2. <b>套娃死锁：</b> 机器 A 等 B，B 等 A，流水线卡死报 StackOverflowError 怎么办？-> <b>【通过 beforeSingletonCreation 挂上“正在制造”的红牌，配合三级缓存预警】</b><br/>
	 * 3. <b>生命周期错乱痛点：</b> 工厂都在大扫除准备倒闭了（销毁阶段），还有人来下订单？-> <b>【毁灭状态拦截】</b>
	 * </blockquote>
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>beanName</code> (入参) -> 【产品的官方名】：</b> 厂长提货单上写的名字。</li>
	 * <li><b><code>singletonFactory</code> (入参) -> 【微型加工厂 / 代工师傅】：</b> 一个 ObjectFactory (通常是 Lambda 表达式)，里面包着真正的 <code>createBean</code> 造物逻辑，随时待命开机。</li>
	 * <li><b><code>return</code> (出参) -> 【绝对唯一的单例机器】：</b> 无论过程多惊险，最终从一号现货仓库大门推出来的、全厂独一份的成品。</li>
	 * </ul>
	 * <hr/>
	 *
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
/* ------------------------------------- 🚧 第一战区：安检口与金库大门 ------------------------------------------------------------------------- */
		/* [架构师视角] 断言与参数校验，快速失败（Fail-Fast）机制。
		 * [动作拆解] 厂长定下的死规矩，连图纸名字都不报就想来提货？大管家直接把人轰出去！*/
		Assert.notNull(beanName, "Bean name must not be null");
		/* 🚨 [致命瓶颈] 全局互斥锁同步
		 * [架构师视角] 保证单例的绝对线程安全性。锁住的是 singletonObjects（一号现货VIP仓库/一级缓存）。
		 * [动作拆解] 🔥 高能预警！全厂最重的一把大锁！为什么要锁？因为此时可能有成百上千个经销商（多线程）同时来提这台机器，必须排好队，依次进仓库查货！*/
		synchronized (this.singletonObjects) {
			/*[架构师视角] 双重检查锁（Double-Check Locking）的第一次核心检查。
			 * [动作拆解] 进了金库，先扫一眼一号现货仓库，看看是不是刚才前面排队的人已经把机器造出来放进去了？*/
			Object singletonObject = this.singletonObjects.get(beanName);
			//  [动作拆解] 果然，现货仓库里没有（null）！准备开工造新机器！
			if (singletonObject == null) {
				/* 🚨 [高能预警] 防炸炉拦截
				 * [架构师视角] 生命周期状态校验，防止在容器销毁（销毁单例）阶段继续创建新单例，导致内存泄漏或状态不一致。
				 * [动作拆解] 大管家突然看了一眼通告牌——全厂是不是正在拆迁倒闭（销毁流程中）？如果是，直接拉响警报，抛异常！
				 * [痛点场景] 某个机器的 destroy() 销毁方法里，又手贱去工厂 getBean()，这属于严重违规！*/
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
/* ------------------------------------- 🛡️ 第二战区：挂红牌与免责声明 -------------------------------------------------------------------------*/
				/* [架构师视角] 单例创建前置标记，解决循环依赖的死循环核心屏障！
				 * [动作拆解] ⚠️ 极其危险的动作！在把图纸交给车间前，先把这台机器的名字写在【正在制造的黑板】上（singletonsCurrentlyInCreation）！
				 * [为什么] 如果 A 造到一半去造 B，B 造到一半又回来要 A，一看黑板发现 A 已经在制造中了，就知道发生【循环依赖】了，从而触发提前暴露半成品（二级缓存）的逻辑，而不是傻傻地再去造一个全新的 A 导致死循环！*/
				beforeSingletonCreation(beanName);
				// 标记位：这次是不是真的造出了一台全新的机器？
				boolean newSingleton = false;
				/* [架构师视角] 异常抑制记录器初始化。
				 * [动作拆解] 发个空本子（LinkedHashSet），准备记录等会儿造机器过程中如果发生爆炸（抛异常），顺带产生的小火花（被抑制的异常），方便排错。 */
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
/* -------------------------------------⚡ 第三战区：拉下电闸，引擎轰鸣！-------------------------------------------------------------------------- */
				try {
					/* 🌟 [终极原爆点] 回调 ObjectFactory 的 getObject() 真正触发 Bean 的实例化、属性填充、初始化（调用内部的 createBean）。
					 * [动作拆解] 💥 厂长，这是最激动人心的一行！不管外面的壳子包装得多复杂，这里直接回调传进来的那个微型加工厂（匿名内部类中的 createBean 方法）。 随着这一句执行，反射机制启动、质检员（BeanPostProcessor）入场、依赖注入开始疯狂运转！整个机器的灵魂都在这里被锻造！ */
					singletonObject = singletonFactory.getObject();
					// 【车间大白话】：机器造好了，没冒烟！打上成功标记！✅
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					/* 🚨 [高能预警] 并发极端情况处理 (异常防弹衣)
					 * [架构师视角] 兜底策略。如果在创建期间，由于诡异的隐式调用或并发 bug，导致该单例居然已经在缓存里出现了。
					 * [动作拆解] 车间正在造着呢，突然发现别人偷偷把这台机器送进一号仓库了？！赶紧再查一次仓库，如果有现成的直接拿来用；要是还没找到，说明真见鬼了，把异常往上抛！*/
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					/* [架构师视角] 核心创建异常封装。
					 * [动作拆解] 造机器彻底失败（比如少螺丝、图纸写错）。把之前那个空本子上记录的小火花（异常）全部绑在这个大爆炸（异常）上，一起交上去，方便厂长看日志追责！*/
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
/* ------------------------------------- 🧹 第四战区：打扫战场与产品入库 ------------------------------------------------------------------------- */
					if (recordSuppressedExceptions) {
						// 把记录本销毁，防止内存泄漏
						this.suppressedExceptions = null;
					}
					/* [架构师视角] 单例创建后置处理，移除创建中状态标记。
					 * [动作拆解] 机器造完了（不管是成功还是失败爆炸），都必须立刻去【正在制造的黑板】上把这台机器的名字擦掉！证明这个工位空出来了！*/
					afterSingletonCreation(beanName);
				}
				/* [架构师视角] 将成功创建的完整 Bean 加入一级缓存，并清理二级、三级缓存。
				 * [动作拆解] 🎊 恭喜厂长！这是一台崭新的、通过所有质检的完美机器！ 执行 addSingleton，由大管家敲锣打鼓把它捧进【一号现货VIP仓库】（singletonObjects），同时把那些为了防循环依赖而准备的【半成品临时仓库】（早期缓存）统统清空砸碎！*/
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			// 【车间大白话】：最后，无论是一开始就在现货仓库找到的，还是刚才拼了老命现造出来的，把钥匙（引用）交给客户！交易完成！🤝
			return singletonObject;
		}
/*
 * 📊 【战略复盘：超级工厂顶级架构思想】
 * 厂长，这段代码看似就是个普通的 if-else 加个锁，但背后藏着 Spring 最深厚的功力：【双重检查的严谨 + 快速失败的底线 + 模板方法的扩展】。
 * 它完美诠释了“空间换时间”和“状态机”的思想——通过各种缓存 Map 和状态标记 Set（如 singletonsCurrentlyInCreation），硬生生在极其复杂的多线程并发与循环依赖泥潭中，走出了一条绝对安全、绝对单例的高速公路！
 * 它不负责具体怎么造机器（那是 createBean 的事），它只负责保证超级工厂的运转秩序绝对不崩盘！堪称架构典范！*/
	}

	/**
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(@Nullable String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null || dependentBeans.isEmpty()) {
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		if (alreadySeen == null) {
			alreadySeen = new HashSet<>();
		}
		alreadySeen.add(beanName);
		for (String transitiveDependency : dependentBeans) {
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		clearSingletonCache();
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependentBeanNames;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			dependentBeanNames = this.dependentBeanMap.remove(beanName);
		}
		if (dependentBeanNames != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependentBeanNames);
			}
			for (String dependentBeanName : dependentBeanNames) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
