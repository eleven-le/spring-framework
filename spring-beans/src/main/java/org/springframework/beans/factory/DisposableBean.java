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
 * <h2>Bean 销毁回调的"合同接口"——容器关闭时的"善终"协议！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.DisposableBean}</li>
 * <li><b>中文名</b>：可销毁 Bean 接口 —— Bean 的"临终遗嘱"协议</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：顶级接口，与 {@code InitializingBean} 成对（一个管"出生"，一个管"善终"）</li>
 * </ul>
 *
 * <h3>💡 三种销毁方式对比</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方式</th><th>触发机制</th><th>执行顺序</th><th>侵入性</th></tr>
 * <tr><td><b>@PreDestroy</b></td><td>CommonAnnotationBPP（DestructionAwareBPP）</td><td>① 最先</td><td>低（JSR-250 标准）</td></tr>
 * <tr><td><b>DisposableBean</b>（本接口）</td><td>DisposableBeanAdapter 直接调用</td><td>② 其次</td><td>中（依赖 Spring API）</td></tr>
 * <tr><td><b>destroy-method / @Bean(destroyMethod)</b></td><td>反射调用自定义方法</td><td>③ 最后</td><td>低（纯 POJO）</td></tr>
 * </table>
 *
 * <h3>🧬 在容器关闭链路中的位置</h3>
 * <pre>
 * context.close()
 * └── doClose()
 *     └── destroyBeans()
 *         └── destroySingletons()
 *             └── destroySingleton(beanName)
 *                 └── destroyBean(beanName, disposableBean)
 *                     └── DisposableBeanAdapter.destroy()
 *                         ├── 1. DestructionAwareBPP.postProcessBeforeDestruction()  ← @PreDestroy
 *                         ├── 2. ((DisposableBean) bean).destroy()  ← 👈 你在这里！
 *                         └── 3. invokeCustomDestroyMethod()  ← destroy-method
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>DisposableBean 与 InitializingBean 对称——一个管初始化，一个管销毁。
 * 适用于释放连接池、关闭线程池、清理缓存等资源回收场景。
 * 注意：只有 singleton 和自定义 scope 的 Bean 会被容器管理销毁，prototype Bean 不会！</p>
 *
 * <hr/>
 * Interface to be implemented by beans that want to release resources on destruction.
 * A {@link BeanFactory} will invoke the destroy method on individual destruction of a
 * scoped bean. An {@link org.springframework.context.ApplicationContext} is supposed
 * to dispose all of its singletons on shutdown, driven by the application lifecycle.
 *
 * <p>A Spring-managed bean may also implement Java's {@link AutoCloseable} interface
 * for the same purpose. An alternative to implementing an interface is specifying a
 * custom destroy method, for example in an XML bean definition. For a list of all
 * bean lifecycle methods, see the {@link BeanFactory BeanFactory javadocs}.
 *
 * @author Juergen Hoeller
 * @since 12.08.2003
 * @see InitializingBean
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getDestroyMethodName()
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#destroySingletons()
 * @see org.springframework.context.ConfigurableApplicationContext#close()
 */
public interface DisposableBean {

	/**
	 * Invoked by the containing {@code BeanFactory} on destruction of a bean.
	 * @throws Exception in case of shutdown errors. Exceptions will get logged
	 * but not rethrown to allow other beans to release their resources as well.
	 */
	void destroy() throws Exception;

}
