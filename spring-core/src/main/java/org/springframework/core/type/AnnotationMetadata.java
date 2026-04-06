/*
 * Copyright 2002-2019 the original author or authors.
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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>类的"完整画像"——结构信息 + 注解信息 = 一个类的全部情报！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.AnnotationMetadata}</li>
 * <li><b>中文名</b>：注解元数据接口 —— 类的"全息档案"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type} 包
 *     <br/>（{@code type} 包 = <b>类型元数据抽象层</b>，Spring 包扫描的"情报中心"）</li>
 * <li><b>接口层级</b>：<b>双继承聚合</b>——同时继承 {@link ClassMetadata}（结构）和 {@link AnnotatedTypeMetadata}（注解）</li>
 * <li><b>方法数</b>：自身 6 个 default + 1 个抽象 + 1 个静态工厂，加上继承来的 = 类信息的"全家桶"</li>
 * </ul>
 *
 * <h3>💡 为什么 AnnotationMetadata 是 Spring 包扫描的"核心货币"？</h3>
 * <p>在 Spring 启动的扫描阶段，每一个 .class 文件都会被解析成一个 AnnotationMetadata 对象。<br/>
 * 它是 Spring 在"不加载类"的前提下，做出<b>所有决策</b>的信息基础：</p>
 * <ul>
 * <li><b>是否注册为 BD？</b>→ hasAnnotation("Component") / hasMetaAnnotation("Component")</li>
 * <li><b>是否是 @Configuration？</b>→ isAnnotated("Configuration") → 走 CGLIB 增强</li>
 * <li><b>有没有 @Bean 方法？</b>→ hasAnnotatedMethods("Bean") → 注册 @Bean 工厂方法</li>
 * <li><b>是否满足 @Conditional？</b>→ getAnnotationAttributes("Conditional") → 条件评估</li>
 * <li><b>有没有 @Import？</b>→ getAnnotationAttributes("Import") → 触发 Import 链</li>
 * </ul>
 * <p>可以说：<b>AnnotationMetadata 是 Spring 注解驱动编程模型的"信息基座"</b>。<br/>
 * 没有它，@Component 扫描、@Configuration 处理、@Conditional 判断全部无法工作。</p>
 *
 * <h3>🧬 继承体系——双线合一</h3>
 * <pre>
 *       ClassMetadata                AnnotatedTypeMetadata
 *      (结构信息：类名/接口/抽象)      (注解信息：有没有/属性值)
 *              ↘                    ↙
 *              AnnotationMetadata              ← 你在这里！两条线的交汇点
 *             (完整画像 = 结构 + 注解 + 方法注解)
 *              ↗                ↘
 * StandardAnnotationMetadata   SimpleAnnotationMetadata
 *   (反射路径：Class 对象)       (ASM 路径：.class 字节码)
 * </pre>
 *
 * <h3>🔀 两条实现路径——反射 vs ASM</h3>
 * <table border="1">
 * <tr><th></th><th>反射路径（Standard*）</th><th>ASM 路径（Simple*）</th></tr>
 * <tr><td><b>入口</b></td><td>AnnotationMetadata.introspect(Class)</td><td>MetadataReaderFactory.getMetadataReader(Resource)</td></tr>
 * <tr><td><b>实现类</b></td><td>StandardAnnotationMetadata</td><td>SimpleAnnotationMetadata</td></tr>
 * <tr><td><b>数据来源</b></td><td>java.lang.reflect（需要类已加载）</td><td>ASM ClassReader（直接读 .class 字节码）</td></tr>
 * <tr><td><b>适用场景</b></td><td>@Configuration 类（已加载后的深度处理）</td><td>包扫描（未加载的批量筛选）</td></tr>
 * <tr><td><b>性能</b></td><td>信息丰富但慢（触发类加载）</td><td>轻量但够用（不触发类加载）</td></tr>
 * </table>
 *
 * <h3>⚡ 核心矛盾：hasAnnotation vs hasMetaAnnotation</h3>
 * <ul>
 * <li>{@link #hasAnnotation} —— 只检查<b>直接标注</b>的注解（isDirectlyPresent）</li>
 * <li>{@link #hasMetaAnnotation} —— 检查通过<b>元注解传递</b>的注解（isMetaPresent）</li>
 * <li>{@link AnnotatedTypeMetadata#isAnnotated} —— <b>两者都检查</b>（isPresent = directly + meta）</li>
 * </ul>
 * <p>为什么要区分？因为有些场景只关心"你自己写了什么"（hasAnnotation），
 * 有些场景关心"你的注解的注解是什么"（hasMetaAnnotation），
 * 大多数场景两个都要（isAnnotated）。</p>
 * <hr>
 *
 * Interface that defines abstract access to the annotations of a specific
 * class, in a form that does not require that class to be loaded yet.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.5
 * @see StandardAnnotationMetadata
 * @see org.springframework.core.type.classreading.MetadataReader#getAnnotationMetadata()
 * @see AnnotatedTypeMetadata
 */
