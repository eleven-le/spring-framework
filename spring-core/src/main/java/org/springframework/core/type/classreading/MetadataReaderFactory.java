/*
 * Copyright 2002-2012 the original author or authors.
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

import org.springframework.core.io.Resource;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>MetadataReader 的工厂——"给我类名或 .class 文件，我返回解析好的元数据"</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.MetadataReaderFactory}</li>
 * <li><b>中文名</b>：元数据读取器工厂 —— ASM 解析的"入口大门"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type.classreading} 包
 *     <br/>（{@code classreading} 包 = ASM 字节码读取层）</li>
 * <li><b>接口层级</b>：2 个方法的工厂接口</li>
 * </ul>
 *
 * <h3>💡 为什么需要工厂？</h3>
 * <p>直接 new SimpleMetadataReader 也能工作，但存在两个问题：</p>
 * <ul>
 * <li><b>缓存</b>：同一个 .class 文件可能被多次解析（@ComponentScan 的 includeFilters/excludeFilters
 *     可能触发重复读取），工厂可以缓存结果</li>
 * <li><b>ResourceLoader 绑定</b>：类名 → .class 文件路径的转换需要 ResourceLoader，工厂持有它</li>
 * </ul>
 *
 * <h3>🧬 工厂体系</h3>
 * <pre>
 *      MetadataReaderFactory                ← 工厂接口
 *              ↓
 *    SimpleMetadataReaderFactory            ← 每次创建新的 MetadataReader（无缓存）
 *              ↓
 *    CachingMetadataReaderFactory           ← 缓存 MetadataReader（生产环境默认用这个）
 * </pre>
 *
 * <h3>🔗 谁在用这个工厂？</h3>
 * <ul>
 * <li>ClassPathScanningCandidateComponentProvider —— 包扫描的核心，用它读取每个 .class</li>
 * <li>ConfigurationClassParser —— 处理 @Import/@ComponentScan 时递归解析类</li>
 * <li>ConditionEvaluator —— @Conditional 条件评估时读取候选类的元数据</li>
 * </ul>
 * <hr>
 *
 * Factory interface for {@link MetadataReader} instances.
 * Allows for caching a MetadataReader per original resource.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see SimpleMetadataReaderFactory
 * @see CachingMetadataReaderFactory
 */
public interface MetadataReaderFactory {

	/**
	 * 【通过类名获取】类名 → .class 资源路径 → ASM 解析 → MetadataReader。
	 * <p>类名转路径规则：com.example.MyService → classpath:com/example/MyService.class
	 * @param className 类的全限定名（如 "com.example.MyService"）
	 * <hr>
	 *
	 * Obtain a MetadataReader for the given class name.
	 * @param className the class name (to be resolved to a ".class" file)
	 * @return a holder for the ClassReader instance (never {@code null})
	 * @throws IOException in case of I/O failure
	 */
	MetadataReader getMetadataReader(String className) throws IOException;

	/**
	 * 【通过 Resource 获取】直接给 .class 文件的 Resource，跳过类名→路径转换。
	 * <p>包扫描场景下，Spring 已经通过 PathMatchingResourcePatternResolver 拿到了
	 * 所有 .class 的 Resource，直接传入即可。
	 * @param resource .class 文件的 Resource 引用
	 * <hr>
	 *
	 * Obtain a MetadataReader for the given resource.
	 * @param resource the resource (pointing to a ".class" file)
	 * @return a holder for the ClassReader instance (never {@code null})
	 * @throws IOException in case of I/O failure
	 */
	MetadataReader getMetadataReader(Resource resource) throws IOException;

}
