package org.springframework.lab.classmigration.notification.template;

import org.springframework.lab.classmigration.notification.NotificationChannel;
import org.springframework.lab.classmigration.notification.NotificationMessage;
import org.springframework.lab.classmigration.notification.NotificationResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知门面 —— 业务代码唯一入口
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 JdbcTemplate —— 统一入口，屏蔽底层细节</li>
 *   <li>对标 RestTemplate —— 门面封装 HTTP 调用的复杂性</li>
 * </ul>
 *
 * <h3>与 PayTemplate 对比 —— 同构但不同域</h3>
 * <ul>
 *   <li>PayTemplate：路由 + 异常兜底</li>
 *   <li>NotificationTemplate：路由 + 多渠道广播能力</li>
 *   <li>两者都是 Resolver + Channel 的组合 —— 门面模式的通用骨架</li>
 * </ul>
 */
public class NotificationTemplate {

	private final Map<String, NotificationChannel> channelMap = new ConcurrentHashMap<>();

	public NotificationTemplate(List<NotificationChannel> channels) {
		for (NotificationChannel ch : channels) {
			channelMap.put(ch.getChannelType(), ch);
			System.out.println("    [NotifyTemplate] 注册渠道: " + ch.getChannelType()
					+ " → " + ch.getClass().getSimpleName());
		}
	}

	/** 单渠道发送 */
	public NotificationResult send(NotificationMessage message) {
		System.out.println("  [NotifyTemplate] 发送通知: " + message);
		NotificationChannel channel = channelMap.get(message.getChannelType());
		if (channel == null) {
			return NotificationResult.fail(message.getMessageId(),
					"不支持的通知渠道: " + message.getChannelType());
		}
		return channel.send(message);
	}

	/** 多渠道广播 —— 同一内容发往多个渠道（如短信+Push） */
	public void broadcast(String messageIdPrefix, String content,
			Map<String, String> channelToRecipient) {
		System.out.println("  [NotifyTemplate] 广播通知到 " + channelToRecipient.size() + " 个渠道");
		int i = 0;
		for (Map.Entry<String, String> entry : channelToRecipient.entrySet()) {
			String channelType = entry.getKey();
			String recipient = entry.getValue();
			NotificationMessage msg = new NotificationMessage(
					messageIdPrefix + "_" + (i++), channelType, recipient, "BROADCAST", content);
			send(msg);
		}
	}
}
