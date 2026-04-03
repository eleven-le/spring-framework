package org.springframework.lab.classmigration.risk;

import java.math.BigDecimal;

/**
 * 风控上下文 —— 承载待评估的业务数据
 *
 * <p>对标 Spring：类比 EvaluationContext / RequestAttributes —— 贯穿整条评估链的上下文对象
 */
public class RiskContext {

	private final String userId;
	private final String orderId;
	private final BigDecimal amount;
	private final String deviceFingerprint;
	private final String ipAddress;

	public RiskContext(String userId, String orderId, BigDecimal amount,
			String deviceFingerprint, String ipAddress) {
		this.userId = userId;
		this.orderId = orderId;
		this.amount = amount;
		this.deviceFingerprint = deviceFingerprint;
		this.ipAddress = ipAddress;
	}

	public String getUserId() { return userId; }
	public String getOrderId() { return orderId; }
	public BigDecimal getAmount() { return amount; }
	public String getDeviceFingerprint() { return deviceFingerprint; }
	public String getIpAddress() { return ipAddress; }

	@Override
	public String toString() {
		return "RiskContext{user='" + userId + "', order='" + orderId + "', amount=" + amount + "}";
	}
}
