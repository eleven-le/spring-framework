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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>{@link AnnotationMetadata} 的反射路径实现——"类已经加载了，直接用反射读取全部信息！"</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.StandardAnnotationMetadata}</li>
 * <li><b>中文名</b>：标准注解元数据 —— 反射路径的"全功能版"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type} 包</li>
 * <li><b>继承</b>：StandardClassMetadata（结构信息） + AnnotationMetadata（注解契约）</li>
 * </ul>
 *
 * <h3>💡 反射路径的使用场景</h3>
 * <p>当一个类已经被 ClassLoader 加载（你有 Class 对象）时，走这条路：</p>
 * <ul>
 * <li>AnnotatedBeanDefinitionReader.register(AppConfig.class) —— 手动注册配置类</li>
 * <li>AnnotationMetadata.introspect(type) —— 静态工厂方法入口</li>
 * <li>@Configuration 类在后续处理阶段（已加载后）的二次内省</li>
 * </ul>
 *
 * <h3>🔑 核心设计：nestedAnnotationsAsMap 兼容开关</h3>
 * <p>反射读取注解时，嵌套注解（如 @ComponentScan 里的 @Filter）默认返回的是 Annotation 实例。<br/>
 * 但 ASM 路径返回的是 Map（因为 ASM 不加载类，无法创建 Annotation 实例）。<br/>
 * nestedAnnotationsAsMap=true 让反射路径也返回 Map，<b>保持两条路径的行为一致</b>。</p>
 *
 * <h3>🧬 继承体系</h3>
 * <pre>
 *     ClassMetadata        AnnotatedTypeMetadata
 *         ↓                       ↓
 *  StandardClassMetadata   AnnotationMetadata
 *         ↘                ↙
 *    StandardAnnotationMetadata     ← 你在这里！反射路径的"全功能版"
 * </pre>
 * <hr>
 *
 * {@link AnnotationMetadata} implementation that uses standard reflection
 * to introspect a given {@link Class}.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.5
 */
public class StandardAnnotationMetadata extends StandardClassMetadata implements AnnotationMetadata {

	/** 合并注解集合——通过 MergedAnnotations.from(Class) 创建，数据源是反射 API */
	private final MergedAnnotations mergedAnnotations;

	/**
	 * 嵌套注解是否转为 Map？
	 * true = 返回 AnnotationAttributes（Map），与 ASM 路径一致
	 * false = 返回原生 Annotation 实例
	 * 5.2+ 推荐 true（通过 introspect() 工厂方法默认就是 true）
	 */
	private final boolean nestedAnnotationsAsMap;

	/** 注解类型名缓存——懒加载，避免重复计算 */
	@Nullable
	private Set<String> annotationTypes;


	/**
	 * @deprecated 5.2 起废弃，推荐用 {@link AnnotationMetadata#introspect(Class)}
	 * <hr>
	 *
	 * Create a new {@code StandardAnnotationMetadata} wrapper for the given Class.
	 * @param introspectedClass the Class to introspect
	 * @see #StandardAnnotationMetadata(Class, boolean)
	 * @deprecated since 5.2 in favor of the factory method {@link AnnotationMetadata#introspect(Class)}
	 */
	@Deprecated
	public StandardAnnotationMetadata(Class<?> introspectedClass) {
		this(introspectedClass, false);
	}

	/**
	 * 构造方法——包装 Class 对象并创建 MergedAnnotations。
	 * <p>SearchStrategy.INHERITED_ANNOTATIONS：搜索当前类 + 父类继承的注解（不搜索接口）。
	 * <p>RepeatableContainers.none()：不处理可重复注解容器（如 @PropertySources）。
	 * @param introspectedClass 要内省的类
	 * @param nestedAnnotationsAsMap 嵌套注解是否转为 Map（推荐 true）
	 * <hr>
	 *
	 * Create a new {@link StandardAnnotationMetadata} wrapper for the given Class,
	 * providing the option to return any nested annotations or annotation arrays in the
	 * form of {@link org.springframework.core.annotation.AnnotationAttributes} instead
	 * of actual {@link Annotation} instances.
	 * @param introspectedClass the Class to introspect
	 * @param nestedAnnotationsAsMap return nested annotations and annotation arrays as
	 * {@link org.springframework.core.annotation.AnnotationAttributes} for compatibility
	 * with ASM-based {@link AnnotationMetadata} implementations
	 * @since 3.1.1
	 * @deprecated since 5.2 in favor of the factory method {@link AnnotationMetadata#introspect(Class)}.
	 * Use {@link MergedAnnotation#asMap(org.springframework.core.annotation.MergedAnnotation.Adapt...) MergedAnnotation.asMap}
	 * from {@link #getAnnotations()} rather than {@link #getAnnotationAttributes(String)}
	 * if {@code nestedAnnotationsAsMap} is {@code false}
	 */
	@Deprecated
	public StandardAnnotationMetadata(Class<?> introspectedClass, boolean nestedAnnotationsAsMap) {
		super(introspectedClass);
		this.mergedAnnotations = MergedAnnotations.from(introspectedClass,
				SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none());
		this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
	}


