/*
 * Copyright 2002-2015 the original author or authors.
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

import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>单例注册表——超级工厂的"VIP 成品仓库门卫协议"</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.SingletonBeanRegistry}</li>
 * <li><b>中文名</b>：单例 Bean 注册表 —— 管理全厂"独一份"成品的统一契约</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块</li>
 * <li><b>接口层级</b>：独立顶层接口（不继承 BeanFactory），被 {@code ConfigurableBeanFactory} 继承</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个独立接口？——"单例管理"与"Bean 定义"是两个正交的关切！</h3>
 * <p>Spring 架构师在设计时面对一个核心洞察：<b>并非所有单例都来自 BeanDefinition</b>。</p>
 * <ul>
 * <li><b>BeanDefinition 驱动的单例</b>：通过 XML/@Component 注册图纸，工厂按图纸生产，走完整生命周期</li>
 * <li><b>手动注册的单例</b>：外部直接塞进来的成品对象（如 {@code environment}、{@code systemProperties}），
 *     没有图纸，不走生命周期，但也需要被统一管理和查找</li>
 * </ul>
 * <p>如果把单例管理混在 BeanFactory 接口里，那"手动注册单例"这个能力就被迫和"按图纸生产"耦合在一起。
 * 拆出 SingletonBeanRegistry 后：</p>
 * <ol>
 * <li><b>职责单一</b>：这个接口只管"单例的存取"，不关心对象是怎么来的（手动塞的 vs 工厂造的）</li>
 * <li><b>独立复用</b>：{@code DefaultSingletonBeanRegistry} 实现了这个接口，可以独立当"单例容器"使用，
 *     不需要依赖整个 BeanFactory 体系——这正是原始 Javadoc 说的 "Can alternatively also be used as a nested helper to delegate to"</li>
 * <li><b>组合继承</b>：{@code ConfigurableBeanFactory extends SingletonBeanRegistry}，
 *     让完整的工厂自然获得单例管理能力，而不是在 BeanFactory 里硬塞方法</li>
 * </ol>
 *
 * <h3>🧬 业务借鉴——你的系统能偷师什么？</h3>
 * <ol>
 * <li><b>"注册表"模式的独立抽象</b><br/>
 * 在电商系统中，"商品信息"和"库存信息"是两个正交关切。即使没有商品详情页（类比 BeanDefinition），
 * 仓库也需要独立管理库存数量（类比 SingletonBeanRegistry）。把库存注册表抽成独立接口，
 * 商品服务和仓储服务都可以组合使用它，而不是把库存方法塞进商品接口里。</li>
 *
 * <li><b>"手动注册"与"自动生产"的双通道设计</b><br/>
 * 配置中心场景：有些配置是通过配置管理平台自动下发的（类比 BeanDefinition 驱动），
 * 有些是运维手动注入的紧急覆盖值（类比 registerSingleton）。两种来源的配置都需要统一查询，
 * 但注入方式完全不同。用独立的 "ConfigRegistry" 接口统一存取，对消费者透明。</li>
 *
 * <li><b>getSingletonMutex() 的"锁暴露"智慧</b><br/>
 * 这个方法把内部互斥锁暴露给外部协作者，让扩展代码能和核心代码共用同一把锁，避免死锁。
 * 业务借鉴：当你的系统有多个组件需要协调并发访问同一份共享状态时，
 * 与其让每个组件各用各的锁（死锁风险），不如提供一个统一的"协调锁"接口。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * SingletonBeanRegistry  ← 👈 你在这里！独立的单例管理契约
 * │
 * ├── ConfigurableBeanFactory (extends ... SingletonBeanRegistry)
 * │     └── ConfigurableListableBeanFactory
 * │
 * └── DefaultSingletonBeanRegistry (implements SingletonBeanRegistry)
 *       └── FactoryBeanRegistrySupport
 *             └── AbstractBeanFactory (implements ConfigurableBeanFactory)
 *                   └── AbstractAutowireCapableBeanFactory
 *                         └── DefaultListableBeanFactory
 * </pre>
 *
 * <h3>🗂️ 二、战区划分·只有 6 个方法，形成完美的 CRUD + 锁协调</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th><th>视角</th></tr>
 * <tr><td>{@code registerSingleton(name, obj)}</td><td>手动入库</td><td>写入——把外部成品塞进仓库</td></tr>
 * <tr><td>{@code getSingleton(name)}</td><td>按名提货</td><td>读取——只查已实例化的成品</td></tr>
 * <tr><td>{@code containsSingleton(name)}</td><td>查有没有</td><td>探测——成品仓库有没有这个名字</td></tr>
 * <tr><td>{@code getSingletonNames()}</td><td>列出花名册</td><td>枚举——已实例化的所有单例名</td></tr>
 * <tr><td>{@code getSingletonCount()}</td><td>统计数量</td><td>度量——已实例化的单例总数</td></tr>
 * <tr><td>{@code getSingletonMutex()}</td><td>暴露协调锁</td><td>并发——外部协作者与核心共用同一把锁</td></tr>
 * </table>
 *
 * <h3>🧠 三、关键设计约束</h3>
 * <ul>
 * <li><b>只管"已实例化"的成品</b>：所有方法都明确说"Only checks already instantiated singletons"，
 *     不会触发懒加载。这保证了查询操作是纯粹的内存读取，O(1) 级别，无副作用。</li>
 * <li><b>不感知别名和 FactoryBean 前缀</b>：getSingleton/containsSingleton 要求传入规范名（canonical name），
 *     调用者需要自行解析别名。这体现了"每层只做自己的事"的分层思想。</li>
 * <li><b>registerSingleton 是"绕过生命周期"的后门</b>：注册的对象不会收到任何回调（afterPropertiesSet/destroy），
 *     原始 Javadoc 明确警告："Register a bean definition instead if your bean needs lifecycle callbacks"。</li>
 * </ul>
 *
 * <hr/>
 * Interface that defines a registry for shared bean instances.
 * Can be implemented by {@link org.springframework.beans.factory.BeanFactory}
 * implementations in order to expose their singleton management facility
 * in a uniform manner.
 *
 * <p>The {@link ConfigurableBeanFactory} interface extends this interface.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see ConfigurableBeanFactory
 * @see org.springframework.beans.factory.support.DefaultSingletonBeanRegistry
 * @see org.springframework.beans.factory.support.AbstractBeanFactory
 */
