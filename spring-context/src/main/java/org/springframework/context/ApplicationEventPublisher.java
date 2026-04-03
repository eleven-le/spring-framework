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

package org.springframework.context;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>事件驱动的"发布出口"——让容器拥有"广播"能力的最小接口！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.ApplicationEventPublisher}</li>
 * <li><b>中文名</b>：应用事件发布者 —— 事件驱动模型的"话筒/广播站入口"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块</li>
 * <li><b>接口层级</b>：{@code ApplicationContext} 的父接口之一，只有 <b>2 个方法</b>（1 default + 1 abstract）！</li>
 * <li><b>关键标注</b>：{@code @FunctionalInterface} —— 可以用 Lambda 表示一个事件发布者！</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个接口？——"发布事件"是一种独立的能力，不应绑死在 ApplicationContext 上！</h3>
 * <p>Spring 事件驱动模型有三个角色：<b>发布者（Publisher）、广播器（Multicaster）、监听者（Listener）</b>。<br/>
 * 如果把"发布事件"的能力直接写在 ApplicationContext 里，那任何想发事件的代码都必须依赖整个 ApplicationContext。<br/>
 * 拆出 ApplicationEventPublisher 后：</p>
 * <ul>
 * <li><b>Service 层只需注入 {@code ApplicationEventPublisher}</b>，不需要依赖庞大的 ApplicationContext</li>
 * <li>符合<b>接口隔离原则（ISP）</b>——你只想发广播，不需要拿到整个控制台</li>
 * <li>ApplicationContext 继承了它，所以容器天然就是一个事件发布者——但使用者不需要知道这一点</li>
 * </ul>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>观察者模式的"发布入口"单一化</b><br/>
 * 不管事件系统内部多复杂（Multicaster、异步线程池、泛型匹配……），对外只暴露一个 {@code publishEvent()} 方法。<br/>
 * <b>业务借鉴</b>：设计领域事件系统时，对 Service 层只暴露一个 {@code DomainEventPublisher.publish(event)} 方法，
 * 内部是投 MQ、投数据库 Outbox 还是同步调用，Service 不需要知道。发布者只管"喊话"，不管"谁在听、怎么听"。</li>
 *
 * <li><b>@FunctionalInterface 的妙用——Lambda 即发布者</b><br/>
 * 因为只有一个抽象方法 {@code publishEvent(Object)}，所以你可以用 Lambda 构造一个发布者：<br/>
 * {@code ApplicationEventPublisher publisher = event -> myQueue.offer(event);}<br/>
 * <b>业务借鉴</b>：在测试中 mock 事件发布者极其方便——不需要启动整个容器，传一个 Lambda 即可验证事件是否被发布。</li>
 *
 * <li><b>"发布"与"广播"的职责拆分</b><br/>
 * Publisher 只负责"把事件丢出去"（发布），Multicaster 负责"找到合适的监听者并派发"（广播）。<br/>
 * ApplicationContext 在中间做桥接：{@code publishEvent()} → 委托给 {@code ApplicationEventMulticaster.multicastEvent()}。<br/>
 * <b>业务借鉴</b>：消息系统中，"生产者 API"和"消息路由/分发引擎"要分开设计。生产者只管 publish，路由规则（topic 匹配、tag 过滤）
 * 由中间件内部处理。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * ApplicationEventPublisher  ← 👈 你在这里！"发布事件"的最小能力契约
 * │
 * └── ApplicationContext（继承了 Publisher，所以容器天然能发事件）
 *       └── ConfigurableApplicationContext
 *             └── AbstractApplicationContext（publishEvent 的真正实现在这里）
 *
 * 发布流转链：
 * 你调用 publisher.publishEvent(event)
 *   → AbstractApplicationContext.publishEvent()
 *     → ApplicationEventMulticaster.multicastEvent()  ← 委托给广播器
 *       → 遍历匹配的 ApplicationListener.onApplicationEvent()  ← 最终触达监听者
 * </pre>
 *
 * <h3>🏭 核心设计思想：单向发布 + 松耦合</h3>
 * <p>这个接口的设计哲学是<b>"发布者不知道谁在听"</b>——经典的观察者模式解耦：</p>
 * <ol>
 * <li><b>发布者零依赖</b>：{@code publishEvent()} 只接收事件对象，不需要知道有哪些监听者、是同步还是异步</li>
 * <li><b>两种入口统一</b>：{@code publishEvent(ApplicationEvent)} 和 {@code publishEvent(Object)} 二合一——
 * 前者是传统的强类型事件，后者支持任意对象（自动包装为 {@code PayloadApplicationEvent}），4.2+ 大幅简化了事件发布</li>
 * </ol>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>极简！只有 <b>2 个方法</b>，形成"强类型"与"任意对象"的发布对：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th><th>事件类型</th></tr>
 * <tr><td>{@code publishEvent(ApplicationEvent)}</td><td>发布强类型事件（default 方法，委托给下面的）</td><td>必须是 ApplicationEvent 子类</td></tr>
 * <tr><td>{@code publishEvent(Object)}</td><td>发布任意对象事件（真正的抽象方法）</td><td>任意对象，非 ApplicationEvent 会包装为 PayloadApplicationEvent</td></tr>
 * </table>
 *
 * <h3>🧠 三、架构师透视·现实应用场景</h3>
 * <ul>
 * <li><b>Service 层发布领域事件</b>：{@code @Autowired ApplicationEventPublisher publisher; publisher.publishEvent(new OrderCreatedEvent(order));}</li>
 * <li><b>事务提交后发事件</b>：配合 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 实现"事务成功才通知"</li>
 * <li><b>4.2+ 简化发布</b>：{@code publisher.publishEvent("订单已创建")} —— 直接发字符串，框架自动包装为 PayloadApplicationEvent&lt;String&gt;</li>
 * <li><b>测试 mock</b>：{@code ApplicationEventPublisher mock = event -> assertThat(event).isInstanceOf(OrderCreatedEvent.class);}</li>
 * </ul>
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>ApplicationEventPublisher 是 Spring 事件驱动模型中面向"生产者"的最小 API。<br/>
 * 它用 1 个抽象方法（+ 1 个 default 方法）就完成了"发布事件"的全部契约，<br/>
 * 配合 ApplicationEventMulticaster（广播器）和 ApplicationListener（监听者）构成完整的观察者模式三角。<br/>
 * 对业务代码来说：<b>只需要注入这个接口，调用 publishEvent()，一切解耦的故事就从这里开始。</b></p>
 *
 * <hr/>
 * Interface that encapsulates event publication functionality.
 *
 * <p>Serves as a super-interface for {@link ApplicationContext}.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 1.1.1
 * @see ApplicationContext
 * @see ApplicationEventPublisherAware
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.event.EventPublicationInterceptor
 */
