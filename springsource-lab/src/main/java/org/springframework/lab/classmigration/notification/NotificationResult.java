package org.springframework.lab.classmigration.notification;

/**
 * 通知发送结果
 */
public class NotificationResult {

	private final boolean success;
	private final String messageId;
	private final String thirdPartyId;  // 第三方流水号
	private final String message;

	private NotificationResult(boolean success, String messageId,
			String thirdPartyId, String message) {
		this.success = success;
		this.messageId = messageId;
		this.thirdPartyId = thirdPartyId;
		this.message = message;
	}

	public static NotificationResult success(String messageId, String thirdPartyId) {
		return new NotificationResult(true, messageId, thirdPartyId, "OK");
	}

	public static NotificationResult fail(String messageId, String message) {
		return new NotificationResult(false, messageId, null, message);
	}

	public boolean isSuccess() { return success; }

	@Override
	public String toString() {
		return success
				? "NotifyResult{OK, msgId='" + messageId + "', 3rdId='" + thirdPartyId + "'}"
				: "NotifyResult{FAIL, msgId='" + messageId + "', reason='" + message + "'}";
	}
}
