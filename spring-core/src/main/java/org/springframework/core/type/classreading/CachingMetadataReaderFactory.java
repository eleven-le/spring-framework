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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 架构坐标</h1>
 * <h2>带缓存的 MetadataReaderFactory——同一个 .class 文件只解析一次！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.classreading.CachingMetadataReaderFactory}</li>
 * <li><b>中文名</b>：缓存元数据读取器工厂 —— "解析结果仓库"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type.classreading} 包</li>
 * <li><b>命名法则</b>：Caching 前缀 = 在父类基础上加了缓存层（装饰器思想）</li>
 * </ul>
 *
 * <h3>💡 为什么需要缓存？</h3>
 * <p>Spring 启动扫描时，同一个 .class 文件可能被多次读取：</p>
 * <ul>
 * <li>@ComponentScan 扫描时读一次</li>
 * <li>@Import 处理时可能再读一次</li>
 * <li>@Conditional 评估时可能又读一次</li>
 * </ul>
 * <p>每次读取都要打开文件→ASM解析→创建对象，开销不小。缓存后只解析一次，后续直接返回。</p>
 *
 * <h3>🔑 两种缓存模式</h3>
 * <ul>
 * <li><b>本地缓存（LocalResourceCache）</b>：LRU LinkedHashMap，默认上限 256 条。
 *     用于独立的 MetadataReaderFactory 实例。</li>
 * <li><b>共享缓存（ConcurrentMap）</b>：当 ResourceLoader 是 DefaultResourceLoader 时，
 *     使用 ResourceLoader 级别的共享缓存（无大小限制）。多个工厂共享同一份缓存。</li>
 * </ul>
 * <p><b>生产环境</b>：ApplicationContext 内部使用的 MetadataReaderFactory 默认就是
 * CachingMetadataReaderFactory + 共享缓存模式。</p>
 * <hr>
 *
 * Caching implementation of the {@link MetadataReaderFactory} interface,
 * caching a {@link MetadataReader} instance per Spring {@link Resource} handle
 * (i.e. per ".class" file).
 *
 * @author Juergen Hoeller
 * @author Costin Leau
 * @since 2.5
 */
public class CachingMetadataReaderFactory extends SimpleMetadataReaderFactory {

	/** 本地缓存默认上限：256 条。超过后 LRU 淘汰最久未使用的条目。 */
	/** Default maximum number of entries for a local MetadataReader cache: 256. */
	public static final int DEFAULT_CACHE_LIMIT = 256;

	/**
	 * 缓存 Map：Resource → MetadataReader。
	 * 可能是 LocalResourceCache（本地 LRU）或 ConcurrentMap（共享缓存）。
	 * null 表示无缓存。
	 */
	/** MetadataReader cache: either local or shared at the ResourceLoader level. */
	@Nullable
	private Map<Resource, MetadataReader> metadataReaderCache;


	/**
	 * Create a new CachingMetadataReaderFactory for the default class loader,
	 * using a local resource cache.
	 */
	public CachingMetadataReaderFactory() {
		super();
		setCacheLimit(DEFAULT_CACHE_LIMIT);
	}

	/**
	 * Create a new CachingMetadataReaderFactory for the given {@link ClassLoader},
	 * using a local resource cache.
	 * @param classLoader the ClassLoader to use
	 */
	public CachingMetadataReaderFactory(@Nullable ClassLoader classLoader) {
		super(classLoader);
		setCacheLimit(DEFAULT_CACHE_LIMIT);
	}

	/**
	 * Create a new CachingMetadataReaderFactory for the given {@link ResourceLoader},
	 * using a shared resource cache if supported or a local resource cache otherwise.
	 * @param resourceLoader the Spring ResourceLoader to use
	 * (also determines the ClassLoader to use)
	 * @see DefaultResourceLoader#getResourceCache
	 */
	public CachingMetadataReaderFactory(@Nullable ResourceLoader resourceLoader) {
		super(resourceLoader);
		if (resourceLoader instanceof DefaultResourceLoader) {
			this.metadataReaderCache =
					((DefaultResourceLoader) resourceLoader).getResourceCache(MetadataReader.class);
		}
		else {
			setCacheLimit(DEFAULT_CACHE_LIMIT);
		}
	}


