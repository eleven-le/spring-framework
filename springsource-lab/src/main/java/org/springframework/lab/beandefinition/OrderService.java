package org.springframework.lab.beandefinition;

import org.springframework.stereotype.Component;

@Component
public class OrderService {

	public String createOrder(String productId) {
		return "ORDER-" + productId;
	}
}
