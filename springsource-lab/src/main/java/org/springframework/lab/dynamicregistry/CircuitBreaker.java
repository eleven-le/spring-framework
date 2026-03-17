package org.springframework.lab.dynamicregistry;

public class CircuitBreaker implements Middleware {

	@Override
	public String name() {
		return "CircuitBreaker";
	}
}
