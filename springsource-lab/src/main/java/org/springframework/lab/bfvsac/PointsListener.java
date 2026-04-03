package org.springframework.lab.bfvsac;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 注解式监听器 —— 用 @EventListener 而非实现接口
 *
 * <p>由 EventListenerMethodProcessor（BeanFactoryPostProcessor）在
 * ApplicationContext#refresh() 的 finishBeanFactoryInitialization 阶段
 * 扫描并注册为 ApplicationListenerMethodAdapter。
 * 裸 BeanFactory 同样无法触发。
 */
@Component
public class PointsListener {

	@EventListener
	public void onPaymentSuccess(PaymentSuccessEvent event) {
		System.out.println("  ✓ [PointsListener @EventListener] 收到事件 → 订单 " + event.getOrderId() + " 触发优惠券发放");
	}
}
