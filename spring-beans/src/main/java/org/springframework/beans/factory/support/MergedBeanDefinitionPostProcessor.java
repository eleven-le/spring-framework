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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BD 合并后的"元信息收集员"——在实例化后抢先扫描注解、缓存注入点！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor}</li>
 * <li><b>中文名</b>：合并 Bean 定义后置处理器 —— BD 合并后的"预扫描员"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 support 包（support 包 = 骨架实现区。
 * 本接口在 support 包而非 config 包，因为它操作的是 {@link RootBeanDefinition}——合并后的终态图纸，
 * 属于实现层概念）</li>
 * <li><b>接口层级</b>：{@code BeanPostProcessor} 的子接口，新增 <b>1 个方法</b> + 1 个默认方法</li>
 * </ul>
 *
 * <h3>🧬 执行时机（在 doCreateBean 中）</h3>
 * <pre>
 * doCreateBean() {
 *   createBeanInstance()                         ← 实例化完成
 *   ★ applyMergedBeanDefinitionPostProcessors()  ← 👈 本接口在此执行！
 *   │   ├── AABPP.postProcessMergedBeanDefinition()  → 缓存 @Autowired/@Value 注入点
 *   │   └── CABPP.postProcessMergedBeanDefinition()  → 缓存 @Resource/@PostConstruct/@PreDestroy
 *   addSingletonFactory()                        ← 三级缓存曝光
 *   populateBean()                               ← 使用上面缓存的注入点执行注入
 * }
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>MergedBeanDefinitionPostProcessor 在 BD 合并后、属性注入前提供"预扫描"窗口，
 * AABPP 和 CommonAnnotationBPP 都通过此接口实现"扫描一次，缓存复用"的高效注入模式。</p>
 *
 * <hr/>
 * Post-processor callback interface for <i>merged</i> bean definitions at runtime.
 * {@link BeanPostProcessor} implementations may implement this sub-interface in order
 * to post-process the merged bean definition (a processed copy of the original bean
 * definition) that the Spring {@code BeanFactory} uses to create a bean instance.
 *
 * <p>The {@link #postProcessMergedBeanDefinition} method may for example introspect
 * the bean definition in order to prepare some cached metadata before post-processing
 * actual instances of a bean. It is also allowed to modify the bean definition but
 * <i>only</i> for definition properties which are actually intended for concurrent
 * modification. Essentially, this only applies to operations defined on the
 * {@link RootBeanDefinition} itself but not to the properties of its base classes.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getMergedBeanDefinition
 */
public interface MergedBeanDefinitionPostProcessor extends BeanPostProcessor {

	/**
	 * Post-process the given merged bean definition for the specified bean.
	 * @param beanDefinition the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see AbstractAutowireCapableBeanFactory#applyMergedBeanDefinitionPostProcessors
	 */
	void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName);

	/**
	 * A notification that the bean definition for the specified name has been reset,
	 * and that this post-processor should clear any metadata for the affected bean.
	 * <p>The default implementation is empty.
	 * @param beanName the name of the bean
	 * @since 5.1
	 * @see DefaultListableBeanFactory#resetBeanDefinition
	 */
	default void resetBeanDefinition(String beanName) {
	}

}
