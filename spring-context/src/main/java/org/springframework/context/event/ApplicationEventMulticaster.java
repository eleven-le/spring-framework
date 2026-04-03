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

package org.springframework.context.event;

import java.util.function.Predicate;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>事件驱动的"调度中心"——管理监听者 + 分发事件的核心引擎！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.event.ApplicationEventMulticaster}</li>
 * <li><b>中文名</b>：应用事件广播器 —— 事件驱动模型的"广播调度中心/交换机"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块，{@code context.event} 包</li>
 * <li><b>接口层级</b>：顶层接口（不继承任何接口），共 <b>9 个方法</b>（4 类：增/删/清/播）</li>
 * <li><b>默认实现</b>：{@code SimpleApplicationEventMulticaster}（同步遍历 + 可选 TaskExecutor 异步）</li>
 * </ul>
 *
 * <h3>💡 为什么需要 Multicaster？——Publisher 和 Listener 之间需要一个"调度中间人"！</h3>
 * <p>Spring 事件驱动模型的三角关系中，Multicaster 是<b>最核心的中间人</b>：</p>
 * <pre>
 * ApplicationEventPublisher（发布者/话筒）    ApplicationListener（监听者/听众）
 *        ↓ publishEvent(event)                       ↑ onApplicationEvent(event)
 *        └──────→ ApplicationEventMulticaster ────────┘
 *                  ← 👈 你在这里！广播调度中心
 * </pre>
 * <p>为什么不让 Publisher 直接调用 Listener？因为需要解决一系列复杂问题：</p>
 * <ul>
 * <li><b>监听者注册/注销管理</b>：谁在听？随时可能增减，需要一个统一的注册表</li>
 * <li><b>事件类型匹配</b>：100 个监听者，只有 3 个关心这个事件，需要过滤</li>
 * <li><b>同步/异步策略</b>：是当场通知还是异步投递？这是调度策略，不该由发布者决定</li>
 * <li><b>异常隔离</b>：一个监听者抛异常不应该影响其他监听者（由 ErrorHandler 处理）</li>
 * </ul>
 * <p>把这些职责集中到 Multicaster，Publisher 只管"喊话"，Listener 只管"处理"，各自干净。</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>中介者模式（Mediator）——解耦发布者与订阅者</b><br/>
 * Multicaster 就是一个中介者：发布者不知道有哪些监听者，监听者不知道谁在发布，双方只认识中介者。<br/>
 * <b>业务借鉴</b>：消息中间件（Kafka/RabbitMQ）本质就是一个 Multicaster——生产者投消息给 Broker，
 * Broker 根据 topic/routing key 分发给匹配的消费者。设计内存级事件总线时照搬这个三角结构即可。</li>
 *
 * <li><b>注册表设计——实例注册 vs BeanName 注册并存</b><br/>
 * Multicaster 同时支持两种注册方式：
 * <ul>
 * <li>{@code addApplicationListener(listener)} —— 直接注册实例（编程式注册、Lambda）</li>
 * <li>{@code addApplicationListenerBean(beanName)} —— 注册 Bean 名称（懒加载，用时再从容器取）</li>
 * </ul>
 * BeanName 注册的好处：监听者可以是 Prototype 作用域，每次事件到达时拿到新鲜实例。<br/>
 * <b>业务借鉴</b>：设计回调/Hook 注册表时，同时支持"直接传对象"和"传名称/ID 懒加载"，覆盖不同生命周期需求。</li>
 *
 * <li><b>批量操作——Predicate 过滤式清理</b><br/>
 * {@code removeApplicationListeners(Predicate)} 和 {@code removeApplicationListenerBeans(Predicate)}
 * 提供了条件式批量移除——不需要逐个引用，用条件过滤即可。<br/>
 * <b>业务借鉴</b>：注册表的清理 API 要提供"条件过滤"能力，而不只是"按 ID 单个移除"。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * ApplicationEventMulticaster  ← 👈 你在这里！顶层接口
 * └── AbstractApplicationEventMulticaster（模板骨架：监听者注册表 + 匹配过滤逻辑）
 *       └── SimpleApplicationEventMulticaster（默认实现：同步遍历 + 可选 TaskExecutor 异步）
 *
 * 在容器中的角色：
 * AbstractApplicationContext
 *   ├── 持有字段：applicationEventMulticaster
 *   ├── initApplicationEventMulticaster()：refresh 第8步，创建/获取广播器
 *   ├── registerListeners()：refresh 第10步，把监听者注册到广播器
 *   └── publishEvent() → multicastEvent()：发布事件时委托广播器分发
 * </pre>
 *
 * <h3>🏭 核心设计思想：注册表 + 类型匹配 + 调度策略三层分离</h3>
 * <ol>
 * <li><b>注册表层</b>：{@code add/remove} 系列方法维护监听者集合（实例集合 + BeanName 集合）</li>
 * <li><b>匹配层</b>：{@code multicastEvent()} 时根据事件类型 + {@code ResolvableType} 筛选匹配的监听者</li>
 * <li><b>调度层</b>：匹配后逐个调用，可选同步/异步（由 {@code SimpleApplicationEventMulticaster} 的 TaskExecutor 决定）</li>
 * </ol>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>9 个方法分为 <b>4 个战区</b>：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>战区</th><th>方法</th><th>使命</th></tr>
 * <tr><td rowspan="2">📥 注册监听者</td><td>{@code addApplicationListener(listener)}</td><td>注册监听者实例</td></tr>
 * <tr><td>{@code addApplicationListenerBean(beanName)}</td><td>注册监听者 Bean 名称（懒加载）</td></tr>
 * <tr><td rowspan="5">📤 移除监听者</td><td>{@code removeApplicationListener(listener)}</td><td>移除指定实例</td></tr>
 * <tr><td>{@code removeApplicationListenerBean(beanName)}</td><td>移除指定 BeanName</td></tr>
 * <tr><td>{@code removeApplicationListeners(predicate)}</td><td>条件批量移除实例</td></tr>
 * <tr><td>{@code removeApplicationListenerBeans(predicate)}</td><td>条件批量移除 BeanName</td></tr>
 * <tr><td>{@code removeAllListeners()}</td><td>清空所有监听者</td></tr>
 * <tr><td rowspan="2">📢 广播事件</td><td>{@code multicastEvent(event)}</td><td>广播事件（自动推断类型）</td></tr>
 * <tr><td>{@code multicastEvent(event, eventType)}</td><td>广播事件（显式指定类型，支持泛型）</td></tr>
 * </table>
 *
 * <h3>🧠 三、架构师透视·现实应用场景</h3>
 * <ul>
 * <li><b>refresh 第8步</b>：{@code initApplicationEventMulticaster()} —— 如果容器中有名为
 * {@code "applicationEventMulticaster"} 的 Bean 就用它，否则创建 {@code SimpleApplicationEventMulticaster}</li>
 * <li><b>refresh 第10步</b>：{@code registerListeners()} —— 把所有 {@code ApplicationListener} Bean
 * 注册到广播器，同时把 earlyEvents（refresh 完成前积压的事件）一次性广播出去</li>
 * <li><b>自定义异步广播</b>：注册一个名为 {@code "applicationEventMulticaster"} 的 Bean，
 * 设置 TaskExecutor 即可让所有事件监听异步执行</li>
 * <li><b>容器关闭时</b>：不会主动清除广播器中的监听者——由 GC 回收</li>
 * </ul>
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>ApplicationEventMulticaster 是 Spring 事件驱动模型的<b>"心脏"</b>——<br/>
 * 它管理着所有监听者的注册表，负责事件类型匹配和分发调度。<br/>
 * Publisher 只负责"把事件丢给我"，Listener 只负责"等着被我调用"，<br/>
 * 而 Multicaster 在中间做了所有脏活：<b>维护注册表、类型匹配、遍历调用、异常处理、同步/异步调度</b>。<br/>
 * 理解了 Multicaster，就理解了 Spring 事件系统的核心引擎。</p>
 *
 * <hr/>
 * Interface to be implemented by objects that can manage a number of
 * {@link ApplicationListener} objects and publish events to them.
 *
 * <p>An {@link org.springframework.context.ApplicationEventPublisher}, typically
 * a Spring {@link org.springframework.context.ApplicationContext}, can use an
 * {@code ApplicationEventMulticaster} as a delegate for actually publishing events.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @see ApplicationListener
 */
