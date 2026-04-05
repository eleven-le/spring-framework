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

package org.springframework.beans.factory;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Bean 初始化回调的"合同接口"——属性注入完毕后的第一个初始化钩子！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.InitializingBean}</li>
 * <li><b>中文名</b>：初始化回调接口 —— Bean 的"开机自检"协议</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：顶级接口，与 {@code DisposableBean} 成对（一个管"出生"，一个管"善终"）</li>
 * </ul>
 *
 * <h3>💡 三种初始化方式对比</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方式</th><th>触发机制</th><th>执行顺序</th><th>侵入性</th></tr>
 * <tr><td><b>@PostConstruct</b></td><td>CommonAnnotationBPP 扫描</td><td>① 最先</td><td>低（JSR-250 标准）</td></tr>
 * <tr><td><b>InitializingBean</b>（本接口）</td><td>initializeBean 中直接调用</td><td>② 其次</td><td>中（依赖 Spring API）</td></tr>
 * <tr><td><b>init-method / @Bean(initMethod)</b></td><td>反射调用自定义方法</td><td>③ 最后</td><td>低（纯 POJO）</td></tr>
 * </table>
 *
 * <h3>🧬 在 initializeBean 中的执行位置</h3>
 * <pre>
 * initializeBean(beanName, bean, mbd)
 * │
 * ├── 1. invokeAwareMethods()          ← BeanNameAware / BeanFactoryAware
 * ├── 2. applyBPPBeforeInitialization() ← @PostConstruct 在这里执行
 * ├── 3. invokeInitMethods()
 * │     ├── 3a. ((InitializingBean) bean).afterPropertiesSet()  ← 👈 你在这里！
 * │     └── 3b. invokeCustomInitMethod()  ← init-method
 * └── 4. applyBPPAfterInitialization()  ← AOP 代理在这里包装
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>InitializingBean 是 Spring 最早的初始化回调机制（1.0 就有），在属性注入完毕后执行。
 * 虽然 @PostConstruct 更推荐，但 InitializingBean 在框架内部被大量使用（如 AbstractAutowireCapableBeanFactory
 * 自身就实现了它），因为框架代码本身就依赖 Spring API，不存在"侵入性"问题。</p>
 *
 * <hr/>
 * Interface to be implemented by beans that need to react once all their properties
 * have been set by a {@link BeanFactory}: e.g. to perform custom initialization,
 * or merely to check that all mandatory properties have been set.
 *
 * <p>An alternative to implementing {@code InitializingBean} is specifying a custom
 * init method, for example in an XML bean definition. For a list of all bean
 * lifecycle methods, see the {@link BeanFactory BeanFactory javadocs}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see DisposableBean
 * @see org.springframework.beans.factory.config.BeanDefinition#getPropertyValues()
 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getInitMethodName()
 */
public interface InitializingBean {

	/**
	 * Invoked by the containing {@code BeanFactory} after it has set all bean properties
	 * and satisfied {@link BeanFactoryAware}, {@code ApplicationContextAware} etc.
	 * <p>This method allows the bean instance to perform validation of its overall
	 * configuration and final initialization when all bean properties have been set.
	 * @throws Exception in the event of misconfiguration (such as failure to set an
	 * essential property) or if initialization fails for any other reason
	 */
	void afterPropertiesSet() throws Exception;

}