public interface SingletonBeanRegistry {

	/**
	 * <h3>📥 方法 1：void registerSingleton(beanName, singletonObject)</h3>
	 * <p><b>🏭【手动入库——把外部成品直接塞进 VIP 仓库，绕过全部生产流水线！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 将一个<b>已经完全初始化好的外部对象</b>直接注册为单例，存入注册表。
	 * 关键约束：注册表<b>不会</b>对这个对象执行任何生命周期回调——不调 afterPropertiesSet，不调 destroy。
	 * 它就是一个纯粹的"成品寄存柜"，你塞什么进去，它就原样保管。</p>
	 * <p><b>【典型使用者】</b></p>
	 * <ul>
	 * <li>{@code prepareBeanFactory()} 中注册 {@code environment}、{@code systemProperties}、{@code systemEnvironment}</li>
	 * <li>外部框架集成时手动注册全局共享对象（如 ServletContext）</li>
	 * </ul>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 工厂的正常流程是：提交图纸 → 排队 → 生产 → 质检 → 入库。<br/>
	 * 但这个方法是"特权通道"：你直接把一台外面买来的成品搬进仓库，不经过任何流水线！<br/>
	 * 好处是快捷灵活，坏处是这台机器不享受工厂的售后服务（不会自动销毁/初始化）。</blockquote>
	 * <hr/>
	 * Register the given existing object as singleton in the bean registry,
	 * under the given bean name.
	 * <p>The given instance is supposed to be fully initialized; the registry
	 * will not perform any initialization callbacks (in particular, it won't
	 * call InitializingBean's {@code afterPropertiesSet} method).
	 * The given instance will not receive any destruction callbacks
	 * (like DisposableBean's {@code destroy} method) either.
	 * <p>When running within a full BeanFactory: <b>Register a bean definition
	 * instead of an existing instance if your bean is supposed to receive
	 * initialization and/or destruction callbacks.</b>
	 * <p>Typically invoked during registry configuration, but can also be used
	 * for runtime registration of singletons. As a consequence, a registry
	 * implementation should synchronize singleton access; it will have to do
	 * this anyway if it supports a BeanFactory's lazy initialization of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the existing singleton object
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.DisposableBean#destroy
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition
	 */
	void registerSingleton(String beanName, Object singletonObject);

	/**
	 * <h3>📤 方法 2：Object getSingleton(beanName)</h3>
	 * <p><b>🏭【按名提货——只查成品仓库，不触发懒加载！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回指定名称的<b>原始</b>单例对象（raw singleton）。注意三个"不"：<br/>
	 * ① <b>不触发懒加载</b>：如果 Bean 定义存在但尚未实例化，返回 null 而不是去创建它<br/>
	 * ② <b>不解析别名</b>：你必须传入规范名（canonical name），它不会帮你翻译别名<br/>
	 * ③ <b>不处理 FactoryBean 前缀</b>：传 "&myBean" 不会自动去掉 "&"</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你走到成品仓库门口，报一个名字。仓库管理员只翻已有的货架，有就拿给你，没有就说"没货"。<br/>
	 * 他绝不会因为你来问了一嘴，就跑去车间现造一个——那是 getBean() 的活！</blockquote>
	 * <hr/>
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Only checks already instantiated singletons; does not return an Object
	 * for singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to access manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to access a singleton
	 * defined by a bean definition that already been created, in a raw fashion.
	 * <p><b>NOTE:</b> This lookup method is not aware of FactoryBean prefixes or aliases.
	 * You need to resolve the canonical bean name first before obtaining the singleton instance.
	 * @param beanName the name of the bean to look for
	 * @return the registered singleton object, or {@code null} if none found
	 * @see ConfigurableListableBeanFactory#getBeanDefinition
	 */
	@Nullable
	Object getSingleton(String beanName);

