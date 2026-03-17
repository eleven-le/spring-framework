package org.springframework.lab.aop;

/**
 * 订单服务接口 — 有接口时默认走 JDK 动态代理
 */
public interface OrderService {

	String createOrder(String product, int quantity);

	String queryOrder(String orderId);
}
