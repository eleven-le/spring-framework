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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BPP 的"销毁级特种兵"——在 Bean 销毁前插入最后的清理钩子！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor}</li>
 * <li><b>中文名</b>：具备销毁感知能力的 Bean 后置处理器 —— Bean 生命终点的"善后员"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包（config 包 = 框架内部配置契约）</li>
 * <li><b>接口层级</b>：{@code BeanPostProcessor} 的子接口，新增 <b>1 个方法</b> + 1 个默认方法</li>
 * </ul>
 *
 * <h3>🧬 方法速查</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>职责</th><th>典型使用者</th></tr>
 * <tr><td><b>postProcessBeforeDestruction</b></td><td>Bean 销毁前最后回调</td><td>CommonAnnotationBPP（执行 @PreDestroy）</td></tr>
 * <tr><td><b>requiresDestruction</b>（默认 true）</td><td>判断此 Bean 是否需要销毁回调</td><td>优化：跳过无需销毁的 Bean</td></tr>
 * </table>
 *
 * <h3>🧬 BPP 完整家族（4 个子接口覆盖 Bean 完整生命周期）</h3>
 * <pre>
 * BeanPostProcessor                              （初始化前后）
 * ├── InstantiationAwareBeanPostProcessor        （实例化前后 + 属性注入）
 * │     └── SmartInstantiationAwareBeanPostProcessor（构造器推断 + 早期引用）
 * ├── MergedBeanDefinitionPostProcessor          （BD 合并后预扫描）
 * └── DestructionAwareBeanPostProcessor          ← 👈 你在这里！（销毁前）
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>DestructionAwareBPP 把 BPP 的介入范围扩展到 Bean 生命周期终点——销毁阶段。
 * @PreDestroy 的执行就依赖 CommonAnnotationBPP 通过此接口介入。</p>
 *
 * <hr/>
 * Subinterface of {@link BeanPostProcessor} that adds a before-destruction callback.
 *
 * <p>The typical usage will be to invoke custom destruction callbacks on
 * specific bean types, matching corresponding initialization callbacks.
 *
 * @author Juergen Hoeller
 * @since 1.0.1
 */
public interface DestructionAwareBeanPostProcessor extends BeanPostProcessor {

	/**
	 * Apply this BeanPostProcessor to the given bean instance before its
	 * destruction, e.g. invoking custom destruction callbacks.
	 * <p>Like DisposableBean's {@code destroy} and a custom destroy method, this
	 * callback will only apply to beans which the container fully manages the
	 * lifecycle for. This is usually the case for singletons and scoped beans.
	 * @param bean the bean instance to be destroyed
	 * @param beanName the name of the bean
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#setDestroyMethodName(String)
	 */
	void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException;

	/**
	 * Determine whether the given bean instance requires destruction by this
	 * post-processor.
	 * <p>The default implementation returns {@code true}. If a pre-5 implementation
	 * of {@code DestructionAwareBeanPostProcessor} does not provide a concrete
	 * implementation of this method, Spring silently assumes {@code true} as well.
	 * @param bean the bean instance to check
	 * @return {@code true} if {@link #postProcessBeforeDestruction} is supposed to
	 * be called for this bean instance eventually, or {@code false} if not needed
	 * @since 4.3
	 */
	default boolean requiresDestruction(Object bean) {
		return true;
	}

}
