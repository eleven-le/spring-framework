/*
 * Copyright 2002-2016 the original author or authors.
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

import java.lang.reflect.Constructor;

import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BPP 家族的"顶级特工"——构造器推断 + 早期引用 + 类型预测，三大内部能力！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor}</li>
 * <li><b>中文名</b>：智能实例化感知 Bean 后置处理器 —— BPP 体系的"最高级别特种兵"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 config 包（config 包 = 框架内部配置契约）</li>
 * <li><b>接口层级</b>：{@code InstantiationAwareBeanPostProcessor} 的子接口，新增 <b>3 个方法</b></li>
 * </ul>
 *
 * <h3>🧬 3 个新增方法——纯框架内部使用</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>时机</th><th>典型使用者</th></tr>
 * <tr><td><b>predictBeanType</b></td><td>类型预测（按类型查找时）</td><td>AbstractAutoProxyCreator（预测代理后类型）</td></tr>
 * <tr><td><b>determineCandidateConstructors</b></td><td>实例化时推断构造器</td><td><b>AABPP</b>（选 @Autowired 构造器）</td></tr>
 * <tr><td><b>getEarlyBeanReference</b></td><td>三级缓存曝光时</td><td>AbstractAutoProxyCreator（返回 AOP 代理早期引用，解决循环依赖）</td></tr>
 * </table>
 *
 * <h3>🧬 BPP 完整继承链</h3>
 * <pre>
 * BeanPostProcessor                           （初始化前后）
 * └── InstantiationAwareBeanPostProcessor     （实例化前后 + 属性注入）
 *       └── SmartInstantiationAwareBeanPostProcessor  ← 👈 你在这里！（构造器推断 + 早期引用 + 类型预测）
 *             ├── AutowiredAnnotationBeanPostProcessor （构造器推断 + @Autowired 注入）
 *             └── AbstractAutoProxyCreator            （AOP 代理创建 + 早期引用）
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>SmartInstantiationAwareBPP 是 BPP 体系的"顶配"——构造器推断、循环依赖解决（三级缓存早期引用）、
 * 类型预测三大内部能力。纯框架内部使用，应用代码不应实现此接口。</p>
 *
 * <hr/>
 * Extension of the {@link InstantiationAwareBeanPostProcessor} interface,
 * adding a callback for predicting the eventual type of a processed bean.
 *
 * <p><b>NOTE:</b> This interface is a special purpose interface, mainly for
 * internal use within the framework. In general, application-provided
 * post-processors should simply implement the plain {@link BeanPostProcessor}
 * interface or derive from the {@link InstantiationAwareBeanPostProcessorAdapter}
 * class. New methods might be added to this interface even in point releases.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see InstantiationAwareBeanPostProcessorAdapter
 */
public interface SmartInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessor {

	/**
	 * Predict the type of the bean to be eventually returned from this
	 * processor's {@link #postProcessBeforeInstantiation} callback.
	 * <p>The default implementation returns {@code null}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the type of the bean, or {@code null} if not predictable
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	@Nullable
	default Class<?> predictBeanType(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	/**
	 * Determine the candidate constructors to use for the given bean.
	 * <p>The default implementation returns {@code null}.
	 * @param beanClass the raw class of the bean (never {@code null})
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	@Nullable
	default Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName)
			throws BeansException {

		return null;
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * <p>This callback gives post-processors a chance to expose a wrapper
	 * early - that is, before the target bean instance is fully initialized.
	 * The exposed object should be equivalent to the what
	 * {@link #postProcessBeforeInitialization} / {@link #postProcessAfterInitialization}
	 * would expose otherwise. Note that the object returned by this method will
	 * be used as bean reference unless the post-processor returns a different
	 * wrapper from said post-process callbacks. In other words: Those post-process
	 * callbacks may either eventually expose the same reference or alternatively
	 * return the raw bean instance from those subsequent callbacks (if the wrapper
	 * for the affected bean has been built for a call to this method already,
	 * it will be exposes as final bean reference by default).
	 * <p>The default implementation returns the given {@code bean} as-is.
	 * @param bean the raw bean instance
	 * @param beanName the name of the bean
	 * @return the object to expose as bean reference
	 * (typically with the passed-in bean instance as default)
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	default Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
