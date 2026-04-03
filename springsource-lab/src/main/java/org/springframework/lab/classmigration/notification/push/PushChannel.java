package org.springframework.lab.classmigration.notification.push;

import org.springframework.lab.classmigration.notification.NotificationMessage;
import org.springframework.lab.classmigration.notification.NotificationResult;
import org.springframework.lab.classmigration.notification.support.AbstractNotificationChannel;

/**
 * Push 通知渠道 —— 落地层
 *
 * <p>只实现 doSend()：调用 APNs / FCM
 */
public class PushChannel extends AbstractNotificationChannel {

	@Override
	public String getChannelType() {
		return "push";
	}

	@Override
	protected NotificationResult doSend(NotificationMessage message) {
		System.out.println("        → 调用 APNs/FCM Push API, to=" + message.getRecipient());
		return NotificationResult.success(message.getMessageId(),
				"PUSH_" + System.currentTimeMillis());
	}
}
