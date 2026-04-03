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

package org.springframework.context.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>@EventListener 的"翻译官"——把注解方法变成真正的 ApplicationListener！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.event.EventListenerMethodProcessor}</li>
 * <li><b>中文名</b>：事件监听器方法处理器 —— 把 @EventListener 注解的方法"升级"为容器级监听器</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 event 包（注意！event 包 = Spring 事件驱动模型的大本营！
 * 这里聚集了事件体系的所有核心成员：事件广播器（ApplicationEventMulticaster/SimpleApplicationEventMulticaster）、
 * 事件监听器适配（GenericApplicationListenerAdapter/SmartApplicationListener）、
 * 以及本类——把 @EventListener 方法"翻译"成 ApplicationListener 的桥梁。
 * 一句话：<b>凡是和"事件发布-监听"相关的，都在这个包里！</b>）</li>
 * <li><b>身份</b>：{@code SmartInitializingSingleton}（所有单例初始化完成后触发）+
 * {@code ApplicationContextAware}（获取上下文）+ {@code BeanFactoryPostProcessor}（5.1+，提前注册避免 AOP 干扰）</li>
 * </ul>
 *
 * <h3>💡 为什么需要这个处理器？——@EventListener 是"语法糖"，需要有人翻译！</h3>
 * <p>Spring 4.2 之前，监听事件必须实现 ApplicationListener 接口：</p>
 * <pre>{@code
 * // 旧方式：必须实现接口
 * public class OrderListener implements ApplicationListener<OrderCreatedEvent> {
 *     @Override public void onApplicationEvent(OrderCreatedEvent event) { ... }
 * }
 * }</pre>
 * <p>Spring 4.2 引入 @EventListener 注解后，只需在任意 Bean 的方法上标注即可：</p>
 * <pre>{@code
 * // 新方式：注解驱动
 * @Component
 * public class OrderHandler {
 *     @EventListener
 *     public void handleOrderCreated(OrderCreatedEvent event) { ... }
 * }
 * }</pre>
 * <p>但 Spring 的事件广播器（ApplicationEventMulticaster）只认 ApplicationListener 接口——
 * 它不认识 @EventListener 注解！<br/>
 * <b>本类就是那个"翻译官"</b>：它在所有单例 Bean 初始化完成后，扫描每个 Bean 的方法，
 * 找到带 @EventListener 的方法，用 {@link org.springframework.context.event.EventListenerFactory}
 * 把它包装成一个 {@code ApplicationListenerMethodAdapter}，然后注册到容器的监听器列表中。</p>
 *
 * <h3>🧬 执行时机与链路</h3>
 * <pre>
 * refresh()
 *   ├── invokeBeanFactoryPostProcessors()
 *   │     └── EventListenerMethodProcessor.postProcessBeanFactory()  ← 提前获取 EventListenerFactory 列表
 *   ├── finishBeanFactoryInitialization()
 *   │     └── preInstantiateSingletons()
 *   │           └── afterSingletonsInstantiated()   ← 👈 SmartInitializingSingleton 回调，本类核心逻辑在此！
 *   │                 └── processBean(beanName, type)
 *   │                       ├── 反射扫描 type 的所有方法
 *   │                       ├── 过滤出带 @EventListener 注解的方法
 *   │                       ├── 遍历每个 @EventListener 方法：
 *   │                       │     ├── 选择合适的 EventListenerFactory
 *   │                       │     ├── 调 factory.createApplicationListener(beanName, type, method)
 *   │                       │     │     └── 返回 ApplicationListenerMethodAdapter
 *   │                       │     └── 注册到 ApplicationContext.addApplicationListener()
 *   │                       └── 完成！@EventListener 方法变成了真正的 ApplicationListener
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>SmartInitializingSingleton 的巧妙运用——"等所有人到齐了再干活"</b><br/>
 * 本类选择在 afterSingletonsInstantiated 时机执行（所有单例 Bean 都已初始化完毕），
 * 而不是用 BPP 在每个 Bean 初始化时逐个扫描。<br/>
 * 原因：@EventListener 方法可能在 AOP 代理类上，如果太早扫描（Bean 还没被代理），
 * 可能找不到真实的目标方法。等所有 Bean 都"定型"后再统一扫描，更安全。<br/>
 * <b>业务借鉴</b>：当你的处理逻辑依赖"所有组件都就绪"时，不要在初始化过程中逐个处理，
 * 而是注册一个"全部就绪后的回调"来统一处理。</li>
 *
 * <li><b>EventListenerFactory 的策略模式——可扩展的翻译机制</b><br/>
 * 本类不直接创建 ApplicationListener 适配器，而是委托 {@code EventListenerFactory}。
 * 默认实现是 DefaultEventListenerFactory，但你可以注册自定义 Factory 来支持特殊注解
 * （如 @TransactionalEventListener 就有自己的 TransactionalEventListenerFactory）。<br/>
 * <b>业务借鉴</b>：当"翻译规则"可能变化时，用策略模式抽象，而不是硬编码。</li>
 *
 * <li><b>5.1 新增 BFPP 身份——提前注册避免 AOP 副作用</b><br/>
 * 自 Spring 5.1 起，本类额外实现了 BeanFactoryPostProcessor，
 * 在 postProcessBeanFactory 中提前获取 EventListenerFactory 列表。<br/>
 * 目的：避免在后续 SmartInitializingSingleton 阶段才去 getBean 获取 Factory，
 * 防止触发不必要的 AOP 代理创建。</li>
 * </ol>
 *
 * <h3>🧬 架构定位</h3>
 * <pre>
 * 注册时机（AnnotationConfigUtils 预装）:
 *   AnnotationConfigUtils.registerAnnotationConfigProcessors()
 *     └── 注册 "internalEventListenerProcessor" → EventListenerMethodProcessor  ← 👈 你在这里！
 *     └── 注册 "internalEventListenerFactory"   → DefaultEventListenerFactory
 *
 * 协作关系：
 *   EventListenerMethodProcessor（扫描 @EventListener 方法）
 *     └── 委托 EventListenerFactory（策略接口）
 *           ├── DefaultEventListenerFactory → 生成 ApplicationListenerMethodAdapter
 *           └── TransactionalEventListenerFactory → 生成 TransactionalApplicationListenerMethodAdapter
 * </pre>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>EventListenerMethodProcessor 的核心价值：<b>在所有单例 Bean 初始化完成后，
 * 扫描 @EventListener 注解方法，通过 EventListenerFactory 将其"翻译"为 ApplicationListener 并注册</b>。<br/>
 * 它是 @EventListener 注解能生效的幕后功臣——没有它，@EventListener 就只是一个普通注解。
 * 它选择 SmartInitializingSingleton 时机执行（而非 BPP），确保所有 Bean 都已"定型"（含 AOP 代理），
 * 从而能准确找到目标方法。</p>
 *
 * <hr/>
 * Registers {@link EventListener} methods as individual {@link ApplicationListener} instances.
 * Implements {@link BeanFactoryPostProcessor} (as of 5.1) primarily for early retrieval,
 * avoiding AOP checks for this processor bean and its {@link EventListenerFactory} delegates.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 4.2
 * @see EventListenerFactory
 * @see DefaultEventListenerFactory
 */
