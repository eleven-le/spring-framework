package org.springframework.lab.aabpp;

import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Primary
@Order(2)
public class PushSender implements MessageSender {
	@Override
	public String send(String content) {
		return "[PUSH] " + content;
	}

	@Override
	public String channel() {
		return "PUSH";
	}
}
