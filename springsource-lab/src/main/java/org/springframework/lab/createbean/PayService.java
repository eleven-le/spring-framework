package org.springframework.lab.createbean;

import org.springframework.stereotype.Component;

/**
 * 被 OrderService @Autowired 依赖，用于观察 populateBean 注入时机。
 * 断点: 在构造器打断点，观察调用栈经过 populateBean → AutowiredAnnotationBPP → getBean(payService)
 */
@Component
public class PayService {

	public PayService() {
		System.out.println("[PayService]     构造器执行");
	}

	public String pay(String orderId, int amount) {
		return "PAY-OK: " + orderId + " / ¥" + amount;
	}
}
