package org.springframework.lab.autowire;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 支付宝渠道 —— 用 @Qualifier("alipay") 标识
 */
@Component
@Qualifier("alipay")
public class AlipayChannel implements PayChannel {
	@Override
	public String route() {
		return "ALIPAY_GATEWAY";
	}

	@Override
	public String name() {
		return "Alipay";
	}
}
