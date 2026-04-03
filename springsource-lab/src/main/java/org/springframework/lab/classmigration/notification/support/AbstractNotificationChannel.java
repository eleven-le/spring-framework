package org.springframework.lab.classmigration.notification.support;

import org.springframework.lab.classmigration.notification.NotificationChannel;
import org.springframework.lab.classmigration.notification.NotificationMessage;
import org.springframework.lab.classmigration.notification.NotificationResult;

import java.util.HashSet;
import java.util.Set;

/**
 * 通知渠道骨架 —— 模板方法层
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 AbstractPlatformTransactionManager —— 骨架管挂起/恢复/同步回调，子类只管 doBegin</li>
 *   <li>对标 RetryTemplate —— 骨架管重试策略+退避策略，子类只管执行</li>
 * </ul>
 *
 * <h3>骨架封装了什么</h3>
 * <ol>
 *   <li>幂等检查 —— 同一 messageId 不重复发送（对标事务的重复提交保护）</li>
 *   <li>重试逻辑 —— 失败自动重试 N 次（对标 RetryTemplate 的退避策略）</li>
 *   <li>统一日志 —— 发送/成功/失败/重试 全链路日志</li>
 *   <li>异常兜底 —— 不让通知失败影响主流程</li>
 * </ol>
 *
 * <h3>子类只实现什么</h3>
 * <ul>
 *   <li>doSend() —— 具体的渠道发送（调短信网关/SMTP/APNs）</li>
 * </ul>
 *
 * <p>★ 对比三个骨架的共性：AbstractPayChannel 管签名+异常翻译，
 * AbstractRiskRule 管开关+适用检查，AbstractNotificationChannel 管幂等+重试。
 * 不同领域，同一个模板方法母题。
 */
public abstract class AbstractNotificationChannel implements NotificationChannel {

	private int maxRetries = 2;

	// 幂等表（实际场景：Redis SET NX + TTL）
	private final Set<String> sentMessageIds = new HashSet<>();

	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	/**
	 * 发送骨架 —— 幂等 → 日志 → 重试 → 降级
	 */
	@Override
	public final NotificationResult send(NotificationMessage message) {
		// Step 1: 幂等检查 —— 对标事务的 isExistingTransaction() 检查
		if (sentMessageIds.contains(message.getMessageId())) {
			System.out.println("      [" + getChannelType() + "] 消息已发送，幂等拦截: "
					+ message.getMessageId());
			return NotificationResult.success(message.getMessageId(), "IDEMPOTENT");
		}

		// Step 2: 带重试的发送
		Exception lastException = null;
		for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
			try {
				System.out.println("      [" + getChannelType() + "] 发送中 (第" + attempt + "次): "
						+ message);

				// 调用子类钩子
				NotificationResult result = doSend(message);

				// 成功则记录幂等标记
				sentMessageIds.add(message.getMessageId());
				System.out.println("      [" + getChannelType() + "] 发送成功: " + result);
				return result;

			} catch (Exception e) {
				lastException = e;
				if (attempt <= maxRetries) {
					System.out.println("      [" + getChannelType() + "] 发送失败，准备重试: "
							+ e.getMessage());
				}
			}
		}

		// Step 3: 重试耗尽 —— 降级处理
		System.out.println("      [" + getChannelType() + "] 重试耗尽，降级: "
				+ lastException.getMessage());
		return NotificationResult.fail(message.getMessageId(),
				getChannelType() + " 发送失败: " + lastException.getMessage());
	}

	// ==================== 子类钩子 ====================

	/** 具体发送逻辑 —— 子类唯一需要实现的方法 */
	protected abstract NotificationResult doSend(NotificationMessage message);
}
