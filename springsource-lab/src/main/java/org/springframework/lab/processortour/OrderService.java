package org.springframework.lab.processortour;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单服务 — 演示 Advisor 路线（@Transactional + 自定义 @AuditLog 共存于同一代理链）。
 *
 * <p>观察点：placeOrder 方法同时被 TransactionInterceptor 和 AuditLogInterceptor 拦截，
 * 说明 InfrastructureAdvisorAutoProxyCreator 会把所有匹配的 Advisor 收集到同一个代理里。
 */
@Service
public class OrderService {

	@Transactional
	@AuditLog(action = "下单")
	public String placeOrder(String userId, String product) {
		System.out.println("    [OrderService#placeOrder] 执行业务: userId=" + userId + ", product=" + product);
		return "ORDER-" + System.currentTimeMillis();
	}
}
