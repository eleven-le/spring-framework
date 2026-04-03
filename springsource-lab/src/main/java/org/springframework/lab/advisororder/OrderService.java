package org.springframework.lab.advisororder;

/**
 * 订单服务接口 —— 拦截链排序 Demo 的目标对象
 * 模拟 C 端场景：下单 / 查询
 */
public interface OrderService {

	/** 下单 */
	String placeOrder(String userId, int amount);

	/** 查询订单 */
	String queryOrder(String orderId);
}
