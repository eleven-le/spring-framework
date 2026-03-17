package org.springframework.lab.configuration;

public class AlipayChannel implements PayChannel {
	@Override
	public String name() {
		return "Alipay";
	}
}
