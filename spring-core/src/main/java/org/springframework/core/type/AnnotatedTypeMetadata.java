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

import java.lang.annotation.Annotation;
import java.util.Map;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotationCollectors;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotationSelectors;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>注解信息的"统一读取器"——不管是类上的注解还是方法上的注解，都用同一套 API 来读！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.AnnotatedTypeMetadata}</li>
 * <li><b>中文名</b>：被注解的类型元数据 —— 注解世界的"通用翻译官"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type} 包
 * <br/>
 * （{@code type} 包 = <b>类型元数据抽象层</b>，定义"不加载类就能读取类/方法/注解信息"的全部契约）</li>
 * <li><b>接口层级</b>：顶层接口，<b>1 个抽象方法 + 4 个 default 方法</b>——定义了注解的"读取和查询"能力</li>
 * </ul>
 *
 * <h3>💡 为什么需要 AnnotatedTypeMetadata？——"注解读取的统一抽象"</h3>
 * <p>
 * Spring 中需要读取注解的地方太多了：
 * </p>
 * <ul>
 * <li><b>类级别</b>：@Component/@Service/@Configuration —— 决定是否注册 BD</li>
 * <li><b>方法级别</b>：@Bean/@EventListener/@Transactional —— 决定方法如何被处理</li>
 * </ul>
 * <p>
 * 这两种场景的注解读取逻辑<b>完全相同</b>：判断注解是否存在、获取注解属性、处理元注解（meta-annotation）。<br/>
 * 如果不抽象，类和方法各写一套注解读取逻辑——代码翻倍、维护翻倍。
 * </p>
 * <p>
 * AnnotatedTypeMetadata 就是这个<b>"统一抽象"</b>：
 * </p>
 * <ul>
 * <li>{@link AnnotationMetadata} 继承它 → 读取<b>类</b>上的注解</li>
 * <li>{@link MethodMetadata} 继承它 → 读取<b>方法</b>上的注解</li>
 * </ul>
 * <p>
 * 两者共享同一套 API，调用方不需要关心底层是类还是方法。
 * </p>
 *
 * <h3>🔑 核心设计：MergedAnnotations——注解的"终极合并引擎"</h3>
 * <p>
 * Spring 5.2 引入了 {@link MergedAnnotations}，替代了之前零散的注解工具方法。<br/>
 * 本接口只有<b>一个</b>抽象方法 {@link #getAnnotations()}，所有其他方法都是基于它的 default 实现。<br/>
 * 这就是<b>"模板方法"的变体</b>——用 default 方法替代抽象类，实现者只需提供数据源（MergedAnnotations），
 * 所有查询逻辑自动获得。
 * </p>
 *
 * <h3>🧬 继承体系定位</h3>
 * 
 * <pre>
 *              AnnotatedTypeMetadata              ← 你在这里！注解读取的统一抽象
 *              (注解信息的通用读取器)
 *               ↗                ↘
 *      AnnotationMetadata      MethodMetadata
 *      (类级别：类结构+注解)    (方法级别：方法信息+注解)
 * </pre>
 *
 * <h3>⚡ 与 ClassMetadata 的分工</h3>
 * <p>
 * {@link ClassMetadata} 负责<b>结构信息</b>（类名/是否接口/父类等），
 * AnnotatedTypeMetadata 负责<b>注解信息</b>（有没有某注解/注解的属性值等）。<br/>
 * 二者在 {@link AnnotationMetadata} 中汇合——类的完整画像 = 结构 + 注解。
 * </p>
 * <hr>
 * </>
 *
 * Defines access to the annotations of a specific type
 * ({@link AnnotationMetadata class}
 * or {@link MethodMetadata method}), in a form that does not necessarily
 * require the
 * class-loading.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Mark Pollack
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 4.0
 * @see AnnotationMetadata
 * @see MethodMetadata
 */
public interface AnnotatedTypeMetadata {

	// =====================================================================================
	// 一、数据源——唯一的抽象方法，所有能力的根基
	// =====================================================================================

	/**
	 * 【核心数据源】返回当前元素（类或方法）上的所有注解的合并视图。
	 * <p>
	 * 这是本接口<b>唯一的抽象方法</b>——所有其他 default 方法都基于它实现。
	 * <p>
	 * {@link MergedAnnotations} 是 Spring 5.2 引入的"注解合并引擎"，它能：
	 * <ul>
	 * <li>处理<b>元注解</b>：@Service 标注了 @Component → isPresent("Component") ==
	 * true</li>
	 * <li>处理<b>属性覆盖</b>：@AliasFor 让子注解属性能覆盖父注解属性</li>
	 * <li>处理<b>可重复注解</b>：@PropertySource 可以写多个</li>
	 * </ul>
	 * <p>
	 * <b>两条路径的数据源不同</b>：
	 * <ul>
	 * <li>反射路径（StandardAnnotationMetadata）→ MergedAnnotations.from(Class)</li>
	 * <li>ASM 路径（SimpleAnnotationMetadata）→
	 * MergedAnnotations.of(List&lt;MergedAnnotation&gt;)</li>
	 * </ul>
	 * <hr>
	 * </>
	 *
	 * Return annotation details based on the direct annotations of the
	 * underlying element.
	 * 
	 * @return merged annotations based on the direct annotations
	 * @since 5.2
	 */
	MergedAnnotations getAnnotations();

	// =====================================================================================
	// 二、注解存在性判断——"这个注解有没有？"
	// =====================================================================================

	/**
	 * 【判断注解是否存在】支持<b>元注解穿透</b>。
	 * <p>
	 * 例如：类上标注了 @Service，而 @Service 标注了 @Component，则：
	 * <ul>
	 * <li>isAnnotated("Service") → true（直接存在）</li>
	 * <li>isAnnotated("Component") → true（元注解穿透！）</li>
	 * </ul>
	 * <p>
	 * <b>关键调用点</b>：@Conditional 条件判断就用这个方法来检查条件注解是否满足。
	 * 
	 * @param annotationName 注解的全限定名（如 "org.springframework.stereotype.Component"）
	 *                       <hr>
	 *
	 *                       Determine whether the underlying element has an
	 *                       annotation or meta-annotation
	 *                       of the given type defined.
	 *                       <p>
	 *                       If this method returns {@code true}, then
	 *                       {@link #getAnnotationAttributes} will return a non-null
	 *                       Map.
	 * @param annotationName the fully qualified class name of the annotation
	 *                       type to look for
	 * @return whether a matching annotation is defined
	 */
	default boolean isAnnotated(String annotationName) {
		return getAnnotations().isPresent(annotationName);
	}

	// =====================================================================================
	// 三、单注解属性读取——"这个注解的属性值是什么？"
	// =====================================================================================

	/**
	 * 【读取注解属性】获取指定注解的属性 Map（支持元注解穿透 + 属性覆盖）。
	 * <p>
	 * 返回 Map 的 key 是属性名（如 "value"），value 是属性值。
	 * <p>
	 * 如果注解不存在，返回 null。
	 * <p>
	 * <b>使用场景</b>：ConfigurationClassParser 用它读取 @ComponentScan 的 basePackages 等属性。
	 * 参数: annotationName 注解的全限定名
	 * <hr>
	 *
	 * Retrieve the attributes of the annotation of the given type, if any (i.e. if
	 * defined on the underlying element, as direct annotation or meta-annotation),
	 * also taking attribute overrides on composed annotations into account.
	 * 
	 * @param annotationName the fully qualified class name of the annotation
	 *                       type to look for
	 * @return a Map of attributes, with the attribute name as key (e.g. "value")
	 *         and the defined attribute value as Map value. This return value will
	 *         be
	 *         {@code null} if no matching annotation is defined.
	 */
	@Nullable
	default Map<String, Object> getAnnotationAttributes(String annotationName) {
		return getAnnotationAttributes(annotationName, false);
	}

	/**
	 * 【读取注解属性（可选字符串化）】
	 * <p>
	 * 与上面的重载区别在于 classValuesAsString 参数：
	 * <ul>
	 * <li>false（默认）：Class 类型的属性返回 Class 对象 → <b>会触发类加载！</b></li>
	 * <li>true：Class 类型的属性返回 String（类全限定名）→ <b>不触发类加载</b></li>
	 * </ul>
	 * <p>
	 * <b>为什么需要 classValuesAsString？</b>
	 * <p>
	 * ASM 路径读取 .class 文件时，注解属性里的 Class 值（如 @Import(MyConfig.class)）
	 * 只能拿到字符串形式的类名。如果强制转成 Class 对象，就会触发类加载——违背了 ASM 路径"不加载"的初衷。<br/>
	 * 所以 classValuesAsString=true 保持字符串，实现反射路径和 ASM 路径的<b>行为一致性</b>。
	 * 
	 * @param annotationName      注解的全限定名
	 * @param classValuesAsString 是否将 Class 类型属性值转为 String
	 *                            <hr>
	 *                            </>
	 *
	 *                            Retrieve the attributes of the annotation of the
	 *                            given type, if any (i.e. if
	 *                            defined on the underlying element, as direct
	 *                            annotation or meta-annotation),
	 *                            also taking attribute overrides on composed
	 *                            annotations into account.
	 * @param annotationName      the fully qualified class name of the annotation
	 *                            type to look for
	 * @param classValuesAsString whether to convert class references to String
	 *                            class names for exposure as values in the returned
	 *                            Map, instead of Class
	 *                            references which might potentially have to be
	 *                            loaded first
	 * @return a Map of attributes, with the attribute name as key (e.g. "value")
	 *         and the defined attribute value as Map value. This return value will
	 *         be
	 *         {@code null} if no matching annotation is defined.
	 */
	@Nullable
	default Map<String, Object> getAnnotationAttributes(String annotationName,
			boolean classValuesAsString) {

		// 从合并注解集合中查找 → 优先取"第一个直接声明的"（firstDirectlyDeclared）
		// 这保证了：如果同一个注解通过多条元注解路径出现，优先用直接标注的那个
		MergedAnnotation<Annotation> annotation = getAnnotations().get(annotationName,
				null, MergedAnnotationSelectors.firstDirectlyDeclared());
		if (!annotation.isPresent()) {
			return null;
		}
		// Adapt.values(classValuesAsString, true)：
		// 第一个参数：是否把 Class 转 String
		// 第二个参数（true）：是否把嵌套注解转为 AnnotationAttributes Map（为了与 ASM 路径兼容）
		return annotation.asAnnotationAttributes(Adapt.values(classValuesAsString, true));
	}

	// =====================================================================================
	// 四、全量注解属性读取——"同名注解出现了多次，把所有的属性都给我"
	// =====================================================================================

	/**
	 * 【读取所有同名注解的全部属性】
	 * <p>
	 * 当一个注解通过多条元注解路径出现时（比如 @Component 被 @Service 和 @Controller 分别传递），
	 * 这个方法会把<b>每一次出现</b>的属性都收集起来，返回 MultiValueMap。
	 * <p>
	 * 注意：这个方法<b>不</b>做属性覆盖合并——每个出现保持各自的原始属性值。
	 * 
	 * @param annotationName 注解的全限定名
	 *                       <hr>
	 *
	 *                       Retrieve all attributes of all annotations of the given
	 *                       type, if any (i.e. if
	 *                       defined on the underlying element, as direct annotation
	 *                       or meta-annotation).
	 *                       Note that this variant does <i>not</i> take attribute
	 *                       overrides into account.
	 * @param annotationName the fully qualified class name of the annotation
	 *                       type to look for
	 * @return a MultiMap of attributes, with the attribute name as key (e.g.
	 *         "value")
	 *         and a list of the defined attribute values as Map value. This return
	 *         value will
	 *         be {@code null} if no matching annotation is defined.
	 * @see #getAllAnnotationAttributes(String, boolean)
	 */
	@Nullable
	default MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName) {
		return getAllAnnotationAttributes(annotationName, false);
	}

	/**
	 * 【读取所有同名注解的全部属性（可选字符串化）】
	 * <p>
	 * 收集所有元注解路径上该注解的属性，通过 metaTypes 去重（同一条路径只取一次）。
	 * <p>
	 * 使用 withNonMergedAttributes() 保持原始属性值，不做合并覆盖。
	 * 
	 * @param annotationName      注解的全限定名
	 * @param classValuesAsString 是否将 Class 类型属性值转为 String
	 *                            <hr>
	 *                            </>
	 *
	 *                            Retrieve all attributes of all annotations of the
	 *                            given type, if any (i.e. if
	 *                            defined on the underlying element, as direct
	 *                            annotation or meta-annotation).
	 *                            Note that this variant does <i>not</i> take
	 *                            attribute overrides into account.
	 * @param annotationName      the fully qualified class name of the annotation
	 *                            type to look for
	 * @param classValuesAsString whether to convert class references to String
	 * @return a MultiMap of attributes, with the attribute name as key (e.g.
	 *         "value")
	 *         and a list of the defined attribute values as Map value. This return
	 *         value will
	 *         be {@code null} if no matching annotation is defined.
	 * @see #getAllAnnotationAttributes(String)
	 */
	@Nullable
	default MultiValueMap<String, Object> getAllAnnotationAttributes(
			String annotationName, boolean classValuesAsString) {

		Adapt[] adaptations = Adapt.values(classValuesAsString, true);
		return getAnnotations().stream(annotationName)
				// 通过元注解路径（metaTypes）去重——同一条路径上的同名注解只保留一个
				.filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
				// withNonMergedAttributes：不做属性合并，保持每个注解的原始属性
				.map(MergedAnnotation::withNonMergedAttributes)
				// 收集到 MultiValueMap：key=属性名, value=所有出现的值列表
				.collect(MergedAnnotationCollectors.toMultiValueMap(map -> map.isEmpty() ? null : map, adaptations));
	}

}
