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

package org.springframework.core.type.classreading;

import org.springframework.asm.Opcodes;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>{@link MethodMetadata} 的 ASM 路径实现——不加载类就拿到方法信息！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.SimpleMethodMetadata}</li>
 * <li><b>中文名</b>：简单方法元数据 —— ASM 路径的方法信息包装器</li>
 * <li><b>命名法则</b>：Simple = ASM 实现（对应反射路径的 StandardMethodMetadata）</li>
 * <li><b>可见性</b>：包私有 + final</li>
 * </ul>
 *
 * <h3>💡 与 StandardMethodMetadata 的区别</h3>
 * <p>StandardMethodMetadata 持有 java.lang.reflect.Method 对象（需要类加载），<br/>
 * SimpleMethodMetadata 只持有 ASM 解析出的基本数据（String + int），<b>完全不触发类加载</b>。</p>
 *
 * <h3>🔑 创建时机</h3>
 * <p>由 SimpleMethodMetadataReadingVisitor.visitEnd() 创建——
 * 只有方法上有注解时才创建（没注解的方法不需要 MethodMetadata）。</p>
 * <hr>
 *
 * {@link MethodMetadata} created from a {@link SimpleMethodMetadataReadingVisitor}.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 */
final class SimpleMethodMetadata implements MethodMetadata {

	private final String methodName;

	/** ASM 访问标志位——用 Opcodes 位运算判断修饰符 */
	private final int access;

	private final String declaringClassName;

	private final String returnTypeName;

	/**
	 * Source 对象——实现了 equals/hashCode/toString，用于标识方法的唯一性。
	 * 由 SimpleMethodMetadataReadingVisitor.Source 提供，包含 className + methodName + descriptor。
	 */
	// The source implements equals(), hashCode(), and toString() for the underlying method.
	private final Object source;

	/** 方法上的合并注解集合 */
	private final MergedAnnotations annotations;


	SimpleMethodMetadata(String methodName, int access, String declaringClassName,
			String returnTypeName, Object source, MergedAnnotations annotations) {

		this.methodName = methodName;
		this.access = access;
		this.declaringClassName = declaringClassName;
		this.returnTypeName = returnTypeName;
		this.source = source;
		this.annotations = annotations;
	}


	@Override
	public String getMethodName() {
		return this.methodName;
	}

	@Override
	public String getDeclaringClassName() {
		return this.declaringClassName;
	}

	@Override
	public String getReturnTypeName() {
		return this.returnTypeName;
	}

	@Override
	public boolean isAbstract() {
		return (this.access & Opcodes.ACC_ABSTRACT) != 0;
	}

	@Override
	public boolean isStatic() {
		return (this.access & Opcodes.ACC_STATIC) != 0;
	}

	@Override
	public boolean isFinal() {
		return (this.access & Opcodes.ACC_FINAL) != 0;
	}

	@Override
	public boolean isOverridable() {
		return !isStatic() && !isFinal() && !isPrivate();
	}

	private boolean isPrivate() {
		return (this.access & Opcodes.ACC_PRIVATE) != 0;
	}

	@Override
	public MergedAnnotations getAnnotations() {
		return this.annotations;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return ((this == obj) || ((obj instanceof SimpleMethodMetadata) &&
				this.source.equals(((SimpleMethodMetadata) obj).source)));
	}

	@Override
	public int hashCode() {
		return this.source.hashCode();
	}

	@Override
	public String toString() {
		return this.source.toString();
	}

}
