package org.springframework.lab.advisorchain;

/**
 * 交易服务实现 —— 纯业务逻辑，不含任何治理代码
 */
public class TradeServiceImpl implements TradeService {

	@Override
	public String placeOrder(String userId, int amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("金额必须 > 0, 当前: " + amount);
		}
		System.out.println("  [TARGET] placeOrder 执行: userId=" + userId + ", amount=" + amount);
		return "ORDER-" + System.currentTimeMillis();
	}

	@Override
	public String refund(String orderId) {
		System.out.println("  [TARGET] refund 执行: orderId=" + orderId);
		return "REFUND-OK";
	}
}
