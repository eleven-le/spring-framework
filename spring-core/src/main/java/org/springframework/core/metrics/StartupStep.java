/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core.metrics;

import java.util.function.Supplier;

import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>启动阶段的"秒表"——记录容器启动过程中每一个阶段/动作的度量步骤！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.metrics.StartupStep}</li>
 * <li><b>中文名</b>：启动步骤 —— 启动过程的"计时码表"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.metrics} 包
 * （注意！{@code metrics} 包 = 框架内部度量/可观测性契约！
 * 这个包只放"启动度量"相关的接口和默认实现，<b>不依赖任何上层模块</b>，
 * 让 spring-beans/spring-context 都能直接用。
 * 一句话：<b>metrics 包定义了"容器启动过程怎么被观测"的所有契约！</b>）</li>
 * <li><b>接口层级</b>：顶层独立接口，由 {@link ApplicationStartup#start(String)} 工厂方法创建</li>
 * </ul>
 *
 * <h3>💡 为什么需要 StartupStep？——"容器启动的黑盒终于能照进光了"！</h3>
 * <p>Spring 容器启动过程涉及几十个阶段（refresh 12 步、BeanDefinition 扫描、BPP 执行、Bean 创建……），
 * 但之前完全是黑盒——到底哪一步慢了？哪一步卡住了？无从得知。<br/>
 * 5.3 引入 StartupStep，让容器可以在每个关键阶段打"计时码表"：</p>
 * <pre>
 * // 在 AbstractApplicationContext.refresh() 中实际使用：
 * StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");
 * ... // 执行 refresh 逻辑
 * contextRefresh.end();   // ← 码表停止，记录耗时
 *
 * // 在 AbstractBeanFactory.doGetBean() 中：
 * StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate");
 * beanCreation.tag("beanName", beanName);  // ← 附加元数据
 * ... // 创建 Bean
 * beanCreation.end();
 * </pre>
 *
 * <h3>🔑 核心设计思路：</h3>
 * <ol>
 * <li><b>生命周期三段式</b>：start() 创建 → tag() 打标 → end() 结束，简单到不能再简单</li>
 * <li><b>父子嵌套</b>：通过 {@link #getParentId()} 形成树状结构，能还原调用层级</li>
 * <li><b>零成本抽象</b>：默认实现 {@link DefaultApplicationStartup} 是完全空操作(no-op)，
 *     生产环境不开启时零开销</li>
 * <li><b>策略模式</b>：想用 JFR 记录？换成 {@code FlightRecorderApplicationStartup} 即可，
 *     Spring Boot Actuator 的 /startup 端点用的是 {@code BufferingApplicationStartup}</li>
 * </ol>
 *
 * <h3>📍 框架内已埋点的关键步骤名（6 个核心码表）：</h3>
 * <pre>
 * ┌──────────────────────────────────────────────┬───────────────────────────────────────────┐
 * │ 步骤名 (name)                                │ 埋点位置                                   │
 * ├──────────────────────────────────────────────┼───────────────────────────────────────────┤
 * │ spring.context.refresh                       │ AbstractApplicationContext.refresh()       │
 * │ spring.context.beans.post-process            │ refresh → invokeBFPP/registerBPP          │
 * │ spring.context.beandef-registry.post-process │ PostProcessorRegistrationDelegate         │
 * │ spring.context.config-classes.parse          │ ConfigurationClassPostProcessor           │
 * │ spring.context.config-classes.enhance        │ ConfigurationClassPostProcessor (CGLIB)   │
 * │ spring.beans.instantiate                     │ AbstractBeanFactory.doGetBean()            │
 * └──────────────────────────────────────────────┴───────────────────────────────────────────┘
 * </pre>
 *
 * <h3>🏭 类族全景：</h3>
 * <pre>
 *                       «interface»
 *                     ApplicationStartup          ← 工厂：start(name) 生产 StartupStep
 *                      /          \
 *   DefaultApplicationStartup    FlightRecorderApplicationStartup
 *   (no-op, 零开销)              (JFR 实录)
 *
 *                       «interface»
 *                       StartupStep               ← 产品：计时码表
 *                      /          \
 *     DefaultStartupStep         FlightRecorderStartupStep
 *     (空实现，啥也不记)          (委托 JFR Event 记录)
 *
 *                       «interface»
 *                    StartupStep.Tags              ← 元数据容器
 *                    StartupStep.Tag               ← 单条 key=value 元数据
 * </pre>
 *
 * <h3>🎯 C 端业务映射：</h3>
 * <ul>
 * <li><b>线上排障</b>：开启 JFR 录制 → 看哪个 Bean 创建耗时最久 → 优化启动速度</li>
 * <li><b>Spring Boot /startup</b>：配合 BufferingApplicationStartup → Actuator 暴露 JSON →
 *     前端可视化启动火焰图</li>
 * </ul>
 *
 * <p>Step recording metrics about a particular phase or action happening during the {@link ApplicationStartup}.
 *
 * <p>The lifecycle of a {@code StartupStep} goes as follows:
 * <ol>
 * <li>the step is created and starts by calling {@link ApplicationStartup#start(String) the application startup}
 * and is assigned a unique {@link StartupStep#getId() id}.
 * <li>we can then attach information with {@link Tags} during processing
 * <li>we then need to mark the {@link #end()} of the step
 * </ol>
 *
 * <p>Implementations can track the "execution time" or other metrics for steps.
 *
 * @author Brian Clozel
 * @since 5.3
 */
public interface StartupStep {

	/**
	 * 返回步骤名称。
	 * <p>命名规范：用 "." 做命名空间分隔，例如 "spring.beans.instantiate"。
	 * 同一个名称可以在启动过程中多次出现（每个 Bean 创建都是一次 "spring.beans.instantiate"）。
	 *
	 * Return the name of the startup step.
	 * <p>A step name describes the current action or phase. This technical
	 * name should be "." namespaced and can be reused to describe other instances of
	 * similar steps during application startup.
	 */
	String getName();

	/**
	 * 返回此步骤在整个启动过程中的唯一 ID（单调递增）。
	 * <p>配合 {@link #getParentId()} 可以还原出树状调用层级。
	 *
	 * Return the unique id for this step within the application startup.
	 */
	long getId();

	/**
	 * 返回父步骤的 ID（如果有的话）。
	 * <p>"父步骤"= 创建当前步骤时，最近一个尚未 end() 的步骤。
	 * 这样就能还原出嵌套关系：refresh → beans.post-process → beans.instantiate。
	 *
	 * Return, if available, the id of the parent step.
	 * <p>The parent step is the step that was started the most recently
	 * when the current step was created.
	 */
	@Nullable
	Long getParentId();

	/**
	 * 给步骤打标签（立即求值版本）。
	 * <p>例如：{@code step.tag("beanName", "orderService")}
	 * <p>返回 this，支持链式调用：{@code step.tag("a","1").tag("b","2")}
	 *
	 * Add a {@link Tag} to the step.
	 * @param key tag key
	 * @param value tag value
	 */
	StartupStep tag(String key, String value);

	/**
	 * 给步骤打标签（延迟求值版本）。
	 * <p>当 value 的计算有开销时用 Supplier，no-op 实现会直接忽略不调用 Supplier.get()，
	 * 真正录制时才会触发求值——又一个零成本抽象！
	 *
	 * Add a {@link Tag} to the step.
	 * @param key tag key
	 * @param value {@link Supplier} for the tag value
	 */
	StartupStep tag(String key, Supplier<String> value);

	/**
	 * 获取此步骤的所有标签集合。
	 *
	 * Return the {@link Tag} collection for this step.
	 */
	Tags getTags();

	/**
	 * 结束此步骤——"码表停止"！
	 * <p>调用后实现类会记录耗时等指标。一旦 end()，步骤状态就冻结了，不能再 tag()。
	 *
	 * Record the state of the step and possibly other metrics like execution time.
	 * <p>Once ended, changes on the step state are not allowed.
	 */
	void end();


	/**
	 * 标签的不可变集合。
	 * <p>继承 {@link Iterable}，可以 for-each 遍历。
	 *
	 * Immutable collection of {@link Tag}.
	 */
	interface Tags extends Iterable<Tag> {
	}


	/**
	 * 单条元数据标签：key=value。
	 * <p>例如 beanName=orderService、count=42。
	 *
	 * Simple key/value association for storing step metadata.
	 */
	interface Tag {

		/**
		 * 标签键。
		 * Return the {@code Tag} name.
		 */
		String getKey();

		/**
		 * 标签值。
		 * Return the {@code Tag} value.
		 */
		String getValue();
	}

}
