package org.springframework.lab.hierarchy;

/**
 * 父子容器都注册的同名 Bean —— 验证"子容器优先"语义
 */
public class SharedService {

	private final String origin;

	public SharedService(String origin) {
		this.origin = origin;
	}

	@Override
	public String toString() {
		return "SharedService{origin='" + origin + "'}";
	}
}
