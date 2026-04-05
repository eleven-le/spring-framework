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

package org.springframework.aop.framework;

import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>AopProxy —— 代理对象的"产出接口"，JDK 动态代理和 CGLIB 的统一门面！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.aop.framework.AopProxy}</li>
 * <li><b>中文名</b>：AOP 代理创建策略接口 —— 隐藏 JDK/CGLIB 差异的"统一出口"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-aop} 模块的 {@code framework} 包
 *     （注意！framework 包 = AOP 代理创建/执行的<b>核心引擎层</b>，不是抽象契约层！
 *     这里放的是真正干活的类：代理工厂、代理创建、拦截链调用等）</li>
 * <li><b>接口层级</b>：只有 1 个方法 {@link #getProxy}（两个重载），极简的<b>策略接口</b></li>
 * </ul>
 *
 * <h3>💡 为什么需要 AopProxy？——"代理技术"是可替换的实现细节</h3>
 * <p>Spring AOP 支持两种代理技术：</p>
 * <ul>
 * <li><b>JDK 动态代理</b>：基于接口，生成 $Proxy0 类，只能代理接口方法</li>
 * <li><b>CGLIB 代理</b>：基于继承，生成 $$EnhancerBySpringCGLIB 子类，能代理具体类的非 final 方法</li>
 * </ul>
 * <p>AopProxy 把这两种技术统一为 {@code getProxy()} 一个方法——调用者不需要知道底层用的哪种技术。<br/>
 * 选择由 {@link DefaultAopProxyFactory} 在创建时自动决定：有接口用 JDK，没接口用 CGLIB。<br/>
 * 这是<b>策略模式</b>的教科书应用：算法可替换，调用者无感知。</p>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AopProxy                    ← 👈 你在这里！（策略接口：getProxy()）
 * ├── JdkDynamicAopProxy      （JDK 动态代理实现，同时实现 InvocationHandler）
 * └── CglibAopProxy           （CGLIB 代理实现，内部使用 Enhancer）
 *       └── ObjenesisCglibAopProxy（优化版：用 Objenesis 免构造器创建代理实例）
 *
 * 创建流程：
 * ProxyFactory.getProxy()
 *   → ProxyCreatorSupport.createAopProxy()  // 委托 AopProxyFactory
 *     → DefaultAopProxyFactory.createAopProxy(config)  // 策略选择
 *       → new JdkDynamicAopProxy(config)   或   new ObjenesisCglibAopProxy(config)
 *         → aopProxy.getProxy()  // 真正创建代理对象
 * </pre>
 *
 * <hr/>
 * Delegate interface for a configured AOP proxy, allowing for the creation
 * of actual proxy objects.
 *
 * <p>Out-of-the-box implementations are available for JDK dynamic proxies
 * and for CGLIB proxies, as applied by {@link DefaultAopProxyFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see DefaultAopProxyFactory
 */
public interface AopProxy {

	/**
	 * Create a new proxy object.
	 * <p>Uses the AopProxy's default class loader (if necessary for proxy creation):
	 * usually, the thread context class loader.
	 * @return the new proxy object (never {@code null})
	 * @see Thread#getContextClassLoader()
	 */
	Object getProxy();

	/**
	 * Create a new proxy object.
	 * <p>Uses the given class loader (if necessary for proxy creation).
	 * {@code null} will simply be passed down and thus lead to the low-level
	 * proxy facility's default, which is usually different from the default chosen
	 * by the AopProxy implementation's {@link #getProxy()} method.
	 * @param classLoader the class loader to create the proxy with
	 * (or {@code null} for the low-level proxy facility's default)
	 * @return the new proxy object (never {@code null})
	 */
	Object getProxy(@Nullable ClassLoader classLoader);

}
