/*
 * Copyright 2002-2019 the original author or authors.
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

import java.util.Collection;

import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionDefinition;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>TransactionAttribute —— TransactionDefinition 的"AOP 增强版"，加上 rollbackOn + qualifier！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.transaction.interceptor.TransactionAttribute}</li>
 * <li><b>中文名</b>：事务属性 —— 在事务定义基础上增加"按异常类型决定是否回滚"和"事务管理器限定符"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-tx} 模块的 {@code interceptor} 包
 *     （注意！interceptor 包 = <b>事务 AOP 拦截层</b>，放的是 TransactionInterceptor + TransactionAspectSupport
 *     + TransactionAttribute 等与 AOP 拦截相关的类。之所以 TransactionAttribute 在这个包而不在根包，
 *     是因为 rollbackOn 只在 AOP 声明式事务中有意义——编程式事务自己 try-catch 控制回滚）</li>
 * <li><b>接口层级</b>：继承 {@code TransactionDefinition}，额外加 3 个方法</li>
 * </ul>
 *
 * <h3>💡 比 TransactionDefinition 多了什么？</h3>
 * <ul>
 * <li><b>{@link #rollbackOn(Throwable)}</b>：按异常类型判断是否回滚——@Transactional(rollbackFor=xxx) 的底层！<br/>
 *     默认规则：RuntimeException 和 Error 回滚，checked Exception 不回滚</li>
 * <li><b>{@link #getQualifier()}</b>：事务管理器的限定符——多数据源场景下指定用哪个 TM</li>
 * <li><b>{@link #getLabels()}</b>：标签，5.3 新增，纯描述性或供自定义 TM 使用</li>
 * </ul>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * TransactionDefinition
 * ├── DefaultTransactionDefinition（可设置属性的基础实现）
 * └── TransactionAttribute         ← 👈 你在这里！（扩展：rollbackOn + qualifier + labels）
 *       └── DefaultTransactionAttribute（加上 descriptor/rollbackOn 默认逻辑）
 *             └── RuleBasedTransactionAttribute（👑 @Transactional 注解解析的最终产物！用规则列表匹配回滚）
 * </pre>
 *
 * <hr/>
 * This interface adds a {@code rollbackOn} specification to {@link TransactionDefinition}.
 * As custom {@code rollbackOn} is only possible with AOP, it resides in the AOP-related
 * transaction subpackage.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Paluch
 * @since 16.03.2003
 * @see DefaultTransactionAttribute
 * @see RuleBasedTransactionAttribute
 */
public interface TransactionAttribute extends TransactionDefinition {

	/**
	 * Return a qualifier value associated with this transaction attribute.
	 * <p>This may be used for choosing a corresponding transaction manager
	 * to process this specific transaction.
	 * @since 3.0
	 */
	@Nullable
	String getQualifier();

	/**
	 * Return labels associated with this transaction attribute.
	 * <p>This may be used for applying specific transactional behavior
	 * or follow a purely descriptive nature.
	 * @since 5.3
	 */
	Collection<String> getLabels();

	/**
	 * Should we roll back on the given exception?
	 * @param ex the exception to evaluate
	 * @return whether to perform a rollback or not
	 */
	boolean rollbackOn(Throwable ex);

}
