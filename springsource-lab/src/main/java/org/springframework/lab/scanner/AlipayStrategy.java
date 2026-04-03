package org.springframework.lab.scanner;

import org.springframework.stereotype.Component;

/**
 * 支付宝策略实现 —— 会被 AssignableTypeFilter(PayStrategy.class) 扫描到
 */
@Component
public class AlipayStrategy implements PayStrategy {

	@Override
	public String channel() {
		return "ALIPAY";
	}

	@Override
	public boolean pay(String orderId, long amount) {
		System.out.println("    [Alipay] 支付 " + orderId + " 金额 " + amount);
		return true;
	}
}
