package org.springframework.lab.extensionmap;

/**
 * 被 BDRPP 动态注册的灰度服务 (模拟 Feature Toggle 场景)
 */
public class GrayService {

	public String route(String userId) {
		return "gray-route: " + userId;
	}
}
