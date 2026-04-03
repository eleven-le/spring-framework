package org.springframework.lab.event;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 实验 2 — 注解式监听器：@EventListener。
 *
 * <p>注册路径: EventListenerMethodProcessor#afterSingletonsInstantiated
 *   → processBean → 找到 @EventListener 方法
 *   → EventListenerFactory#createApplicationListener
 *   → 生成 ApplicationListenerMethodAdapter
 *   → context.addApplicationListener() 注册到 Multicaster。
 *
 * <p>支持特性:
 * - SpEL condition 条件过滤 (#root.event, #root.args)
 * - @Order 排序
 * - 方法返回值作为新事件自动 publishEvent（事件链）
 * - PayloadApplicationEvent 自动拆包（方法参数不必是 ApplicationEvent 子类）
 */
@Component
public class AnnotationBasedListeners {

	/**
	 * 监听传统 ApplicationEvent 子类。
	 * 通过 @Order(2) 排在 InterfaceBasedListener(order=1) 之后。
	 */
	@EventListener
	@Order(2)
	public void onOrderCreated(OrderCreatedEvent event) {
		System.out.println("  [@EventListener] 收到 " + event
				+ "  thread=" + Thread.currentThread().getName());
	}

	/**
	 * 监听非 ApplicationEvent 类型 — 框架自动拆 PayloadApplicationEvent 包装。
	 * 断点: ApplicationListenerMethodAdapter#resolveArguments 可见拆包逻辑。
	 */
	@EventListener
	@Order(3)
	public void onOrderPaid(OrderPaidEvent event) {
		System.out.println("  [@EventListener] 收到 " + event
				+ "  thread=" + Thread.currentThread().getName());
	}

	/**
	 * 带 SpEL 条件的监听器 — 只处理金额 > 5000 的事件。
	 * condition 在 ApplicationListenerMethodAdapter#shouldHandle 中通过
	 * EventExpressionEvaluator 求值。
	 */
	@EventListener(condition = "#event.amount > 5000")
	@Order(4)
	public void onHighValuePaid(OrderPaidEvent event) {
		System.out.println("  [@EventListener+condition] 高额支付! " + event);
	}

	/**
	 * 事件链: 返回值自动发布为新事件。
	 * 执行链: handleResult → publishEvents → applicationContext.publishEvent(返回值)
	 */
	@EventListener
	@Order(10)
	public OrderPaidEvent onOrderCreatedChain(OrderCreatedEvent event) {
		System.out.println("  [@EventListener 事件链] 订单创建 → 自动触发支付事件");
		return new OrderPaidEvent(event.getOrderId(), event.getPrice());
	}
}
