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

package org.springframework.transaction.annotation;

import java.lang.reflect.AnnotatedElement;

import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>TransactionAnnotationParser —— 事务注解解析的"策略接口"，支持多种注解风格！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.transaction.annotation.TransactionAnnotationParser}</li>
 * <li><b>中文名</b>：事务注解解析器 —— 把 @Transactional 注解解析为 TransactionAttribute 对象</li>
 * <li><b>所属车间 🏭</b>：{@code spring-tx} 模块的 {@code annotation} 包
 *     （注意！annotation 包 = <b>事务注解解析层</b>，负责把各种 @Transactional 注解
 *     （Spring 的 / JTA 的 / EJB 的）统一解析为 Spring 内部的 TransactionAttribute。
 *     与 interceptor 包的区别：interceptor 管"拦截执行"，annotation 管"注解解析"）</li>
 * <li><b>接口层级</b>：策略接口，只有 2 个方法：{@code isCandidateClass} + {@code parseTransactionAnnotation}</li>
 * </ul>
 *
 * <h3>💡 为什么需要策略接口？——多种 @Transactional 注解并存</h3>
 * <ul>
 * <li>{@code org.springframework.transaction.annotation.Transactional} —— Spring 自己的</li>
 * <li>{@code javax.transaction.Transactional} —— JTA 1.2 标准</li>
 * <li>{@code javax.ejb.TransactionAttribute} —— EJB3 标准</li>
 * </ul>
 * <p>{@code AnnotationTransactionAttributeSource} 持有多个 Parser 实例，按顺序尝试解析。
 * 这让 Spring 能同时识别多种事务注解，用户可以混用而不冲突。</p>
 *
 * <h3>🧬 三个实现</h3>
 * <pre>
 * TransactionAnnotationParser          ← 👈 你在这里！（策略接口）
 * ├── SpringTransactionAnnotationParser （解析 Spring @Transactional——最常用！）
 * ├── JtaTransactionAnnotationParser   （解析 JTA @Transactional）
 * └── Ejb3TransactionAnnotationParser  （解析 EJB @TransactionAttribute）
 * </pre>
 *
 * <hr/>
 * Strategy interface for parsing known transaction annotation types.
 * {@link AnnotationTransactionAttributeSource} delegates to such
 * parsers for supporting specific annotation types such as Spring's own
 * {@link Transactional}, JTA 1.2's {@link javax.transaction.Transactional}
 * or EJB3's {@link javax.ejb.TransactionAttribute}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see AnnotationTransactionAttributeSource
 * @see SpringTransactionAnnotationParser
 * @see Ejb3TransactionAnnotationParser
 * @see JtaTransactionAnnotationParser
 */
public interface TransactionAnnotationParser {

	/**
	 * Determine whether the given class is a candidate for transaction attributes
	 * in the annotation format of this {@code TransactionAnnotationParser}.
	 * <p>If this method returns {@code false}, the methods on the given class
	 * will not get traversed for {@code #parseTransactionAnnotation} introspection.
	 * Returning {@code false} is therefore an optimization for non-affected
	 * classes, whereas {@code true} simply means that the class needs to get
	 * fully introspected for each method on the given class individually.
	 * @param targetClass the class to introspect
	 * @return {@code false} if the class is known to have no transaction
	 * annotations at class or method level; {@code true} otherwise. The default
	 * implementation returns {@code true}, leading to regular introspection.
	 * @since 5.2
	 */
	default boolean isCandidateClass(Class<?> targetClass) {
		return true;
	}

	/**
	 * Parse the transaction attribute for the given method or class,
	 * based on an annotation type understood by this parser.
	 * <p>This essentially parses a known transaction annotation into Spring's metadata
	 * attribute class. Returns {@code null} if the method/class is not transactional.
	 * @param element the annotated method or class
	 * @return the configured transaction attribute, or {@code null} if none found
	 * @see AnnotationTransactionAttributeSource#determineTransactionAttribute
	 */
	@Nullable
	TransactionAttribute parseTransactionAnnotation(AnnotatedElement element);

}
