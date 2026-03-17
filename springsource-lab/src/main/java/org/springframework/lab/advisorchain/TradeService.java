package org.springframework.lab.advisorchain;

/**
 * 交易服务接口 —— 拦截链 Demo 的目标对象
 * 模拟 C 端交易场景：下单 / 退款
 */
public interface TradeService {

	/** 下单：金额 > 0 正常，金额 <= 0 抛异常 */
	String placeOrder(String userId, int amount);

	/** 退款 */
	String refund(String orderId);
}
