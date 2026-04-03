package org.springframework.lab.naming;

/**
 * 支付渠道——顶层接口契约。
 * 对照 Spring：BeanFactory（纯接口，只声明能力）
 *
 * <p>命名规则：接口名 = 名词/名词组合，不加前缀后缀。
 */
public interface PayChannel {

	/** 执行支付，返回支付结果 */
	PayResult pay(String orderId, long amountInCents);

	/** 渠道标识 */
	String channelCode();
}
