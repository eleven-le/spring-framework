package org.springframework.lab.aop;

import org.springframework.stereotype.Service;

/**
 * 订单服务实现 — 实现接口, 默认被 JDK 动态代理
 */
@Service
public class OrderServiceImpl implements OrderService {

	@Override
	public String createOrder(String product, int quantity) {
		System.out.println("  [OrderService] 创建订单: " + product + " x " + quantity);
		return "ORD-" + System.currentTimeMillis();
	}

	@Override
	public String queryOrder(String orderId) {
		System.out.println("  [OrderService] 查询订单: " + orderId);
		return "订单详情: " + orderId;
	}
}
