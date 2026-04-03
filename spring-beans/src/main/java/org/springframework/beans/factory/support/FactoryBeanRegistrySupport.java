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

package org.springframework.beans.factory.support;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>FactoryBean 产物缓存支持——继承链的"第三层"，专治"3D 打印机"产物管理！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.FactoryBeanRegistrySupport}</li>
 * <li><b>中文名</b>：FactoryBean 注册表支撑类 —— 为"造机器的机器"的产物提供独立缓存和质检入口</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块</li>
 * <li><b>类层级</b>：{@code DefaultSingletonBeanRegistry} 的子类，{@code AbstractBeanFactory} 的直接父类</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个类？——FactoryBean 的产物需要"独立仓库"，不能和工厂本体混在一起！</h3>
 * <p>Spring 中存在一种特殊的 Bean——{@code FactoryBean}：它不是最终产品，而是"造产品的工厂"。
 * 比如 MyBatis 的 {@code SqlSessionFactoryBean}，它本身是个 FactoryBean，
 * 但用户真正需要的是它 {@code getObject()} 产出的 {@code SqlSessionFactory}。</p>
 * <p>这带来了一个核心矛盾：</p>
 * <ul>
 * <li><b>FactoryBean 本体</b>：注册在 {@code singletonObjects}（一级缓存），名字是 "myFactory"</li>
 * <li><b>FactoryBean 产物</b>：从 {@code getObject()} 获得的对象，需要<b>单独缓存</b>，不能混进一级缓存——
 *     因为一级缓存里 "myFactory" 这个位置已经被工厂本体占了！</li>
 * </ul>
 * <p>如果把 FactoryBean 产物缓存逻辑混在 DefaultSingletonBeanRegistry 里，问题有三：</p>
 * <ol>
 * <li><b>概念入侵</b>：DefaultSingletonBeanRegistry 完全不知道"FactoryBean"是什么，
 *     它只管"单例的存取"。把 FactoryBean 的概念塞进去，违反了分层设计</li>
 * <li><b>缓存冲突</b>：一级缓存中 beanName → 工厂本体，产物也要缓存但名字相同——
 *     必须开辟独立的 {@code factoryBeanObjectCache}</li>
 * <li><b>后处理入口</b>：FactoryBean 产物也需要走 BeanPostProcessor 质检，
 *     但质检逻辑属于 AbstractBeanFactory 层。FactoryBeanRegistrySupport 通过
 *     {@code postProcessObjectFromFactoryBean()} 这个模板方法钩子，把质检权留给上层覆盖</li>
 * </ol>
 *
 * <h3>🧬 这个类只做三件事</h3>
 * <ol>
 * <li><b>独立缓存</b>：{@code factoryBeanObjectCache}（ConcurrentHashMap）——存放 FactoryBean.getObject() 的产物</li>
 * <li><b>取产物</b>：{@code getObjectFromFactoryBean()} —— 六幕大戏：双重检查锁 + 循环依赖防御 + 质检员入场 + 入库</li>
 * <li><b>调产物</b>：{@code doGetObjectFromFactoryBean()} —— 真正调用 factory.getObject() 的原爆点，
 *     包含 SecurityManager 特权执行和 NullBean 空对象防御</li>
 * </ol>
 *
 * <h3>🧬 业务借鉴——你的系统能偷师什么？</h3>
 * <ol>
 * <li><b>"工厂与产物分离缓存"模式</b><br/>
 * 在电商系统中，"模板"和"模板生成的实例"是两个不同的实体。
 * 比如促销模板（FactoryBean 本体）和具体促销活动实例（FactoryBean 产物），
 * 它们用同一个名字但对应不同对象，需要各自独立缓存。<br/>
 * 业务借鉴：当你的系统有"配置/模板→实例"的产出关系时，配置缓存和实例缓存必须分开，
 * 否则用同一个 key 会互相覆盖。</li>
 *
 * <li><b>"模板方法钩子"延迟到子类实现</b><br/>
 * {@code postProcessObjectFromFactoryBean()} 在本类中只是 return object（空实现），
 * 真正的 AOP 质检在 {@code AbstractAutowireCapableBeanFactory} 中覆盖实现。<br/>
 * 业务借鉴：在你的基础框架中，如果某个步骤"现在不知道怎么做，但知道一定要做"，
 * 就定义一个空的 protected 钩子方法，让具体业务子类去填充逻辑。</li>
 *
 * <li><b>"双重检查 + 循环引用重入防御"</b><br/>
 * {@code getObjectFromFactoryBean()} 在 doGetObjectFromFactoryBean 执行后再查一次缓存，
 * 因为 getObject() 内部可能触发了其他 getBean 调用导致当前对象被提前放入缓存。
 * 业务借鉴：任何涉及回调/钩子的方法，在回调返回后都应该重新验证状态，
 * 因为回调期间世界可能已经变了。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AliasRegistry (接口)
 * └── SimpleAliasRegistry             ← 第一层：别名管理
 *       └── DefaultSingletonBeanRegistry  ← 第二层：单例三级缓存+依赖图+销毁
 *             └── FactoryBeanRegistrySupport  ← 👈 你在这里！第三层：FactoryBean 产物独立缓存
 *                   └── AbstractBeanFactory      ← 第四层：完整的 getBean 流程编排
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>FactoryBeanRegistrySupport 是继承链中<b>最"薄"但最"专"的一层</b>——只有一个字段
 * {@code factoryBeanObjectCache}，只解决一个问题"FactoryBean 产物的独立缓存"。
 * 但正是这层薄薄的抽象，彻底隔离了"普通单例管理"和"FactoryBean 产物管理"两个不同关切，
 * 让上层 AbstractBeanFactory 在 getObjectForBeanInstance 中可以简洁地调用
 * getObjectFromFactoryBean，而不用操心缓存细节。<br/>
 * 这体现了 Spring 架构的核心哲学：<b>每一层只增加一种能力，不多不少</b>。</p>
 *
 * <hr/>
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 2.5.1
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	/** Cache of singleton objects created by FactoryBeans: FactoryBean name to object. */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


	/**
	 * Determine the type for the given FactoryBean.
	 * @param factoryBean the FactoryBean instance to check
	 * @return the FactoryBean's object type,
	 * or {@code null} if the type cannot be determined yet
	 */
	@Nullable
	protected Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
		try {
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(
						(PrivilegedAction<Class<?>>) factoryBean::getObjectType, getAccessControlContext());
			}
			else {
				return factoryBean.getObjectType();
			}
		}
		catch (Throwable ex) {
			// Thrown from the FactoryBean's getObjectType implementation.
			logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * Obtain an object to expose from the given FactoryBean, if available
	 * in cached form. Quick check for minimal synchronization.
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean,
	 * or {@code null} if not available
	 */
	@Nullable
	protected Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}

	/**
	 * <h3>💥 厂长亲临前线！3D 打印机顶级安保与质检操作手册 🚀</h3>
	 * <h4>【架构巅峰】FactoryBeanRegistrySupport#getObjectFromFactoryBean 源码精读</h4>
	 * <p> 这段代码可以说是大管家（BeanFactory）在操作 3D打印机（FactoryBean）时的<b>“顶级安保与质检操作手册”</b>！
	 * 它不仅要保证打印机吐出来的产品是单例（只造一次）、还要防范多条流水线同时抢夺这台打印机（并发安全）、更要防备在打印过程中出现的“套娃式”循环依赖、
	 * 最后还要把产品交给咱们的流水线质检员（BeanPostProcessor）去“魔改”！
	 *
	 * <ul>
	 * <li><b>操作对象：</b> 3D 打印机 (FactoryBean 实例)</li>
	 * <li><b>核心防御：</b> 全局单例互斥锁 (Singleton Mutex) + 双重检查 (Double-Check)</li>
	 * <li><b>终极魔改：</b> 质检员介入打补丁 (Post-Processing / AOP 代理生成)</li>
	 * </ul>
	 *
	 *
	 * @param factory 3D打印机本尊 (FactoryBean 实例)
	 * @param beanName 产品的官方注册名
	 * @param shouldPostProcess 厂长指令：是否允许质检员介入魔改
	 * @return 最终交付的成品（原味产品 或 披着机甲的 AOP 代理产物）


	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Obtain an object to expose from the given FactoryBean.
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @param shouldPostProcess whether the bean is subject to post-processing
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {

/* ====================================== 💥 第一幕：单例产品的 VIP 专线与“闲人免进”锁 ====================================== */
		/* [架构师视角] 判断当前 FactoryBean 是否声明生产单例，并且大管家 BeanFactory 已经记录了这台机器的单例注册信息。
		 * [动作拆解] 大管家看了一眼图纸：“哟，厂长吩咐了，这台打印机吐出的产品是全厂独一份的（Singleton）！” */
		if (factory.isSingleton() && containsSingleton(beanName)) {
			/* 🚨 [致命瓶颈] 全局互斥与并发防御
			 * [架构师视角] 细粒度并发控制！使用 getSingletonMutex() 获取单例注册表的全局互斥锁，防止多线程同时触发同一个 FactoryBean 的昂贵生产过程。
			 * [动作拆解] 既然是独一份，绝不能让几个工长同时来抢！大管家啪的一下，把这间特种车间的大门锁上（synchronized），挂上“闲人免进”的牌子！
			 */
			synchronized (getSingletonMutex()) {
				/* [架构师视角] 双重检查锁（Double-Check Locking）的第一层：查缓存。
				 * [动作拆解] 大管家先打开专属于 FactoryBean 产物的“VIP现货小保险箱”（factoryBeanObjectCache），摸一摸有没有现成的。*/
				Object object = this.factoryBeanObjectCache.get(beanName);
				if (object == null) {

/* ====================================== 🚀 第二幕：拉下电闸，3D打印机真正开机造物！ ====================================== */
					/* [架构师视角] 缓存未命中，委托给 doGetObjectFromFactoryBean 真正调用 factory.getObject()。
					 * [动作拆解] 保险箱是空的！大管家亲手拉下 3D打印机 的高压电闸，轰隆隆……火花四溅，机器吐出了一个原汁原味的产品！*/
					object = doGetObjectFromFactoryBean(factory, beanName);

/* ====================================== 🕵️‍♂️ 第三幕：防范“套娃连环计”（循环依赖极致防御） ====================================== */
					/* 🚨 [高能预警] 循环依赖极限拉扯
					 * [架构师视角] 极其精妙的设计！在 doGetObjectFromFactoryBean 执行期间，自定义的 getObject() 逻辑可能触发了其他 getBean 调用，导致当前对象被提前暴露或由于循环依赖已经被放入缓存。因此必须进行第二次缓存检查！
					 * [动作拆解] 警报！大管家是个老油条，他知道刚刚打印机开机时，可能发生过“产品A依赖B，B又调头找A”的连环套娃！为了防重复制造，大管家回头又看了一眼“VIP小保险箱”！ */
					// Only post-process and store if not put there already during getObject() call above
					// (e.g. because of circular reference processing triggered by custom getBean calls)
					Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
					if (alreadyThere != null) {
						/*  [动作拆解] 卧槽，还真有别的流水线趁乱把这东西造出来放进去了！ 大管家果断丢掉手里刚打印的，拿起保险箱里的那个（保证单例绝对唯一，绝不闹真假美猴王）！*/
						object = alreadyThere;
					}
					else {
/* ====================================== 🔥 第四幕：流水线质检员（BeanPostProcessor）入场魔改！====================================== */
						/* [动作拆解] 确认没人截胡。现在看看图纸上有没有要求“质检”（shouldPostProcess）。 */
						if (shouldPostProcess) {
							/* [架构师视角] 检查目标 bean 是否正处于其他的创建流程中（例如正在被外层普通的 Bean 实例化）。如果是，暂缓后置处理以防死循环。
							 * [动作拆解] 大管家看了一眼机器状态，如果它正处于“半成品组装状态”，就先别让质检员上了，直接把原味产品交出去。*/
							if (isSingletonCurrentlyInCreation(beanName)) {
								// Temporarily return non-post-processed object, not storing it yet..
								return object;
							}
							/* [原理解析] 把 beanName 标记为“正在创建中”，防止质检过程中出现重入导致死循环。
							 * [动作拆解] 大管家在黑板上写下：“XX产品正在接受质检，其他流水线别来捣乱！”
							 */
							beforeSingletonCreation(beanName);
							try {
								/* 🌟 [核心魔改点] AOP 代理的诞生之地
								 * [架构师视角] 循环调用所有 BeanPostProcessor 的 postProcessAfterInitialization。这里是将普通对象包装为 CGLIB/JDK 动态代理对象的绝对核心！
								 * [动作拆解] 质检员们一拥而上！有的给产品刷漆，有的给产品装上监控探头（AOP）。 最后出来的 object，可能已经不是刚才那个原味产品，而是被披上一层机甲的“强化版”了！*/
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							catch (Throwable ex) {
								throw new BeanCreationException(beanName,
										"Post-processing of FactoryBean's singleton object failed", ex);
							}
							finally {
								/* [动作拆解] 质检结束，大管家擦掉黑板上的名字。 */
								afterSingletonCreation(beanName);
							}
						}

/* ====================================== 📦 第五幕：存入保险箱，大功告成！====================================== */
						/* [动作拆解] 东西造好了，也被质检员强化过了。如果名册里还有它，就锁进“VIP现货小保险箱”（factoryBeanObjectCache）。 下次厂长再要，直接给现货！*/
						if (containsSingleton(beanName)) {
							this.factoryBeanObjectCache.put(beanName, object);
						}
					}
				}
				// [动作拆解] 解开大门的锁，把最终的完美产品交给厂长！单例战区，撤退！
				return object;
			}
		}

/* ====================================== 🌪️ 第六幕：多例（Prototype）产品的狂野流水线 ====================================== */
		else {
			/* [原理解析] 如果 factory.isSingleton() 为 false，说明这是一个多例（Prototype）对象，每次都要重新创建，不加锁，也不存缓存。
			 * [车间大白话] 如果厂长说“这玩意儿是消耗品，我要一个你当场给我现造一个”！那大管家就不废话了，不上锁，不存保险箱！ 直接通电开机（doGetObjectFromFactoryBean）！ */
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			if (shouldPostProcess) {
				try {
					// [动作拆解] 同样扔给质检员去魔改打补丁！
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			// [动作拆解] 造完、改完，直接拿走，不留痕迹！
			return object;
		}


/*
📊 【战略复盘：超级工厂顶级架构思想】
厂长，这段代码短小精悍，却暗藏杀机，它集中体现了 Spring 的三大防御与扩展思想：

1. 极限的并发防御与锁的粒度控制 (Double-Check Locking & Mutex)：
Spring 在处理单例时，没有粗暴地给整个方法加锁，而是巧妙地锁住了 getSingletonMutex()，并在打印前后进行了两次缓存检查（防多线程、防循环引用导致的内部重入）。

2. 生命周期钩子机制 (Open-Closed Principle / 模板方法变种)：
shouldPostProcess 和 postProcessObjectFromFactoryBean 的设计，让 Spring 核心容器不需要懂什么是 AOP、什么是事务，它只负责留出一个“质检口”。第三方插件只要派驻质检员（BeanPostProcessor），就能在这个阶段把机器偷梁换柱！

3. 空间换时间与职责分离：
为 FactoryBean 的产物单独开辟了一个 factoryBeanObjectCache 仓库，与普通 Bean 的单例池隔离开来，让不同类型的物料管理互不干扰！


4. 3D打印机不仅通电了，而且连质检员怎么在上面搞“机甲魔改”的整体流程咱们都看清楚了！但现在的战场留下了两个极其诱人的黑盒悬念：
👉 A： doGetObjectFromFactoryBean 这个方法，看看大管家到底是怎么跟第三方组件（比如 MyBatis）对接，调用它们自己写的 factory.getObject() 方法的？（这是探究框架整合机制的核心！）
👉 B：掀开质检员的面具！ 进入 postProcessObjectFromFactoryBean 这个方法，看看咱们的质检员（尤其是 BeanPostProcessor）到底是用了什么黑魔法，把一个普通的对象变成了一个具有 AOP 拦截能力的动态代理对象！
*/
}

	/**
	 * <h3>💥 厂长直捣黄龙！3D打印机原爆点点火实况 🏭</h3>
	 * <h4>【架构巅峰】FactoryBeanRegistrySupport#doGetObjectFromFactoryBean 源码精读</h4>
	 * <p>报告厂长！我们现在已经彻底潜入了 3D 打印机的最深处、齿轮咬合最紧密的动力核心舱！这段代码虽然不长，但它是整个超级工厂与第三方生态（如 MyBatis SqlSessionFactoryBean、Dubbo 的 ReferenceBean）发生物理级碰撞的<b>绝对原爆点</b>！</p>
	 *
	 * <ul>
	 * <li><b>核心枢纽：</b> <code>factory.getObject()</code> (造物权彻底移交第三方)</li>
	 * <li><b>安保防御：</b> SecurityManager 特权越权执行</li>
	 * <li><b>容错机制：</b> NullBean 终极占位防爆</li>
	 * </ul>
	 *
	 * <blockquote>
	 * <b>【大管家的造物倒计时】</b><br/>
	 * “全厂注意！安保探头就绪！托盘准备好！我马上要拉下 3D打印机的绿色 START 键！不管是 MyBatis 还是 Redis 定制的机器，只要按下这按钮，不管吐出什么神器，还是吐出一团空气，我都得给厂长稳稳接住咯！”
	 * </blockquote>
	 *
	 * * <h4>📋 【车间物料交接清单 (入参/出参解构)】</h4>
	 * <ul>
	 * <li><b><code>factory</code> (入参) -> 【3D打印机本尊】：</b> 拥有自定义造物能力的特种机器，第三方组件接入 Spring 的超级桥梁。</li>
	 * <li><b><code>beanName</code> (入参) -> 【产品的官方名】：</b> 厂长提货单上写的名字，也是产品出厂登记在册的流水号。</li>
	 * <li><b><code>return</code> (出参) -> 【接在托盘里的神秘产品】：</b> 机器轰鸣之后，真正掉落出来的业务实体（也可能是一个装空气的 NullBean 真空防伪盒）。</li>
	 * <li><b><code>BeanCreationException</code> (异常) -> 🚨【车间爆炸事故】：</b> 机器预热未完成或内部代码抛错导致生产线崩溃，大管家出具的最高级别事故报告！</li>
	 * </ul>
	 * <hr/>
	 *
	 * Obtain an object to expose from the given FactoryBean.
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, String beanName) throws BeanCreationException {
		/* [动作拆解] 大管家深吸一口气，准备了一个托盘，用来接住马上要掉出来的产品。*/
		Object object;
		try {
/* ========================================================  🛡️ 第一幕：厂区安检与特权通行证 (Java SecurityManager 机制) ========================================================  */
			/* [架构师视角] 判断当前 JVM 是否开启了安全管理器（SecurityManager）。如果开启了，执行高权限操作需要特殊授权。
			 * [动作拆解] 大管家抬头看了一眼厂区的安保探头。哟，今天厂长开启了“最高级别安防模式”（SecurityManager != null）！*/
			if (System.getSecurityManager() != null) {
				// [动作拆解] 既然安保这么严，大管家赶紧掏出自己的“特级通行证”（AccessControlContext）。
				AccessControlContext acc = getAccessControlContext();
				try {
					/* [架构师视角] 在特权上下文中执行 factory::getObject。即使调用栈中有无权限的类，只要 Spring 核心有权限，就能强行越权造物（沙箱逃逸）。
					 * [动作拆解] 大管家把特级通行证往安检机上一刷（doPrivileged），大吼一声：“奉厂长之命，特权开机，都给我闪开！” 轰隆一声！3D打印机在安保的注视下强行启动，吐出了产品（factory::getObject）！*/
					object = AccessController.doPrivileged((PrivilegedExceptionAction<Object>) factory::getObject, acc);
				}
				catch (PrivilegedActionException pae) {
					// [动作拆解] 如果特权执行时机器还是冒烟了，就把核心异常扒出来往上抛。
					throw pae.getException();
				}
			}
			else {
/* ========================================================  🚀 第二幕：火力全开，无差别造物！(正常情况下的执行路径) ========================================================*/
				/* 🚨 [致命瓶颈] 终极原爆点！控制反转 (IoC) 的最高潮！
				 * [架构师视角] 这是 Spring 框架整合天下万物的核心接口调用！无论是谁（MyBatis/Redis/MQ）写的 FactoryBean，都在这一行真正实例化了目标对象。
				 * [动作拆解] 厂区没开最高安防？那就直接干！大管家猛拉电闸，3D打印机马力全开，随着一阵电火花和机械轰鸣，第三方厂商定做的那个【神秘物件】当场落地（factory.getObject()）！ */
				object = factory.getObject();
			}
		}
/* ========================================================  🚑 第三幕：车间事故紧急处理预案 (异常捕获) ======================================================== */
		catch (FactoryBeanNotInitializedException ex) {
			/* [架构师视角] FactoryBean 特有异常，表示自身依赖尚未满足，处于未就绪状态。
			 * [动作拆解] 3D打印机突然亮起红灯：“大管家，我预热还没结束呢，料匣还是空的！” 大管家马上拉响警报：“厂长，这机器目前正处于半成品状态，造不了！”*/
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			/* [架构师视角] 宽泛捕获一切 Throwable，作为顶级容器，防止第三方 FactoryBean 内部代码写的烂导致整个 Spring 容器崩溃。
			 * [动作拆解] 3D打印机直接爆炸起火（抛出未知异常）！大管家拿灭火器一顿喷，然后写下事故报告：“厂长，这破机器在生产时炸了，快叫第三方厂家来修！”*/
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

/* ========================================================  💨 第四幕：面对“空气产品”的终极防御 (NullBean 空对象模式) ========================================================  */
		/* [动作拆解] 机器停了，大管家往托盘里一看：“卧槽？一顿操作猛如虎，结果吐出了一团空气（object == null）？！” */
		// Do not accept a null value for a FactoryBean that's not fully
		// initialized yet: Many FactoryBeans just return null then.
		if (object == null) {
			/* [架构师视角] 如果此时这个 bean 正在被外层创建流程阻拦，说明陷入了未完成的循环或提早触发状态，返回 null 是极其危险的（可能掩盖了真实的循环依赖问题）。
			 * [动作拆解] 大管家查生产记录：“如果这打印机本来就还没组装完（currently in creation），你特么就给我吐空气？这是严重的生产事故，必须上报！”*/
			if (isSingletonCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			/* 🚨 [高能预警] 空对象模式的降维打击
			 * [架构师视角] 防御性编程极致。Spring 拒绝让 null 值进入后续的 ConcurrentHashMap（会抛 NPE）。使用 NullBean 作为占位标记，优雅消灭下游的所有 if (obj != null) 判断。
			 * [动作拆解] 如果机器没毛病，但就是喜欢吐空气（正常返回 null）。大管家机智地拿出了厂里特制的【防伪真空包装盒】（NullBean），把空气装进去，变成了一个实体物件！妙啊！ */
			object = new NullBean();
		}
		// [动作拆解] 大管家双手捧着刚刚打印出来的产品（或者是装了空气的真空盒），大步流星地走出 3D 打印车间！
		return object;

/*
 * 📊 【战略复盘：超级工厂顶级架构思想】
 * 报告厂长，这段代码看似是个简单的 try-catch，但其实蕴含了 Spring 架构师的三大杀招：
 *
 * 1. 控制反转与开放封闭的极致体现 (IoC & OCP)：
 * 核心代码只有一句 factory.getObject()。Spring 根本不关心里面造的是数据库连接池，还是 RPC 代理对象。你只要按我的规矩实现 FactoryBean 接口，剩下的舞台全交给你！框架极强的横向扩展能力就源于此！
 *
 * 2. 空对象模式的降维打击 (Null Object Pattern)：
 * 面对第三方代码可能返回的 null，Spring 没有用无数个 if (obj != null) 去恶心后续的生命周期流程，而是优雅地包装成一个 NullBean 实体。完美避开了并发容器对 null 值的敏感，也消灭了潜在的空指针异常洪水！
 *
 * 3. 沙箱逃逸与特权执行 (SecurityManager 兼容)：
 * 作为一个顶级基础设施，Spring 必须考虑被部署在极端严格的 Java 安全策略环境中（如企业级应用服务器）。AccessController.doPrivileged 保证了即使外层业务线权限受限，核心流水线依然能拿到最高授权完成制造！
 */
	}

	/**
	 * Post-process the given object that has been obtained from the FactoryBean.
	 * The resulting object will get exposed for bean references.
	 * <p>The default implementation simply returns the given object as-is.
	 * Subclasses may override this, for example, to apply post-processors.
	 * @param object the object obtained from the FactoryBean.
	 * @param beanName the name of the bean
	 * @return the object to expose
	 * @throws org.springframework.beans.BeansException if any post-processing failed
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * Get a FactoryBean for the given bean if possible.
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance
	 * @return the bean instance as FactoryBean
	 * @throws BeansException if the given bean cannot be exposed as a FactoryBean
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return (FactoryBean<?>) beanInstance;
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanObjectCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanObjectCache.clear();
		}
	}

	/**
	 * Return the security context for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the security context returned by this method.
	 * @see AccessController#getContext()
	 */
	protected AccessControlContext getAccessControlContext() {
		return AccessController.getContext();
	}

}
