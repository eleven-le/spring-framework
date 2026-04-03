package org.springframework.lab.scanner;

import org.springframework.stereotype.Component;

/**
 * 普通订单服务 —— 被 Reader 和 Scanner 两条路径注册的目标类
 */
@Component
public class OrderService {

	public String placeOrder(String productId) {
		return "Order placed for: " + productId;
	}
}
