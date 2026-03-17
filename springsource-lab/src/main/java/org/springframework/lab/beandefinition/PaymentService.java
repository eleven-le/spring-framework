package org.springframework.lab.beandefinition;

import org.springframework.stereotype.Component;

@Component
public class PaymentService {

	public String pay(String orderId) {
		return "PAID-" + orderId;
	}
}
