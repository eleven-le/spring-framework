/*
 * Copyright 2002-2014 the original author or authors.
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
 * <h2>"所有单例就绪后"的回调——比 InitializingBean 更晚，确保全局无遗漏！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.SmartInitializingSingleton}</li>
 * <li><b>中文名</b>：智能单例初始化回调 —— 全员到齐后的"点名确认"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：顶级接口，4.1 引入，与 InitializingBean 互补</li>
 * </ul>
 *
 * <h3>💡 SmartInitializingSingleton vs InitializingBean vs ContextRefreshedEvent</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比</th><th>InitializingBean</th><th>SmartInitializingSingleton（本接口）</th><th>ContextRefreshedEvent</th></tr>
 * <tr><td>触发时机</td><td>当前 Bean 初始化时</td><td>所有单例初始化完毕后</td><td>refresh() 最末尾</td></tr>
 * <tr><td>能否安全 getBeansOfType</td><td>不安全（可能触发早期初始化）</td><td>安全（全部就绪）</td><td>安全</td></tr>
 * <tr><td>依赖层级</td><td>spring-beans</td><td>spring-beans</td><td>spring-context</td></tr>
 * <tr><td>典型使用者</td><td>框架内部大量使用</td><td>EventListenerMethodProcessor</td><td>应用层常用</td></tr>
 * </table>
 *
 * <h3>🧬 在 refresh 中的执行位置</h3>
 * <pre>
 * refresh()
 * └── finishBeanFactoryInitialization(beanFactory)
 *     └── beanFactory.preInstantiateSingletons()
 *         ├── 第一轮：逐个 getBean() 创建所有非 lazy 单例
 *         └── 第二轮：遍历所有 SmartInitializingSingleton  ← 👈 你在这里！
 *              └── smartSingleton.afterSingletonsInstantiated()
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>SmartInitializingSingleton 解决了 InitializingBean 的痛点——单个 Bean 初始化时，
 * 其他 Bean 可能还没创建。本接口保证"全员到齐"后才回调，适合需要全局视角的初始化逻辑。
 * 典型案例：EventListenerMethodProcessor 在此时扫描所有 Bean 上的 @EventListener 方法并注册监听器。</p>
 *
 * <hr/>
 * Callback interface triggered at the end of the singleton pre-instantiation phase
 * during {@link BeanFactory} bootstrap. This interface can be implemented by
 * singleton beans in order to perform some initialization after the regular
 * singleton instantiation algorithm, avoiding side effects with accidental early
 * initialization (e.g. from {@link ListableBeanFactory#getBeansOfType} calls).
 * In that sense, it is an alternative to {@link InitializingBean} which gets
 * triggered right at the end of a bean's local construction phase.
 *
 * <p>This callback variant is somewhat similar to
 * {@link org.springframework.context.event.ContextRefreshedEvent} but doesn't
 * require an implementation of {@link org.springframework.context.ApplicationListener},
 * with no need to filter context references across a context hierarchy etc.
 * It also implies a more minimal dependency on just the {@code beans} package
 * and is being honored by standalone {@link ListableBeanFactory} implementations,
 * not just in an {@link org.springframework.context.ApplicationContext} environment.
 *
 * <p><b>NOTE:</b> If you intend to start/manage asynchronous tasks, preferably
 * implement {@link org.springframework.context.Lifecycle} instead which offers
 * a richer model for runtime management and allows for phased startup/shutdown.
 *
 * @author Juergen Hoeller
 * @since 4.1
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#preInstantiateSingletons()
 */
public interface SmartInitializingSingleton {

	/**
	 * Invoked right at the end of the singleton pre-instantiation phase,
	 * with a guarantee that all regular singleton beans have been created
	 * already. {@link ListableBeanFactory#getBeansOfType} calls within
	 * this method won't trigger accidental side effects during bootstrap.
	 * <p><b>NOTE:</b> This callback won't be triggered for singleton beans
	 * lazily initialized on demand after {@link BeanFactory} bootstrap,
	 * and not for any other bean scope either. Carefully use it for beans
	 * with the intended bootstrap semantics only.
	 */
	void afterSingletonsInstantiated();

}
