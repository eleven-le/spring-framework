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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.asm.Opcodes;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>{@link AnnotationMetadata} 的 ASM 路径实现——不加载类就拿到完整元数据！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.SimpleAnnotationMetadata}</li>
 * <li><b>中文名</b>：简单注解元数据 —— ASM 路径的"全功能版"</li>
 * <li><b>命名法则</b>：Simple 前缀 = ASM 轻量实现（对应反射路径的 Standard 前缀）</li>
 * <li><b>可见性</b>：<b>包私有 + final</b>——只能由同包的 SimpleAnnotationMetadataReadingVisitor 创建</li>
 * </ul>
 *
 * <h3>💡 与 StandardAnnotationMetadata 的对比</h3>
 * <table border="1">
 * <tr><th></th><th>StandardAnnotationMetadata</th><th>SimpleAnnotationMetadata（你在这里）</th></tr>
 * <tr><td><b>数据来源</b></td><td>Class 对象 + 反射 API</td><td>ASM 字节码解析的结果（全是 String/int）</td></tr>
 * <tr><td><b>修饰符判断</b></td><td>Modifier.isAbstract(class.getModifiers())</td><td>(access & Opcodes.ACC_ABSTRACT) != 0</td></tr>
 * <tr><td><b>触发类加载？</b></td><td>是（需要 Class 对象）</td><td><b>否！</b>（这是 ASM 路径存在的根本意义）</td></tr>
 * <tr><td><b>创建时机</b></td><td>AnnotationMetadata.introspect(Class)</td><td>SimpleAnnotationMetadataReadingVisitor.visitEnd()</td></tr>
 * </table>
 *
 * <h3>🔑 设计特点：纯数据对象（Value Object）</h3>
 * <p>所有字段在构造时一次性赋值，之后只有 getter——完全不可变（除了 annotationTypes 缓存）。<br/>
 * 这保证了线程安全，也符合 MetadataReader 作为"缓存值"的需求。</p>
 * <hr>
 *
 * {@link AnnotationMetadata} created from a
 * {@link SimpleAnnotationMetadataReadingVisitor}.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 */
final class SimpleAnnotationMetadata implements AnnotationMetadata {

	/** 类全限定名——ASM 从 .class 文件的类描述符中解析出来，已转换为 . 分隔格式 */
	private final String className;

	/** ASM 访问标志位——用位运算判断 interface/abstract/final 等修饰符 */
	private final int access;

	/** 外围类名——内部类才有，顶层类为 null */
	@Nullable
	private final String enclosingClassName;

	/** 父类名——接口或 Object 为 null */
	@Nullable
	private final String superClassName;

	/** 是否是独立内部类（static inner class）*/
	private final boolean independentInnerClass;

	/** 实现的接口名数组 */
	private final String[] interfaceNames;

	/** 声明的成员类名数组 */
	private final String[] memberClassNames;

	/** 被注解标注的方法元数据数组——只包含有注解的方法（没注解的方法不收集，节省内存）*/
	private final MethodMetadata[] annotatedMethods;

	/** 类上的合并注解集合——由 MergedAnnotations.of(List) 从 ASM 收集的注解列表创建 */
	private final MergedAnnotations annotations;

	/** 注解类型名缓存——懒加载 */
	@Nullable
	private Set<String> annotationTypes;


	SimpleAnnotationMetadata(String className, int access, @Nullable String enclosingClassName,
			@Nullable String superClassName, boolean independentInnerClass, String[] interfaceNames,
			String[] memberClassNames, MethodMetadata[] annotatedMethods, MergedAnnotations annotations) {

		this.className = className;
		this.access = access;
		this.enclosingClassName = enclosingClassName;
		this.superClassName = superClassName;
		this.independentInnerClass = independentInnerClass;
		this.interfaceNames = interfaceNames;
		this.memberClassNames = memberClassNames;
		this.annotatedMethods = annotatedMethods;
		this.annotations = annotations;
	}

	@Override
	public String getClassName() {
		return this.className;
	}

	@Override
	public boolean isInterface() {
		return (this.access & Opcodes.ACC_INTERFACE) != 0;
	}

	@Override
	public boolean isAnnotation() {
		return (this.access & Opcodes.ACC_ANNOTATION) != 0;
	}

	@Override
	public boolean isAbstract() {
		return (this.access & Opcodes.ACC_ABSTRACT) != 0;
	}

	@Override
	public boolean isFinal() {
		return (this.access & Opcodes.ACC_FINAL) != 0;
	}

	@Override
	public boolean isIndependent() {
		return (this.enclosingClassName == null || this.independentInnerClass);
	}

	@Override
	@Nullable
	public String getEnclosingClassName() {
		return this.enclosingClassName;
	}

	@Override
	@Nullable
	public String getSuperClassName() {
		return this.superClassName;
	}

	@Override
	public String[] getInterfaceNames() {
		return this.interfaceNames.clone();
	}

	@Override
	public String[] getMemberClassNames() {
		return this.memberClassNames.clone();
	}

	@Override
	public MergedAnnotations getAnnotations() {
		return this.annotations;
	}

	@Override
	public Set<String> getAnnotationTypes() {
		Set<String> annotationTypes = this.annotationTypes;
		if (annotationTypes == null) {
			annotationTypes = Collections.unmodifiableSet(
					AnnotationMetadata.super.getAnnotationTypes());
			this.annotationTypes = annotationTypes;
		}
		return annotationTypes;
	}

	@Override
	public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
		Set<MethodMetadata> annotatedMethods = null;
		for (MethodMetadata annotatedMethod : this.annotatedMethods) {
			if (annotatedMethod.isAnnotated(annotationName)) {
				if (annotatedMethods == null) {
					annotatedMethods = new LinkedHashSet<>(4);
				}
				annotatedMethods.add(annotatedMethod);
			}
		}
		return (annotatedMethods != null ? annotatedMethods : Collections.emptySet());
	}


	@Override
	public boolean equals(@Nullable Object obj) {
		return ((this == obj) || ((obj instanceof SimpleAnnotationMetadata) &&
				this.className.equals(((SimpleAnnotationMetadata) obj).className)));
	}

	@Override
	public int hashCode() {
		return this.className.hashCode();
	}

	@Override
	public String toString() {
		return this.className;
	}

}
