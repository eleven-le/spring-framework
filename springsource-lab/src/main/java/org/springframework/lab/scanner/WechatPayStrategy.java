package org.springframework.lab.scanner;

import org.springframework.stereotype.Component;

/**
 * 微信支付策略实现 —— 会被 AssignableTypeFilter(PayStrategy.class) 扫描到
 */
@Component
public class WechatPayStrategy implements PayStrategy {

	@Override
	public String channel() {
		return "WECHAT";
	}

	@Override
	public boolean pay(String orderId, long amount) {
		System.out.println("    [Wechat] 支付 " + orderId + " 金额 " + amount);
		return true;
	}
}
