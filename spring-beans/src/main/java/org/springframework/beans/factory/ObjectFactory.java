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

import org.springframework.beans.BeansException;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>最简对象工厂——一个 getObject() 搞定延迟获取！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.ObjectFactory}</li>
 * <li><b>中文名</b>：对象工厂 —— 延迟获取的"取货凭证"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：{@code @FunctionalInterface}，只有一个 {@code getObject()} 方法</li>
 * </ul>
 *
 * <h3>💡 ObjectFactory vs FactoryBean vs ObjectProvider</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比</th><th>ObjectFactory（本接口）</th><th>FactoryBean</th><th>ObjectProvider</th></tr>
 * <tr><td>角色</td><td>延迟获取的函数式接口</td><td>注册在容器中的工厂 Bean</td><td>ObjectFactory 的增强版</td></tr>
 * <tr><td>注册方式</td><td>作为 API 注入给其他 Bean</td><td>作为 SPI 注册到 BeanFactory</td><td>注入点自动适配</td></tr>
 * <tr><td>核心用途</td><td>三级缓存 / scope 代理</td><td>产出复杂对象（如 SqlSessionFactory）</td><td>安全注入（可选/非唯一）</td></tr>
 * </table>
 *
 * <h3>🧬 在三级缓存中的关键角色</h3>
 * <pre>
 * 三级缓存 singletonFactories（Map&lt;String, ObjectFactory&lt;?&gt;&gt;）
 * │
 * └── addSingletonFactory(beanName, () -&gt; getEarlyBeanReference(beanName, mbd, bean))
 *     │                                     ↑ ObjectFactory 的 lambda 实现
 *     └── 解决循环依赖时调用 getObject() → 触发 SmartInstantiationAwareBPP 的早期引用回调
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>ObjectFactory 是 Spring 中最简单的延迟获取抽象——"我先不要对象，给我一张取货凭证，
 * 需要时再调 getObject()"。它在三级缓存（解决循环依赖）和 scope 代理中扮演核心角色。
 * ObjectProvider 是它的增强版，增加了"可选获取"和"流式遍历"能力。</p>
 *
 * <hr/>
 * Defines a factory which can return an Object instance
 * (possibly shared or independent) when invoked.
 *
 * <p>This interface is typically used to encapsulate a generic factory which
 * returns a new instance (prototype) of some target object on each invocation.
 *
 * <p>This interface is similar to {@link FactoryBean}, but implementations
 * of the latter are normally meant to be defined as SPI instances in a
 * {@link BeanFactory}, while implementations of this class are normally meant
 * to be fed as an API to other beans (through injection). As such, the
 * {@code getObject()} method has different exception handling behavior.
 *
 * @author Colin Sampaleanu
 * @since 1.0.2
 * @param <T> the object type
 * @see FactoryBean
 */
@FunctionalInterface
public interface ObjectFactory<T> {

	/**
	 * Return an instance (possibly shared or independent)
	 * of the object managed by this factory.
	 * @return the resulting instance
	 * @throws BeansException in case of creation errors
	 */
	T getObject() throws BeansException;

}
