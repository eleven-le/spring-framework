package org.springframework.lab.classmigration.pay.alipay;

import org.springframework.lab.classmigration.pay.PayOrder;
import org.springframework.lab.classmigration.pay.PayResult;
import org.springframework.lab.classmigration.pay.support.AbstractPayChannel;

/**
 * 支付宝渠道实现 —— 落地层，定义 "用什么做"
 *
 * <p>对标 DataSourceTransactionManager —— 绑定具体技术（JDBC Connection），
 * 只实现 doGetTransaction/doBegin/doCommit
 *
 * <p>子类只关心：
 * <ol>
 *   <li>doSign() → RSA2 签名算法</li>
 *   <li>doExecutePrepay() → 调用支付宝 OpenAPI</li>
 *   <li>doExecuteQuery() → 调用支付宝查询接口</li>
 * </ol>
 * <p>不关心：参数校验、日志、异常翻译、耗时统计 —— 全部在骨架
 */
public class AlipayChannel extends AbstractPayChannel {

	@Override
	public String getChannelCode() {
		return "alipay";
	}

	@Override
	protected String doSign(PayOrder order) {
		// 真实场景：用 RSA2 私钥签名
		return "RSA2_SIGN(" + order.getOrderId() + ")";
	}

	@Override
	protected PayResult doExecutePrepay(PayOrder order, String signature) {
		// 真实场景：HttpClient 调用 alipay.trade.precreate
		System.out.println("      → 调用支付宝 alipay.trade.precreate API");
		System.out.println("      → 签名算法: RSA2, app_id=2021..., sign=" + signature);
		return PayResult.success("ALI_" + System.currentTimeMillis());
	}

	@Override
	protected PayResult doExecuteQuery(String orderId) {
		System.out.println("      → 调用支付宝 alipay.trade.query API");
		return PayResult.success("ALI_QUERY_" + orderId);
	}
}
