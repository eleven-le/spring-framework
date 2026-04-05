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

import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Bean 生命周期的"质检员"——在初始化前后介入每个 Bean，实现横切关注点的核心扩展接口！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.BeanPostProcessor}</li>
 * <li><b>中文名</b>：Bean 后置处理器 —— 生产线上的"质检工位"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包（注意！config 包 = 框架内部配置契约！
 * BeanPostProcessor 和 BeanFactoryPostProcessor 都在这里，它们是 Spring 扩展性的两大支柱。
 * 一句话：<b>config 包定义了"容器怎么配置、怎么扩展"的所有契约！</b>）</li>
 * <li><b>接口层级</b>：顶层独立接口，只定义 <b>2 个方法</b>（before + after）</li>
 * </ul>
 *
 * <h3>💡 为什么需要 BeanPostProcessor？——"每个 Bean 都要过的安检门"！</h3>
 * <p>Spring 创建 Bean 的流程是：实例化 → 属性注入 → 初始化。<br/>
 * BPP 在<b>初始化阶段</b>的前后各插入一个钩子，让你有机会对<b>每一个 Bean</b>做横切处理：</p>
 * <pre>
 * createBeanInstance()          ← 实例化（反射创建对象）
 * populateBean()                ← 属性注入（@Autowired/@Value）
 * initializeBean() {
 *   invokeAwareMethods()        ← Aware 回调
 *   ★ postProcessBeforeInitialization()  ← 👈 BPP 前置！（@PostConstruct 在此执行）
 *   invokeInitMethods()         ← InitializingBean.afterPropertiesSet + init-method
 *   ★ postProcessAfterInitialization()   ← 👈 BPP 后置！（AOP 代理在此创建）
 * }
 * </pre>
 *
 * <h3>🧬 Spring 内置的关键 BPP 实现</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>实现类</th><th>执行时机</th><th>功能</th></tr>
 * <tr><td>{@code AutowiredAnnotationBeanPostProcessor}</td><td>注入阶段（InstantiationAwareBPP）</td><td>处理 @Autowired/@Value/@Inject</td></tr>
 * <tr><td>{@code CommonAnnotationBeanPostProcessor}</td><td>前置</td><td>处理 @PostConstruct/@PreDestroy/@Resource</td></tr>
 * <tr><td>{@code ApplicationContextAwareProcessor}</td><td>前置</td><td>回调 ApplicationContextAware 等 6 个 Aware 接口</td></tr>
 * <tr><td>{@code AbstractAutoProxyCreator}</td><td>后置</td><td>AOP 代理创建（@Transactional/@Async/自定义切面）</td></tr>
 * <tr><td>{@code AsyncAnnotationBeanPostProcessor}</td><td>后置</td><td>@Async 方法的代理包装</td></tr>
 * </table>
 *
 * <h3>🧬 继承体系——BPP 的"特种兵"子接口</h3>
 * <pre>
 * BeanPostProcessor（顶层）                ← 👈 你在这里！（初始化前后）
 * ├── InstantiationAwareBeanPostProcessor  （实例化前后 + 属性注入阶段）
 * │     └── SmartInstantiationAwareBeanPostProcessor（构造器推断 + 早期引用）
 * ├── DestructionAwareBeanPostProcessor    （销毁阶段）
 * └── MergedBeanDefinitionPostProcessor    （BD 合并后、实例化前——收集注解元信息）
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"对每个产品统一质检"的流水线模式</b><br/>
 * BPP 像工厂质检工位：每个产品（Bean）从流水线经过时，都要接受同一批质检员的检查/加工。<br/>
 * <b>业务借鉴</b>：订单系统中，每个订单创建后都要经过"风控检查 → 优惠计算 → 积分扣减"——
 * 这些横切逻辑可以设计成"OrderPostProcessor"链。</li>
 *
 * <li><b>before 用于"增强属性"，after 用于"包装代理"——分工明确</b><br/>
 * before 阶段 Bean 还是原始对象，适合做属性注入、@PostConstruct 回调。
 * after 阶段是最后的机会，适合用代理包装（AOP、@Transactional）。<br/>
 * 如果 AOP 在 before 阶段就创建代理，那 initializeBean 里的 afterPropertiesSet 就会调到代理对象上——时序错乱！</li>
 *
 * <li><b>排序机制：PriorityOrdered → Ordered → 普通</b><br/>
 * 多个 BPP 的执行顺序由 PriorityOrdered/Ordered 接口控制。
 * 注意：编程式注册的 BPP 按注册顺序执行，@Order 注解对 BPP <b>不生效</b>！</li>
 * </ol>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>BeanPostProcessor 的核心价值：<b>在 Bean 初始化的前后插入钩子，
 * 让框架和用户都能以"横切"方式对每个 Bean 进行增强</b>。<br/>
 * @PostConstruct、AOP 代理、@Async、Aware 回调……这些 Spring 的核心特性，
 * 全部是通过 BPP 机制实现的。它是 Spring 扩展性的第一支柱（另一支柱是 BFPP）。</p>
 *
 * <hr/>
 * Factory hook that allows for custom modification of new bean instances &mdash;
 * for example, checking for marker interfaces or wrapping beans with proxies.
 *
 * <p>Typically, post-processors that populate beans via marker interfaces
 * or the like will implement {@link #postProcessBeforeInitialization},
 * while post-processors that wrap beans with proxies will normally
 * implement {@link #postProcessAfterInitialization}.
 *
 * <h3>Registration</h3>
 * <p>An {@code ApplicationContext} can autodetect {@code BeanPostProcessor} beans
 * in its bean definitions and apply those post-processors to any beans subsequently
 * created. A plain {@code BeanFactory} allows for programmatic registration of
 * post-processors, applying them to all beans created through the bean factory.
 *
 * <h3>Ordering</h3>
 * <p>{@code BeanPostProcessor} beans that are autodetected in an
 * {@code ApplicationContext} will be ordered according to
 * {@link org.springframework.core.PriorityOrdered} and
 * {@link org.springframework.core.Ordered} semantics. In contrast,
 * {@code BeanPostProcessor} beans that are registered programmatically with a
 * {@code BeanFactory} will be applied in the order of registration; any ordering
 * semantics expressed through implementing the
 * {@code PriorityOrdered} or {@code Ordered} interface will be ignored for
 * programmatically registered post-processors. Furthermore, the
 * {@link org.springframework.core.annotation.Order @Order} annotation is not
 * taken into account for {@code BeanPostProcessor} beans.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 10.10.2003
 * @see InstantiationAwareBeanPostProcessor
 * @see DestructionAwareBeanPostProcessor
 * @see ConfigurableBeanFactory#addBeanPostProcessor
 * @see BeanFactoryPostProcessor
 */
public interface BeanPostProcessor {

	/**
	 * Apply this {@code BeanPostProcessor} to the given new bean instance <i>before</i> any bean
	 * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
	 * or a custom init-method). The bean will already be populated with property values.
	 * The returned bean instance may be a wrapper around the original.
	 * <p>The default implementation returns the given {@code bean} as-is.
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return the bean instance to use, either the original or a wrapped one;
	 * if {@code null}, no subsequent BeanPostProcessors will be invoked
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 */
	@Nullable
	default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * Apply this {@code BeanPostProcessor} to the given new bean instance <i>after</i> any bean
	 * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
	 * or a custom init-method). The bean will already be populated with property values.
	 * The returned bean instance may be a wrapper around the original.
	 * <p>In case of a FactoryBean, this callback will be invoked for both the FactoryBean
	 * instance and the objects created by the FactoryBean (as of Spring 2.0). The
	 * post-processor can decide whether to apply to either the FactoryBean or created
	 * objects or both through corresponding {@code bean instanceof FactoryBean} checks.
	 * <p>This callback will also be invoked after a short-circuiting triggered by a
	 * {@link InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation} method,
	 * in contrast to all other {@code BeanPostProcessor} callbacks.
	 * <p>The default implementation returns the given {@code bean} as-is.
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return the bean instance to use, either the original or a wrapped one;
	 * if {@code null}, no subsequent BeanPostProcessors will be invoked
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.FactoryBean
	 */
	@Nullable
	default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
