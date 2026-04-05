/*
 * Copyright 2002-2024 the original author or authors.
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

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Supplier;

import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>{@link ApplicationStartup} 的"空操作"默认实现——什么都不记录，零开销！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.metrics.DefaultApplicationStartup}</li>
 * <li><b>中文名</b>：默认启动度量器 —— "假码表"（按了没反应，但也不会报错）</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.metrics} 包</li>
 * <li><b>可见性</b>：包级私有（package-private）！外部不能直接 new，只能通过
 *     {@link ApplicationStartup#DEFAULT} 常量获取</li>
 * </ul>
 *
 * <h3>💡 设计模式：空对象模式 (Null Object Pattern)</h3>
 * <p>不需要到处写 {@code if (applicationStartup != null)}，直接调用即可。
 * 空对象吃掉所有调用，返回固定的单例 DefaultStartupStep。</p>
 *
 * <h3>🔑 关键实现细节：</h3>
 * <ul>
 * <li>整个类只有一个 static final 单例 {@code DEFAULT_STARTUP_STEP}——所有 start() 都返回同一个对象</li>
 * <li>DefaultStartupStep 的 tag() 返回 this（链式调用不报错但不记录）</li>
 * <li>end() 是空方法</li>
 * <li>getId() 永远返回 0，getParentId() 永远返回 null</li>
 * <li>getTags() 返回空迭代器</li>
 * </ul>
 *
 * <p>Default "no op" {@code ApplicationStartup} implementation.
 *
 * <p>This variant is designed for minimal overhead and does not record events.
 *
 * @author Brian Clozel
 */
class DefaultApplicationStartup implements ApplicationStartup {

	/** 全局唯一的空步骤单例——所有 start() 调用都返回这同一个对象 */
	private static final DefaultStartupStep DEFAULT_STARTUP_STEP = new DefaultStartupStep();

	/**
	 * 无论传什么 name，都返回同一个空步骤。
	 * <p>💡 这就是零开销的秘密：没有对象分配、没有时间记录、没有任何副作用。
	 */
	@Override
	public DefaultStartupStep start(String name) {
		return DEFAULT_STARTUP_STEP;
	}


	/**
	 * 空步骤实现——{@link StartupStep} 的 Null Object。
	 * <p>所有方法都是固定返回值或空操作，不记录任何数据。
	 */
	static class DefaultStartupStep implements StartupStep {

		/** 空标签集合单例 */
		private final DefaultTags TAGS = new DefaultTags();

		@Override
		public String getName() {
			return "default";  // ← 固定名称，无意义
		}

		@Override
		public long getId() {
			return 0L;  // ← 固定 ID = 0
		}

		@Override
		@Nullable
		public Long getParentId() {
			return null;  // ← 没有父步骤
		}

		@Override
		public Tags getTags() {
			return this.TAGS;
		}

		/** tag() 直接返回 this，不记录——链式调用安全但无效 */
		@Override
		public StartupStep tag(String key, String value) {
			return this;
		}

		/**
		 * 延迟求值版本也直接返回 this。
		 * <p>💡 注意：这里<b>没有调用 value.get()</b>！
		 * 如果你的 tag value 计算很重（比如 toString 很贵），
		 * 用 Supplier 版本 + 默认 no-op 策略 = 连 value 都不会算，真正的零开销。
		 */
		@Override
		public StartupStep tag(String key, Supplier<String> value) {
			return this;
		}

		/** 空操作——码表本来就没开始，也不需要结束 */
		@Override
		public void end() {
		}


		/** 空标签集合——迭代器直接返回 {@link Collections#emptyIterator()} */
		static class DefaultTags implements StartupStep.Tags {

			@Override
			public Iterator<StartupStep.Tag> iterator() {
				return Collections.emptyIterator();
			}
		}
	}

}
