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

package org.springframework.core.metrics.jfr;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>{@link ApplicationStartup} 的 JFR 实现——把启动步骤录进 Java Flight Recorder！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.metrics.jfr.FlightRecorderApplicationStartup}</li>
 * <li><b>中文名</b>：飞行记录器启动度量器 —— "黑匣子码表工厂"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.metrics.jfr} 子包
 * （注意！{@code jfr} 子包 = JDK Flight Recorder 专属实现层！
 * 把 JFR 相关的 3 个类（ApplicationStartup/StartupStep/Event）隔离在子包里，
 * 不污染父包的纯契约层。需要 JDK 11+ 的 {@code jdk.jfr} 模块才能用。
 * 一句话：<b>jfr 子包是 metrics 契约的"JFR 驱动"，按需插拔！</b>）</li>
 * <li><b>可见性</b>：public——用户可以直接 new 然后设置给 ApplicationContext</li>
 * </ul>
 *
 * <h3>💡 为什么需要 JFR 实现？——"低开销 + 事后分析"的黄金组合！</h3>
 * <p>JFR 是 JDK 内置的诊断引擎，开销极低（< 1%），支持持续录制。
 * 配合这个实现，你可以：</p>
 * <ol>
 * <li>启动时加 JVM 参数：{@code -XX:StartFlightRecording:filename=recording.jfr,duration=10s}</li>
 * <li>事后用 JDK Mission Control (JMC) 打开 .jfr 文件</li>
 * <li>在 "Spring Application" 分类下看到每个启动步骤的耗时、标签、父子关系</li>
 * </ol>
 *
 * <h3>🔑 关键实现细节：</h3>
 * <ul>
 * <li><b>AtomicLong 序号生成器</b>：每次 start() 分配单调递增 ID（线程安全）</li>
 * <li><b>ConcurrentLinkedDeque 栈</b>：维护"当前活跃步骤"的嵌套关系，
 *     栈顶就是最近一个未结束的步骤 = 新步骤的 parentId</li>
 * <li><b>end() 回调</b>：步骤结束时从栈中移除自己，恢复父步骤为栈顶</li>
 * </ul>
 *
 * <h3>🎯 使用方式：</h3>
 * <pre>
 * // 在创建 ApplicationContext 之后、refresh 之前设置：
 * AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
 * ctx.setApplicationStartup(new FlightRecorderApplicationStartup());
 * ctx.register(AppConfig.class);
 * ctx.refresh();
 * // 然后用 JMC 打开录制文件即可看到启动步骤
 * </pre>
 *
 * <p>{@link ApplicationStartup} implementation for the Java Flight Recorder.
 * <p>This variant records {@link StartupStep} as Flight Recorder events. Because
 * such events only support base types, the
 * {@link org.springframework.core.metrics.StartupStep.Tags} are serialized as a
 * single String attribute.
 * <p>Once this is configured on the application context, you can record data by
 * launching the application with recording enabled:
 * {@code java -XX:StartFlightRecording:filename=recording.jfr,duration=10s -jar app.jar}.
 *
 * @author Brian Clozel
 * @since 5.3
 */
public class FlightRecorderApplicationStartup implements ApplicationStartup {

	/** 单调递增的步骤 ID 生成器（AtomicLong 保证线程安全） */
	private final AtomicLong currentSequenceId = new AtomicLong(0);

	/**
	 * "活跃步骤栈"——用 ConcurrentLinkedDeque 模拟栈结构。
	 * <p>每次 start() 把新 ID 压栈，end() 时弹出。
	 * 栈顶始终是"最近一个还没结束的步骤"，即新步骤的 parentId。
	 * <p>💡 为什么用 Deque 而不是 Stack？
	 * Stack 是 synchronized 的老古董，Deque 是 Java 6+ 推荐的栈结构，
	 * 而 ConcurrentLinkedDeque 还是无锁并发安全的。
	 */
	private final Deque<Long> currentSteps;


	public FlightRecorderApplicationStartup() {
		this.currentSteps = new ConcurrentLinkedDeque<>();
		// 压入 0 作为根节点（所有顶层步骤的 parent）
		this.currentSteps.offerFirst(this.currentSequenceId.get());
	}


	/**
	 * 创建 JFR 版本的启动步骤。
	 * <p>流程：
	 * <ol>
	 * <li>原子递增获取新 ID</li>
	 * <li>压入活跃栈</li>
	 * <li>取栈顶作为 parentId（此时栈顶是刚压入的自己，所以取的是"压入前的栈顶"——
	 *     但这里代码先 offerFirst 再 getFirst，拿到的其实是自己...
	 *     💡 实际上 parentId 是在构造 FlightRecorderStartupStep 时通过栈的第二个元素推断的，
	 *     这里的 getFirst() 拿到的是自己的 ID，因为 FlightRecorderStartupStep 构造器里
	 *     参数名叫 parentId 但传入的是当前 ID——实际的父子关系由 JFR 事件的时间嵌套来体现）</li>
	 * <li>注册 end() 回调：步骤结束时从栈中移除自己的 ID</li>
	 * </ol>
	 */
	@Override
	public StartupStep start(String name) {
		long sequenceId = this.currentSequenceId.incrementAndGet();
		this.currentSteps.offerFirst(sequenceId);
		return new FlightRecorderStartupStep(sequenceId, name,
				this.currentSteps.getFirst(), committedStep -> this.currentSteps.removeFirstOccurrence(sequenceId));
	}

}
