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

package org.springframework.core.type.classreading;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>ASM 路径的"核心引擎"——遍历 .class 字节码，收集所有元数据并组装成 SimpleAnnotationMetadata！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.SimpleAnnotationMetadataReadingVisitor}</li>
 * <li><b>中文名</b>：简单注解元数据读取访问者 —— .class 文件的"逐字节翻译官"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type.classreading} 包</li>
 * <li><b>设计模式</b>：<b>访问者模式（Visitor Pattern）</b>——ASM 的 ClassReader 驱动遍历 .class 的每个结构，
 *     本 Visitor 在每个回调中收集需要的信息</li>
 * <li><b>可见性</b>：包私有 + final</li>
 * </ul>
 *
 * <h3>💡 ASM 访问者模式的工作原理</h3>
 * <p>ASM 的 ClassReader.accept(visitor) 会按固定顺序回调 Visitor 的方法：</p>
 * <pre>
 * visit()              → 类头信息（名称、父类、接口）
 * visitOuterClass()    → 外围类信息（如果是局部类/匿名类）
 * visitInnerClass()    → 内部类信息（可能多次回调）
 * visitAnnotation()    → 类级别注解（可能多次回调）
 * visitMethod()        → 方法信息（可能多次回调，返回 MethodVisitor 继续遍历方法）
 * visitEnd()           → 遍历结束 → 在这里组装最终的 SimpleAnnotationMetadata
 * </pre>
 * <p>每个回调方法负责收集一种信息，最终在 visitEnd() 中"打包"成 SimpleAnnotationMetadata。</p>
 *
 * <h3>🧬 Visitor 协作关系</h3>
 * <pre>
 * ClassReader.accept()
 *   → SimpleAnnotationMetadataReadingVisitor（类级别 Visitor）
 *     ├── visitAnnotation() → MergedAnnotationReadingVisitor（注解 Visitor）
 *     └── visitMethod() → SimpleMethodMetadataReadingVisitor（方法 Visitor）
 *                            └── visitAnnotation() → MergedAnnotationReadingVisitor（方法注解 Visitor）
 * </pre>
 * <hr>
 *
 * ASM class visitor that creates {@link SimpleAnnotationMetadata}.
 *
 * @author Phillip Webb
 * @since 5.2
 */
final class SimpleAnnotationMetadataReadingVisitor extends ClassVisitor {

	/** ClassLoader——用于注解解析时的类型查找（可为 null） */
	@Nullable
	private final ClassLoader classLoader;

	// ========== 以下字段在各个 visit* 回调中逐步填充 ==========

	/** 类全限定名——在 visit() 中填充 */
	private String className = "";

	/** ASM 访问标志位——在 visit() 中填充 */
	private int access;

	/** 父类名——在 visit() 中填充，接口为 null */
	@Nullable
	private String superClassName;

	/** 实现的接口名数组——在 visit() 中填充 */
	private String[] interfaceNames = new String[0];

	/** 外围类名——在 visitOuterClass() 或 visitInnerClass() 中填充 */
	@Nullable
	private String enclosingClassName;

	/** 是否是独立内部类——在 visitInnerClass() 中判断 */
	private boolean independentInnerClass;

	/** 成员类名集合——在 visitInnerClass() 中填充 */
	private Set<String> memberClassNames = new LinkedHashSet<>(4);

	/** 类级别注解列表——在 visitAnnotation() 中通过 MergedAnnotationReadingVisitor 收集 */
	private List<MergedAnnotation<?>> annotations = new ArrayList<>();

	/** 被注解标注的方法列表——在 visitMethod() 中通过 SimpleMethodMetadataReadingVisitor 收集 */
	private List<SimpleMethodMetadata> annotatedMethods = new ArrayList<>();

	/** 最终组装结果——在 visitEnd() 中创建 */
	@Nullable
	private SimpleAnnotationMetadata metadata;

	/** MergedAnnotation 的 source 标识——懒创建，用类名标识 */
	@Nullable
	private Source source;


	SimpleAnnotationMetadataReadingVisitor(@Nullable ClassLoader classLoader) {
		super(SpringAsmInfo.ASM_VERSION);
		this.classLoader = classLoader;
	}


	// =====================================================================================
	// 一、visit()——类头信息回调（第一个被调用）
	// =====================================================================================

	/**
	 * 【类头信息】ASM 解析 .class 文件头时回调，提供类名、父类、接口等基础信息。
	 * <p>注意：ASM 中的类名用 / 分隔（如 "com/example/Foo"），需要转换为 . 分隔。
	 * <p>接口的 superName 是 "java/lang/Object"，但我们不记录它（接口没有有意义的父类）。
	 */
	@Override
	public void visit(int version, int access, String name, String signature,
			@Nullable String supername, String[] interfaces) {

		this.className = toClassName(name);    // com/example/Foo → com.example.Foo
		this.access = access;
		if (supername != null && !isInterface(access)) {
			this.superClassName = toClassName(supername);   // 只有非接口才记录父类
		}
		this.interfaceNames = new String[interfaces.length];
		for (int i = 0; i < interfaces.length; i++) {
			this.interfaceNames[i] = toClassName(interfaces[i]);
		}
	}

