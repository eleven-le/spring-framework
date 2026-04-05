/*
 * Copyright 2002-2012 the original author or authors.
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

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Aware 家族的"工厂感知员"——让 Bean 拿到自己所属的 BeanFactory！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.BeanFactoryAware}</li>
 * <li><b>中文名</b>：BeanFactory 感知接口 —— Bean 获取工厂引用的"回调凭证"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：{@code Aware} 的子接口，属于"第一批 Aware"（BeanFactory 级直接调用）</li>
 * </ul>
 *
 * <h3>💡 执行时机——第一批 Aware，不经过 BPP</h3>
 * <pre>
 * initializeBean(beanName, bean, mbd)
 * │
 * ├── invokeAwareMethods(beanName, bean)     ← 第一批 Aware（硬编码直接调用）
 * │     ├── BeanNameAware.setBeanName()
 * │     ├── BeanClassLoaderAware.setBeanClassLoader()
 * │     └── BeanFactoryAware.setBeanFactory()  ← 👈 你在这里！
 * │
 * ├── applyBPPBeforeInitialization()         ← 第二批 Aware 在 ApplicationContextAwareProcessor 中
 * ├── invokeInitMethods()
 * └── applyBPPAfterInitialization()
 * </pre>
 *
 * <h3>💡 BeanFactoryAware vs ApplicationContextAware</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比</th><th>BeanFactoryAware</th><th>ApplicationContextAware</th></tr>
 * <tr><td>获取对象</td><td>BeanFactory（底层工厂）</td><td>ApplicationContext（上层容器）</td></tr>
 * <tr><td>执行方式</td><td>invokeAwareMethods 硬编码</td><td>ApplicationContextAwareProcessor（BPP）</td></tr>
 * <tr><td>依赖层级</td><td>spring-beans（轻量）</td><td>spring-context（更重）</td></tr>
 * <tr><td>适用场景</td><td>框架内部、SPI 扩展</td><td>应用层需要事件/资源/国际化</td></tr>
 * </table>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>BeanFactoryAware 让 Bean 在初始化阶段拿到工厂引用，可用于 Dependency Lookup（主动查找）。
 * 但大多数情况推荐用 DI（依赖注入）而非 DL（依赖查找）。框架内部使用场景：
 * CommonAnnotationBPP、AutowiredAnnotationBPP 等处理器都实现了它来获取工厂做 Bean 解析。</p>
 *
 * <hr/>
 * Interface to be implemented by beans that wish to be aware of their
 * owning {@link BeanFactory}.
 *
 * <p>For example, beans can look up collaborating beans via the factory
 * (Dependency Lookup). Note that most beans will choose to receive references
 * to collaborating beans via corresponding bean properties or constructor
 * arguments (Dependency Injection).
 *
 * <p>For a list of all bean lifecycle methods, see the
 * {@link BeanFactory BeanFactory javadocs}.
 *
 * @author Rod Johnson
 * @author Chris Beams
 * @since 11.03.2003
 * @see BeanNameAware
 * @see BeanClassLoaderAware
 * @see InitializingBean
 * @see org.springframework.context.ApplicationContextAware
 */
public interface BeanFactoryAware extends Aware {

	/**
	 * Callback that supplies the owning factory to a bean instance.
	 * <p>Invoked after the population of normal bean properties
	 * but before an initialization callback such as
	 * {@link InitializingBean#afterPropertiesSet()} or a custom init-method.
	 * @param beanFactory owning BeanFactory (never {@code null}).
	 * The bean can immediately call methods on the factory.
	 * @throws BeansException in case of initialization errors
	 * @see BeanInitializationException
	 */
	void setBeanFactory(BeanFactory beanFactory) throws BeansException;

}
