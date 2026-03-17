package org.springframework.lab.autowire;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 银联渠道 —— 灰度上线中, 不设 @Primary
 * 演示: ObjectProvider 可选注入灰度组件
 */
@Component
@Qualifier("unionpay")
public class UnionPayChannel implements PayChannel {
	@Override
	public String route() {
		return "UNIONPAY_B2C";
	}

	@Override
	public String name() {
		return "UnionPay";
	}
}
