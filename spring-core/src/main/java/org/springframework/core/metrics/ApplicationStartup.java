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

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>启动度量的"码表工厂"——负责创建 {@link StartupStep} 的唯一入口！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.metrics.ApplicationStartup}</li>
 * <li><b>中文名</b>：应用启动度量器 —— 码表的"发放窗口"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.metrics} 包
 * （注意！{@code metrics} 包 = 框架内部度量/可观测性契约！
 * 只有 2 个顶层接口 + 1 个默认实现 + 1 个 jfr 子包。
 * 一句话：<b>metrics 包是"启动可观测"的最小契约层，放在 spring-core 让全框架零依赖使用！</b>）</li>
 * <li><b>接口层级</b>：顶层独立接口，只定义 <b>1 个方法</b>（start）</li>
 * </ul>
 *
 * <h3>💡 为什么需要 ApplicationStartup？——"策略模式的经典一招"！</h3>
 * <p>Spring 在启动过程中的关键节点会调用 {@code applicationStartup.start("xxx")} 来"领一块码表"，
 * 但框架<b>不关心</b>这块码表背后是空操作、JFR 录制、还是内存缓冲。<br/>
 * 这就是策略模式：<b>调用方只依赖接口，具体记录策略由使用者决定。</b></p>
 *
 * <h3>🔌 三种策略实现：</h3>
 * <pre>
 *                     «interface»
 *                   ApplicationStartup
 *                   /       |        \
 *    DefaultApplicationStartup   FlightRecorderApplicationStartup   BufferingApplicationStartup
 *    (no-op 空操作，默认)         (JDK Flight Recorder)              (Spring Boot 提供，内存缓冲)
 * </pre>
 *
 * <h3>📍 在容器中的传播路径：</h3>
 * <pre>
 * 1. AbstractApplicationContext 持有 applicationStartup 字段
 * 2. ConfigurableApplicationContext.setApplicationStartup() 设置策略
 * 3. AbstractApplicationContext.prepareBeanFactory() 时注入给 BeanFactory
 * 4. 各关键节点调用 applicationStartup.start("spring.xxx") 领码表
 * </pre>
 *
 * <p>Instruments the application startup phase using {@link StartupStep steps}.
 * <p>The core container and its infrastructure components can use the {@code ApplicationStartup}
 * to mark steps during the application startup and collect data about the execution context
 * or their processing time.
 *
 * @author Brian Clozel
 * @since 5.3
 */
public interface ApplicationStartup {

	/**
	 * 默认的 no-op 实现——单例常量。
	 * <p>生产环境默认就是这个，start() 返回的 StartupStep 什么都不做，
	 * tag()/end() 全是空方法，零开销。
	 * <p>💡 典型的"空对象模式"(Null Object Pattern)——不用判空，直接调！
	 *
	 * Default "no op" {@code ApplicationStartup} implementation.
	 * <p>This variant is designed for minimal overhead and does not record data.
	 */
	ApplicationStartup DEFAULT = new DefaultApplicationStartup();

	/**
	 * 创建一个新的启动步骤并标记其开始——"领一块码表，按下开始键"！
	 * <p>name 用 "." 分隔命名空间，如 "spring.beans.instantiate"。
	 * <p>同一个 name 可以被多次 start()（每创建一个 Bean 就 start 一次）。
	 *
	 * Create a new step and marks its beginning.
	 * <p>A step name describes the current action or phase. This technical
	 * name should be "." namespaced and can be reused to describe other instances of
	 * the same step during application startup.
	 * @param name the step name
	 */
	StartupStep start(String name);

}