	// =====================================================================================
	// 一、AnnotatedTypeMetadata 的核心实现——返回注解数据源
	// =====================================================================================

	@Override
	public MergedAnnotations getAnnotations() {
		return this.mergedAnnotations;
	}

	/**
	 * 【缓存优化】注解类型名集合只计算一次，后续直接返回缓存。
	 * <p>委托给父接口的 default 实现（遍历 MergedAnnotations 流），然后包装成不可变集合缓存。
	 */
	@Override
	public Set<String> getAnnotationTypes() {
		Set<String> annotationTypes = this.annotationTypes;
		if (annotationTypes == null) {
			annotationTypes = Collections.unmodifiableSet(AnnotationMetadata.super.getAnnotationTypes());
			this.annotationTypes = annotationTypes;
		}
		return annotationTypes;
	}

	// =====================================================================================
	// 二、注解属性读取——根据 nestedAnnotationsAsMap 选择不同策略
	// =====================================================================================

	/**
	 * 【策略分叉】根据 nestedAnnotationsAsMap 决定走哪条路径：
	 * <ul>
	 * <li>true → 走 AnnotationMetadata 接口的 default 实现（MergedAnnotations 路径，返回 Map）</li>
	 * <li>false → 走 AnnotatedElementUtils（原生反射路径，嵌套注解返回 Annotation 实例）</li>
	 * </ul>
	 */
	@Override
	@Nullable
	public Map<String, Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		if (this.nestedAnnotationsAsMap) {
			return AnnotationMetadata.super.getAnnotationAttributes(annotationName, classValuesAsString);
		}
		return AnnotatedElementUtils.getMergedAnnotationAttributes(
				getIntrospectedClass(), annotationName, classValuesAsString, false);
	}

	/** 同上策略分叉，批量版本 */
	@Override
	@Nullable
	public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		if (this.nestedAnnotationsAsMap) {
			return AnnotationMetadata.super.getAllAnnotationAttributes(annotationName, classValuesAsString);
		}
		return AnnotatedElementUtils.getAllAnnotationAttributes(
				getIntrospectedClass(), annotationName, classValuesAsString, false);
	}

	// =====================================================================================
	// 三、方法注解查询——反射路径的实现
	// =====================================================================================

	/**
	 * 【快速判断是否有被注解标注的方法】
	 * <p>优化：先用 isCandidateClass 做快速排除（如果类名/包名与注解不匹配，直接返回 false）。
	 * <p>然后遍历 getDeclaredMethods()，找到第一个匹配就返回 true——短路求值。
	 */
	@Override
	public boolean hasAnnotatedMethods(String annotationName) {
		if (AnnotationUtils.isCandidateClass(getIntrospectedClass(), annotationName)) {
			try {
				Method[] methods = ReflectionUtils.getDeclaredMethods(getIntrospectedClass());
				for (Method method : methods) {
					if (isAnnotatedMethod(method, annotationName)) {
						return true;
					}
				}
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex);
			}
		}
		return false;
	}

	/**
	 * 【获取所有被注解标注的方法元数据】
	 * <p>遍历 getDeclaredMethods()，为每个匹配方法创建 StandardMethodMetadata 包装。
	 * <p>过滤桥接方法（bridge）——桥接方法是编译器生成的，不是用户写的，不应该被处理。
	 */
	@Override
	@SuppressWarnings("deprecation")
	public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
		Set<MethodMetadata> annotatedMethods = null;
		if (AnnotationUtils.isCandidateClass(getIntrospectedClass(), annotationName)) {
			try {
				Method[] methods = ReflectionUtils.getDeclaredMethods(getIntrospectedClass());
				for (Method method : methods) {
					if (isAnnotatedMethod(method, annotationName)) {
						if (annotatedMethods == null) {
							annotatedMethods = new LinkedHashSet<>(4);
						}
						// 为每个匹配方法创建 StandardMethodMetadata，传递 nestedAnnotationsAsMap 保持一致
						annotatedMethods.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap));
					}
				}
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex);
			}
		}
		return (annotatedMethods != null ? annotatedMethods : Collections.emptySet());
	}


	/**
	 * 【方法注解判断的三重过滤】
	 * 1. 不是桥接方法（bridge method 是泛型擦除产生的，跳过）
	 * 2. 方法上至少有一个注解（快速排除无注解方法）
	 * 3. 通过 AnnotatedElementUtils 判断是否有指定注解（支持元注解穿透）
	 */
	private static boolean isAnnotatedMethod(Method method, String annotationName) {
		return !method.isBridge() && method.getAnnotations().length > 0 &&
				AnnotatedElementUtils.isAnnotated(method, annotationName);
	}

	/**
	 * 【包级别工厂方法】由 AnnotationMetadata.introspect(Class) 调用。
	 * <p>固定 nestedAnnotationsAsMap=true，保持与 ASM 路径的行为一致。
	 */
	static AnnotationMetadata from(Class<?> introspectedClass) {
		return new StandardAnnotationMetadata(introspectedClass, true);
	}

}
