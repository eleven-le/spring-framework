package org.springframework.lab.classmigration.pay.wechat;

import org.springframework.lab.classmigration.pay.PayOrder;
import org.springframework.lab.classmigration.pay.PayResult;
import org.springframework.lab.classmigration.pay.support.AbstractPayChannel;

/**
 * 微信支付渠道实现 —— 另一个落地层
 *
 * <p>与 AlipayChannel 对比：
 * <ul>
 *   <li>签名算法不同：HMAC-SHA256 vs RSA2</li>
 *   <li>API 不同：v3 REST API vs OpenAPI</li>
 *   <li>但骨架流程完全复用 —— 这就是三层分离的价值</li>
 * </ul>
 *
 * <p>对标 HibernateTransactionManager —— 同样继承 APTM 骨架，但绑定 Hibernate Session
 */
public class WechatPayChannel extends AbstractPayChannel {

	@Override
	public String getChannelCode() {
		return "wechat";
	}

	@Override
	protected String doSign(PayOrder order) {
		// 真实场景：HMAC-SHA256 签名
		return "HMAC_SHA256(" + order.getOrderId() + ")";
	}

	@Override
	protected PayResult doExecutePrepay(PayOrder order, String signature) {
		System.out.println("      → 调用微信 v3/pay/transactions/native API");
		System.out.println("      → 签名算法: HMAC-SHA256, mch_id=1900..., sign=" + signature);
		return PayResult.success("WX_" + System.currentTimeMillis());
	}

	@Override
	protected PayResult doExecuteQuery(String orderId) {
		System.out.println("      → 调用微信 v3/pay/transactions/id/{id} API");
		return PayResult.success("WX_QUERY_" + orderId);
	}
}
