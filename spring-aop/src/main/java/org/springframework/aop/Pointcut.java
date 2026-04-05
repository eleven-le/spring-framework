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

package org.springframework.aop;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Pointcut = ClassFilter + MethodMatcher —— AOP 的"目标瞄准镜"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.aop.Pointcut}</li>
 * <li><b>中文名</b>：切点 —— 决定"哪些类的哪些方法"需要被增强</li>
 * <li><b>所属车间 🏭</b>：{@code spring-aop} 模块的 {@code org.springframework.aop} 根包
 *     （注意！aop 根包 = AOP 最顶层抽象契约！只放最核心的接口定义）</li>
 * <li><b>接口层级</b>：AOP 体系的<b>匹配引擎顶层抽象</b>，只有 2 个方法 + 1 个常量</li>
 * </ul>
 *
 * <h3>💡 为什么 Pointcut 要拆成 ClassFilter + MethodMatcher？——"两级过滤"的性能智慧</h3>
 * <p>AOP 代理创建时，需要判断某个 Bean 是否需要被代理。如果每次都精确到方法级别匹配，开销太大。<br/>
 * Spring 把匹配拆成两级：</p>
 * <ul>
 * <li><b>第一级 ClassFilter</b>：类级别粗筛——这个类需不需要看？不需要直接跳过，连方法都不用遍历</li>
 * <li><b>第二级 MethodMatcher</b>：方法级别精筛——这个类里的哪些方法需要被增强？</li>
 * </ul>
 * <p>这就像安检流程：先看你的证件（ClassFilter），证件不对直接拒绝，不用再开包检查（MethodMatcher）。<br/>
 * 这种<b>先粗后细的两级过滤</b>在 Bean 数量巨大时能显著减少不必要的方法遍历。</p>
 *
 * <h3>🧬 设计精髓</h3>
 * <ol>
 * <li><b>MethodMatcher 还分静态/动态两种</b><br/>
 * 静态匹配（{@code isRuntime()=false}）：只看方法签名，结果可缓存<br/>
 * 动态匹配（{@code isRuntime()=true}）：还要看运行时参数值，每次调用都要匹配<br/>
 * 99% 的切点都是静态匹配（如 execution 表达式），Spring 会缓存匹配结果。只有极少数场景（如参数绑定）需要动态匹配。</li>
 *
 * <li><b>Pointcut.TRUE —— "全量匹配"的哨兵对象</b><br/>
 * 当 Advisor 不关心切点（如 IntroductionAdvisor），用 {@code Pointcut.TRUE} 表示"匹配所有"，
 * 避免 null 检查，这是<b>Null Object 模式</b>的典型应用。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * Pointcut                               ← 👈 你在这里！（顶层抽象：ClassFilter + MethodMatcher）
 * ├── StaticMethodMatcherPointcut        （静态匹配基类，只需实现 matches(Method, Class)）
 * │     └── NameMatchMethodPointcut      （按方法名匹配）
 * ├── AspectJExpressionPointcut          （AspectJ 表达式切点——最常用！execution/within/annotation 等）
 * ├── AnnotationMatchingPointcut         （按注解匹配）
 * ├── ComposablePointcut                 （组合切点：union / intersection）
 * └── TruePointcut                       （Pointcut.TRUE 的实现——匹配一切）
 * </pre>
 *
 * <hr/>
 * Core Spring pointcut abstraction.
 *
 * <p>A pointcut is composed of a {@link ClassFilter} and a {@link MethodMatcher}.
 * Both these basic terms and a Pointcut itself can be combined to build up combinations
 * (e.g. through {@link org.springframework.aop.support.ComposablePointcut}).
 *
 * @author Rod Johnson
 * @see ClassFilter
 * @see MethodMatcher
 * @see org.springframework.aop.support.Pointcuts
 * @see org.springframework.aop.support.ClassFilters
 * @see org.springframework.aop.support.MethodMatchers
 */
public interface Pointcut {

	/**
	 * Return the ClassFilter for this pointcut.
	 * @return the ClassFilter (never {@code null})
	 */
	ClassFilter getClassFilter();

	/**
	 * Return the MethodMatcher for this pointcut.
	 * @return the MethodMatcher (never {@code null})
	 */
	MethodMatcher getMethodMatcher();


	/**
	 * Canonical Pointcut instance that always matches.
	 */
	Pointcut TRUE = TruePointcut.INSTANCE;

}