public interface ApplicationEventMulticaster {

	/**
	 * <h3>📥 方法 1：addApplicationListener(listener) —— 注册监听者实例</h3>
	 * <p><b>🏭【直接把人领到广播站登记——实例注册】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 将一个 {@code ApplicationListener} 实例添加到广播器的监听者注册表中。<br/>
	 * 注册后，后续所有 {@code multicastEvent()} 调用都会考虑这个监听者（如果事件类型匹配的话）。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你带着一个人（listener 实例）走进广播站："这位同事以后要听广播，帮他登个记。"<br/>
	 * 广播站把他的名字和"感兴趣的频道"（泛型参数）记在花名册上。<br/>
	 * 以后有匹配的广播，就直接通知他。</blockquote>
	 * <p><b>【与 addApplicationListenerBean 的区别】</b><br/>
	 * 这里注册的是<b>已有实例</b>——适合编程式注册、Lambda、手动创建的监听者。<br/>
	 * 而 {@code addApplicationListenerBean(beanName)} 注册的是<b>Bean 名称</b>，用时才从容器取。</p>
	 * <hr/>
	 * Add a listener to be notified of all events.
	 * @param listener the listener to add
	 * @see #removeApplicationListener(ApplicationListener)
	 * @see #removeApplicationListeners(Predicate)
	 */
	void addApplicationListener(ApplicationListener<?> listener);

