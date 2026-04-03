package org.springframework.lab.classmigration.notification.sms;

import org.springframework.lab.classmigration.notification.NotificationMessage;
import org.springframework.lab.classmigration.notification.NotificationResult;
import org.springframework.lab.classmigration.notification.support.AbstractNotificationChannel;

/**
 * 短信通知渠道 —— 落地层
 *
 * <p>只实现 doSend()：调用阿里云短信网关
 * <p>幂等/重试/日志全部由骨架处理
 */
public class SmsChannel extends AbstractNotificationChannel {

	@Override
	public String getChannelType() {
		return "sms";
	}

	@Override
	protected NotificationResult doSend(NotificationMessage message) {
		// 真实场景：调用阿里云 SMS SDK / 腾讯云 SMS API
		System.out.println("        → 调用短信网关 SendSms API, to=" + message.getRecipient());
		return NotificationResult.success(message.getMessageId(),
				"SMS_" + System.currentTimeMillis());
	}
}
