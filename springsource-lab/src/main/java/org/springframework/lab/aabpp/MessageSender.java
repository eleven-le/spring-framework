package org.springframework.lab.aabpp;

/**
 * 消息发送接口 — 用于演示多种注入形态
 */
public interface MessageSender {
	String send(String content);
	String channel();
}
