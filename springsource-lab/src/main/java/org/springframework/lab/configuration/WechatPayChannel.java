package org.springframework.lab.configuration;

public class WechatPayChannel implements PayChannel {
	@Override
	public String name() {
		return "WechatPay";
	}
}
