package org.springframework.lab.aabpp;

/**
 * Prototype 作用域 bean — 用于演示 "Prototype注入Singleton" 的 stale reference 坑
 */
public class RequestContext {
	private final String requestId;

	public RequestContext(String requestId) {
		this.requestId = requestId;
	}

	public String getRequestId() {
		return requestId;
	}

	@Override
	public String toString() {
		return "RequestContext{" + requestId + "}";
	}
}
