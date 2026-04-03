package org.springframework.lab.classmigration.notification;

/**
 * 通知消息 —— 值对象，贯穿通知链路
 */
public class NotificationMessage {

	private final String messageId;
	private final String channelType;   // sms / email / push
	private final String recipient;     // 手机号/邮箱/设备token
	private final String templateCode;  // 模板编码
	private final String content;       // 渲染后内容

	public NotificationMessage(String messageId, String channelType,
			String recipient, String templateCode, String content) {
		this.messageId = messageId;
		this.channelType = channelType;
		this.recipient = recipient;
		this.templateCode = templateCode;
		this.content = content;
	}

	public String getMessageId() { return messageId; }
	public String getChannelType() { return channelType; }
	public String getRecipient() { return recipient; }
	public String getTemplateCode() { return templateCode; }
	public String getContent() { return content; }

	@Override
	public String toString() {
		return "Notification{id='" + messageId + "', channel='" + channelType
				+ "', to='" + recipient + "'}";
	}
}
