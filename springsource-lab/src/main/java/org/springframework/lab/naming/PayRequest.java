package org.springframework.lab.naming;

/**
 * 支付请求——Processor 的处理对象。
 * Processor 会对 PayRequest 做增强（加风控标记、加日志追踪ID等）。
 */
public class PayRequest {
	private final String orderId;
	private final long amountInCents;
	private final String channelCode;
	private String riskTag;      // Processor 增强字段
	private String traceId;      // Processor 增强字段

	public PayRequest(String orderId, long amountInCents, String channelCode) {
		this.orderId = orderId;
		this.amountInCents = amountInCents;
		this.channelCode = channelCode;
	}

	public String getOrderId() { return orderId; }
	public long getAmountInCents() { return amountInCents; }
	public String getChannelCode() { return channelCode; }
	public String getRiskTag() { return riskTag; }
	public String getTraceId() { return traceId; }
	public void setRiskTag(String riskTag) { this.riskTag = riskTag; }
	public void setTraceId(String traceId) { this.traceId = traceId; }

	@Override
	public String toString() {
		return "PayRequest{order='" + orderId + "', amount=" + amountInCents
				+ ", channel='" + channelCode + "'"
				+ (riskTag != null ? ", risk='" + riskTag + "'" : "")
				+ (traceId != null ? ", trace='" + traceId + "'" : "")
				+ "}";
	}
}
