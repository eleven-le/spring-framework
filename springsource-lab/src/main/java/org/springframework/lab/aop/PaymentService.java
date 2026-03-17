package org.springframework.lab.aop;

import org.springframework.stereotype.Service;

/**
 * 支付服务 — 无接口, 强制走 CGLIB 子类代理
 *
 * 对比 OrderServiceImpl (有接口 → JDK 代理) 来理解代理类型选择策略:
 * DefaultAopProxyFactory#createAopProxy 中:
 *   - 有接口 + 未设 proxyTargetClass=true → JdkDynamicAopProxy
 *   - 无接口 / 设 proxyTargetClass=true   → ObjenesisCglibAopProxy
 */
@Service
public class PaymentService {

	public String pay(String orderId, double amount) {
		System.out.println("  [PaymentService] 支付: " + orderId + " ¥" + amount);
		return "PAY-OK";
	}

	public String refund(String orderId) {
		System.out.println("  [PaymentService] 退款: " + orderId);
		return "REFUND-OK";
	}
}
