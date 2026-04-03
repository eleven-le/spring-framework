package org.springframework.lab.processortour;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 通知服务 — 演示 BPP+Advisor 路线（@Async）。
 *
 * <p>@Async 走的是 AsyncAnnotationBeanPostProcessor → AbstractAdvisingBeanPostProcessor，
 * 它在 postProcessAfterInitialization 时自己创建代理（不依赖 AutoProxyRegistrar）。
 */
@Service
public class NotificationService {

	@Async
	public void sendNotification(String userId, String message) {
		System.out.println("    [NotificationService#sendNotification] 异步发送: "
				+ message + " → " + userId + " (thread=" + Thread.currentThread().getName() + ")");
	}
}
