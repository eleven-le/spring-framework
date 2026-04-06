/*
 * Copyright 2002-2018 the original author or authors.
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

import java.io.FileNotFoundException;
import java.io.IOException;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>{@link MetadataReaderFactory} 的基础实现——每次请求都创建新的 MetadataReader（无缓存）</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.SimpleMetadataReaderFactory}</li>
 * <li><b>中文名</b>：简单元数据读取器工厂 —— "现做现卖"版工厂</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type.classreading} 包</li>
 * <li><b>命名法则</b>：Simple 前缀 = 最基础的实现（无缓存、无优化）</li>
 * </ul>
 *
 * <h3>💡 核心职责</h3>
 * <p>两件事：</p>
 * <ol>
 * <li><b>类名→Resource 转换</b>：用 ResourceLoader 把 "com.example.Foo" 转成 "classpath:com/example/Foo.class"</li>
 * <li><b>创建 SimpleMetadataReader</b>：new SimpleMetadataReader(resource, classLoader) 触发 ASM 解析</li>
 * </ol>
 * <p>没有缓存——每次调用都会重新读取 .class 文件并 ASM 解析。<br/>
 * 生产环境通常用子类 {@link CachingMetadataReaderFactory}（带缓存）。</p>
 * <hr>
 *
 * Simple implementation of the {@link MetadataReaderFactory} interface,
 * creating a new ASM {@link org.springframework.asm.ClassReader} for every request.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
public class SimpleMetadataReaderFactory implements MetadataReaderFactory {

	/** 资源加载器——用于将类名转换为 .class 文件的 Resource */
	private final ResourceLoader resourceLoader;


	/**
	 * Create a new SimpleMetadataReaderFactory for the default class loader.
	 */
	public SimpleMetadataReaderFactory() {
		this.resourceLoader = new DefaultResourceLoader();
	}

	/**
	 * Create a new SimpleMetadataReaderFactory for the given resource loader.
	 * @param resourceLoader the Spring ResourceLoader to use
	 * (also determines the ClassLoader to use)
	 */
	public SimpleMetadataReaderFactory(@Nullable ResourceLoader resourceLoader) {
		this.resourceLoader = (resourceLoader != null ? resourceLoader : new DefaultResourceLoader());
	}

	/**
	 * Create a new SimpleMetadataReaderFactory for the given class loader.
	 * @param classLoader the ClassLoader to use
	 */
	public SimpleMetadataReaderFactory(@Nullable ClassLoader classLoader) {
		this.resourceLoader =
				(classLoader != null ? new DefaultResourceLoader(classLoader) : new DefaultResourceLoader());
	}


	/**
	 * Return the ResourceLoader that this MetadataReaderFactory has been
	 * constructed with.
	 */
	public final ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}


	/**
	 * 【类名→Resource→MetadataReader】
	 * <p>转换规则：com.example.MyService → classpath:com/example/MyService.class</p>
	 * <p><b>内部类兼容</b>：如果 com.example.Outer.Inner 找不到（.class 文件路径用 $ 分隔内部类），
	 * 会尝试 com.example.Outer$Inner。因为 Java 源码用 . 分隔，但 .class 文件用 $ 分隔内部类。
	 */
	@Override
	public MetadataReader getMetadataReader(String className) throws IOException {
		try {
			// 类名 → classpath 资源路径：com.example.Foo → classpath:com/example/Foo.class
			String resourcePath = ResourceLoader.CLASSPATH_URL_PREFIX +
					ClassUtils.convertClassNameToResourcePath(className) + ClassUtils.CLASS_FILE_SUFFIX;
			Resource resource = this.resourceLoader.getResource(resourcePath);
			return getMetadataReader(resource);
		}
		catch (FileNotFoundException ex) {
			// 找不到？可能是内部类——尝试把最后一个 . 换成 $
			// 例如 com.example.Outer.Inner → com.example.Outer$Inner
			// Maybe an inner class name using the dot name syntax? Need to use the dollar syntax here...
			// ClassUtils.forName has an equivalent check for resolution into Class references later on.
			int lastDotIndex = className.lastIndexOf('.');
			if (lastDotIndex != -1) {
				String innerClassName =
						className.substring(0, lastDotIndex) + '$' + className.substring(lastDotIndex + 1);
				String innerClassResourcePath = ResourceLoader.CLASSPATH_URL_PREFIX +
						ClassUtils.convertClassNameToResourcePath(innerClassName) + ClassUtils.CLASS_FILE_SUFFIX;
				Resource innerClassResource = this.resourceLoader.getResource(innerClassResourcePath);
				if (innerClassResource.exists()) {
					return getMetadataReader(innerClassResource);
				}
			}
			throw ex;
		}
	}

	/**
	 * 【Resource→MetadataReader】直接创建 SimpleMetadataReader 触发 ASM 解析。
	 * <p>子类 CachingMetadataReaderFactory 覆写此方法，加入缓存逻辑。
	 */
	@Override
	public MetadataReader getMetadataReader(Resource resource) throws IOException {
		return new SimpleMetadataReader(resource, this.resourceLoader.getClassLoader());
	}

}
