package org.springframework.lab.scanner;

/**
 * 支付策略接口 —— 用于演示按接口类型扫描 (AssignableTypeFilter)
 */
public interface PayStrategy {

	String channel();

	boolean pay(String orderId, long amount);
}
