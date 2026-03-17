package org.springframework.lab.autowire;

/**
 * 支付渠道接口 —— 多实现场景的核心抽象
 * 用于演示: 按类型注入时多候选人的歧义消解
 */
public interface PayChannel {
	String route();
	String name();
}
