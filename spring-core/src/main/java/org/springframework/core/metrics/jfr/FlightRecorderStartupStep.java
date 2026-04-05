/*
 * Copyright 2012-2024 the original author or authors.
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

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.core.metrics.StartupStep;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>{@link StartupStep} 的 JFR 实现——每个步骤就是一个 JFR Event！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.metrics.jfr.FlightRecorderStartupStep}</li>
 * <li><b>中文名</b>：飞行记录器启动步骤 —— "真码表"（数据写进黑匣子）</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.metrics.jfr} 子包
 * （JFR 专属实现层，3 个类配合工作：ApplicationStartup → StartupStep → Event）</li>
 * <li><b>可见性</b>：包级私有（package-private）——只由 {@link FlightRecorderApplicationStartup} 创建</li>
 * </ul>
 *
 * <h3>💡 核心设计：委托模式</h3>
 * <p>这个类本身<b>不继承 JFR Event</b>，而是持有一个 {@link FlightRecorderStartupEvent} 字段，
 * 把 JFR 的 begin()/end()/commit() 生命周期委托给它。
 * <br/>为什么不直接继承？因为 StartupStep 是接口，Java 不支持同时继承 Event + 实现接口的复杂场景，
 * 用组合更灵活。</p>
 *
 * <h3>🔑 end() 是灵魂方法，做了 4 件事：</h3>
 * <ol>
 * <li>{@code event.end()} —— 标记 JFR 事件结束（JFR 会自动算出 duration）</li>
 * <li>{@code event.shouldCommit()} —— JFR 的过滤机制（配置了 threshold 的事件如果太短会被丢弃）</li>
 * <li>序列化 Tags 为 "key=value,key=value," 格式（JFR 不支持复杂类型，只能拼字符串）</li>
 * <li>{@code event.commit()} —— 真正写入 JFR 缓冲区</li>
 * <li>{@code recordingCallback} —— 通知 ApplicationStartup 从活跃栈中移除自己</li>
 * </ol>
 *
 * <p>{@link StartupStep} implementation for the Java Flight Recorder.
 *
 * <p>This variant delegates to a {@link FlightRecorderStartupEvent JFR event extension}
 * to collect and record data in Java Flight Recorder.
 *
 * @author Brian Clozel
 */
class FlightRecorderStartupStep implements StartupStep {

	/** 委托的 JFR 事件对象——真正的数据载体 */
	private final FlightRecorderStartupEvent event;

	/** 标签集合（append-only 数组） */
	private final FlightRecorderTags tags = new FlightRecorderTags();

	/**
	 * end() 时的回调——由 {@link FlightRecorderApplicationStartup} 注入，
	 * 负责从活跃步骤栈中移除当前步骤 ID
	 */
	private final Consumer<FlightRecorderStartupStep> recordingCallback;


	/**
	 * 构造即开始计时！
	 * <p>注意 {@code this.event.begin()} 在构造器中就调用了——
	 * 这意味着 start() 返回的瞬间，JFR 码表就已经在走了。
	 */
	public FlightRecorderStartupStep(long id, String name, long parentId,
			Consumer<FlightRecorderStartupStep> recordingCallback) {

		this.event = new FlightRecorderStartupEvent(id, name, parentId);
		this.event.begin();  // ← JFR 开始计时
		this.recordingCallback = recordingCallback;
	}


	@Override
	public String getName() {
		return this.event.name;
	}

	@Override
	public long getId() {
		return this.event.eventId;
	}

	@Override
	public Long getParentId() {
		return this.event.parentId;
	}

	/** tag 会真正存储——与 DefaultStartupStep 的空操作形成对比 */
	@Override
	public StartupStep tag(String key, String value) {
		this.tags.add(key, value);
		return this;
	}

	/**
	 * 延迟求值版本——这里<b>立即调用了 value.get()</b>！
	 * <p>与 DefaultStartupStep 不同，JFR 版本需要真正记录数据，所以必须求值。
	 */
	@Override
	public StartupStep tag(String key, Supplier<String> value) {
		this.tags.add(key, value.get());
		return this;
	}

	@Override
	public Tags getTags() {
		return this.tags;
	}

	/**
	 * 🔥 灵魂方法——结束步骤并提交 JFR 事件。
	 * <pre>
	 * 1. event.end()          → JFR 停止计时
	 * 2. event.shouldCommit() → JFR 判断是否值得记录（可能被 threshold 过滤）
	 * 3. 序列化 tags           → "beanName=orderService,count=42,"
	 * 4. event.commit()       → 写入 JFR 环形缓冲区
	 * 5. recordingCallback    → 从活跃栈移除自己
	 * </pre>
	 */
	@Override
	public void end() {
		this.event.end();                    // 1. 停止计时
		if (this.event.shouldCommit()) {     // 2. JFR 过滤判断
			StringBuilder builder = new StringBuilder();
			this.tags.forEach(tag ->
					builder.append(tag.getKey()).append('=').append(tag.getValue()).append(',')
			);
			this.event.setTags(builder.toString());  // 3. 拼字符串塞给 Event
		}
		this.event.commit();                 // 4. 写入 JFR 缓冲区
		this.recordingCallback.accept(this); // 5. 从活跃栈中移除
	}

	protected FlightRecorderStartupEvent getEvent() {
		return this.event;
	}


	/**
	 * JFR 版标签集合——用原始数组 + System.arraycopy 实现 append-only。
	 * <p>💡 为什么不用 ArrayList？因为标签数量极少（通常 1-3 个），
	 * 原始数组避免了 ArrayList 的额外对象头和扩容逻辑，更轻量。
	 * <p>remove() 直接抛异常——标签只能追加不能删除（append-only 语义）。
	 */
	static class FlightRecorderTags implements Tags {

		private Tag[] tags = new Tag[0];

		/** 每次添加都创建新数组并复制——典型的 copy-on-write 思路 */
		public void add(String key, String value) {
			Tag[] newTags = new Tag[this.tags.length + 1];
			System.arraycopy(this.tags, 0, newTags, 0, this.tags.length);
			newTags[newTags.length - 1] = new FlightRecorderTag(key, value);
			this.tags = newTags;
		}

		public void add(String key, Supplier<String> value) {
			add(key, value.get());
		}

		@Override
		public Iterator<Tag> iterator() {
			return new TagsIterator();
		}


		/** 简单的数组下标迭代器 */
		private class TagsIterator implements Iterator<Tag> {

			private int idx = 0;

			@Override
			public boolean hasNext() {
				return this.idx < tags.length;
			}

			@Override
			public Tag next() {
				return tags[this.idx++];
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("tags are append only");
			}
		}
	}


	/** 简单的不可变 key-value 对 */
	static class FlightRecorderTag implements Tag {

		private final String key;

		private final String value;

		public FlightRecorderTag(String key, String value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public String getKey() {
			return this.key;
		}

		@Override
		public String getValue() {
			return this.value;
		}
	}

}
