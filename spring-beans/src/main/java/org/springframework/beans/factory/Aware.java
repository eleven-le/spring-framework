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
 * <h2>Aware 家族的"族徽"——所有感知接口的标记超类！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.Aware}</li>
 * <li><b>中文名</b>：感知标记接口 —— Bean 向容器"索要资源"的统一族徽</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：标记接口（无任何方法），3.1 引入，统一已有的 *Aware 接口</li>
 * </ul>
 *
 * <h3>💡 Aware 家族分两批执行——BeanFactory 级 vs ApplicationContext 级</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>批次</th><th>执行者</th><th>包含的 Aware</th><th>时机</th></tr>
 * <tr><td><b>第一批</b></td><td>AbstractAutowireCapableBeanFactory<br/>.invokeAwareMethods()</td>
 *     <td>BeanNameAware / BeanClassLoaderAware / BeanFactoryAware</td>
 *     <td>initializeBean 最前面</td></tr>
 * <tr><td><b>第二批</b></td><td>ApplicationContextAwareProcessor<br/>（BPP）</td>
 *     <td>EnvironmentAware / EmbeddedValueResolverAware / ResourceLoaderAware /<br/>
 *     ApplicationEventPublisherAware / MessageSourceAware / ApplicationContextAware</td>
 *     <td>BPP.postProcessBeforeInitialization</td></tr>
 * </table>
 *
 * <h3>🧬 Aware 家族成员一览</h3>
 * <pre>
 * Aware（标记接口）  ← 👈 你在这里！
 * ├── BeanNameAware              → 获取 beanName
 * ├── BeanClassLoaderAware       → 获取 ClassLoader
 * ├── BeanFactoryAware           → 获取 BeanFactory
 * ├── EnvironmentAware           → 获取 Environment
 * ├── EmbeddedValueResolverAware → 获取 ${} 解析器
 * ├── ResourceLoaderAware        → 获取资源加载器
 * ├── ApplicationEventPublisherAware → 获取事件发布器
 * ├── MessageSourceAware         → 获取国际化消息源
 * └── ApplicationContextAware    → 获取 ApplicationContext（最强，但侵入性也最大）
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>Aware 本身只是标记接口——不包含任何方法。它的价值在于给所有 *Aware 子接口一个统一的"族徽"，
 * 方便框架通过 instanceof Aware 快速判断一个 Bean 是否需要感知回调。
 * 实际的回调逻辑分散在 invokeAwareMethods()（BeanFactory 级）和 ApplicationContextAwareProcessor（Context 级）两处。</p>
 *
 * <hr/>
 * A marker superinterface indicating that a bean is eligible to be notified by the
 * Spring container of a particular framework object through a callback-style method.
 * The actual method signature is determined by individual subinterfaces but should
 * typically consist of just one void-returning method that accepts a single argument.
 *
 * <p>Note that merely implementing {@link Aware} provides no default functionality.
 * Rather, processing must be done explicitly, for example in a
 * {@link org.springframework.beans.factory.config.BeanPostProcessor}.
 * Refer to {@link org.springframework.context.support.ApplicationContextAwareProcessor}
 * for an example of processing specific {@code *Aware} interface callbacks.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
public interface Aware {

}
