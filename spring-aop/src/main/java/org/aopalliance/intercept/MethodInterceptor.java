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

package org.aopalliance.intercept;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>MethodInterceptor —— AOP 拦截链中每个"关卡"的统一契约！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.aopalliance.intercept.MethodInterceptor}</li>
 * <li><b>中文名</b>：方法拦截器 —— 拦截链上的"执行单元"，所有 AOP 增强最终都被适配成它</li>
 * <li><b>所属车间 🏭</b>：{@code org.aopalliance.intercept} 包（注意！这是 <b>AOP Alliance</b> 规范包，
 *     不是 Spring 自己的包！Spring AOP 遵循此规范，保证和其他 AOP 框架互操作）</li>
 * <li><b>接口层级</b>：{@code Interceptor → MethodInterceptor}，{@code @FunctionalInterface}，只有一个方法 {@link #invoke}</li>
 * </ul>
 *
 * <h3>💡 为什么所有增强最终都要变成 MethodInterceptor？——"统一炮弹口径"</h3>
 * <p>Spring AOP 支持 5 种通知类型：@Before、@After、@AfterReturning、@AfterThrowing、@Around。<br/>
 * 但拦截链执行引擎不可能为每种类型写一套逻辑。所以 Spring 在构建拦截链时，把所有通知都<b>适配成 MethodInterceptor</b>：</p>
 * <ul>
 * <li>{@code @Before} → {@code MethodBeforeAdviceInterceptor}（先执行 before 逻辑，再 proceed）</li>
 * <li>{@code @AfterReturning} → {@code AfterReturningAdviceInterceptor}（proceed 后拿到返回值再执行）</li>
 * <li>{@code @AfterThrowing} → {@code ThrowsAdviceInterceptor}（proceed 异常时执行）</li>
 * <li>{@code @After} → {@code AspectJAfterAdvice}（finally 块中执行）</li>
 * <li>{@code @Around} → 本身就是 MethodInterceptor，无需适配</li>
 * </ul>
 * <p>这是<b>适配器模式</b>的经典应用：对外接受多态输入，对内统一为一种类型，简化执行引擎。</p>
 *
 * <h3>🧬 关键设计：proceed() 的"递归弹"机制</h3>
 * <p>{@code invocation.proceed()} 不是直接调用目标方法，而是<b>调用拦截链中的下一个拦截器</b>。<br/>
 * 只有当链上所有拦截器都 proceed 完毕，最后一个才会真正调用目标方法。<br/>
 * 这就是<b>责任链模式</b>——每个拦截器决定：我是放行（proceed），还是短路（直接 return/throw）？<br/>
 * <b>业务借鉴</b>：审批流程、风控规则链都可以用同样的模式：每个节点拿到控制权后决定放行还是拦截。</p>
 *
 * <h3>🧬 在 Spring AOP 中的位置</h3>
 * <pre>
 * Advice (AOP Alliance)
 * └── Interceptor (AOP Alliance)
 *       └── MethodInterceptor (AOP Alliance) ← 👈 你在这里！
 *             ├── TransactionInterceptor     （事务拦截器——@Transactional 的执行引擎）
 *             ├── MethodBeforeAdviceInterceptor（@Before 的适配器）
 *             ├── AfterReturningAdviceInterceptor（@AfterReturning 的适配器）
 *             ├── ThrowsAdviceInterceptor    （@AfterThrowing 的适配器）
 *             └── ExposeInvocationInterceptor（拦截链第 0 号——暴露 MethodInvocation 到 ThreadLocal）
 * </pre>
 *
 * <hr/>
 * Intercepts calls on an interface on its way to the target. These
 * are nested "on top" of the target.
 *
 * <p>The user should implement the {@link #invoke(MethodInvocation)}
 * method to modify the original behavior. E.g. the following class
 * implements a tracing interceptor (traces all the calls on the
 * intercepted method(s)):
 *
 * <pre class=code>
 * class TracingInterceptor implements MethodInterceptor {
 *   Object invoke(MethodInvocation i) throws Throwable {
 *     System.out.println("method "+i.getMethod()+" is called on "+
 *                        i.getThis()+" with args "+i.getArguments());
 *     Object ret=i.proceed();
 *     System.out.println("method "+i.getMethod()+" returns "+ret);
 *     return ret;
 *   }
 * }
 * </pre>
 *
 * @author Rod Johnson
 */
@FunctionalInterface
public interface MethodInterceptor extends Interceptor {

	/**
	 * Implement this method to perform extra treatments before and
	 * after the invocation. Polite implementations would certainly
	 * like to invoke {@link Joinpoint#proceed()}.
	 * @param invocation the method invocation joinpoint
	 * @return the result of the call to {@link Joinpoint#proceed()};
	 * might be intercepted by the interceptor
	 * @throws Throwable if the interceptors or the target object
	 * throws an exception
	 */
	@Nullable
	Object invoke(@Nonnull MethodInvocation invocation) throws Throwable;

}
