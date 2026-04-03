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

package org.springframework.context;

import java.util.EventListener;
import java.util.function.Consumer;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>事件驱动的"接收端"——观察者模式中的"订阅者/监听者"契约！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.ApplicationListener}</li>
 * <li><b>中文名</b>：应用事件监听者 —— 事件驱动模型的"收音机/听众"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块</li>
 * <li><b>接口层级</b>：继承 JDK 的 {@code java.util.EventListener}（标记接口），只有 <b>1 个抽象方法</b>！</li>
 * <li><b>关键标注</b>：{@code @FunctionalInterface} —— 可以用 Lambda 构造一个监听者！</li>
 * <li><b>泛型参数</b>：{@code <E extends ApplicationEvent>} —— 泛型即过滤条件，声明你关心哪种事件！</li>
 * </ul>
 *
 * <h3>💡 为什么需要 ApplicationListener？——观察者模式的"订阅端"需要统一契约！</h3>
 * <p>Spring 事件驱动模型的三角关系：</p>
 * <pre>
 * ApplicationEventPublisher（发布者/话筒）
 *        ↓ publishEvent(event)
 * ApplicationEventMulticaster（广播器/调度中心）
 *        ↓ multicastEvent → 遍历匹配的监听者
 * ApplicationListener（监听者/听众）← 👈 你在这里！
 *        ↓ onApplicationEvent(event)
 * 你的业务逻辑
 * </pre>
 * <p>没有这个统一接口，广播器不知道该调用谁的什么方法。<br/>
 * 有了它，所有监听者都实现同一个 {@code onApplicationEvent()}，广播器只需遍历调用即可。</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>泛型即过滤——声明式订阅</b><br/>
 * {@code ApplicationListener<OrderCreatedEvent>} 就表示"我只关心订单创建事件"。<br/>
 * 广播器根据泛型参数自动过滤——不需要监听者自己写 {@code if (event instanceof ...)}。<br/>
 * <b>业务借鉴</b>：设计消息消费者时，用泛型参数声明消费的消息类型，路由器根据类型自动分发，
 * 而不是让每个消费者自己判断"这条消息是不是给我的"。</li>
 *
 * <li><b>@FunctionalInterface——极致简化注册</b><br/>
 * 可以用 Lambda 快速创建监听者：{@code context.addApplicationListener(event -> log.info(event.toString()));}<br/>
 * 还提供了 {@code forPayload()} 工厂方法，直接接收 Payload 而不需要处理事件包装。<br/>
 * <b>业务借鉴</b>：在回调/Hook 设计中，用 @FunctionalInterface + 工厂方法降低使用门槛。</li>
 *
 * <li><b>两种注册方式并存（编程式 vs 注解式）</b><br/>
 * 传统方式：实现 {@code ApplicationListener<XxxEvent>} 接口并注册为 Bean<br/>
 * 现代方式：在任意 Bean 的方法上标 {@code @EventListener}（由 {@code EventListenerMethodProcessor} 转换为 {@code ApplicationListenerMethodAdapter}）<br/>
 * <b>业务借鉴</b>：设计扩展点时，同时提供"接口实现"和"注解声明"两种方式，覆盖不同场景。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * java.util.EventListener（JDK 标记接口）
 * └── ApplicationListener&lt;E&gt;  ← 👈 你在这里！基础监听者契约
 *       ├── SmartApplicationListener（支持 supportsEventType + 排序）
 *       │     └── GenericApplicationListener（支持 ResolvableType 精确匹配）
 *       │           └── ApplicationListenerMethodAdapter（@EventListener 方法的适配器）
 *       ├── SourceFilteringListener（按 source 过滤的装饰器）
 *       └── 你的业务监听者 implements ApplicationListener&lt;YourEvent&gt;
 * </pre>
 *
 * <h3>🏭 核心设计思想：泛型过滤 + 单方法回调</h3>
 * <p>这个接口只有 1 个抽象方法 + 1 个静态工厂方法，体现了：</p>
 * <ol>
 * <li><b>泛型过滤</b>：通过 {@code <E extends ApplicationEvent>} 声明感兴趣的事件类型，
 * Multicaster 在分发时通过 {@code ResolvableType} 解析泛型参数，实现精准投递</li>
 * <li><b>单方法回调</b>：所有事件处理逻辑都收口在 {@code onApplicationEvent(E event)} 一个方法中，
 * 简单、统一、可 Lambda 化</li>
 * </ol>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th><th>说明</th></tr>
 * <tr><td>{@code onApplicationEvent(E event)}</td><td>处理事件的回调入口</td><td>抽象方法，监听者的核心职责</td></tr>
 * <tr><td>{@code forPayload(Consumer<T>)}</td><td>静态工厂——从 Payload 消费者创建监听者</td><td>5.3+ 便捷方法，跳过事件包装直接拿载荷</td></tr>
 * </table>
 *
 * <h3>🧠 三、架构师透视·现实应用场景</h3>
 * <ul>
 * <li><b>方式1——接口实现</b>：{@code @Component class OrderListener implements ApplicationListener<OrderCreatedEvent>}</li>
 * <li><b>方式2——@EventListener 注解</b>：{@code @EventListener public void handle(OrderCreatedEvent e) {...}}（推荐！更简洁）</li>
 * <li><b>方式3——Lambda + forPayload</b>：{@code ApplicationListener.forPayload((String s) -> log.info(s));}</li>
 * <li><b>排序控制</b>：实现 {@code SmartApplicationListener} 或加 {@code @Order} 控制同一事件多个监听者的执行顺序</li>
 * <li><b>异步监听</b>：{@code @Async @EventListener} 使监听者在独立线程执行（需要配合 Multicaster 的 TaskExecutor）</li>
 * <li><b>事务事件</b>：{@code @TransactionalEventListener(phase = AFTER_COMMIT)} 事务提交后才触发</li>
 * </ul>
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>ApplicationListener 是 Spring 观察者模式三角中的"接收端"——<br/>
 * 与 ApplicationEventPublisher（发布端）和 ApplicationEventMulticaster（调度端）配合，<br/>
 * 构成了完整的事件驱动基础设施。<br/>
 * 它用<b>泛型参数做声明式过滤</b>，用<b>单方法做统一回调</b>，用<b>@FunctionalInterface 做 Lambda 支持</b>——<br/>
 * 简单到只有一个方法，强大到支撑整个 Spring 事件体系的订阅侧。</p>
 *
 * <hr/>
 * Interface to be implemented by application event listeners.
 *
 * <p>Based on the standard {@code java.util.EventListener} interface
 * for the Observer design pattern.
 *
 * <p>As of Spring 3.0, an {@code ApplicationListener} can generically declare
 * the event type that it is interested in. When registered with a Spring
 * {@code ApplicationContext}, events will be filtered accordingly, with the
 * listener getting invoked for matching event objects only.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @param <E> the specific {@code ApplicationEvent} subclass to listen to
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.event.SmartApplicationListener
 * @see org.springframework.context.event.GenericApplicationListener
 * @see org.springframework.context.event.EventListener
 */