	/**
	 * Specify the maximum number of entries for the MetadataReader cache.
	 * <p>Default is 256 for a local cache, whereas a shared cache is
	 * typically unbounded. This method enforces a local resource cache,
	 * even if the {@link ResourceLoader} supports a shared resource cache.
	 */
	public void setCacheLimit(int cacheLimit) {
		if (cacheLimit <= 0) {
			this.metadataReaderCache = null;
		}
		else if (this.metadataReaderCache instanceof LocalResourceCache) {
			((LocalResourceCache) this.metadataReaderCache).setCacheLimit(cacheLimit);
		}
		else {
			this.metadataReaderCache = new LocalResourceCache(cacheLimit);
		}
	}

	/**
	 * Return the maximum number of entries for the MetadataReader cache.
	 */
	public int getCacheLimit() {
		if (this.metadataReaderCache instanceof LocalResourceCache) {
			return ((LocalResourceCache) this.metadataReaderCache).getCacheLimit();
		}
		else {
			return (this.metadataReaderCache != null ? Integer.MAX_VALUE : 0);
		}
	}


	/**
	 * 【带缓存的 getMetadataReader】——核心方法，覆写父类。
	 * <p>三种策略：
	 * <ol>
	 * <li>ConcurrentMap（共享缓存）→ 无需同步，直接 get/put</li>
	 * <li>LocalResourceCache（本地 LRU）→ synchronized 保护</li>
	 * <li>null（无缓存）→ 退化为父类行为（每次新建）</li>
	 * </ol>
	 */
	@Override
	public MetadataReader getMetadataReader(Resource resource) throws IOException {
		if (this.metadataReaderCache instanceof ConcurrentMap) {
			// 共享缓存（ConcurrentMap）——线程安全，无需同步
			MetadataReader metadataReader = this.metadataReaderCache.get(resource);
			if (metadataReader == null) {
				metadataReader = super.getMetadataReader(resource);
				this.metadataReaderCache.put(resource, metadataReader);
			}
			return metadataReader;
		}
		else if (this.metadataReaderCache != null) {
			// 本地缓存（LocalResourceCache = LinkedHashMap）——非线程安全，需要 synchronized
			synchronized (this.metadataReaderCache) {
				MetadataReader metadataReader = this.metadataReaderCache.get(resource);
				if (metadataReader == null) {
					metadataReader = super.getMetadataReader(resource);
					this.metadataReaderCache.put(resource, metadataReader);
				}
				return metadataReader;
			}
		}
		else {
			// 无缓存——退化为父类行为
			return super.getMetadataReader(resource);
		}
	}

	/**
	 * Clear the local MetadataReader cache, if any, removing all cached class metadata.
	 */
	public void clearCache() {
		if (this.metadataReaderCache instanceof LocalResourceCache) {
			synchronized (this.metadataReaderCache) {
				this.metadataReaderCache.clear();
			}
		}
		else if (this.metadataReaderCache != null) {
			// Shared resource cache -> reset to local cache.
			setCacheLimit(DEFAULT_CACHE_LIMIT);
		}
	}


	@SuppressWarnings("serial")
	private static class LocalResourceCache extends LinkedHashMap<Resource, MetadataReader> {

		private volatile int cacheLimit;

		public LocalResourceCache(int cacheLimit) {
			super(cacheLimit, 0.75f, true);
			this.cacheLimit = cacheLimit;
		}

		public void setCacheLimit(int cacheLimit) {
			this.cacheLimit = cacheLimit;
		}

		public int getCacheLimit() {
			return this.cacheLimit;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<Resource, MetadataReader> eldest) {
			return size() > this.cacheLimit;
		}
	}

}
