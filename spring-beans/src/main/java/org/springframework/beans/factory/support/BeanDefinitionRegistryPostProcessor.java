/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.beans.factory.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BFPP 的"进化型"——不仅能改图纸，还能新增图纸的超级审核员！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor}</li>
 * <li><b>中文名</b>：Bean 定义注册表后置处理器（BDRPP）—— 图纸的"设计师 + 审核员"双重身份</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 support 包（注意！support 包 = 骨架实现区！
 * 这个接口之所以在 support 包而非 config 包，是因为它依赖了 support 包中的
 * {@link BeanDefinitionRegistry} 接口——它操作的是"注册表"这个实现层概念，
 * 而非 config 包中的纯配置契约。
 * 一句话：<b>BFPP 只改图纸（config 层），BDRPP 还能往注册表里塞新图纸（support 层）！</b>）</li>
 * <li><b>接口层级</b>：{@code BeanFactoryPostProcessor} 的子接口，新增 <b>1 个方法</b></li>
 * </ul>
 *
 * <h3>💡 BDRPP vs BFPP——"设计师"和"审核员"的区别</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比维度</th><th>BeanFactoryPostProcessor（BFPP）</th><th>BeanDefinitionRegistryPostProcessor（BDRPP）</th></tr>
 * <tr><td>能力</td><td>只能<b>修改</b>已有 BD 的属性</td><td>可以<b>新增</b>BD 到注册表！</td></tr>
 * <tr><td>方法</td><td>postProcessBeanFactory()</td><td>postProcessBeanDefinitionRegistry() + 继承的 postProcessBeanFactory()</td></tr>
 * <tr><td>执行顺序</td><td>后执行</td><td><b>先执行</b>（图纸都没画完，你审核个什么？）</td></tr>
 * <tr><td>典型实现</td><td>PropertySourcesPlaceholderConfigurer</td><td><b>ConfigurationClassPostProcessor</b>（一号大将！）</td></tr>
 * </table>
 *
 * <h3>🧬 执行时序（在 PostProcessorRegistrationDelegate 中严格编排）</h3>
 * <pre>
 * invokeBeanFactoryPostProcessors() {
 *   // ===== 第一大轮：先执行所有 BDRPP =====
 *   ① PriorityOrdered 的 BDRPP  → ConfigurationClassPostProcessor 在此执行！（图纸大爆发）
 *   ② Ordered 的 BDRPP           → （如果有的话）
 *   ③ 普通 BDRPP                  → （循环：每批执行后重新查询，因为上一批可能注册了新 BDRPP）
 *
 *   // ===== 第二大轮：再执行所有 BFPP =====
 *   ④ PriorityOrdered 的 BFPP
 *   ⑤ Ordered 的 BFPP
 *   ⑥ 普通 BFPP                   → PropertySourcesPlaceholderConfigurer 在此替换 ${...}
 * }
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"注册新图纸"的能力是 Spring 可插拔架构的基石</b><br/>
 * 没有 BDRPP，Spring 就无法在运行时动态发现和注册 Bean。
 * @ComponentScan 扫描、@Import 导入、@Bean 方法——这些 BD 全靠 BDRPP（ConfigurationClassPostProcessor）新增。<br/>
 * <b>业务借鉴</b>：你的插件系统如果只能"修改已有配置"而不能"新增配置"，那扩展性是残缺的。
 * 设计一个"RegistryPostProcessor"让插件能往注册表里塞新条目。</li>
 *
 * <li><b>"BDRPP 先于 BFPP"的铁律——新增 → 修改的顺序不可逆</b><br/>
 * 图纸都没画完（BDRPP 没执行完），审核修改（BFPP）就没意义。
 * Spring 通过严格的分批排序保证了这个时序。</li>
 * </ol>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>BDRPP 的核心价值：<b>在 BFPP 的"修改图纸"能力之上，增加了"新增图纸"的能力，
 * 使 Spring 能在启动时动态发现和注册 Bean</b>。ConfigurationClassPostProcessor 作为唯一的
 * PriorityOrdered BDRPP，是整个注解驱动体系的起爆点。</p>
 *
 * <hr/>
 * Extension to the standard {@link BeanFactoryPostProcessor} SPI, allowing for
 * the registration of further bean definitions <i>before</i> regular
 * BeanFactoryPostProcessor detection kicks in. In particular,
 * BeanDefinitionRegistryPostProcessor may register further bean definitions
 * which in turn define BeanFactoryPostProcessor instances.
 *
 * @author Juergen Hoeller
 * @since 3.0.1
 * @see org.springframework.context.annotation.ConfigurationClassPostProcessor
 */
public interface BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean definition registry after its
	 * standard initialization. All regular bean definitions will have been loaded,
	 * but no beans will have been instantiated yet. This allows for adding further
	 * bean definitions before the next post-processing phase kicks in.
	 * @param registry the bean definition registry used by the application context
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;

}
