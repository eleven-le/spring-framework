package org.springframework.lab.advisororder;

import org.springframework.stereotype.Service;

/**
 * 订单服务实现 —— 纯业务逻辑
 */
@Service
public class OrderServiceImpl implements OrderService {

	@Override
	public String placeOrder(String userId, int amount) {
		System.out.println("    [TARGET] OrderService.placeOrder → userId=" + userId + ", amount=" + amount);
		return "ORDER-" + System.currentTimeMillis();
	}

	@Override
	public String queryOrder(String orderId) {
		System.out.println("    [TARGET] OrderService.queryOrder → orderId=" + orderId);
		return "QUERY-OK";
	}
}