public class EventListenerMethodProcessor
		implements SmartInitializingSingleton, ApplicationContextAware, BeanFactoryPostProcessor {

	/**
	 * Boolean flag controlled by a {@code spring.spel.ignore} system property that instructs Spring to
	 * ignore SpEL, i.e. to not initialize the SpEL infrastructure.
	 * <p>The default is "false".
	 */
	private static final boolean shouldIgnoreSpel = SpringProperties.getFlag("spring.spel.ignore");


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private ConfigurableApplicationContext applicationContext;

	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	@Nullable
	private List<EventListenerFactory> eventListenerFactories;

	@Nullable
	private final EventExpressionEvaluator evaluator;

	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));


	public EventListenerMethodProcessor() {
		if (shouldIgnoreSpel) {
			this.evaluator = null;
		}
		else {
			this.evaluator = new EventExpressionEvaluator();
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
				"ApplicationContext does not implement ConfigurableApplicationContext");
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	/**
	 * <h3>架构巅峰：广播站站长的战前准备 📻</h3>
	 * <p>
	 * 刚才一直在死磕【元老 1】（配置类解析器），这段代码属于另一位极其重量级的核心幕后大佬
	 * —— <b>【广播站站长】(EventListenerMethodProcessor)</b>！
	 * </p>
	 * <p>
	 * 如果你在开发中用过 {@code @EventListener} 注解来实现事件监听和解耦，那你现在抓到的
	 * 正是处理这个注解的最高总指挥！这位站长其实也是一个图纸审核员 (BeanFactoryPostProcessor)。
	 * 让我们用“车间大白话”一行行拆解他在<b>图纸审核阶段（第 5 步）</b>到底在密谋什么：
	 * </p>
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		/*
		 * 📻 工序一：接过工厂大权 (获取工厂引用)
		 * ---------------------------------------------------------
		 * [车间大白话] 站长一进门，先把大管家 (beanFactory) 的联系方式存进自己的手机里。
		 * 因为在未来的阶段，他要经常找大管家要各种活生生的 Bean 对象。
		 */
		this.beanFactory = beanFactory;

		/*
		 * 📻 工序二：全厂搜寻“喇叭制造厂” (获取 EventListenerFactory)
		 * ---------------------------------------------------------
		 * [原理解析] EventListenerFactory 是 Spring 内部专门用来把贴了 @EventListener
		 * 的普通方法，转换成真正的 ApplicationListener (监听器对象) 的工厂零件。
		 *
		 * [车间大白话] 站长心想：“等会儿肯定有很多业务类想听广播，但我自己不会造喇叭啊！”
		 * 于是他让大管家去图纸堆里找一找，看看厂里有没有注册过专门造喇叭的包工头。
		 * (注：Spring 默认提供 DefaultEventListenerFactory，如果有响应式环境还会加上
		 * TransactionalEventListenerFactory)。站长把这些包工头集合起来，列成清单。
		 */
		Map<String, EventListenerFactory> beans = beanFactory.getBeansOfType(EventListenerFactory.class, false, false);
		List<EventListenerFactory> factories = new ArrayList<>(beans.values());

		/*
		 * 📻 工序三：论资排辈，整装待发 (优先级排序)
		 * ---------------------------------------------------------
		 * [车间大白话] 既然找来了好几个造喇叭的包工头，总得有个先后顺序吧？
		 * 站长根据他们头上的 @Order 注解排个队，然后把这张排好序的“喇叭包工头名单”
		 * 揣进自己兜里 (this.eventListenerFactories)，准备未来大干一场。
		 */
		AnnotationAwareOrderComparator.sort(factories);
		this.eventListenerFactories = factories;

		/*
		 * =================================================================================
		 * 💡 [架构师破局真相]：时机未到！为什么他只干了这么点活？
		 * =================================================================================
		 * 看到这里你可能会疑惑：“这位站长怎么不干正事啊？我写在业务类里的 @EventListener
		 * 方法，他怎么不去解析绑定？”
		 *
		 * 🚨 这就是 Spring 架构设计最绝妙的地方：因为时机未到！
		 *
		 * 请时刻记住咱们现在所处的阶段：这是 refresh() 的第 5 步！此时的工厂里，全是一张张
		 * 图纸 (BeanDefinition)，根本没有真正活生生的业务对象 (Bean)！如果业务对象都没被
		 * new 出来，你的方法也就不存在，站长怎么可能现在就去给你绑定监听器呢？
		 *
		 * 所以，在这段代码里，广播站站长只是在做“战前准备”：把需要的工具人先找齐揣在兜里。
		 *
		 * 👑 [他什么时候才真正发威？]
		 * EventListenerMethodProcessor 还实现了另一个极其特殊的接口：SmartInitializingSingleton。
		 * 等到未来的第 11 步，工厂把所有的单例 Bean 全部 new 出来、装配好之后，这位站长会
		 * 再次出场（触发 afterSingletonsInstantiated 方法）。到那时候，他就会拿出兜里的这些
		 * 包工头，去扫描每一个活生生的对象，把带有 @EventListener 的方法统统变成真正的监听器，
		 * 并接入全厂的大喇叭系统！
		 */
	}


	@Override
	public void afterSingletonsInstantiated() {
		ConfigurableListableBeanFactory beanFactory = this.beanFactory;
		Assert.state(this.beanFactory != null, "No ConfigurableListableBeanFactory set");
		String[] beanNames = beanFactory.getBeanNamesForType(Object.class);
		for (String beanName : beanNames) {
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
				Class<?> type = null;
				try {
					type = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
				}
				catch (Throwable ex) {
					// An unresolvable bean type, probably from a lazy bean - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
					}
				}
				if (type != null) {
					if (ScopedObject.class.isAssignableFrom(type)) {
						try {
							Class<?> targetClass = AutoProxyUtils.determineTargetClass(
									beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
							if (targetClass != null) {
								type = targetClass;
							}
						}
						catch (Throwable ex) {
							// An invalid scoped proxy arrangement - let's ignore it.
							if (logger.isDebugEnabled()) {
								logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
							}
						}
					}
					try {
						processBean(beanName, type);
					}
					catch (Throwable ex) {
						throw new BeanInitializationException("Failed to process @EventListener " +
								"annotation on bean with name '" + beanName + "'", ex);
					}
				}
			}
		}
	}

	private void processBean(final String beanName, final Class<?> targetType) {
		if (!this.nonAnnotatedClasses.contains(targetType) &&
				AnnotationUtils.isCandidateClass(targetType, EventListener.class) &&
				!isSpringContainerClass(targetType)) {

			Map<Method, EventListener> annotatedMethods = null;
			try {
				annotatedMethods = MethodIntrospector.selectMethods(targetType,
						(MethodIntrospector.MetadataLookup<EventListener>) method ->
								AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class));
			}
			catch (Throwable ex) {
				// An unresolvable type in a method signature, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
				}
			}

			if (CollectionUtils.isEmpty(annotatedMethods)) {
				this.nonAnnotatedClasses.add(targetType);
				if (logger.isTraceEnabled()) {
					logger.trace("No @EventListener annotations found on bean class: " + targetType.getName());
				}
			}
			else {
				// Non-empty set of methods
				ConfigurableApplicationContext context = this.applicationContext;
				Assert.state(context != null, "No ApplicationContext set");
				List<EventListenerFactory> factories = this.eventListenerFactories;
				Assert.state(factories != null, "EventListenerFactory List not initialized");
				for (Method method : annotatedMethods.keySet()) {
					for (EventListenerFactory factory : factories) {
						if (factory.supportsMethod(method)) {
							Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
							ApplicationListener<?> applicationListener =
									factory.createApplicationListener(beanName, targetType, methodToUse);
							if (applicationListener instanceof ApplicationListenerMethodAdapter) {
								((ApplicationListenerMethodAdapter) applicationListener).init(context, this.evaluator);
							}
							context.addApplicationListener(applicationListener);
							break;
						}
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @EventListener methods processed on bean '" +
							beanName + "': " + annotatedMethods);
				}
			}
		}
	}

	/**
	 * Determine whether the given class is an {@code org.springframework}
	 * bean class that is not annotated as a user or test {@link Component}...
	 * which indicates that there is no {@link EventListener} to be found there.
	 * @since 5.1
	 */
	private static boolean isSpringContainerClass(Class<?> clazz) {
		return (clazz.getName().startsWith("org.springframework.") &&
				!AnnotatedElementUtils.isAnnotated(ClassUtils.getUserClass(clazz), Component.class));
	}

}
