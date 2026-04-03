package org.springframework.lab.aabpp;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class SmsSender implements MessageSender {
	@Override
	public String send(String content) {
		return "[SMS] " + content;
	}

	@Override
	public String channel() {
		return "SMS";
	}
}