@FunctionalInterface
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {

	/**
	 * <h3>📥 方法 1：void onApplicationEvent(E event) —— 唯一抽象方法</h3>
	 * <p><b>🏭【事件到达时的回调入口——你的业务逻辑写在这里！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 当一个匹配泛型参数 {@code E} 的事件被发布时，Multicaster 会调用这个方法。<br/>
	 * "匹配"的意思是：事件的实际类型是 {@code E} 或 {@code E} 的子类。<br/>
	 * 例如 {@code ApplicationListener<ContextRefreshedEvent>} 只会在容器 refresh 完成时被调用。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你在广播站登记了："我只听'订单创建'类的广播。"<br/>
	 * 广播站每次收到广播后，看看类型——"这次是'订单创建'，你登记过，给你听！"→ 调用你的 onApplicationEvent<br/>
	 * "这次是'用户注册'，跟你无关，跳过！"→ 不调用</blockquote>
	 * <p><b>【注意事项】</b></p>
	 * <ul>
	 * <li>默认是<b>同步调用</b>——在 publishEvent 的线程中执行。如果你的处理逻辑很重，会阻塞发布者！</li>
	 * <li>如果需要异步，要么在 Multicaster 上设置 TaskExecutor，要么用 {@code @Async @EventListener}</li>
	 * <li>如果方法抛出异常，默认会传播到发布者——<b>一个监听者的异常可能影响其他监听者！</b></li>
	 * </ul>
	 * <hr/>
	 * Handle an application event.
	 * @param event the event to respond to
	 */
	void onApplicationEvent(E event);


	/**
	 * <h3>🏭 方法 2：static forPayload(Consumer) —— 静态工厂方法</h3>
	 * <p><b>🏭【快捷创建监听者——跳过事件包装，直接拿载荷！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 工厂方法，接收一个 {@code Consumer<T>}，返回一个 {@code ApplicationListener<PayloadApplicationEvent<T>>}。<br/>
	 * 本质：帮你做了"拆信封"的动作——你不需要处理 PayloadApplicationEvent 包装，直接拿到里面的 Payload 对象。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 正常收广播：收到一个信封（PayloadApplicationEvent），你要自己拆开取出里面的便签纸。<br/>
	 * 用 forPayload：你告诉广播站"帮我拆好信封，直接把便签纸内容递给我"——<br/>
	 * {@code ApplicationListener.forPayload((String msg) -> log.info(msg));}</blockquote>
	 * <p><b>【使用场景】</b></p>
	 * <pre>
	 * // 编程式注册一个简单的载荷监听者
	 * context.addApplicationListener(
	 *     ApplicationListener.forPayload((OrderDTO order) -> {
	 *         log.info("收到订单: {}", order.getId());
	 *     })
	 * );
	 * </pre>
	 * <hr/>
	 * Create a new {@code ApplicationListener} for the given payload consumer.
	 * @param consumer the event payload consumer
	 * @param <T> the type of the event payload
	 * @return a corresponding {@code ApplicationListener} instance
	 * @since 5.3
	 * @see PayloadApplicationEvent
	 */
	static <T> ApplicationListener<PayloadApplicationEvent<T>> forPayload(Consumer<T> consumer) {
		return event -> consumer.accept(event.getPayload());
	}

}
