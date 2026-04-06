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

import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>{@link ClassMetadata} 的反射实现——拿着 Class 对象直接问 JVM 要信息！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.StandardClassMetadata}</li>
 * <li><b>中文名</b>：标准类元数据 —— 反射路径的"翻译器"</li>
 * <li><b>命名法则</b>：<b>Standard 前缀</b> = 基于标准 Java 反射 API 实现（对应 ASM 路径的 Simple 前缀）</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type} 包</li>
 * </ul>
 *
 * <h3>💡 反射路径 vs ASM 路径</h3>
 * <p>StandardClassMetadata 持有一个 {@code Class<?>} 对象，通过 Java 反射 API 获取类的结构信息。<br/>
 * 前提是：<b>类已经被 ClassLoader 加载了</b>。适用于已加载的类（如手动 register 的 @Configuration 类）。</p>
 * <p><b>5.2 起已废弃构造器</b>——推荐直接用 {@link StandardAnnotationMetadata}（子类），
 * 因为只拿结构信息不拿注解信息的场景几乎不存在。</p>
 *
 * <h3>🧬 实现原理：每个方法都是对 java.lang.Class / java.lang.reflect.Modifier 的简单委托</h3>
 * <hr>
 *
 * {@link ClassMetadata} implementation that uses standard reflection
 * to introspect a given {@code Class}.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 2.5
 */
public class StandardClassMetadata implements ClassMetadata {

	/** 被内省的 Class 对象——所有信息的来源 */
	private final Class<?> introspectedClass;


	/**
	 * 构造方法——包装一个 Class 对象。
	 * <hr>
	 *
	 * Create a new StandardClassMetadata wrapper for the given Class.
	 * @param introspectedClass the Class to introspect
	 * @deprecated since 5.2 in favor of {@link StandardAnnotationMetadata}
	 */
	@Deprecated
	public StandardClassMetadata(Class<?> introspectedClass) {
		Assert.notNull(introspectedClass, "Class must not be null");
		this.introspectedClass = introspectedClass;
	}

	/**
	 * 【暴露底层 Class】子类 StandardAnnotationMetadata 用它来做反射注解读取。
	 * <hr>
	 *
	 * Return the underlying Class.
	 */
	public final Class<?> getIntrospectedClass() {
		return this.introspectedClass;
	}


	@Override
	public String getClassName() {
		return this.introspectedClass.getName();
	}

	@Override
	public boolean isInterface() {
		return this.introspectedClass.isInterface();
	}

	@Override
	public boolean isAnnotation() {
		return this.introspectedClass.isAnnotation();
	}

	@Override
	public boolean isAbstract() {
		return Modifier.isAbstract(this.introspectedClass.getModifiers());
	}

	@Override
	public boolean isFinal() {
		return Modifier.isFinal(this.introspectedClass.getModifiers());
	}

	@Override
	public boolean isIndependent() {
		return (!hasEnclosingClass() ||
				(this.introspectedClass.getDeclaringClass() != null &&
						Modifier.isStatic(this.introspectedClass.getModifiers())));
	}

	@Override
	@Nullable
	public String getEnclosingClassName() {
		Class<?> enclosingClass = this.introspectedClass.getEnclosingClass();
		return (enclosingClass != null ? enclosingClass.getName() : null);
	}

	@Override
	@Nullable
	public String getSuperClassName() {
		Class<?> superClass = this.introspectedClass.getSuperclass();
		return (superClass != null ? superClass.getName() : null);
	}

	@Override
	public String[] getInterfaceNames() {
		Class<?>[] ifcs = this.introspectedClass.getInterfaces();
		String[] ifcNames = new String[ifcs.length];
		for (int i = 0; i < ifcs.length; i++) {
			ifcNames[i] = ifcs[i].getName();
		}
		return ifcNames;
	}

	@Override
	public String[] getMemberClassNames() {
		LinkedHashSet<String> memberClassNames = new LinkedHashSet<>(4);
		for (Class<?> nestedClass : this.introspectedClass.getDeclaredClasses()) {
			memberClassNames.add(nestedClass.getName());
		}
		return StringUtils.toStringArray(memberClassNames);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return ((this == obj) || ((obj instanceof StandardClassMetadata) &&
				getIntrospectedClass().equals(((StandardClassMetadata) obj).getIntrospectedClass())));
	}

	@Override
	public int hashCode() {
		return getIntrospectedClass().hashCode();
	}

	@Override
	public String toString() {
		return getClassName();
	}

}
