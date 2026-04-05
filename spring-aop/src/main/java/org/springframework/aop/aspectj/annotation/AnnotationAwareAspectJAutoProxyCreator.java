/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>AnnotationAwareAspectJAutoProxyCreator —— Spring AOP 的"终极 BPP"，@Aspect 注解的处理引擎！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator}</li>
 * <li><b>中文名</b>：注解感知的 AspectJ 自动代理创建器 —— 你写 @Aspect/@Before/@Around 最终都是它在处理！</li>
 * <li><b>所属车间 🏭</b>：{@code spring-aop} 模块的 {@code aspectj.annotation} 包
 *     （注意！aspectj.annotation 包 = <b>AspectJ 注解风格与 Spring AOP 的桥接层</b>，
 *     负责把 @Aspect 类解析成 Spring 的 Advisor 体系。与 aspectj 根包的区别：
 *     根包放 AspectJ 表达式/切点相关，annotation 子包专门处理 @Aspect 注解解析）</li>
 * <li><b>注册时机</b>：{@code @EnableAspectJAutoProxy} 或 {@code <aop:aspectj-autoproxy/>} 会注册这个 BPP</li>
 * </ul>
 *
 * <h3>💡 这是 AOP 自动代理继承链的"终极形态"</h3>
 * <pre>
 * AbstractAutoProxyCreator                （模板骨架：wrapIfNecessary + createProxy）
 *   └── AbstractAdvisorAutoProxyCreator   （从 BeanFactory 中找所有 Advisor Bean + 匹配过滤）
 *         └── AspectJAwareAdvisorAutoProxyCreator（排序时考虑 AspectJ 优先级语义）
 *               └── AnnotationAwareAspectJAutoProxyCreator ← 👈 你在这里！（终极版：解析 @Aspect 注解类）
 * </pre>
 *
 * <h3>🧬 它比父类多做了什么？——@Aspect 类的解析</h3>
 * <ol>
 * <li><b>持有 {@code BeanFactoryAspectJAdvisorsBuilder}</b><br/>
 * 在 {@code findCandidateAdvisors()} 中，除了父类的"从 BeanFactory 中找 Advisor Bean"，
 * 还额外调用 {@code aspectJAdvisorsBuilder.buildAspectJAdvisors()} 扫描所有 @Aspect 类，
 * 把其中的 @Before/@After/@Around 等方法<b>解析成 Advisor 对象</b>，合并到候选列表中。</li>
 *
 * <li><b>持有 {@code AspectJAdvisorFactory}</b>（默认 {@code ReflectiveAspectJAdvisorFactory}）<br/>
 * 负责把 @Aspect 类中的每个通知方法 → {@code InstantiationModelAwarePointcutAdvisorImpl}（一个 PointcutAdvisor）。</li>
 *
 * <li><b>includePatterns 过滤</b><br/>
 * 支持通过 {@code <aop:include>} 或代码设置正则表达式，只处理名字匹配的 @Aspect Bean。</li>
 * </ol>
 *
 * <h3>🎯 核心调用链速览</h3>
 * <pre>
 * @EnableAspectJAutoProxy
 *   → 注册 AnnotationAwareAspectJAutoProxyCreator 到容器（internalAutoProxyCreator）
 *     → Bean 初始化后触发 postProcessAfterInitialization()
 *       → wrapIfNecessary()
 *         → findEligibleAdvisors()
 *           → findCandidateAdvisors()     // 从 BeanFactory 找 Advisor + 解析 @Aspect 类
 *           → findAdvisorsThatCanApply()   // Pointcut 匹配过滤
 *         → createProxy()                  // ProxyFactory 创建代理
 * </pre>
 *
 * <hr/>
 * {@link AspectJAwareAdvisorAutoProxyCreator} subclass that processes all AspectJ
 * annotation aspects in the current application context, as well as Spring Advisors.
 *
 * <p>Any AspectJ annotated classes will automatically be recognized, and their
 * advice applied if Spring AOP's proxy-based model is capable of applying it.
 * This covers method execution joinpoints.
 *
 * <p>If the &lt;aop:include&gt; element is used, only @AspectJ beans with names matched by
 * an include pattern will be considered as defining aspects to use for Spring auto-proxying.
 *
 * <p>Processing of Spring Advisors follows the rules established in
 * {@link org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see org.springframework.aop.aspectj.annotation.AspectJAdvisorFactory
 */
@SuppressWarnings("serial")
public class AnnotationAwareAspectJAutoProxyCreator extends AspectJAwareAdvisorAutoProxyCreator {

	@Nullable
	private List<Pattern> includePatterns;

	@Nullable
	private AspectJAdvisorFactory aspectJAdvisorFactory;

	@Nullable
	private BeanFactoryAspectJAdvisorsBuilder aspectJAdvisorsBuilder;


	/**
	 * Set a list of regex patterns, matching eligible @AspectJ bean names.
	 * <p>Default is to consider all @AspectJ beans as eligible.
	 */
	public void setIncludePatterns(List<String> patterns) {
		this.includePatterns = new ArrayList<>(patterns.size());
		for (String patternText : patterns) {
			this.includePatterns.add(Pattern.compile(patternText));
		}
	}

	public void setAspectJAdvisorFactory(AspectJAdvisorFactory aspectJAdvisorFactory) {
		Assert.notNull(aspectJAdvisorFactory, "AspectJAdvisorFactory must not be null");
		this.aspectJAdvisorFactory = aspectJAdvisorFactory;
	}

	@Override
	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		super.initBeanFactory(beanFactory);
		if (this.aspectJAdvisorFactory == null) {
			this.aspectJAdvisorFactory = new ReflectiveAspectJAdvisorFactory(beanFactory);
		}
		this.aspectJAdvisorsBuilder =
				new BeanFactoryAspectJAdvisorsBuilderAdapter(beanFactory, this.aspectJAdvisorFactory);
	}


	@Override
	protected List<Advisor> findCandidateAdvisors() {
		// Add all the Spring advisors found according to superclass rules.
		List<Advisor> advisors = super.findCandidateAdvisors();
		// Build Advisors for all AspectJ aspects in the bean factory.
		if (this.aspectJAdvisorsBuilder != null) {
			advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
		}
		return advisors;
	}

	@Override
	protected boolean isInfrastructureClass(Class<?> beanClass) {
		// Previously we setProxyTargetClass(true) in the constructor, but that has too
		// broad an impact. Instead we now override isInfrastructureClass to avoid proxying
		// aspects. I'm not entirely happy with that as there is no good reason not
		// to advise aspects, except that it causes advice invocation to go through a
		// proxy, and if the aspect implements e.g the Ordered interface it will be
		// proxied by that interface and fail at runtime as the advice method is not
		// defined on the interface. We could potentially relax the restriction about
		// not advising aspects in the future.
		return (super.isInfrastructureClass(beanClass) ||
				(this.aspectJAdvisorFactory != null && this.aspectJAdvisorFactory.isAspect(beanClass)));
	}

	/**
	 * Check whether the given aspect bean is eligible for auto-proxying.
	 * <p>If no &lt;aop:include&gt; elements were used then "includePatterns" will be
	 * {@code null} and all beans are included. If "includePatterns" is non-null,
	 * then one of the patterns must match.
	 */
	protected boolean isEligibleAspectBean(String beanName) {
		if (this.includePatterns == null) {
			return true;
		}
		else {
			for (Pattern pattern : this.includePatterns) {
				if (pattern.matcher(beanName).matches()) {
					return true;
				}
			}
			return false;
		}
	}


	/**
	 * Subclass of BeanFactoryAspectJAdvisorsBuilderAdapter that delegates to
	 * surrounding AnnotationAwareAspectJAutoProxyCreator facilities.
	 */
	private class BeanFactoryAspectJAdvisorsBuilderAdapter extends BeanFactoryAspectJAdvisorsBuilder {

		public BeanFactoryAspectJAdvisorsBuilderAdapter(
				ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {

			super(beanFactory, advisorFactory);
		}

		@Override
		protected boolean isEligibleBean(String beanName) {
			return AnnotationAwareAspectJAutoProxyCreator.this.isEligibleAspectBean(beanName);
		}
	}

}
