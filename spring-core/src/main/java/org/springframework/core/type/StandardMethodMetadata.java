/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.core.type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>{@link MethodMetadata} 的反射路径实现——持有 Method 对象，直接问反射 API</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.StandardMethodMetadata}</li>
 * <li><b>中文名</b>：标准方法元数据 —— 反射路径的方法信息包装器</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type} 包</li>
 * <li><b>命名法则</b>：Standard = 反射实现（对应 ASM 路径的 SimpleMethodMetadata）</li>
 * </ul>
 *
 * <h3>💡 创建时机</h3>
 * <p>由 {@link StandardAnnotationMetadata#getAnnotatedMethods} 创建——
 * 当反射路径发现一个被注解标注的方法时，用 StandardMethodMetadata 包装这个 Method 对象。</p>
 *
 * <h3>🧬 与 SimpleMethodMetadata 的对比</h3>
 * <ul>
 * <li>StandardMethodMetadata：持有 java.lang.reflect.Method，通过 Modifier 获取修饰符</li>
 * <li>SimpleMethodMetadata：持有 ASM 的 int access 标志位，通过 Opcodes 位运算获取修饰符</li>
 * <li>两者实现 MethodMetadata 同一套接口，调用者完全无感知差异</li>
 * </ul>
 * <hr>
 *
 * {@link MethodMetadata} implementation that uses standard reflection
 * to introspect a given {@code Method}.
 *
 * @author Juergen Hoeller
 * @author Mark Pollack
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 */
public class StandardMethodMetadata implements MethodMetadata {

	/** 被内省的 Method 对象——所有信息的来源 */
	private final Method introspectedMethod;

	/** 与 StandardAnnotationMetadata 保持一致的嵌套注解处理策略 */
	private final boolean nestedAnnotationsAsMap;

	/** 方法上的合并注解集合——SearchStrategy.DIRECT 只看方法自身的注解，不搜索父类 */
	private final MergedAnnotations mergedAnnotations;


	/**
	 * Create a new StandardMethodMetadata wrapper for the given Method.
	 * @param introspectedMethod the Method to introspect
	 * @deprecated since 5.2 in favor of obtaining instances via {@link AnnotationMetadata}
	 */
	@Deprecated
	public StandardMethodMetadata(Method introspectedMethod) {
		this(introspectedMethod, false);
	}

	/**
	 * Create a new StandardMethodMetadata wrapper for the given Method,
	 * providing the option to return any nested annotations or annotation arrays in the
	 * form of {@link org.springframework.core.annotation.AnnotationAttributes} instead
	 * of actual {@link java.lang.annotation.Annotation} instances.
	 * @param introspectedMethod the Method to introspect
	 * @param nestedAnnotationsAsMap return nested annotations and annotation arrays as
	 * {@link org.springframework.core.annotation.AnnotationAttributes} for compatibility
	 * with ASM-based {@link AnnotationMetadata} implementations
	 * @since 3.1.1
	 * @deprecated since 5.2 in favor of obtaining instances via {@link AnnotationMetadata}
	 */
	@Deprecated
	public StandardMethodMetadata(Method introspectedMethod, boolean nestedAnnotationsAsMap) {
		Assert.notNull(introspectedMethod, "Method must not be null");
		this.introspectedMethod = introspectedMethod;
		this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
		this.mergedAnnotations = MergedAnnotations.from(
				introspectedMethod, SearchStrategy.DIRECT, RepeatableContainers.none());
	}


	@Override
	public MergedAnnotations getAnnotations() {
		return this.mergedAnnotations;
	}

	/**
	 * Return the underlying Method.
	 */
	public final Method getIntrospectedMethod() {
		return this.introspectedMethod;
	}

	@Override
	public String getMethodName() {
		return this.introspectedMethod.getName();
	}

	@Override
	public String getDeclaringClassName() {
		return this.introspectedMethod.getDeclaringClass().getName();
	}

	@Override
	public String getReturnTypeName() {
		return this.introspectedMethod.getReturnType().getName();
	}

	@Override
	public boolean isAbstract() {
		return Modifier.isAbstract(this.introspectedMethod.getModifiers());
	}

	@Override
	public boolean isStatic() {
		return Modifier.isStatic(this.introspectedMethod.getModifiers());
	}

	@Override
	public boolean isFinal() {
		return Modifier.isFinal(this.introspectedMethod.getModifiers());
	}

	@Override
	public boolean isOverridable() {
		return !isStatic() && !isFinal() && !isPrivate();
	}

	private boolean isPrivate() {
		return Modifier.isPrivate(this.introspectedMethod.getModifiers());
	}

	@Override
	@Nullable
	public Map<String, Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		if (this.nestedAnnotationsAsMap) {
			return MethodMetadata.super.getAnnotationAttributes(annotationName, classValuesAsString);
		}
		return AnnotatedElementUtils.getMergedAnnotationAttributes(this.introspectedMethod,
				annotationName, classValuesAsString, false);
	}

	@Override
	@Nullable
	public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		if (this.nestedAnnotationsAsMap) {
			return MethodMetadata.super.getAllAnnotationAttributes(annotationName, classValuesAsString);
		}
		return AnnotatedElementUtils.getAllAnnotationAttributes(this.introspectedMethod,
				annotationName, classValuesAsString, false);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return ((this == obj) || ((obj instanceof StandardMethodMetadata) &&
				this.introspectedMethod.equals(((StandardMethodMetadata) obj).introspectedMethod)));
	}

	@Override
	public int hashCode() {
		return this.introspectedMethod.hashCode();
	}

	@Override
	public String toString() {
		return this.introspectedMethod.toString();
	}

}
