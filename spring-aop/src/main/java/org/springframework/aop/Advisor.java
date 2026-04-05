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

package org.springframework.aop;

import org.aopalliance.aop.Advice;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Advisor = Advice + Pointcut 的"组合包"——AOP 世界的"作战单元"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.aop.Advisor}</li>
 * <li><b>中文名</b>：通知器 / 顾问 —— 把"在哪切"和"切了做什么"打包成一个可投递的弹药包</li>
 * <li><b>所属车间 🏭</b>：{@code spring-aop} 模块的 {@code org.springframework.aop} 根包
 *     （注意！aop 根包 = AOP 最顶层抽象契约，只放接口和最基本的类型定义！）</li>
 * <li><b>接口层级</b>：AOP 体系的<b>顶层组合接口</b>，只有 2 个方法：{@link #getAdvice()} + {@link #isPerInstance()}</li>
 * </ul>
 *
 * <h3>💡 为什么需要 Advisor？——"通知"和"切点"为什么要绑在一起？</h3>
 * <p>AOP 里有两个独立的关注点：</p>
 * <ul>
 * <li><b>Advice（通知/增强）</b>：要做什么？——日志、事务、权限检查等具体逻辑</li>
 * <li><b>Pointcut（切点）</b>：在哪做？——匹配哪些类的哪些方法</li>
 * </ul>
 * <p>如果把它们分开管理，框架就需要自己去"配对"——谁跟谁搭档？顺序怎么排？<br/>
 * Advisor 的价值就是<b>把一个 Advice 和它的适用范围（Pointcut）绑定成一个不可分割的投递单元</b>。<br/>
 * 这就像军队里的"作战命令"：不是只说"开炮"（Advice），也不是只说"目标在哪"（Pointcut），
 * 而是把"向某目标开炮"打包成一条完整的作战指令。</p>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>组合模式（Composite）的"最小投递单元"</b><br/>
 * Spring 没有让使用者自己去组装 Advice + Pointcut，而是定义了 Advisor 作为<b>最小投递单元</b>。<br/>
 * 所有后续的自动代理、拦截链排序、缓存匹配，都以 Advisor 为粒度操作。<br/>
 * <b>业务借鉴</b>：设计规则引擎时，不要让调用方自己组装"条件"和"动作"，而是定义一个 Rule 对象
 * 把 Condition + Action 绑在一起，这样规则的增删改查、排序、去重都有统一的操作单元。</li>
 *
 * <li><b>子接口 PointcutAdvisor vs IntroductionAdvisor 的"职责分叉"</b><br/>
 * Advisor 本身不持有 Pointcut（只有 getAdvice），{@code PointcutAdvisor} 才加上 getPointcut。<br/>
 * 另一个分支 {@code IntroductionAdvisor} 用于"引介增强"——给目标类动态添加接口。<br/>
 * 这种<b>在父接口留最小公约数，在子接口分化差异</b>的设计，让 Advisor 体系能同时支持两种截然不同的 AOP 场景。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * Advisor                          ← 👈 你在这里！（顶层：Advice 的持有者）
 * ├── PointcutAdvisor              （组合 Pointcut，99% 的场景用这个）
 * │     ├── DefaultPointcutAdvisor （通用实现：手动组装 Pointcut + Advice）
 * │     ├── AspectJPointcutAdvisor （AspectJ 注解驱动）
 * │     └── BeanFactoryTransactionAttributeSourceAdvisor（事务专用！）
 * └── IntroductionAdvisor          （引介增强：给目标类动态加接口）
 *       └── DefaultIntroductionAdvisor
 * </pre>
 *
 * <hr/>
 * Base interface holding AOP <b>advice</b> (action to take at a joinpoint)
 * and a filter determining the applicability of the advice (such as
 * a pointcut). <i>This interface is not for use by Spring users, but to
 * allow for commonality in support for different types of advice.</i>
 *
 * <p>Spring AOP is based around <b>around advice</b> delivered via method
 * <b>interception</b>, compliant with the AOP Alliance interception API.
 * The Advisor interface allows support for different types of advice,
 * such as <b>before</b> and <b>after</b> advice, which need not be
 * implemented using interception.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public interface Advisor {

	/**
	 * Common placeholder for an empty {@code Advice} to be returned from
	 * {@link #getAdvice()} if no proper advice has been configured (yet).
	 * @since 5.0
	 */
	Advice EMPTY_ADVICE = new Advice() {};


	/**
	 * Return the advice part of this aspect. An advice may be an
	 * interceptor, a before advice, a throws advice, etc.
	 * @return the advice that should apply if the pointcut matches
	 * @see org.aopalliance.intercept.MethodInterceptor
	 * @see BeforeAdvice
	 * @see ThrowsAdvice
	 * @see AfterReturningAdvice
	 */
	Advice getAdvice();

	/**
	 * Return whether this advice is associated with a particular instance
	 * (for example, creating a mixin) or shared with all instances of
	 * the advised class obtained from the same Spring bean factory.
	 * <p><b>Note that this method is not currently used by the framework.</b>
	 * Typical Advisor implementations always return {@code true}.
	 * Use singleton/prototype bean definitions or appropriate programmatic
	 * proxy creation to ensure that Advisors have the correct lifecycle model.
	 * @return whether this advice is associated with a particular target instance
	 */
	boolean isPerInstance();

}
