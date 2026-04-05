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

import java.io.Flushable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>TransactionStatus —— 事务的"运行时状态句柄"：是否新事务、是否 rollbackOnly、保存点管理！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.transaction.TransactionStatus}</li>
 * <li><b>中文名</b>：事务状态 —— 持有当前事务的运行时信息，也是编程式控制回滚的入口</li>
 * <li><b>所属车间 🏭</b>：{@code spring-tx} 模块的 {@code transaction} 根包
 *     （transaction 根包 = 事务抽象的顶层契约层）</li>
 * <li><b>接口层级</b>：继承 {@code TransactionExecution}（isNewTransaction/isRollbackOnly/isCompleted）+ {@code SavepointManager} + {@code Flushable}</li>
 * </ul>
 *
 * <h3>💡 TransactionDefinition vs TransactionStatus——"配置"vs"状态"</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>维度</th><th>TransactionDefinition</th><th>TransactionStatus</th></tr>
 * <tr><td>角色</td><td>事务的"期望配置"（我想要什么）</td><td>事务的"实际状态"（现在是什么）</td></tr>
 * <tr><td>生命周期</td><td>可复用（同一个 Definition 多次使用）</td><td>一次性（每次 getTransaction 产生新的 Status）</td></tr>
 * <tr><td>可变性</td><td>只读（配置信息不变）</td><td>可写（setRollbackOnly/createSavepoint 改变状态）</td></tr>
 * </table>
 *
 * <h3>🧬 关键能力</h3>
 * <ul>
 * <li><b>isNewTransaction()</b>：当前是否是新建的物理事务。REQUIRED 加入已有事务时返回 false，REQUIRES_NEW 一定返回 true</li>
 * <li><b>setRollbackOnly()</b>：标记只能回滚。被 TransactionInterceptor 在异常时调用，或业务代码主动标记</li>
 * <li><b>hasSavepoint()</b>：是否基于保存点（NESTED 传播行为时为 true）</li>
 * <li><b>createSavepoint()/rollbackToSavepoint()</b>：保存点管理，NESTED 传播行为的核心机制</li>
 * </ul>
 *
 * <h3>🧬 默认实现</h3>
 * <pre>
 * TransactionStatus                    ← 👈 你在这里！
 * └── DefaultTransactionStatus         （AbstractPlatformTransactionManager 使用的默认实现）
 *       持有：transaction 对象（底层连接）+ newTransaction 标记 + suspendedResources（挂起的资源）
 * </pre>
 *
 * <hr/>
 * Representation of an ongoing {@link PlatformTransactionManager} transaction.
 * Extends the common {@link TransactionExecution} interface.
 *
 * <p>Transactional code can use this to retrieve status information,
 * and to programmatically request a rollback (instead of throwing
 * an exception that causes an implicit rollback).
 *
 * <p>Includes the {@link SavepointManager} interface to provide access
 * to savepoint management facilities. Note that savepoint management
 * is only available if supported by the underlying transaction manager.
 *
 * @author Juergen Hoeller
 * @since 27.03.2003
 * @see #setRollbackOnly()
 * @see PlatformTransactionManager#getTransaction
 * @see org.springframework.transaction.support.TransactionCallback#doInTransaction
 * @see org.springframework.transaction.interceptor.TransactionInterceptor#currentTransactionStatus()
 */
public interface TransactionStatus extends TransactionExecution, SavepointManager, Flushable {

	/**
	 * Return whether this transaction internally carries a savepoint,
	 * that is, has been created as nested transaction based on a savepoint.
	 * <p>This method is mainly here for diagnostic purposes, alongside
	 * {@link #isNewTransaction()}. For programmatic handling of custom
	 * savepoints, use the operations provided by {@link SavepointManager}.
	 * @see #isNewTransaction()
	 * @see #createSavepoint()
	 * @see #rollbackToSavepoint(Object)
	 * @see #releaseSavepoint(Object)
	 */
	boolean hasSavepoint();

	/**
	 * Flush the underlying session to the datastore, if applicable:
	 * for example, all affected Hibernate/JPA sessions.
	 * <p>This is effectively just a hint and may be a no-op if the underlying
	 * transaction manager does not have a flush concept. A flush signal may
	 * get applied to the primary resource or to transaction synchronizations,
	 * depending on the underlying resource.
	 */
	@Override
	void flush();

}
