package org.springframework.lab.naming;

/**
 * 支付结果 VO
 */
public class PayResult {
	private final String orderId;
	private final boolean success;
	private final String message;
	private final String riskTag;

	public PayResult(String orderId, boolean success, String message) {
		this(orderId, success, message, null);
	}

	public PayResult(String orderId, boolean success, String message, String riskTag) {
		this.orderId = orderId;
		this.success = success;
		this.message = message;
		this.riskTag = riskTag;
	}

	public String getOrderId() { return orderId; }
	public boolean isSuccess() { return success; }
	public String getMessage() { return message; }
	public String getRiskTag() { return riskTag; }

	@Override
	public String toString() {
		String base = "PayResult{orderId='" + orderId + "', success=" + success + ", msg='" + message + "'";
		if (riskTag != null) {
			base += ", riskTag='" + riskTag + "'";
		}
		return base + "}";
	}
}
