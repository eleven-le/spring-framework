package org.springframework.lab.bfvsac;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 积分监听器 —— 监听支付成功后发放积分
 *
 * <p>这个 Bean 实现了 ApplicationListener，只有 ApplicationContext
 * 在 registerListeners() 步骤中才会将它注册到 ApplicationEventMulticaster。
 * 裸 BeanFactory 即使创建了这个 Bean，也不会触发监听。
 */
@Component
public class PaymentSuccessListener implements ApplicationListener<PaymentSuccessEvent> {

	@Override
	public void onApplicationEvent(PaymentSuccessEvent event) {
		System.out.println("  ✓ [PaymentSuccessListener] 收到事件 → 订单 " + event.getOrderId() + " 发放积分 100 分");
	}
}