@FunctionalInterface
public interface ApplicationEventPublisher {

	/**
	 * <h3>📢 方法 1：void publishEvent(ApplicationEvent event) —— default 方法</h3>
	 * <p><b>🏭【发布强类型事件——传统入口，直接委托】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 发布一个 {@code ApplicationEvent} 类型的事件，通知所有匹配的监听者。<br/>
	 * 这是一个 <b>default 方法</b>，内部直接调用 {@code publishEvent((Object) event)}——<br/>
	 * 也就是说，它只是一个<b>类型友好的入口</b>，真正的逻辑在 {@code publishEvent(Object)} 中。<br/>
	 * 这里的"matching"指的是：事件类型与监听者的泛型参数匹配。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你拿着一份写好的"公告"（ApplicationEvent 子类）交给广播站："帮我播这个！"<br/>
	 * 广播站收到后，不是自己播——而是转交给"广播调度中心"（Multicaster）去找该听的人。<br/>
	 * 至于是现场直播（同步）还是录播（异步），这个方法不管——那是 Multicaster 的事。</blockquote>
	 * <p><b>【关键细节】</b><br/>
	 * 这个 default 方法是 Spring 4.2 引入 {@code publishEvent(Object)} 后的<b>向后兼容桥接</b>——<br/>
	 * 旧代码用 {@code publishEvent(ApplicationEvent)}，新代码用 {@code publishEvent(Object)}，<br/>
	 * 但最终都走同一条路。如果你实现这个接口，只需实现 {@code publishEvent(Object)} 即可。</p>
	 * <hr/>
	 * Notify all <strong>matching</strong> listeners registered with this
	 * application of an application event. Events may be framework events
	 * (such as ContextRefreshedEvent) or application-specific events.
	 * <p>Such an event publication step is effectively a hand-off to the
	 * multicaster and does not imply synchronous/asynchronous execution
	 * or even immediate execution at all. Event listeners are encouraged
	 * to be as efficient as possible, individually using asynchronous
	 * execution for longer-running and potentially blocking operations.
	 * @param event the event to publish
	 * @see #publishEvent(Object)
	 * @see org.springframework.context.event.ContextRefreshedEvent
	 * @see org.springframework.context.event.ContextClosedEvent
	 */
	default void publishEvent(ApplicationEvent event) {
		publishEvent((Object) event);
	}

