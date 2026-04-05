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

package org.springframework.core.metrics.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>JFR 事件载体——启动步骤在 Java Flight Recorder 中的"数据行"！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.metrics.jfr.FlightRecorderStartupEvent}</li>
 * <li><b>中文名</b>：飞行记录器启动事件 —— JFR 的"一条记录"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.metrics.jfr} 子包</li>
 * <li><b>继承</b>：{@code jdk.jfr.Event} —— JDK Flight Recorder 的事件基类</li>
 * <li><b>可见性</b>：包级私有——只由 {@link FlightRecorderStartupStep} 内部使用</li>
 * </ul>
 *
 * <h3>💡 JFR Event 的工作原理（30 秒速览）：</h3>
 * <ol>
 * <li>继承 {@code jdk.jfr.Event}，声明 public 字段 = JFR 的"列"</li>
 * <li>{@code @Category} = JMC 中的分组目录，{@code @Label} = 列的显示名</li>
 * <li>生命周期：{@code begin()} → 干活 → {@code end()} → {@code shouldCommit()} → {@code commit()}</li>
 * <li>JFR 自动计算 begin~end 之间的 duration（纳秒精度）</li>
 * </ol>
 *
 * <h3>🔑 为什么 Tags 要序列化成 String？</h3>
 * <p>JFR Event 的字段只能是基本类型 + String，不支持 Map/List/自定义对象。
 * 所以 {@link FlightRecorderStartupStep#end()} 在提交前把 Tags 拼成
 * {@code "beanName=orderService,count=42,"} 这种字符串塞进来。</p>
 *
 * <h3>🖥️ 在 JDK Mission Control (JMC) 中看到的效果：</h3>
 * <pre>
 * ┌─ Spring Application ─────────────────────────────────────────┐
 * │  Startup Step                                                │
 * │  ┌─────────┬──────────────────────────────┬─────────┬──────┐ │
 * │  │ eventId │ name                         │ tags    │ dur  │ │
 * │  ├─────────┼──────────────────────────────┼─────────┼──────┤ │
 * │  │ 1       │ spring.context.refresh       │         │ 1.2s │ │
 * │  │ 2       │ spring.beans.instantiate     │ bean=.. │ 50ms │ │
 * │  └─────────┴──────────────────────────────┴─────────┴──────┘ │
 * └──────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>{@link Event} extension for recording {@link FlightRecorderStartupStep}
 * in Java Flight Recorder.
 *
 * <p>{@link org.springframework.core.metrics.StartupStep.Tags} are serialized
 * as a single {@code String}, since Flight Recorder events do not support complex types.
 *
 * @author Brian Clozel
 * @since 5.3
 */
@Category("Spring Application")   // ← JMC 中的分组目录名
@Label("Startup Step")             // ← JMC 中的事件类型名
@Description("Spring Application Startup")
class FlightRecorderStartupEvent extends Event {

	/** 步骤唯一 ID（对应 StartupStep.getId()） */
	public final long eventId;

	/** 父步骤 ID（对应 StartupStep.getParentId()） */
	public final long parentId;

	/** 步骤名称，如 "spring.context.refresh" */
	@Label("Name")
	public final String name;

	/**
	 * 序列化后的标签字符串，格式："key1=value1,key2=value2,"
	 * <p>💡 不是 final 的！因为 tags 在 end() 时才拼好塞进来。
	 */
	@Label("Tags")
	String tags = "";

	public FlightRecorderStartupEvent(long eventId, String name, long parentId) {
		this.name = name;
		this.eventId = eventId;
		this.parentId = parentId;
	}

	public void setTags(String tags) {
		this.tags = tags;
	}

}
