package org.springframework.lab.naming;

import org.springframework.stereotype.Component;

/**
 * 具体渠道实现——继承 Abstract 骨架，只实现 doExecute。
 *
 * <p>对照 Spring：
 * ClassPathXmlApplicationContext extends AbstractXmlApplicationContext extends AbstractApplicationContext
 * 每一层只做自己的事：
 *   - Abstract 定义骨架
 *   - 中间层加特性（XML 解析）
 *   - 具体类提供入口
 *
 * <p>业务映射：
 * PayChannel(接口) → AbstractPayChannel(骨架) → AlipayChannel(具体渠道)
 */
@Component("alipayChannel")
public class AlipayChannel extends AbstractPayChannel {

	@Override
	public String channelCode() {
		return "ALIPAY";
	}

	@Override
	protected void prepare(String orderId) {
		System.out.println("    [Alipay] 准备支付宝签名参数");
	}

	@Override
	protected PayResult doExecute(String orderId, long amountInCents) {
		System.out.println("    [Alipay] 调用支付宝 SDK, orderId=" + orderId);
		return new PayResult(orderId, true, "ALIPAY_SUCCESS");
	}

	@Override
	protected void onSuccess(String orderId, PayResult result) {
		System.out.println("    [Alipay] 记录支付宝流水号");
	}
}