	/**
	 * <h3>📥 方法 2：addApplicationListenerBean(beanName) —— 注册监听者 Bean 名称</h3>
	 * <p><b>🏭【只登记名字，用时再去找人——懒加载注册】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 将一个监听者的 <b>Bean 名称</b>添加到注册表，而不是实例本身。<br/>
	 * 当事件需要广播时，Multicaster 才通过 {@code BeanFactory.getBean(beanName)} 获取实例。<br/>
	 * 好处：支持 <b>Prototype 作用域的监听者</b>——每次事件到达都能拿到新实例。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你告诉广播站："有个叫 'orderListener' 的人要听广播，但他现在不在这儿。"<br/>
	 * 广播站记下名字。等真有广播要发的时候，广播站才去 HR 系统（BeanFactory）里找这个人。<br/>
	 * 如果他是"临时工"（Prototype），每次都能找到一个"新的他"。</blockquote>
	 * <hr/>
	 * Add a listener bean to be notified of all events.
	 * @param listenerBeanName the name of the listener bean to add
	 * @see #removeApplicationListenerBean(String)
	 * @see #removeApplicationListenerBeans(Predicate)
	 */
	void addApplicationListenerBean(String listenerBeanName);

	/**
	 * <h3>📤 方法 3：removeApplicationListener(listener) —— 移除指定监听者实例</h3>
	 * <p><b>🏭【把人从广播站花名册上划掉——取消订阅】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 从注册表中移除指定的监听者实例。移除后，后续事件不再通知它。<br/>
	 * 注意：只移除通过 {@code addApplicationListener()} 注册的实例，不影响通过 BeanName 注册的。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "这位同事不想听广播了，把他从花名册上划掉。"</blockquote>
	 * <hr/>
	 * Remove a listener from the notification list.
	 * @param listener the listener to remove
	 * @see #addApplicationListener(ApplicationListener)
	 * @see #removeApplicationListeners(Predicate)
	 */
	void removeApplicationListener(ApplicationListener<?> listener);

	/**
	 * <h3>📤 方法 4：removeApplicationListenerBean(beanName) —— 移除指定 BeanName 监听者</h3>
	 * <p><b>🏭【把名字从预约名单上划掉】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 从注册表中移除指定 Bean 名称的监听者。对应 {@code addApplicationListenerBean()} 的逆操作。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "那个叫 'orderListener' 的人不用再通知了，把他名字从预约名单上划掉。"</blockquote>
	 * <hr/>
	 * Remove a listener bean from the notification list.
	 * @param listenerBeanName the name of the listener bean to remove
	 * @see #addApplicationListenerBean(String)
	 * @see #removeApplicationListenerBeans(Predicate)
	 */
	void removeApplicationListenerBean(String listenerBeanName);

	/**
	 * <h3>📤 方法 5：removeApplicationListeners(Predicate) —— 条件批量移除实例</h3>
	 * <p><b>🏭【按条件批量清退——"所有临时工都走！"】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 用 {@code Predicate} 条件式批量移除监听者<b>实例</b>（不影响 BeanName 注册的）。<br/>
	 * 典型用法：按 {@code SmartApplicationListener.getListenerId()} 过滤，或按类型过滤。<br/>
	 * 注意：{@code @EventListener} 方法生成的 {@code ApplicationListenerMethodAdapter} 也是实例，也会被过滤到。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "把所有带'临时'标签的监听者都移除！"——你给一个判断条件，广播站遍历花名册，<br/>
	 * 符合条件的统统划掉。比逐个移除高效得多。</blockquote>
	 * <hr/>
	 * Remove all matching listeners from the set of registered
	 * {@code ApplicationListener} instances (which includes adapter classes
	 * such as {@link ApplicationListenerMethodAdapter}, e.g. for annotated
	 * {@link EventListener} methods).
	 * <p>Note: This just applies to instance registrations, not to listeners
	 * registered by bean name.
	 * @param predicate the predicate to identify listener instances to remove,
	 * e.g. checking {@link SmartApplicationListener#getListenerId()}
	 * @since 5.3.5
	 * @see #addApplicationListener(ApplicationListener)
	 * @see #removeApplicationListener(ApplicationListener)
	 */
	void removeApplicationListeners(Predicate<ApplicationListener<?>> predicate);

