/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.transaction.interceptor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Properties;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>TransactionInterceptor —— @Transactional 的"最终执行者"，AOP 拦截链中的事务关卡！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.transaction.interceptor.TransactionInterceptor}</li>
 * <li><b>中文名</b>：事务拦截器 —— 实现 MethodInterceptor，在方法调用前后包裹事务逻辑</li>
 * <li><b>所属车间 🏭</b>：{@code spring-tx} 模块的 {@code interceptor} 包
 *     （interceptor 包 = <b>事务 AOP 拦截层</b>，负责把事务管理"织入"到方法调用中）</li>
 * <li><b>双重身份</b>：{@code MethodInterceptor}（AOP 拦截器）+ 继承 {@code TransactionAspectSupport}（事务逻辑基类）</li>
 * </ul>
 *
 * <h3>💡 从 @Transactional 注解到 TransactionInterceptor——完整链路</h3>
 * <pre>
 * @Transactional 注解
 *   → SpringTransactionAnnotationParser 解析 → RuleBasedTransactionAttribute
 *     → AnnotationTransactionAttributeSource 缓存
 *       → BeanFactoryTransactionAttributeSourceAdvisor（Advisor = Pointcut + Advice）
 *         → Pointcut: TransactionAttributeSourcePointcut（匹配带 @Transactional 的方法）
 *         → Advice: TransactionInterceptor ← 👈 就是它！
 *           → 被 AnnotationAwareAspectJAutoProxyCreator 识别
 *             → 加入 AOP 拦截链 → 代理对象创建完成
 * </pre>
 *
 * <h3>🧬 invoke() 执行流程</h3>
 * <pre>
 * invoke(MethodInvocation invocation)
 *   → 获取目标类 targetClass
 *   → 调用父类 invokeWithinTransaction(method, targetClass, invocation::proceed)
 *       ├── 1. 从 TransactionAttributeSource 获取事务属性
 *       ├── 2. 确定使用哪个 TransactionManager（qualifier / 默认 / BeanFactory 查找）
 *       ├── 3. getTransaction(txAttr) → 开启/加入事务
 *       ├── 4. 执行 invocation.proceed() → 调用目标方法（或链中下一个拦截器）
 *       ├── 5a. 正常返回 → commitTransactionAfterReturning()
 *       └── 5b. 抛异常 → completeTransactionAfterThrowing()
 *                           → rollbackOn(ex) 判断是否回滚
 *                             → 是 → rollback()
 *                             → 否 → commit()（checked 异常默认不回滚！）
 * </pre>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * TransactionAspectSupport          （基类：invokeWithinTransaction 核心逻辑 + TM 路由 + ThreadLocal 栈）
 *   └── TransactionInterceptor      ← 👈 你在这里！（薄薄的适配层：实现 MethodInterceptor.invoke()）
 * </pre>
 * <p>注意：TransactionInterceptor 本身非常薄——核心事务逻辑全在父类 TransactionAspectSupport 中。<br/>
 * 它存在的意义是把 Spring 事务逻辑<b>适配成 AOP Alliance 的 MethodInterceptor 接口</b>。</p>
 *
 * <hr/>
 * AOP Alliance MethodInterceptor for declarative transaction
 * management using the common Spring transaction infrastructure
 * ({@link org.springframework.transaction.PlatformTransactionManager}/
 * {@link org.springframework.transaction.ReactiveTransactionManager}).
 *
 * <p>Derives from the {@link TransactionAspectSupport} class which
 * contains the integration with Spring's underlying transaction API.
 * TransactionInterceptor simply calls the relevant superclass methods
 * such as {@link #invokeWithinTransaction} in the correct order.
 *
 * <p>TransactionInterceptors are thread-safe.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @see TransactionProxyFactoryBean
 * @see org.springframework.aop.framework.ProxyFactoryBean
 * @see org.springframework.aop.framework.ProxyFactory
 */
@SuppressWarnings("serial")
public class TransactionInterceptor extends TransactionAspectSupport implements MethodInterceptor, Serializable {

	/**
	 * Create a new TransactionInterceptor.
	 * <p>Transaction manager and transaction attributes still need to be set.
	 * @see #setTransactionManager
	 * @see #setTransactionAttributes(java.util.Properties)
	 * @see #setTransactionAttributeSource(TransactionAttributeSource)
	 */
	public TransactionInterceptor() {
	}

	/**
	 * Create a new TransactionInterceptor.
	 * @param ptm the default transaction manager to perform the actual transaction management
	 * @param tas the attribute source to be used to find transaction attributes
	 * @since 5.2.5
	 * @see #setTransactionManager
	 * @see #setTransactionAttributeSource
	 */
	public TransactionInterceptor(TransactionManager ptm, TransactionAttributeSource tas) {
		setTransactionManager(ptm);
		setTransactionAttributeSource(tas);
	}

	/**
	 * Create a new TransactionInterceptor.
	 * @param ptm the default transaction manager to perform the actual transaction management
	 * @param tas the attribute source to be used to find transaction attributes
	 * @see #setTransactionManager
	 * @see #setTransactionAttributeSource
	 * @deprecated as of 5.2.5, in favor of
	 * {@link #TransactionInterceptor(TransactionManager, TransactionAttributeSource)}
	 */
	@Deprecated
	public TransactionInterceptor(PlatformTransactionManager ptm, TransactionAttributeSource tas) {
		setTransactionManager(ptm);
		setTransactionAttributeSource(tas);
	}

	/**
	 * Create a new TransactionInterceptor.
	 * @param ptm the default transaction manager to perform the actual transaction management
	 * @param attributes the transaction attributes in properties format
	 * @see #setTransactionManager
	 * @see #setTransactionAttributes(java.util.Properties)
	 * @deprecated as of 5.2.5, in favor of {@link #setTransactionAttributes(Properties)}
	 */
	@Deprecated
	public TransactionInterceptor(PlatformTransactionManager ptm, Properties attributes) {
		setTransactionManager(ptm);
		setTransactionAttributes(attributes);
	}


	@Override
	@Nullable
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// Work out the target class: may be {@code null}.
		// The TransactionAttributeSource should be passed the target class
		// as well as the method, which may be from an interface.
		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);

		// Adapt to TransactionAspectSupport's invokeWithinTransaction...
		return invokeWithinTransaction(invocation.getMethod(), targetClass, new CoroutinesInvocationCallback() {
			@Override
			@Nullable
			public Object proceedWithInvocation() throws Throwable {
				return invocation.proceed();
			}
			@Override
			public Object getTarget() {
				return invocation.getThis();
			}
			@Override
			public Object[] getArguments() {
				return invocation.getArguments();
			}
		});
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void writeObject(ObjectOutputStream oos) throws IOException {
		// Rely on default serialization, although this class itself doesn't carry state anyway...
		oos.defaultWriteObject();

		// Deserialize superclass fields.
		oos.writeObject(getTransactionManagerBeanName());
		oos.writeObject(getTransactionManager());
		oos.writeObject(getTransactionAttributeSource());
		oos.writeObject(getBeanFactory());
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization, although this class itself doesn't carry state anyway...
		ois.defaultReadObject();

		// Serialize all relevant superclass fields.
		// Superclass can't implement Serializable because it also serves as base class
		// for AspectJ aspects (which are not allowed to implement Serializable)!
		setTransactionManagerBeanName((String) ois.readObject());
		setTransactionManager((PlatformTransactionManager) ois.readObject());
		setTransactionAttributeSource((TransactionAttributeSource) ois.readObject());
		setBeanFactory((BeanFactory) ois.readObject());
	}

}