	/**
	 * <h3>📢 方法 2：void publishEvent(Object event) —— 唯一抽象方法</h3>
	 * <p><b>🏭【发布任意对象事件——万能入口，自动包装】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 这是 {@code @FunctionalInterface} 的<b>唯一抽象方法</b>，也是事件发布的真正落点。<br/>
	 * 接收任意 Object 作为事件：
	 * <ul>
	 * <li>如果传入的是 {@code ApplicationEvent} 子类 → 直接广播</li>
	 * <li>如果传入的是普通对象（如 String、DTO） → 自动包装为 {@code PayloadApplicationEvent<T>} 再广播</li>
	 * </ul>
	 * 这意味着你<b>不再需要为每个业务事件都继承 ApplicationEvent</b>——直接发一个 POJO 就行！</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 以前发广播必须用"标准公文格式"（继承 ApplicationEvent），很麻烦。<br/>
	 * 现在你可以直接拿一张便签纸写几个字就交给广播站（传一个普通对象）——<br/>
	 * 广播站会自动帮你装进标准信封（PayloadApplicationEvent）再发出去。<br/>
	 * 收件人拆信封取出便签纸内容，一样用！</blockquote>
	 * <p><b>【使用场景】</b></p>
	 * <pre>
	 * // 传统方式：必须继承 ApplicationEvent
	 * publisher.publishEvent(new OrderCreatedEvent(this, orderId));
	 *
	 * // 4.2+ 简化方式：直接发 POJO
	 * publisher.publishEvent(new OrderCreatedDTO(orderId, userId));
	 * // 框架自动包装为 PayloadApplicationEvent&lt;OrderCreatedDTO&gt;
	 *
	 * // 甚至可以发字符串
	 * publisher.publishEvent("订单已创建: " + orderId);
	 * </pre>
	 * <p><b>【监听端怎么接？】</b></p>
	 * <pre>
	 * // 方式1：实现 ApplicationListener
	 * implements ApplicationListener&lt;PayloadApplicationEvent&lt;OrderCreatedDTO&gt;&gt;
	 *
	 * // 方式2：@EventListener（推荐，更简洁）
	 * &#064;EventListener
	 * public void handle(OrderCreatedDTO event) { ... }  // 框架自动解包 Payload
	 * </pre>
	 * <hr/>
	 * Notify all <strong>matching</strong> listeners registered with this
	 * application of an event.
	 * <p>If the specified {@code event} is not an {@link ApplicationEvent},
	 * it is wrapped in a {@link PayloadApplicationEvent}.
	 * <p>Such an event publication step is effectively a hand-off to the
	 * multicaster and does not imply synchronous/asynchronous execution
	 * or even immediate execution at all. Event listeners are encouraged
	 * to be as efficient as possible, individually using asynchronous
	 * execution for longer-running and potentially blocking operations.
	 * @param event the event to publish
	 * @since 4.2
	 * @see #publishEvent(ApplicationEvent)
	 * @see PayloadApplicationEvent
	 */
	void publishEvent(Object event);

}
