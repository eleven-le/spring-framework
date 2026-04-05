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

package org.springframework.aop.framework.autoproxy;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>DefaultAdvisorAutoProxyCreator —— "扫描所有 Advisor Bean 自动代理"的默认实现！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator}</li>
 * <li><b>中文名</b>：默认 Advisor 自动代理创建器 —— 简单粗暴：找到容器里所有 Advisor，自动匹配、自动代理</li>
 * <li><b>所属车间 🏭</b>：{@code spring-aop} 模块的 {@code framework.autoproxy} 包
 *     （autoproxy 包 = 自动代理基础设施）</li>
 * <li><b>定位</b>：继承链中间层的"开箱即用版"，不用写任何自定义逻辑，注册到容器就能自动工作</li>
 * </ul>
 *
 * <h3>💡 与 AnnotationAwareAspectJAutoProxyCreator 的区别</h3>
 * <ul>
 * <li><b>DefaultAdvisorAutoProxyCreator</b>：只处理已经是 {@code Advisor} 类型的 Bean（你自己编程式注册的 Advisor）</li>
 * <li><b>AnnotationAwareAspectJAutoProxyCreator</b>：除了 Advisor Bean，还能<b>解析 @Aspect 注解类</b>生成 Advisor</li>
 * </ul>
 * <p>现代 Spring 项目几乎都用 @Aspect 注解，所以实际生产中 AnnotationAwareAspectJAutoProxyCreator 用得更多。<br/>
 * DefaultAdvisorAutoProxyCreator 更多出现在纯编程式 AOP 或老项目中。</p>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * AbstractAutoProxyCreator
 *   └── AbstractAdvisorAutoProxyCreator
 *         ├── DefaultAdvisorAutoProxyCreator    ← 👈 你在这里！（纯 Advisor Bean 扫描）
 *         └── AspectJAwareAdvisorAutoProxyCreator
 *               └── AnnotationAwareAspectJAutoProxyCreator（终极版：@Aspect 注解 + Advisor Bean）
 * </pre>
 *
 * <hr/>
 * {@code BeanPostProcessor} implementation that creates AOP proxies based on all
 * candidate {@code Advisor}s in the current {@code BeanFactory}. This class is
 * completely generic; it contains no special code to handle any particular aspects,
 * such as pooling aspects.
 *
 * <p>It's possible to filter out advisors - for example, to use multiple post processors
 * of this type in the same factory - by setting the {@code usePrefix} property to true,
 * in which case only advisors beginning with the DefaultAdvisorAutoProxyCreator's bean
 * name followed by a dot (like "aapc.") will be used. This default prefix can be changed
 * from the bean name by setting the {@code advisorBeanNamePrefix} property.
 * The separator (.) will also be used in this case.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator implements BeanNameAware {

	/** Separator between prefix and remainder of bean name. */
	public static final String SEPARATOR = ".";


	private boolean usePrefix = false;

	@Nullable
	private String advisorBeanNamePrefix;


	/**
	 * Set whether to only include advisors with a certain prefix in the bean name.
	 * <p>Default is {@code false}, including all beans of type {@code Advisor}.
	 * @see #setAdvisorBeanNamePrefix
	 */
	public void setUsePrefix(boolean usePrefix) {
		this.usePrefix = usePrefix;
	}

	/**
	 * Return whether to only include advisors with a certain prefix in the bean name.
	 */
	public boolean isUsePrefix() {
		return this.usePrefix;
	}

	/**
	 * Set the prefix for bean names that will cause them to be included for
	 * auto-proxying by this object. This prefix should be set to avoid circular
	 * references. Default value is the bean name of this object + a dot.
	 * @param advisorBeanNamePrefix the exclusion prefix
	 */
	public void setAdvisorBeanNamePrefix(@Nullable String advisorBeanNamePrefix) {
		this.advisorBeanNamePrefix = advisorBeanNamePrefix;
	}

	/**
	 * Return the prefix for bean names that will cause them to be included
	 * for auto-proxying by this object.
	 */
	@Nullable
	public String getAdvisorBeanNamePrefix() {
		return this.advisorBeanNamePrefix;
	}

	@Override
	public void setBeanName(String name) {
		// If no infrastructure bean name prefix has been set, override it.
		if (this.advisorBeanNamePrefix == null) {
			this.advisorBeanNamePrefix = name + SEPARATOR;
		}
	}


	/**
	 * Consider {@code Advisor} beans with the specified prefix as eligible, if activated.
	 * @see #setUsePrefix
	 * @see #setAdvisorBeanNamePrefix
	 */
	@Override
	protected boolean isEligibleAdvisorBean(String beanName) {
		if (!isUsePrefix()) {
			return true;
		}
		String prefix = getAdvisorBeanNamePrefix();
		return (prefix != null && beanName.startsWith(prefix));
	}

}
