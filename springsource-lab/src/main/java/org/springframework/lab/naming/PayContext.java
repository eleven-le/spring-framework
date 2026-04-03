package org.springframework.lab.naming;

/**
 * 支付上下文——被 PayContextAware 注入的对象。
 * 对照 Spring 的 ApplicationContext。
 */
public class PayContext {
	private final String merchantId;
	private final String environment;

	public PayContext(String merchantId, String environment) {
		this.merchantId = merchantId;
		this.environment = environment;
	}

	public String getMerchantId() { return merchantId; }
	public String getEnvironment() { return environment; }

	@Override
	public String toString() {
		return "PayContext{merchant='" + merchantId + "', env='" + environment + "'}";
	}
}
