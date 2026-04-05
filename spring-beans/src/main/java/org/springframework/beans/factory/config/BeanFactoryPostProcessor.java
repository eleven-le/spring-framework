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

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Bean 图纸的"审核员"——在 Bean 实例化之前修改 BeanDefinition 的核心扩展接口！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.BeanFactoryPostProcessor}</li>
 * <li><b>中文名</b>：Bean 工厂后置处理器 —— 图纸的"审核修改员"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包（注意！config 包 = 框架内部配置契约！
 * BFPP 和 BPP 是 Spring 扩展性的两大支柱，都定义在这个包里。
 * 一句话：<b>BPP 管"Bean 实例"，BFPP 管"Bean 图纸（BeanDefinition）"——层次完全不同！</b>）</li>
 * <li><b>接口层级</b>：顶层独立函数式接口（@FunctionalInterface），只定义 <b>1 个方法</b></li>
 * </ul>
 *
 * <h3>💡 为什么需要 BeanFactoryPostProcessor？——"图纸画好后、施工之前"需要一个审核窗口！</h3>
 * <p>Spring 容器启动的关键时间线：</p>
 * <pre>
 * refresh()
 *   ├── obtainFreshBeanFactory()              ← 加载所有 BeanDefinition（图纸入库完毕）
 *   ├── invokeBeanFactoryPostProcessors()     ← 👈 BFPP 在这里执行！（图纸审核窗口）
 *   ├── registerBeanPostProcessors()          ← 注册 BPP（质检员就位）
 *   └── finishBeanFactoryInitialization()     ← 按图纸施工（实例化所有单例 Bean）
 * </pre>
 * <p>BFPP 的执行时机是<b>"图纸全部入库之后、施工之前"</b>——这是唯一可以修改图纸的窗口期。</p>
 *
 * <h3>🧬 BFPP vs BPP——两大扩展支柱的根本区别</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比维度</th><th>BeanFactoryPostProcessor（BFPP）</th><th>BeanPostProcessor（BPP）</th></tr>
 * <tr><td>操作对象</td><td><b>BeanDefinition（图纸）</b></td><td><b>Bean 实例（成品）</b></td></tr>
 * <tr><td>执行时机</td><td>所有 BD 加载完毕后、Bean 创建前</td><td>每个 Bean 初始化前后</td></tr>
 * <tr><td>执行次数</td><td>启动时执行一次</td><td>每创建一个 Bean 执行一次</td></tr>
 * <tr><td>典型用途</td><td>替换占位符、修改 BD 属性、新增 BD</td><td>@PostConstruct、AOP 代理、Aware 回调</td></tr>
 * <tr><td>⚠️ 铁律</td><td><b>绝不能触碰 Bean 实例！</b>否则导致过早实例化</td><td>可以操作 Bean 实例</td></tr>
 * </table>
 *
 * <h3>🧬 继承体系</h3>
 * <pre>
 * BeanFactoryPostProcessor（顶层）          ← 👈 你在这里！（修改已有图纸）
 * └── BeanDefinitionRegistryPostProcessor  （进阶：不仅能改图纸，还能新增图纸！）
 *       └── ConfigurationClassPostProcessor（"一号大将"：解析 @Configuration，新增大量 BD）
 *
 * ⚠️ BDRPP 先于 BFPP 执行（先新增图纸，再修改图纸）
 * </pre>
 *
 * <h3>🧬 Spring 内置的关键 BFPP 实现</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>实现类</th><th>功能</th></tr>
 * <tr><td>PropertySourcesPlaceholderConfigurer</td><td>替换 BD 中的 ${...} 占位符为真实值</td></tr>
 * <tr><td>ConfigurationClassPostProcessor（BDRPP）</td><td>解析 @Configuration，新增 @Bean/@Import 的 BD</td></tr>
 * <tr><td>EventListenerMethodProcessor</td><td>提前获取 EventListenerFactory 列表</td></tr>
 * </table>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"元数据可修改"的架构弹性</b><br/>
 * Spring 在 BD 和 Bean 之间插入了一个"审核窗口"，让 BFPP 可以在施工前最后一次修改图纸。<br/>
 * <b>业务借鉴</b>：订单从"草稿"到"确认"之间，插入一个"规则引擎审核"步骤，
 * 可以修改金额、调整优惠——这就是"元数据可修改"的思路。</li>
 *
 * <li><b>"绝不碰实例"的铁律——防止过早实例化</b><br/>
 * BFPP 的方法签名只暴露 ConfigurableListableBeanFactory，你可以读 BD、改 BD，
 * 但<b>绝对不能调 getBean() 获取实例</b>！因为此时 BPP 还没注册，
 * 过早创建的 Bean 不会经过质检（@Autowired 不生效、AOP 不织入）——这是致命 Bug！</li>
 * </ol>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>BeanFactoryPostProcessor 的核心价值：<b>在"图纸全部入库"和"开始施工"之间，
 * 提供一个修改 BeanDefinition 的扩展窗口</b>。<br/>
 * 它是 Spring 扩展性的第二支柱（第一支柱是 BPP）。占位符替换、配置类解析等核心特性都依赖此机制。
 * 记住铁律：BFPP 只改图纸，绝不碰实例！</p>
 *
 * <hr/>
 * Factory hook that allows for custom modification of an application context's
 * bean definitions, adapting the bean property values of the context's underlying
 * bean factory.
 *
 * <p>Useful for custom config files targeted at system administrators that
 * override bean properties configured in the application context. See
 * {@link PropertyResourceConfigurer} and its concrete implementations for
 * out-of-the-box solutions that address such configuration needs.
 *
 * <p>A {@code BeanFactoryPostProcessor} may interact with and modify bean
 * definitions, but never bean instances. Doing so may cause premature bean
 * instantiation, violating the container and causing unintended side effects.
 * If bean instance interaction is required, consider implementing
 * {@link BeanPostProcessor} instead.
 *
 * <h3>Registration</h3>
 * <p>An {@code ApplicationContext} auto-detects {@code BeanFactoryPostProcessor}
 * beans in its bean definitions and applies them before any other beans get created.
 * A {@code BeanFactoryPostProcessor} may also be registered programmatically
 * with a {@code ConfigurableApplicationContext}.
 *
 * <h3>Ordering</h3>
 * <p>{@code BeanFactoryPostProcessor} beans that are autodetected in an
 * {@code ApplicationContext} will be ordered according to
 * {@link org.springframework.core.PriorityOrdered} and
 * {@link org.springframework.core.Ordered} semantics. In contrast,
 * {@code BeanFactoryPostProcessor} beans that are registered programmatically
 * with a {@code ConfigurableApplicationContext} will be applied in the order of
 * registration; any ordering semantics expressed through implementing the
 * {@code PriorityOrdered} or {@code Ordered} interface will be ignored for
 * programmatically registered post-processors. Furthermore, the
 * {@link org.springframework.core.annotation.Order @Order} annotation is not
 * taken into account for {@code BeanFactoryPostProcessor} beans.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 06.07.2003
 * @see BeanPostProcessor
 * @see PropertyResourceConfigurer
 */
@FunctionalInterface
public interface BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for overriding or adding
	 * properties even to eager-initializing beans.
	 * @param beanFactory the bean factory used by the application context
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;

}
