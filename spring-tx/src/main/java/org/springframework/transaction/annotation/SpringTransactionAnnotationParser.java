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

package org.springframework.transaction.annotation;

import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>SpringTransactionAnnotationParser —— 解析 Spring @Transactional 注解的"翻译官"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.transaction.annotation.SpringTransactionAnnotationParser}</li>
 * <li><b>中文名</b>：Spring 事务注解解析器 —— 把 @Transactional 注解的属性翻译成 RuleBasedTransactionAttribute</li>
 * <li><b>所属车间 🏭</b>：{@code spring-tx} 模块的 {@code annotation} 包
 *     （annotation 包 = 事务注解解析层）</li>
 * <li><b>身份</b>：{@code TransactionAnnotationParser} 的 Spring 实现，处理 {@code @Transactional} 注解</li>
 * </ul>
 *
 * <h3>💡 解析流程——@Transactional 属性 → RuleBasedTransactionAttribute</h3>
 * <pre>
 * parseTransactionAnnotation(AnnotatedElement)
 *   → AnnotatedElementUtils.findMergedAnnotationAttributes(element, Transactional.class)
 *     → 如果找到注解属性：
 *       → new RuleBasedTransactionAttribute()
 *       → 设置 propagation（传播行为）
 *       → 设置 isolation（隔离级别）
 *       → 设置 timeout / timeoutString
 *       → 设置 readOnly
 *       → 设置 qualifier（事务管理器限定符）→ 优先 value，其次 transactionManager
 *       → 设置 labels
 *       → 构建 rollbackRules 列表：
 *           → rollbackFor / rollbackForClassName → RollbackRuleAttribute
 *           → noRollbackFor / noRollbackForClassName → NoRollbackRuleAttribute
 *       → return RuleBasedTransactionAttribute
 * </pre>
 *
 * <h3>🧬 产物 RuleBasedTransactionAttribute 的 rollbackOn 匹配机制</h3>
 * <p>rollbackOn(ex) 会遍历所有 RollbackRuleAttribute，找到与异常类<b>继承距离最近</b>的规则：</p>
 * <ul>
 * <li>如果最近的规则是 RollbackRuleAttribute → 回滚</li>
 * <li>如果最近的规则是 NoRollbackRuleAttribute → 不回滚</li>
 * <li>如果没有匹配的规则 → 回退到默认：RuntimeException/Error 回滚，checked 异常不回滚</li>
 * </ul>
 *
 * <hr/>
 * Strategy implementation for parsing Spring's {@link Transactional} annotation.
 *
 * @author Juergen Hoeller
 * @author Mark Paluch
 * @since 2.5
 */
@SuppressWarnings("serial")
public class SpringTransactionAnnotationParser implements TransactionAnnotationParser, Serializable {

	@Override
	public boolean isCandidateClass(Class<?> targetClass) {
		return AnnotationUtils.isCandidateClass(targetClass, Transactional.class);
	}

	@Override
	@Nullable
	public TransactionAttribute parseTransactionAnnotation(AnnotatedElement element) {
		AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(
				element, Transactional.class, false, false);
		if (attributes != null) {
			return parseTransactionAnnotation(attributes);
		}
		else {
			return null;
		}
	}

	public TransactionAttribute parseTransactionAnnotation(Transactional ann) {
		return parseTransactionAnnotation(AnnotationUtils.getAnnotationAttributes(ann, false, false));
	}

	protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
		RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();

		Propagation propagation = attributes.getEnum("propagation");
		rbta.setPropagationBehavior(propagation.value());
		Isolation isolation = attributes.getEnum("isolation");
		rbta.setIsolationLevel(isolation.value());

		rbta.setTimeout(attributes.getNumber("timeout").intValue());
		String timeoutString = attributes.getString("timeoutString");
		Assert.isTrue(!StringUtils.hasText(timeoutString) || rbta.getTimeout() < 0,
				"Specify 'timeout' or 'timeoutString', not both");
		rbta.setTimeoutString(timeoutString);

		rbta.setReadOnly(attributes.getBoolean("readOnly"));
		rbta.setQualifier(attributes.getString("value"));
		rbta.setLabels(Arrays.asList(attributes.getStringArray("label")));

		List<RollbackRuleAttribute> rollbackRules = new ArrayList<>();
		for (Class<?> rbRule : attributes.getClassArray("rollbackFor")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		for (String rbRule : attributes.getStringArray("rollbackForClassName")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		for (Class<?> rbRule : attributes.getClassArray("noRollbackFor")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		for (String rbRule : attributes.getStringArray("noRollbackForClassName")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		rbta.setRollbackRules(rollbackRules);

		return rbta;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (other instanceof SpringTransactionAnnotationParser);
	}

	@Override
	public int hashCode() {
		return SpringTransactionAnnotationParser.class.hashCode();
	}

}