	/**
	 * <h3>🔍 方法 3：boolean containsSingleton(beanName)</h3>
	 * <p><b>🏭【探测——成品仓库里有没有这台机器？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 纯粹的存在性检查，语义精准：<b>只检查已经实例化的单例</b>，不检查 BeanDefinition。<br/>
	 * 与 Spring 体系中其他"contains"方法的精确区别：</p>
	 * <table border="1" cellpadding="3" cellspacing="0">
	 * <tr><th>方法</th><th>检查范围</th><th>是否递归父容器</th></tr>
	 * <tr><td>{@code containsSingleton(name)}</td><td>只查已实例化的单例</td><td>否</td></tr>
	 * <tr><td>{@code containsBeanDefinition(name)}</td><td>只查图纸档案</td><td>否</td></tr>
	 * <tr><td>{@code containsBean(name)}</td><td>单例+图纸+父容器</td><td>是</td></tr>
	 * <tr><td>{@code containsLocalBean(name)}</td><td>本容器的单例+图纸</td><td>否</td></tr>
	 * </table>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "仓库里有没有这台机器的现货？"<br/>
	 * 只看成品货架，不翻图纸档案，也不打电话问总公司。有就是有，没有就是没有。</blockquote>
	 * <hr/>
	 * Check if this registry contains a singleton instance with the given name.
	 * <p>Only checks already instantiated singletons; does not return {@code true}
	 * for singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to check whether a
	 * singleton defined by a bean definition has already been created.
	 * <p>To check whether a bean factory contains a bean definition with a given name,
	 * use ListableBeanFactory's {@code containsBeanDefinition}. Calling both
	 * {@code containsBeanDefinition} and {@code containsSingleton} answers
	 * whether a specific bean factory contains a local bean instance with the given name.
	 * <p>Use BeanFactory's {@code containsBean} for general checks whether the
	 * factory knows about a bean with a given name (whether manually registered singleton
	 * instance or created by bean definition), also checking ancestor factories.
	 * <p><b>NOTE:</b> This lookup method is not aware of FactoryBean prefixes or aliases.
	 * You need to resolve the canonical bean name first before checking the singleton status.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a singleton instance with the given name
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 * @see org.springframework.beans.factory.BeanFactory#containsBean
	 */
	boolean containsSingleton(String beanName);

	/**
	 * <h3>📋 方法 4：String[] getSingletonNames()</h3>
	 * <p><b>🏭【列出花名册——已实例化的所有单例名字】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回所有已实例化的单例 Bean 名称数组。按注册顺序排列（底层用 LinkedHashSet 维护）。
	 * 同样只查成品，不会触发尚未实例化的 BeanDefinition。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "把仓库里所有已经造好的机器名字报一遍！"——花名册上有就报，图纸上画了但还没造的不算。</blockquote>
	 * <hr/>
	 * Return the names of singleton beans registered in this registry.
	 * <p>Only checks already instantiated singletons; does not return names
	 * for singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to check which singletons
	 * defined by a bean definition have already been created.
	 * @return the list of names as a String array (never {@code null})
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionNames
	 * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionNames
	 */
	String[] getSingletonNames();

	/**
	 * <h3>🔢 方法 5：int getSingletonCount()</h3>
	 * <p><b>🏭【统计数量——仓库里有多少台成品机器？】</b></p>
	 * <p>语义与 getSingletonNames 一致，只是返回数量而非名称数组。用于监控和诊断。</p>
	 * <hr/>
	 * Return the number of singleton beans registered in this registry.
	 * <p>Only checks already instantiated singletons; does not count
	 * singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to count the number of
	 * singletons defined by a bean definition that have already been created.
	 * @return the number of singleton beans
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionCount
	 * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionCount
	 */
	int getSingletonCount();

	/**
	 * <h3>🔒 方法 6：Object getSingletonMutex()</h3>
	 * <p><b>🏭【暴露协调锁——让外部协作者和核心共用同一把锁，避免死锁！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回本注册表内部用于同步单例访问的互斥对象。在 {@code DefaultSingletonBeanRegistry} 中，
	 * 这把锁就是 {@code singletonObjects}（一级缓存 ConcurrentHashMap 本身）。<br/>
	 * 为什么要暴露？因为扩展代码（如 BeanPostProcessor、自定义 Scope 实现）在做单例相关操作时，
	 * 如果各用各的锁，极易和核心代码产生死锁。共用同一把锁，死锁风险归零。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 仓库只有一把大门钥匙。核心流水线和外包质检员都必须用同一把钥匙开门，
	 * 否则核心拿着 A 锁等 B，外包拿着 B 锁等 A，两边就永远卡住了。</blockquote>
	 * <hr/>
	 * Return the singleton mutex used by this registry (for external collaborators).
	 * @return the mutex object (never {@code null})
	 * @since 4.2
	 */
	Object getSingletonMutex();

}
