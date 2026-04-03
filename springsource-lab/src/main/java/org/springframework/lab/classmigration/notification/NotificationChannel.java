package org.springframework.lab.classmigration.notification;

/**
 * 通知渠道接口 —— 契约层
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 MessageSource —— 只定义 getMessage()，不关心消息从哪来</li>
 *   <li>对标 Resource —— 统一抽象，子类可以是 ClassPath/File/URL</li>
 * </ul>
 *
 * <h3>与 PayChannel 的对比</h3>
 * <ul>
 *   <li>PayChannel：下单→查询（有状态变更）</li>
 *   <li>NotificationChannel：发送（幂等，可重试）</li>
 *   <li>两者都用同一个迁移模式：接口→骨架→实现，验证模式的通用性</li>
 * </ul>
 */
public interface NotificationChannel {

	/** 渠道类型标识 */
	String getChannelType();

	/** 发送通知 */
	NotificationResult send(NotificationMessage message);
}
