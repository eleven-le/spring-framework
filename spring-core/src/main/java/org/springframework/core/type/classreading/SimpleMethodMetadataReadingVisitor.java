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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>ASM 方法级别的 Visitor——遍历方法结构，收集方法注解</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.SimpleMethodMetadataReadingVisitor}</li>
 * <li><b>中文名</b>：简单方法元数据读取访问者 —— 方法的"注解收集器"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type.classreading} 包</li>
 * <li><b>设计模式</b>：访问者模式——由 SimpleAnnotationMetadataReadingVisitor.visitMethod() 返回</li>
 * <li><b>可见性</b>：包私有 + final</li>
 * </ul>
 *
 * <h3>💡 工作流程</h3>
 * <ol>
 * <li>SimpleAnnotationMetadataReadingVisitor.visitMethod() 创建本 Visitor</li>
 * <li>ASM 遍历方法的注解，每个注解回调 visitAnnotation() → MergedAnnotationReadingVisitor 解析</li>
 * <li>visitEnd() 时：如果有注解 → 创建 SimpleMethodMetadata → 通过 consumer 回调通知类 Visitor</li>
 * <li>如果没有注解 → 不创建 SimpleMethodMetadata（节省内存，没注解的方法 Spring 不关心）</li>
 * </ol>
 * <hr>
 *
 * ASM method visitor that creates {@link SimpleMethodMetadata}.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 */
final class SimpleMethodMetadataReadingVisitor extends MethodVisitor {

	@Nullable
	private final ClassLoader classLoader;

	/** 声明该方法的类名 */
	private final String declaringClassName;

	/** ASM 访问标志位 */
	private final int access;

	private final String methodName;

	/** ASM 方法描述符——包含参数类型和返回类型信息（如 "(Ljava/lang/String;)V"） */
	private final String descriptor;

	/** 收集到的方法注解列表 */
	private final List<MergedAnnotation<?>> annotations = new ArrayList<>(4);

	/** 结果消费者——方法有注解时，通过这个回调将 SimpleMethodMetadata 传给类 Visitor */
	private final Consumer<SimpleMethodMetadata> consumer;

	@Nullable
	private Source source;


	SimpleMethodMetadataReadingVisitor(@Nullable ClassLoader classLoader, String declaringClassName,
			int access, String methodName, String descriptor, Consumer<SimpleMethodMetadata> consumer) {

		super(SpringAsmInfo.ASM_VERSION);
		this.classLoader = classLoader;
		this.declaringClassName = declaringClassName;
		this.access = access;
		this.methodName = methodName;
		this.descriptor = descriptor;
		this.consumer = consumer;
	}


	/**
	 * 【方法注解】每遇到方法上的一个注解，回调此方法。
	 * 返回 MergedAnnotationReadingVisitor 继续解析注解属性，解析完通过 this.annotations::add 收集。
	 */
	@Override
	@Nullable
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return MergedAnnotationReadingVisitor.get(this.classLoader, getSource(),
				descriptor, visible, this.annotations::add);
	}

	/**
	 * 【方法遍历结束】
	 * <p>关键逻辑：<b>只有有注解的方法才创建 SimpleMethodMetadata</b>。
	 * 没注解的方法直接跳过——Spring 只关心被注解标注的方法（@Bean/@EventListener/@Transactional 等）。
	 * <p>返回类型从 ASM descriptor 中解析（如 "(Ljava/lang/String;)Lcom/example/Order;" → "com.example.Order"）。
	 */
	@Override
	public void visitEnd() {
		if (!this.annotations.isEmpty()) {
			// 从 ASM 方法描述符解析返回类型
			String returnTypeName = Type.getReturnType(this.descriptor).getClassName();
			MergedAnnotations annotations = MergedAnnotations.of(this.annotations);
			SimpleMethodMetadata metadata = new SimpleMethodMetadata(this.methodName, this.access,
					this.declaringClassName, returnTypeName, getSource(), annotations);
			// 通过 consumer 回调通知类级别 Visitor，将方法元数据加入列表
			this.consumer.accept(metadata);
		}
	}

	private Object getSource() {
		Source source = this.source;
		if (source == null) {
			source = new Source(this.declaringClassName, this.methodName, this.descriptor);
			this.source = source;
		}
		return source;
	}


	/**
	 * {@link MergedAnnotation} source.
	 */
	static final class Source {

		private final String declaringClassName;

		private final String methodName;

		private final String descriptor;

		@Nullable
		private String toStringValue;

		Source(String declaringClassName, String methodName, String descriptor) {
			this.declaringClassName = declaringClassName;
			this.methodName = methodName;
			this.descriptor = descriptor;
		}

		@Override
		public int hashCode() {
			int result = 1;
			result = 31 * result + this.declaringClassName.hashCode();
			result = 31 * result + this.methodName.hashCode();
			result = 31 * result + this.descriptor.hashCode();
			return result;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (other == null || getClass() != other.getClass()) {
				return false;
			}
			Source otherSource = (Source) other;
			return (this.declaringClassName.equals(otherSource.declaringClassName) &&
					this.methodName.equals(otherSource.methodName) && this.descriptor.equals(otherSource.descriptor));
		}

		@Override
		public String toString() {
			String value = this.toStringValue;
			if (value == null) {
				StringBuilder builder = new StringBuilder();
				builder.append(this.declaringClassName);
				builder.append('.');
				builder.append(this.methodName);
				Type[] argumentTypes = Type.getArgumentTypes(this.descriptor);
				builder.append('(');
				for (int i = 0; i < argumentTypes.length; i++) {
					if (i != 0) {
						builder.append(',');
					}
					builder.append(argumentTypes[i].getClassName());
				}
				builder.append(')');
				value = builder.toString();
				this.toStringValue = value;
			}
			return value;
		}
	}

}
