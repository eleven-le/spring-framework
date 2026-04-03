package org.springframework.lab.classmigration.pay;

/**
 * 支付领域统一异常 —— 异常翻译的产物
 *
 * <p>对标 Spring：DataAccessException —— 将各厂商特定异常翻译为统一异常体系
 * <p>骨架类负责 catch 原始异常 → 包装为 PayException，调用方只需 catch 一种
 */
public class PayException extends RuntimeException {

	private final String channelCode;
	private final String errorCode;

	public PayException(String channelCode, String errorCode, String message, Throwable cause) {
		super("[" + channelCode + "] " + message, cause);
		this.channelCode = channelCode;
		this.errorCode = errorCode;
	}

	public PayException(String channelCode, String message) {
		this(channelCode, "UNKNOWN", message, null);
	}

	public String getChannelCode() { return channelCode; }
	public String getErrorCode() { return errorCode; }
}