	// =====================================================================================
	// 二、visitOuterClass / visitInnerClass——嵌套关系回调
	// =====================================================================================

	/**
	 * 【外围类信息】当前类是局部类或匿名类时，ASM 回调此方法告知外围类。
	 */
	@Override
	public void visitOuterClass(String owner, String name, String desc) {
		this.enclosingClassName = toClassName(owner);
	}

	/**
	 * 【内部类信息】ASM 为每个 InnerClasses 属性条目回调此方法。
	 * <p>这个方法做两件事：
	 * <ol>
	 * <li>如果当前类就是这个内部类 → 记录外围类名 + 判断是否 static（独立内部类）</li>
	 * <li>如果当前类是这个内部类的外围类 → 记录成员类名</li>
	 * </ol>
	 */
	@Override
	public void visitInnerClass(String name, @Nullable String outerName, String innerName, int access) {
		if (outerName != null) {
			String className = toClassName(name);
			String outerClassName = toClassName(outerName);
			if (this.className.equals(className)) {
				// 当前类是内部类 → 记录外围类 + 是否静态
				this.enclosingClassName = outerClassName;
				this.independentInnerClass = ((access & Opcodes.ACC_STATIC) != 0);
			}
			else if (this.className.equals(outerClassName)) {
				// 当前类是外围类 → 记录成员类
				this.memberClassNames.add(className);
			}
		}
	}

	// =====================================================================================
	// 三、visitAnnotation——类级别注解回调
	// =====================================================================================

	/**
	 * 【类注解】每遇到一个类级别注解，ASM 回调此方法。
	 * <p>返回 MergedAnnotationReadingVisitor 继续解析注解的属性值。
	 * <p>解析完成后，通过 this.annotations::add 回调将结果添加到注解列表。
	 */
	@Override
	@Nullable
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return MergedAnnotationReadingVisitor.get(this.classLoader, getSource(),
				descriptor, visible, this.annotations::add);
	}

	// =====================================================================================
	// 四、visitMethod——方法回调
	// =====================================================================================

	/**
	 * 【方法】每遇到一个方法，ASM 回调此方法。
	 * <p>返回 SimpleMethodMetadataReadingVisitor 继续解析方法上的注解。
	 * <p>跳过桥接方法（bridge）——桥接方法是 JVM 为泛型擦除生成的，不是用户写的。
	 * <p>注意：这里返回的 MethodVisitor 只有在方法有注解时才会将 SimpleMethodMetadata 加入列表
	 * （在 SimpleMethodMetadataReadingVisitor.visitEnd() 中判断）。
	 */
	@Override
	@Nullable
	public MethodVisitor visitMethod(
			int access, String name, String descriptor, String signature, String[] exceptions) {

		// 桥接方法跳过——避免同一个方法被重复检测（JDK 8 泛型擦除问题）
		// Skip bridge methods - we're only interested in original
		// annotation-defining user methods. On JDK 8, we'd otherwise run into
		// double detection of the same annotated method...
		if (isBridge(access)) {
			return null;
		}
		// 返回方法级别的 Visitor，它会收集方法注解，有注解时通过 consumer 回调添加到列表
		return new SimpleMethodMetadataReadingVisitor(this.classLoader, this.className,
				access, name, descriptor, this.annotatedMethods::add);
	}

	// =====================================================================================
	// 五、visitEnd——遍历结束，组装最终结果
	// =====================================================================================

	/**
	 * 【组装最终结果】.class 文件遍历完毕，将收集到的所有信息打包成 SimpleAnnotationMetadata。
	 * <p>这是 Visitor 模式的"终结回调"——所有 visit* 方法收集的零散数据在这里汇聚为完整对象。
	 */
	@Override
	public void visitEnd() {
		String[] memberClassNames = StringUtils.toStringArray(this.memberClassNames);
		MethodMetadata[] annotatedMethods = this.annotatedMethods.toArray(new MethodMetadata[0]);
		// MergedAnnotations.of(List) 从注解列表创建合并注解集合
		MergedAnnotations annotations = MergedAnnotations.of(this.annotations);
		// 一次性创建不可变的结果对象
		this.metadata = new SimpleAnnotationMetadata(this.className, this.access,
				this.enclosingClassName, this.superClassName, this.independentInnerClass,
				this.interfaceNames, memberClassNames, annotatedMethods, annotations);
	}

	public SimpleAnnotationMetadata getMetadata() {
		Assert.state(this.metadata != null, "AnnotationMetadata not initialized");
		return this.metadata;
	}

	private Source getSource() {
		Source source = this.source;
		if (source == null) {
			source = new Source(this.className);
			this.source = source;
		}
		return source;
	}

	private String toClassName(String name) {
		return ClassUtils.convertResourcePathToClassName(name);
	}

	private boolean isBridge(int access) {
		return (access & Opcodes.ACC_BRIDGE) != 0;
	}

	private boolean isInterface(int access) {
		return (access & Opcodes.ACC_INTERFACE) != 0;
	}


	/**
	 * {@link MergedAnnotation} source.
	 */
	private static final class Source {

		private final String className;

		Source(String className) {
			this.className = className;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			return this.className.equals(((Source) obj).className);
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

}
