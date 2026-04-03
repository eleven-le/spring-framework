package org.springframework.lab.scanner;

import org.springframework.stereotype.Component;

/**
 * VIP 订单服务 —— 用于演示 Registry 替换场景
 */
@Component
public class VipOrderService extends OrderService {

	@Override
	public String placeOrder(String productId) {
		return "VIP Order placed for: " + productId;
	}
}
