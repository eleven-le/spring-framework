/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.transaction;

import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>PlatformTransactionManager —— Spring 事务的"三板斧"顶层接口：getTransaction / commit / rollback！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.transaction.PlatformTransactionManager}</li>
 * <li><b>中文名</b>：平台事务管理器 —— Spring 声明式/编程式事务的<b>统一操作入口</b></li>
 * <li><b>所属车间 🏭</b>：{@code spring-tx} 模块的 {@code org.springframework.transaction} 根包
 *     （注意！transaction 根包 = <b>事务抽象的顶层契约层</b>，只放接口和异常定义！
 *     具体实现在 support 子包，AOP 拦截在 interceptor 子包，注解解析在 annotation 子包）</li>
 * <li><b>接口层级</b>：继承 {@code TransactionManager}（标记接口），只有 3 个方法</li>
 * </ul>
 *
 * <h3>💡 三个方法 = 事务生命周期的完整覆盖</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>职责</th><th>核心逻辑</th></tr>
 * <tr><td>{@link #getTransaction}</td><td>开启/加入事务</td><td>根据 TransactionDefinition 的传播行为决定：新建事务 / 加入已有事务 / 挂起已有事务等</td></tr>
 * <tr><td>{@link #commit}</td><td>提交事务</td><td>检查 rollbackOnly 标记，触发 beforeCommit/afterCommit 同步回调</td></tr>
 * <tr><td>{@link #rollback}</td><td>回滚事务</td><td>释放资源，触发 afterCompletion 同步回调</td></tr>
 * </table>
 *
 * <h3>🧬 设计精髓——策略模式的"SPI 入口"</h3>
 * <ol>
 * <li><b>一个接口屏蔽 N 种事务技术</b><br/>
 * JDBC、JPA、Hibernate、JTA、MongoDB 等各有各的事务 API。<br/>
 * PlatformTransactionManager 用<b>策略模式</b>把它们统一为 3 个方法。<br/>
 * @Transactional 注解不需要知道底层用的是什么数据库/ORM，只需要一个 TM 实例即可。<br/>
 * <b>业务借鉴</b>：支付渠道（微信/支付宝/银联）可以抽出统一的 PaymentManager 接口，上层业务无感切换。</li>
 *
 * <li><b>ApplicationContext 不直接依赖此接口</b><br/>
 * 事务管理器是一个<b>普通的 Bean</b>，由用户配置注入。Spring 不假设你一定需要事务——这是可选能力。<br/>
 * 这与 BeanFactory（容器必须有）形成对比，体现了"核心 vs 可选"的边界意识。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * TransactionManager（标记接口）
 * ├── PlatformTransactionManager     ← 👈 你在这里！（命令式事务：getTransaction/commit/rollback）
 * │     └── AbstractPlatformTransactionManager（模板骨架：传播行为 + 同步回调 + 挂起/恢复）
 * │           ├── DataSourceTransactionManager  （JDBC 事务——最常用！）
 * │           ├── JpaTransactionManager         （JPA/Hibernate 事务）
 * │           └── JtaTransactionManager         （JTA 分布式事务）
 * └── ReactiveTransactionManager     （响应式事务：WebFlux/R2DBC 场景）
 * </pre>
 *
 * <hr/>
 * This is the central interface in Spring's imperative transaction infrastructure.
 * Applications can use this directly, but it is not primarily meant as an API:
 * Typically, applications will work with either TransactionTemplate or
 * declarative transaction demarcation through AOP.
 *
 * <p>For implementors, it is recommended to derive from the provided
 * {@link org.springframework.transaction.support.AbstractPlatformTransactionManager}
 * class, which pre-implements the defined propagation behavior and takes care
 * of transaction synchronization handling. Subclasses have to implement
 * template methods for specific states of the underlying transaction,
 * for example: begin, suspend, resume, commit.
 *
 * <p>A classic implementation of this strategy interface is
 * {@link org.springframework.transaction.jta.JtaTransactionManager}. However,
 * in common single-resource scenarios, Spring's specific transaction managers
 * for e.g. JDBC, JPA, JMS are preferred choices.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16.05.2003
 * @see org.springframework.transaction.support.TransactionTemplate
 * @see org.springframework.transaction.interceptor.TransactionInterceptor
 * @see org.springframework.transaction.ReactiveTransactionManager
 */
public interface PlatformTransactionManager extends TransactionManager {

	/**
	 * Return a currently active transaction or create a new one, according to
	 * the specified propagation behavior.
	 * <p>Note that parameters like isolation level or timeout will only be applied
	 * to new transactions, and thus be ignored when participating in active ones.
	 * <p>Furthermore, not all transaction definition settings will be supported
	 * by every transaction manager: A proper transaction manager implementation
	 * should throw an exception when unsupported settings are encountered.
	 * <p>An exception to the above rule is the read-only flag, which should be
	 * ignored if no explicit read-only mode is supported. Essentially, the
	 * read-only flag is just a hint for potential optimization.
	 * @param definition the TransactionDefinition instance (can be {@code null} for defaults),
	 * describing propagation behavior, isolation level, timeout etc.
	 * @return transaction status object representing the new or current transaction
	 * @throws TransactionException in case of lookup, creation, or system errors
	 * @throws IllegalTransactionStateException if the given transaction definition
	 * cannot be executed (for example, if a currently active transaction is in
	 * conflict with the specified propagation behavior)
	 * @see TransactionDefinition#getPropagationBehavior
	 * @see TransactionDefinition#getIsolationLevel
	 * @see TransactionDefinition#getTimeout
	 * @see TransactionDefinition#isReadOnly
	 */
	TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException;

	/**
	 * Commit the given transaction, with regard to its status. If the transaction
	 * has been marked rollback-only programmatically, perform a rollback.
	 * <p>If the transaction wasn't a new one, omit the commit for proper
	 * participation in the surrounding transaction. If a previous transaction
	 * has been suspended to be able to create a new one, resume the previous
	 * transaction after committing the new one.
	 * <p>Note that when the commit call completes, no matter if normally or
	 * throwing an exception, the transaction must be fully completed and
	 * cleaned up. No rollback call should be expected in such a case.
	 * <p>Depending on the concrete transaction manager setup, {@code commit}
	 * may propagate {@link org.springframework.dao.DataAccessException} as well,
	 * either from before-commit flushes or from the actual commit step.
	 * @param status object returned by the {@code getTransaction} method
	 * @throws UnexpectedRollbackException in case of an unexpected rollback
	 * that the transaction coordinator initiated
	 * @throws HeuristicCompletionException in case of a transaction failure
	 * caused by a heuristic decision on the side of the transaction coordinator
	 * @throws TransactionSystemException in case of commit or system errors
	 * (typically caused by fundamental resource failures)
	 * @throws IllegalTransactionStateException if the given transaction
	 * is already completed (that is, committed or rolled back)
	 * @see TransactionStatus#setRollbackOnly
	 */
	void commit(TransactionStatus status) throws TransactionException;

	/**
	 * Perform a rollback of the given transaction.
	 * <p>If the transaction wasn't a new one, just set it rollback-only for proper
	 * participation in the surrounding transaction. If a previous transaction
	 * has been suspended to be able to create a new one, resume the previous
	 * transaction after rolling back the new one.
	 * <p><b>Do not call rollback on a transaction if commit threw an exception.</b>
	 * The transaction will already have been completed and cleaned up when commit
	 * returns, even in case of a commit exception. Consequently, a rollback call
	 * after commit failure will lead to an IllegalTransactionStateException.
	 * <p>Depending on the concrete transaction manager setup, {@code rollback}
	 * may propagate {@link org.springframework.dao.DataAccessException} as well.
	 * @param status object returned by the {@code getTransaction} method
	 * @throws TransactionSystemException in case of rollback or system errors
	 * (typically caused by fundamental resource failures)
	 * @throws IllegalTransactionStateException if the given transaction
	 * is already completed (that is, committed or rolled back)
	 */
	void rollback(TransactionStatus status) throws TransactionException;

}
