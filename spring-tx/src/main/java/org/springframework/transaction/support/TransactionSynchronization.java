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

package org.springframework.transaction.support;

import java.io.Flushable;

import org.springframework.core.Ordered;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>TransactionSynchronization —— 事务同步回调接口，afterCommit/afterCompletion 的契约！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.transaction.support.TransactionSynchronization}</li>
 * <li><b>中文名</b>：事务同步 —— 在事务提交/回滚/完成等关键时刻触发回调的接口</li>
 * <li><b>所属车间 🏭</b>：{@code spring-tx} 模块的 {@code support} 包
 *     （support 包 = 事务抽象的默认实现层）</li>
 * <li><b>接口层级</b>：实现 {@code Ordered}（控制回调执行顺序）+ {@code Flushable}</li>
 * </ul>
 *
 * <h3>💡 6 个回调方法——事务生命周期的完整钩子</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>回调方法</th><th>触发时机</th><th>典型场景</th></tr>
 * <tr><td>{@code suspend()}</td><td>事务被挂起时（REQUIRES_NEW）</td><td>解绑资源</td></tr>
 * <tr><td>{@code resume()}</td><td>事务被恢复时</td><td>重新绑定资源</td></tr>
 * <tr><td>{@code flush()}</td><td>事务 flush 时</td><td>Hibernate Session flush</td></tr>
 * <tr><td>{@code beforeCommit(readOnly)}</td><td>提交前</td><td>数据校验、Session flush</td></tr>
 * <tr><td>{@code beforeCompletion()}</td><td>提交或回滚前</td><td>关闭资源（Hibernate Session）</td></tr>
 * <tr><td>{@code afterCommit()}</td><td><b>提交后</b></td><td>👑 最常用！发消息、删缓存、发事件——保证事务已提交再执行</td></tr>
 * <tr><td>{@code afterCompletion(status)}</td><td>完成后（提交/回滚/未知）</td><td>清理资源、释放连接</td></tr>
 * </table>
 *
 * <h3>🧬 实战中最高频的用法——afterCommit</h3>
 * <pre>
 * // 方式一：编程式注册
 * TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
 *     public void afterCommit() {
 *         mqTemplate.send("order.created", orderId);  // 事务提交后才发消息
 *     }
 * });
 *
 * // 方式二：注解式（更优雅）
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onOrderCreated(OrderCreatedEvent event) {
 *     mqTemplate.send("order.created", event.getOrderId());
 * }
 * // 底层也是注册 TransactionSynchronization！
 * </pre>
 *
 * <h3>🧬 3 种完成状态常量</h3>
 * <ul>
 * <li>{@code STATUS_COMMITTED = 0}：正常提交</li>
 * <li>{@code STATUS_ROLLED_BACK = 1}：正常回滚</li>
 * <li>{@code STATUS_UNKNOWN = 2}：未知（如混合提交/系统错误）</li>
 * </ul>
 *
 * <hr/>
 * Interface for transaction synchronization callbacks.
 * Supported by AbstractPlatformTransactionManager.
 *
 * <p>TransactionSynchronization implementations can implement the Ordered interface
 * to influence their execution order. A synchronization that does not implement the
 * Ordered interface is appended to the end of the synchronization chain.
 *
 * <p>System synchronizations performed by Spring itself use specific order values,
 * allowing for fine-grained interaction with their execution order (if necessary).
 *
 * <p>Implements the {@link Ordered} interface to enable the execution order of
 * synchronizations to be controlled declaratively, as of 5.3. The default
 * {@link #getOrder() order} is {@link Ordered#LOWEST_PRECEDENCE}, indicating
 * late execution; return a lower value for earlier execution.
 *
 * @author Juergen Hoeller
 * @since 02.06.2003
 * @see TransactionSynchronizationManager
 * @see AbstractPlatformTransactionManager
 * @see org.springframework.jdbc.datasource.DataSourceUtils#CONNECTION_SYNCHRONIZATION_ORDER
 */
public interface TransactionSynchronization extends Ordered, Flushable {

	/** Completion status in case of proper commit. */
	int STATUS_COMMITTED = 0;

	/** Completion status in case of proper rollback. */
	int STATUS_ROLLED_BACK = 1;

	/** Completion status in case of heuristic mixed completion or system errors. */
	int STATUS_UNKNOWN = 2;


	/**
	 * Return the execution order for this transaction synchronization.
	 * <p>Default is {@link Ordered#LOWEST_PRECEDENCE}.
	 */
	@Override
	default int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * Suspend this synchronization.
	 * Supposed to unbind resources from TransactionSynchronizationManager if managing any.
	 * @see TransactionSynchronizationManager#unbindResource
	 */
	default void suspend() {
	}

	/**
	 * Resume this synchronization.
	 * Supposed to rebind resources to TransactionSynchronizationManager if managing any.
	 * @see TransactionSynchronizationManager#bindResource
	 */
	default void resume() {
	}

	/**
	 * Flush the underlying session to the datastore, if applicable:
	 * for example, a Hibernate/JPA session.
	 * @see org.springframework.transaction.TransactionStatus#flush()
	 */
	@Override
	default void flush() {
	}

	/**
	 * Invoked before transaction commit (before "beforeCompletion").
	 * Can e.g. flush transactional O/R Mapping sessions to the database.
	 * <p>This callback does <i>not</i> mean that the transaction will actually be committed.
	 * A rollback decision can still occur after this method has been called. This callback
	 * is rather meant to perform work that's only relevant if a commit still has a chance
	 * to happen, such as flushing SQL statements to the database.
	 * <p>Note that exceptions will get propagated to the commit caller and cause a
	 * rollback of the transaction.
	 * @param readOnly whether the transaction is defined as read-only transaction
	 * @throws RuntimeException in case of errors; will be <b>propagated to the caller</b>
	 * (note: do not throw TransactionException subclasses here!)
	 * @see #beforeCompletion
	 */
	default void beforeCommit(boolean readOnly) {
	}

	/**
	 * Invoked before transaction commit/rollback.
	 * Can perform resource cleanup <i>before</i> transaction completion.
	 * <p>This method will be invoked after {@code beforeCommit}, even when
	 * {@code beforeCommit} threw an exception. This callback allows for
	 * closing resources before transaction completion, for any outcome.
	 * @throws RuntimeException in case of errors; will be <b>logged but not propagated</b>
	 * (note: do not throw TransactionException subclasses here!)
	 * @see #beforeCommit
	 * @see #afterCompletion
	 */
	default void beforeCompletion() {
	}

	/**
	 * Invoked after transaction commit. Can perform further operations right
	 * <i>after</i> the main transaction has <i>successfully</i> committed.
	 * <p>Can e.g. commit further operations that are supposed to follow on a successful
	 * commit of the main transaction, like confirmation messages or emails.
	 * <p><b>NOTE:</b> The transaction will have been committed already, but the
	 * transactional resources might still be active and accessible. As a consequence,
	 * any data access code triggered at this point will still "participate" in the
	 * original transaction, allowing to perform some cleanup (with no commit following
	 * anymore!), unless it explicitly declares that it needs to run in a separate
	 * transaction. Hence: <b>Use {@code PROPAGATION_REQUIRES_NEW} for any
	 * transactional operation that is called from here.</b>
	 * @throws RuntimeException in case of errors; will be <b>propagated to the caller</b>
	 * (note: do not throw TransactionException subclasses here!)
	 */
	default void afterCommit() {
	}

	/**
	 * Invoked after transaction commit/rollback.
	 * Can perform resource cleanup <i>after</i> transaction completion.
	 * <p><b>NOTE:</b> The transaction will have been committed or rolled back already,
	 * but the transactional resources might still be active and accessible. As a
	 * consequence, any data access code triggered at this point will still "participate"
	 * in the original transaction, allowing to perform some cleanup (with no commit
	 * following anymore!), unless it explicitly declares that it needs to run in a
	 * separate transaction. Hence: <b>Use {@code PROPAGATION_REQUIRES_NEW}
	 * for any transactional operation that is called from here.</b>
	 * @param status completion status according to the {@code STATUS_*} constants
	 * @throws RuntimeException in case of errors; will be <b>logged but not propagated</b>
	 * (note: do not throw TransactionException subclasses here!)
	 * @see #STATUS_COMMITTED
	 * @see #STATUS_ROLLED_BACK
	 * @see #STATUS_UNKNOWN
	 * @see #beforeCompletion
	 */
	default void afterCompletion(int status) {
	}

}
