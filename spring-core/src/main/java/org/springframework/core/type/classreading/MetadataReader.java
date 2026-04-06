/*
 * Copyright 2002-2009 the original author or authors.
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

import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>ASM 路径的"交付物"——一个 .class 文件被 ASM 解析后，你拿到的就是这个！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.MetadataReader}</li>
 * <li><b>中文名</b>：元数据读取器 —— .class 文件的"解析结果信封"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type.classreading} 包
 *     <br/>（注意！{@code classreading} 包 = <b>ASM 字节码读取层</b>！这是 Spring 包扫描"不加载类"的秘密武器）</li>
 * <li><b>接口层级</b>：3 个方法的门面接口（Facade 模式）</li>
 * </ul>
 *
 * <h3>📦 包的设计寓意：{@code core.type.classreading}</h3>
 * <p>这个包是 {@code core.type} 接口契约的 <b>ASM 实现层</b>：</p>
 * <ul>
 * <li>{@code core.type} 定义了 ClassMetadata / AnnotationMetadata / MethodMetadata 等接口</li>
 * <li>{@code core.type.classreading} 通过 ASM ClassReader 读取 .class 字节码来实现这些接口</li>
 * <li>核心角色：MetadataReader（解析结果）→ MetadataReaderFactory（创建工厂）→ *ReadingVisitor（ASM 访问者）</li>
 * </ul>
 * <p><b>为什么要独立成子包？</b>因为 ASM 实现涉及大量 Visitor 类和底层字节码操作，
 * 这些是"实现细节"不应该污染 core.type 的"契约层"。分包体现了<b>接口与实现分离</b>的设计原则。</p>
 *
 * <h3>💡 MetadataReader 的角色——"信封"</h3>
 * <p>MetadataReader 本身不做任何解析工作，它只是<b>持有解析结果</b>的容器：</p>
 * <ul>
 * <li>Resource —— .class 文件的引用（从哪来的）</li>
 * <li>ClassMetadata —— 类的结构信息</li>
 * <li>AnnotationMetadata —— 类的注解信息（包含方法注解）</li>
 * </ul>
 * <p>实际解析工作由 SimpleAnnotationMetadataReadingVisitor（ASM Visitor）完成，
 * MetadataReader 只是把结果打包交给调用者。</p>
 *
 * <h3>🔗 核心调用链</h3>
 * <pre>
 * ClassPathScanningCandidateComponentProvider.scanCandidateComponents()
 *   → MetadataReaderFactory.getMetadataReader(resource)        // 工厂创建
 *     → new SimpleMetadataReader(resource, classLoader)         // ASM 解析
 *       → ClassReader.accept(SimpleAnnotationMetadataReadingVisitor) // ASM 遍历 .class
 *   → metadataReader.getAnnotationMetadata()                    // 取出结果
 *   → 判断是否有 @Component → 是否注册 BD
 * </pre>
 * <hr>
 *
 * Simple facade for accessing class metadata,
 * as read by an ASM {@link org.springframework.asm.ClassReader}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
public interface MetadataReader {

	/**
	 * 【文件来源】.class 文件的 Resource 引用。
	 * <p>可以用它获取文件路径、输入流等。Spring 用 Resource 作为缓存的 key（CachingMetadataReaderFactory）。
	 * <hr>
	 *
	 * Return the resource reference for the class file.
	 */
	Resource getResource();

	/**
	 * 【类结构信息】只包含类名、修饰符、继承关系等——不含注解。
	 * <p>实际上 SimpleMetadataReader 的实现中，这个方法和 getAnnotationMetadata() 返回的是<b>同一个对象</b>
	 * （SimpleAnnotationMetadata 同时实现了 ClassMetadata 和 AnnotationMetadata）。
	 * <p>但接口层面仍然区分，因为有些调用者只需要结构信息，不需要注解——语义更清晰。
	 * <hr>
	 *
	 * Read basic class metadata for the underlying class.
	 */
	ClassMetadata getClassMetadata();

	/**
	 * 【完整注解元数据】类的结构信息 + 注解信息 + 方法注解信息——全部包含。
	 * <p>这是包扫描的<b>主要消费者</b>：拿到这个就能做所有决策（是否 @Component、是否 @Configuration、
	 * 有没有 @Bean 方法、@Conditional 是否满足等）。
	 * <hr>
	 *
	 * Read full annotation metadata for the underlying class,
	 * including metadata for annotated methods.
	 */
	AnnotationMetadata getAnnotationMetadata();

}
