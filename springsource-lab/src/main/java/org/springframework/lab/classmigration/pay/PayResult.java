package org.springframework.lab.classmigration.pay;

/**
 * 支付结果 —— 统一返回值，屏蔽各渠道差异
 *
 * <p>对标 Spring：类比 TransactionStatus —— 骨架产出的统一状态对象
 */
public class PayResult {

	private final boolean success;
	private final String channelTradeNo;  // 渠道流水号
	private final String message;

	private PayResult(boolean success, String channelTradeNo, String message) {
		this.success = success;
		this.channelTradeNo = channelTradeNo;
		this.message = message;
	}

	public static PayResult success(String channelTradeNo) {
		return new PayResult(true, channelTradeNo, "OK");
	}

	public static PayResult fail(String message) {
		return new PayResult(false, null, message);
	}

	public boolean isSuccess() { return success; }
	public String getChannelTradeNo() { return channelTradeNo; }
	public String getMessage() { return message; }

	@Override
	public String toString() {
		return success
				? "PayResult{SUCCESS, tradeNo='" + channelTradeNo + "'}"
				: "PayResult{FAIL, msg='" + message + "'}";
	}
}