public interface AnnotationMetadata extends ClassMetadata, AnnotatedTypeMetadata {

	// =====================================================================================
	// 一、注解类型枚举——"你身上直接贴了哪些标签？"
	// =====================================================================================

	/**
	 * 【列出所有直接标注的注解类型】
	 * <p>只返回<b>直接标注</b>（isDirectlyPresent）的注解全限定名，不包含元注解。
	 * <p>例如：类上标注了 @Service 和 @Transactional，返回 {"...Service", "...Transactional"}。
	 * 不会返回 @Service 上面的 @Component（那是元注解）。
	 * <p><b>使用场景</b>：ConfigurationClassParser 遍历所有直接注解，逐个检查是否需要特殊处理。
	 * <hr>
	 *
	 * Get the fully qualified class names of all annotation types that
	 * are <em>present</em> on the underlying class.
	 * @return the annotation type names
	 */
	default Set<String> getAnnotationTypes() {
		return getAnnotations().stream()
				.filter(MergedAnnotation::isDirectlyPresent)
				.map(annotation -> annotation.getType().getName())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * 【查询某个注解上面的元注解】
	 * <p>给定一个注解名，返回这个注解自身被标注的所有元注解。
	 * <p>例如：getMetaAnnotationTypes("Service") → {"Component", "Indexed", ...}
	 * <p><b>注意</b>：这不是查当前类的元注解，而是查"当前类上的某个注解"的元注解。
	 * @param annotationName 注解的全限定名
	 * <hr>
	 *
	 * Get the fully qualified class names of all meta-annotation types that
	 * are <em>present</em> on the given annotation type on the underlying class.
	 * @param annotationName the fully qualified class name of the meta-annotation
	 * type to look for
	 * @return the meta-annotation type names, or an empty set if none found
	 */
	default Set<String> getMetaAnnotationTypes(String annotationName) {
		// 先从当前类的注解中找到指定的直接注解
		MergedAnnotation<?> annotation = getAnnotations().get(annotationName, MergedAnnotation::isDirectlyPresent);
		if (!annotation.isPresent()) {
			return Collections.emptySet();
		}
		// 然后对这个注解的 Class 做一次 MergedAnnotations 搜索，拿到它上面的所有注解
		return MergedAnnotations.from(annotation.getType(), SearchStrategy.INHERITED_ANNOTATIONS).stream()
				.map(mergedAnnotation -> mergedAnnotation.getType().getName())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	// =====================================================================================
	// 二、注解存在性判断——直接 vs 元注解
	// =====================================================================================

	/**
	 * 【判断是否直接标注了某个注解】只看 isDirectlyPresent。
	 * <p>例如：类标注了 @Service → hasAnnotation("Service")=true, hasAnnotation("Component")=false。
	 * <p>与 {@link AnnotatedTypeMetadata#isAnnotated} 的区别：isAnnotated 会穿透元注解，hasAnnotation 不会。
	 * @param annotationName 注解的全限定名
	 * <hr>
	 *
	 * Determine whether an annotation of the given type is <em>present</em> on
	 * the underlying class.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return {@code true} if a matching annotation is present
	 */
	default boolean hasAnnotation(String annotationName) {
		return getAnnotations().isDirectlyPresent(annotationName);
	}

	/**
	 * 【判断是否通过元注解间接拥有某个注解】只看 isMetaPresent。
	 * <p>例如：类标注了 @Service → hasMetaAnnotation("Component")=true（@Service→@Component）。
	 * <p>hasMetaAnnotation("Service")=false（@Service 是直接标注的，不是元注解传递的）。
	 * <p><b>使用场景</b>：ClassPathScanningCandidateComponentProvider 用 isAnnotated（= 直接+元注解）
	 * 来判断是否是 @Component 候选者，因为 @Service/@Repository/@Controller 都是通过元注解传递 @Component。
	 * @param metaAnnotationName 元注解的全限定名
	 * <hr>
	 *
	 * Determine whether the underlying class has an annotation that is itself
	 * annotated with the meta-annotation of the given type.
	 * @param metaAnnotationName the fully qualified class name of the
	 * meta-annotation type to look for
	 * @return {@code true} if a matching meta-annotation is present
	 */
	default boolean hasMetaAnnotation(String metaAnnotationName) {
		return getAnnotations().get(metaAnnotationName,
				MergedAnnotation::isMetaPresent).isPresent();
	}

	// =====================================================================================
	// 三、方法注解查询——"你的方法上有没有特定注解？"
	// =====================================================================================

	/**
	 * 【判断是否有被指定注解标注的方法】
	 * <p>例如：hasAnnotatedMethods("Bean") → 检查类里是否有 @Bean 方法。
	 * <p><b>使用场景</b>：ConfigurationClassUtils.checkConfigurationClassCandidate() 用它
	 * 来判断一个类是否是"lite @Configuration"（没标 @Configuration 但有 @Bean 方法）。
	 * @param annotationName 注解的全限定名
	 * <hr>
	 *
	 * Determine whether the underlying class has any methods that are
	 * annotated (or meta-annotated) with the given annotation type.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 */
	default boolean hasAnnotatedMethods(String annotationName) {
		return !getAnnotatedMethods(annotationName).isEmpty();
	}

	/**
	 * 【获取所有被指定注解标注的方法的元数据】——<b>唯一的抽象方法！</b>
	 * <p>返回 Set&lt;MethodMetadata&gt;，每个 MethodMetadata 包含方法名、返回类型、注解属性等。
	 * <p><b>两条路径的实现差异</b>：
	 * <ul>
	 * <li>StandardAnnotationMetadata：通过反射 getDeclaredMethods() + AnnotatedElementUtils 检查</li>
	 * <li>SimpleAnnotationMetadata：在 ASM 访问阶段已经收集好了 annotatedMethods 数组，这里只做过滤</li>
	 * </ul>
	 * <p><b>关键调用点</b>：ConfigurationClassParser 用它获取 @Bean 方法列表，然后为每个方法创建 BeanMethod。
	 * @param annotationName 注解的全限定名
	 * @return 匹配的方法元数据集合，空集合表示没有匹配方法
	 * <hr>
	 *
	 * Retrieve the method metadata for all methods that are annotated
	 * (or meta-annotated) with the given annotation type.
	 * <p>For any returned method, {@link MethodMetadata#isAnnotated} will
	 * return {@code true} for the given annotation type.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return a set of {@link MethodMetadata} for methods that have a matching
	 * annotation. The return value will be an empty set if no methods match
	 * the annotation type.
	 */
	Set<MethodMetadata> getAnnotatedMethods(String annotationName);


	// =====================================================================================
	// 四、静态工厂方法——反射路径的快捷入口
	// =====================================================================================

	/**
	 * 【静态工厂方法】通过反射创建 AnnotationMetadata 实例。
	 * <p>这是反射路径的<b>推荐入口</b>（5.2+ 推荐用这个，而不是直接 new StandardAnnotationMetadata）。
	 * <p>内部调用 StandardAnnotationMetadata.from(type)，设置 nestedAnnotationsAsMap=true
	 * 以保持与 ASM 路径的行为一致。
	 * <p><b>使用场景</b>：AnnotatedBeanDefinitionReader.register() 在手动注册 BD 时，
	 * 用这个方法创建类的 AnnotationMetadata。
	 * @param type 要内省的类
	 * @return AnnotationMetadata 实例
	 * <hr>
	 *
	 * Factory method to create a new {@link AnnotationMetadata} instance
	 * for the given class using standard reflection.
	 * @param type the class to introspect
	 * @return a new {@link AnnotationMetadata} instance
	 * @since 5.2
	 */
	static AnnotationMetadata introspect(Class<?> type) {
		return StandardAnnotationMetadata.from(type);
	}

}