	/**
	 * <h3>📤 方法 6：removeApplicationListenerBeans(Predicate) —— 条件批量移除 BeanName</h3>
	 * <p><b>🏭【按条件批量清退预约名单】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 用 {@code Predicate<String>} 条件式批量移除通过 <b>BeanName 注册</b>的监听者。<br/>
	 * 不影响通过实例注册的监听者。与方法 5 互补——一个清理实例注册表，一个清理名称注册表。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "把预约名单上所有名字以 'temp' 开头的都划掉！"</blockquote>
	 * <hr/>
	 * Remove all matching listener beans from the set of registered
	 * listener bean names (referring to bean classes which in turn
	 * implement the {@link ApplicationListener} interface directly).
	 * <p>Note: This just applies to bean name registrations, not to
	 * programmatically registered {@code ApplicationListener} instances.
	 * @param predicate the predicate to identify listener bean names to remove
	 * @since 5.3.5
	 * @see #addApplicationListenerBean(String)
	 * @see #removeApplicationListenerBean(String)
	 */
	void removeApplicationListenerBeans(Predicate<String> predicate);

	/**
	 * <h3>🧹 方法 7：removeAllListeners() —— 清空所有监听者</h3>
	 * <p><b>🏭【广播站大扫除——花名册全部清空！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 移除所有注册的监听者（实例注册的 + BeanName 注册的，全部清空）。<br/>
	 * 清空后，{@code multicastEvent()} 将不会通知任何人，直到重新注册监听者。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * "广播站花名册全部作废！从现在起，广播出去也没人听——直到有人重新来登记。"<br/>
	 * 这通常发生在容器关闭或重建场景中。</blockquote>
	 * <hr/>
	 * Remove all listeners registered with this multicaster.
	 * <p>After a remove call, the multicaster will perform no action
	 * on event notification until new listeners are registered.
	 * @see #removeApplicationListeners(Predicate)
	 */
	void removeAllListeners();

	/**
	 * <h3>📢 方法 8：multicastEvent(ApplicationEvent event) —— 广播事件（简单版）</h3>
	 * <p><b>🏭【广播站开始广播——自动推断频道！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 将事件广播给所有匹配的监听者。事件类型（用于匹配监听者）由框架从事件对象自动推断。<br/>
	 * 内部实现通常是：推断出 {@code ResolvableType} → 委托给 {@code multicastEvent(event, eventType)}。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你把一份公告交给广播站："帮我播出去！"<br/>
	 * 广播站先看公告类型（ContextRefreshedEvent？OrderCreatedEvent？），<br/>
	 * 然后翻花名册，找到所有关心这类公告的人，逐个通知。</blockquote>
	 * <p><b>【推荐用法】</b><br/>
	 * 如果你的事件涉及泛型（如 {@code PayloadApplicationEvent<OrderDTO>}），<br/>
	 * 建议用方法 9 {@code multicastEvent(event, eventType)} 显式传入类型，避免泛型擦除导致匹配不准确。</p>
	 * <hr/>
	 * Multicast the given application event to appropriate listeners.
	 * <p>Consider using {@link #multicastEvent(ApplicationEvent, ResolvableType)}
	 * if possible as it provides better support for generics-based events.
	 * @param event the event to multicast
	 */
	void multicastEvent(ApplicationEvent event);

	/**
	 * <h3>📢 方法 9：multicastEvent(ApplicationEvent, ResolvableType) —— 广播事件（精确版）</h3>
	 * <p><b>🏭【广播站精确广播——你来指定频道！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 将事件广播给所有匹配的监听者，显式指定事件的 {@code ResolvableType}。<br/>
	 * 如果 {@code eventType} 为 null，框架会从 event 对象自动推断（退化为方法 8 的行为）。<br/>
	 * 指定 eventType 的核心价值：<b>解决泛型擦除问题</b>——{@code PayloadApplicationEvent<OrderDTO>}
	 * 在运行时泛型被擦除，如果不显式传 eventType，匹配可能不精确。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你把公告交给广播站，同时明确说："这份公告是'订单创建'类的，播给听这个频道的人。"<br/>
	 * 广播站不需要自己猜频道，直接用你指定的频道去匹配花名册——更快更准确。<br/>
	 * 尤其是"带信封的公告"（PayloadApplicationEvent），信封外面没写是什么类，<br/>
	 * 你得告诉广播站"这里面是 OrderDTO"，它才能精确匹配到 {@code @EventListener(OrderDTO.class)} 的人。</blockquote>
	 * <p><b>【这就是 AbstractApplicationContext.publishEvent() 内部调用的版本】</b><br/>
	 * 源码中：{@code getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);}<br/>
	 * 其中 eventType 是通过 {@code ResolvableType.forInstance(event)} 预先解析好的。</p>
	 * <hr/>
	 * Multicast the given application event to appropriate listeners.
	 * <p>If the {@code eventType} is {@code null}, a default type is built
	 * based on the {@code event} instance.
	 * @param event the event to multicast
	 * @param eventType the type of event (can be {@code null})
	 * @since 4.2
	 */
	void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType);

}
