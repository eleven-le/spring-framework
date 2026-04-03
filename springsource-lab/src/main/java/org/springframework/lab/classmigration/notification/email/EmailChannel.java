package org.springframework.lab.classmigration.notification.email;

import org.springframework.lab.classmigration.notification.NotificationMessage;
import org.springframework.lab.classmigration.notification.NotificationResult;
import org.springframework.lab.classmigration.notification.support.AbstractNotificationChannel;

/**
 * 邮件通知渠道 —— 落地层
 *
 * <p>只实现 doSend()：调用 SMTP / SES
 */
public class EmailChannel extends AbstractNotificationChannel {

	@Override
	public String getChannelType() {
		return "email";
	}

	@Override
	protected NotificationResult doSend(NotificationMessage message) {
		System.out.println("        → 调用 SMTP 发送邮件, to=" + message.getRecipient());
		return NotificationResult.success(message.getMessageId(),
				"EMAIL_" + System.currentTimeMillis());
	}
}
