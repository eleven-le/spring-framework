package org.springframework.lab.processortour;

import org.springframework.stereotype.Service;

/**
 * 支付服务 — 演示自定义 @AuditLog 注解独立生效（纯 Advisor 路线）。
 */
@Service
public class PaymentService {

	@AuditLog(action = "支付")
	public void pay(String userId, long amountCents) {
		System.out.println("    [PaymentService#pay] 扣款: userId=" + userId + ", amount=" + amountCents + "分");
	}
}
