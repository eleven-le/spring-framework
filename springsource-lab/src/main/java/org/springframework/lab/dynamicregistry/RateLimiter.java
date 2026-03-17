package org.springframework.lab.dynamicregistry;

public class RateLimiter implements Middleware {

	@Override
	public String name() {
		return "RateLimiter";
	}
}
