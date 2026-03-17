package org.springframework.lab.configuration;

/**
 * 模拟 RPC 代理 Bean, 由 Registrar 动态注册.
 */
public class RpcProxy {

	private final String serviceName;

	public RpcProxy(String serviceName) {
		this.serviceName = serviceName;
	}

	@Override
	public String toString() {
		return "RpcProxy{service='" + serviceName + "'}";
	}
}
