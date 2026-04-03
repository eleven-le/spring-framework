package org.springframework.lab.beanfactory;

/**
 * 支付渠道 — 多实现场景的目标类。
 * 模拟 C 端交易链路中"多渠道/多策略"按类型查找的注入歧义问题。
 */
public class PayChannel {

	private final String code;
	private final String name;

	public PayChannel(String code, String name) {
		this.code = code;
		this.name = name;
	}

	public String getCode() { return code; }
	public String getName() { return name; }

	@Override
	public String toString() {
		return "PayChannel{" + code + "=" + name + "}";
	}
}
