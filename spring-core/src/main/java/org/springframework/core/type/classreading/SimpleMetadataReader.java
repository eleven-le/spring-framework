/*
 * Copyright 2002-2022 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;

import org.springframework.asm.ClassReader;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>{@link MetadataReader} 的唯一实现——ASM 解析的"执行者"</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.SimpleMetadataReader}</li>
 * <li><b>中文名</b>：简单元数据读取器 —— .class 字节码的"X光机"</li>
 * <li><b>命名法则</b>：Simple 前缀 = 基于 ASM 的轻量实现（不加载类）</li>
 * <li><b>可见性</b>：<b>包私有 + final</b>——外部不能直接 new，必须通过 MetadataReaderFactory</li>
 * </ul>
 *
 * <h3>💡 核心逻辑：构造器里就完成了全部解析</h3>
 * <p>构造器做了三件事：</p>
 * <ol>
 * <li>创建 ASM 访问者 SimpleAnnotationMetadataReadingVisitor</li>
 * <li>用 ClassReader.accept() 驱动 ASM 遍历 .class 文件的每个字节</li>
 * <li>从访问者中取出解析结果 SimpleAnnotationMetadata</li>
 * </ol>
 * <p>之后 MetadataReader 就是一个纯粹的"结果容器"——只有 getter，没有任何计算。</p>
 *
 * <h3>⚡ PARSING_OPTIONS——只要元数据，不要执行细节</h3>
 * <p>SKIP_DEBUG | SKIP_CODE | SKIP_FRAMES：跳过调试信息、方法体字节码、栈帧信息。<br/>
 * 因为 Spring 只需要类/方法/注解的<b>声明信息</b>，不需要方法体内的实现——大幅减少解析时间和内存。</p>
 * <hr>
 *
 * {@link MetadataReader} implementation based on an ASM
 * {@link org.springframework.asm.ClassReader}.
 *
 * @author Juergen Hoeller
 * @author Costin Leau
 * @since 2.5
 */
final class SimpleMetadataReader implements MetadataReader {

	/**
	 * ASM 解析选项——只解析声明信息，跳过所有实现细节：
	 * SKIP_DEBUG：跳过调试信息（行号表、局部变量表）
	 * SKIP_CODE：跳过方法体字节码（方法的实现不关心）
	 * SKIP_FRAMES：跳过栈映射帧（JVM 验证用的，Spring 不需要）
	 */
	private static final int PARSING_OPTIONS = ClassReader.SKIP_DEBUG
			| ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES;

	/** .class 文件的 Resource 引用 */
	private final Resource resource;

	/** 解析结果——SimpleAnnotationMetadata 同时实现 ClassMetadata 和 AnnotationMetadata */
	private final AnnotationMetadata annotationMetadata;


	/**
	 * 【核心构造器——在这里完成全部 ASM 解析】
	 * <p>流程：创建 Visitor → ClassReader.accept(visitor) → 取出结果。
	 * <p>整个 .class 文件的解析在构造器中<b>一次性完成</b>，之后只有 getter。
	 */
	SimpleMetadataReader(Resource resource, @Nullable ClassLoader classLoader) throws IOException {
		// 1. 创建 ASM 访问者——它会在 accept() 过程中收集类信息、注解信息、方法信息
		SimpleAnnotationMetadataReadingVisitor visitor = new SimpleAnnotationMetadataReadingVisitor(classLoader);
		// 2. 驱动 ASM 遍历 .class 文件——Visitor 模式的经典使用
		getClassReader(resource).accept(visitor, PARSING_OPTIONS);
		this.resource = resource;
		// 3. 取出解析结果——所有信息都在 visitor.getMetadata() 里了
		this.annotationMetadata = visitor.getMetadata();
	}

	@SuppressWarnings("deprecation")
	private static ClassReader getClassReader(Resource resource) throws IOException {
		try (InputStream is = resource.getInputStream()) {
			try {
				return new ClassReader(is);
			}
			catch (IllegalArgumentException ex) {
				throw new org.springframework.core.NestedIOException("ASM ClassReader failed to parse class file - " +
						"probably due to a new Java class file version that isn't supported yet: " + resource, ex);
			}
		}
	}


	@Override
	public Resource getResource() {
		return this.resource;
	}

	/**
	 * 注意：返回的是 annotationMetadata（同一个对象）——
	 * SimpleAnnotationMetadata 同时实现了 ClassMetadata 和 AnnotationMetadata。
	 * 这样做避免了创建两个对象，节省内存。
	 */
	@Override
	public ClassMetadata getClassMetadata() {
		return this.annotationMetadata;
	}

	@Override
	public AnnotationMetadata getAnnotationMetadata() {
		return this.annotationMetadata;
	}

}
