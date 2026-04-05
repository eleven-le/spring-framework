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

package org.springframework.beans.factory;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>ObjectFactory 的"升级版"——安全注入、可选获取、流式遍历三合一！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.ObjectProvider}</li>
 * <li><b>中文名</b>：对象提供者 —— 注入点的"智能取货窗口"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：继承 {@code ObjectFactory<T>} + {@code Iterable<T>}，4.3 引入</li>
 * </ul>
 *
 * <h3>💡 方法速查——三类能力</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 * <tr><td rowspan="2"><b>必须获取</b></td><td>getObject()</td><td>继承自 ObjectFactory，找不到抛异常</td></tr>
 * <tr><td>getObject(Object... args)</td><td>带构造参数版本</td></tr>
 * <tr><td rowspan="3"><b>安全获取</b></td><td>getIfAvailable()</td><td>找不到返回 null（不抛异常）</td></tr>
 * <tr><td>getIfAvailable(Supplier)</td><td>找不到用默认值</td></tr>
 * <tr><td>getIfUnique()</td><td>不唯一也返回 null（不抛异常）</td></tr>
 * <tr><td rowspan="2"><b>流式遍历</b></td><td>stream()</td><td>所有匹配 Bean 的流（5.1+）</td></tr>
 * <tr><td>orderedStream()</td><td>按 @Order 排序的流（5.1+）</td></tr>
 * </table>
 *
 * <h3>🧬 典型使用场景</h3>
 * <pre>
 * // 场景1：可选依赖（替代 @Autowired(required=false)）
 * @Autowired
 * private ObjectProvider&lt;CacheManager&gt; cacheManagerProvider;
 * // 使用时：cacheManagerProvider.getIfAvailable(() -&gt; new NoOpCacheManager())
 *
 * // 场景2：多实现遍历（替代 List&lt;Strategy&gt; 注入）
 * @Autowired
 * private ObjectProvider&lt;Strategy&gt; strategies;
 * // 使用时：strategies.orderedStream().forEach(s -&gt; s.execute())
 *
 * // 场景3：延迟获取（打破循环依赖）
 * @Autowired
 * private ObjectProvider&lt;ServiceB&gt; serviceBProvider;
 * // 使用时才触发 getBean：serviceBProvider.getObject()
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>ObjectProvider 是 Spring 4.3 引入的"安全注入"利器——解决了 @Autowired 在"可选依赖"
 * 和"多候选者"场景下的痛点。推荐在构造器注入中用 ObjectProvider 替代 @Autowired(required=false)，
 * 代码更清晰、意图更明确。5.1 后增加的 stream()/orderedStream() 更是让多实现遍历优雅到位。</p>
 *
 * <hr/>
 * A variant of {@link ObjectFactory} designed specifically for injection points,
 * allowing for programmatic optionality and lenient not-unique handling.
 *
 * <p>As of 5.1, this interface extends {@link Iterable} and provides {@link Stream}
 * support. It can be therefore be used in {@code for} loops, provides {@link #forEach}
 * iteration and allows for collection-style {@link #stream} access.
 *
 * @author Juergen Hoeller
 * @since 4.3
 * @param <T> the object type
 * @see BeanFactory#getBeanProvider
 * @see org.springframework.beans.factory.annotation.Autowired
 */
public interface ObjectProvider<T> extends ObjectFactory<T>, Iterable<T> {

	/**
	 * Return an instance (possibly shared or independent) of the object
	 * managed by this factory.
	 * <p>Allows for specifying explicit construction arguments, along the
	 * lines of {@link BeanFactory#getBean(String, Object...)}.
	 * @param args arguments to use when creating a corresponding instance
	 * @return an instance of the bean
	 * @throws BeansException in case of creation errors
	 * @see #getObject()
	 */
	T getObject(Object... args) throws BeansException;

	/**
	 * Return an instance (possibly shared or independent) of the object
	 * managed by this factory.
	 * @return an instance of the bean, or {@code null} if not available
	 * @throws BeansException in case of creation errors
	 * @see #getObject()
	 */
	@Nullable
	T getIfAvailable() throws BeansException;

	/**
	 * Return an instance (possibly shared or independent) of the object
	 * managed by this factory.
	 * @param defaultSupplier a callback for supplying a default object
	 * if none is present in the factory
	 * @return an instance of the bean, or the supplied default object
	 * if no such bean is available
	 * @throws BeansException in case of creation errors
	 * @since 5.0
	 * @see #getIfAvailable()
	 */
	default T getIfAvailable(Supplier<T> defaultSupplier) throws BeansException {
		T dependency = getIfAvailable();
		return (dependency != null ? dependency : defaultSupplier.get());
	}

	/**
	 * Consume an instance (possibly shared or independent) of the object
	 * managed by this factory, if available.
	 * @param dependencyConsumer a callback for processing the target object
	 * if available (not called otherwise)
	 * @throws BeansException in case of creation errors
	 * @since 5.0
	 * @see #getIfAvailable()
	 */
	default void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
		T dependency = getIfAvailable();
		if (dependency != null) {
			dependencyConsumer.accept(dependency);
		}
	}

	/**
	 * Return an instance (possibly shared or independent) of the object
	 * managed by this factory.
	 * @return an instance of the bean, or {@code null} if not available or
	 * not unique (i.e. multiple candidates found with none marked as primary)
	 * @throws BeansException in case of creation errors
	 * @see #getObject()
	 */
	@Nullable
	T getIfUnique() throws BeansException;

	/**
	 * Return an instance (possibly shared or independent) of the object
	 * managed by this factory.
	 * @param defaultSupplier a callback for supplying a default object
	 * if no unique candidate is present in the factory
	 * @return an instance of the bean, or the supplied default object
	 * if no such bean is available or if it is not unique in the factory
	 * (i.e. multiple candidates found with none marked as primary)
	 * @throws BeansException in case of creation errors
	 * @since 5.0
	 * @see #getIfUnique()
	 */
	default T getIfUnique(Supplier<T> defaultSupplier) throws BeansException {
		T dependency = getIfUnique();
		return (dependency != null ? dependency : defaultSupplier.get());
	}

	/**
	 * Consume an instance (possibly shared or independent) of the object
	 * managed by this factory, if unique.
	 * @param dependencyConsumer a callback for processing the target object
	 * if unique (not called otherwise)
	 * @throws BeansException in case of creation errors
	 * @since 5.0
	 * @see #getIfAvailable()
	 */
	default void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
		T dependency = getIfUnique();
		if (dependency != null) {
			dependencyConsumer.accept(dependency);
		}
	}

	/**
	 * Return an {@link Iterator} over all matching object instances,
	 * without specific ordering guarantees (but typically in registration order).
	 * @since 5.1
	 * @see #stream()
	 */
	@Override
	default Iterator<T> iterator() {
		return stream().iterator();
	}

	/**
	 * Return a sequential {@link Stream} over all matching object instances,
	 * without specific ordering guarantees (but typically in registration order).
	 * @since 5.1
	 * @see #iterator()
	 * @see #orderedStream()
	 */
	default Stream<T> stream() {
		throw new UnsupportedOperationException("Multi element access not supported");
	}

	/**
	 * Return a sequential {@link Stream} over all matching object instances,
	 * pre-ordered according to the factory's common order comparator.
	 * <p>In a standard Spring application context, this will be ordered
	 * according to {@link org.springframework.core.Ordered} conventions,
	 * and in case of annotation-based configuration also considering the
	 * {@link org.springframework.core.annotation.Order} annotation,
	 * analogous to multi-element injection points of list/array type.
	 * @since 5.1
	 * @see #stream()
	 * @see org.springframework.core.OrderComparator
	 */
	default Stream<T> orderedStream() {
		throw new UnsupportedOperationException("Ordered element access not supported");
	}

}
