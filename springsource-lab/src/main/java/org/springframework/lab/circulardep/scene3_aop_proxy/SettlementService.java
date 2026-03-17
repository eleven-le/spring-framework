package org.springframework.lab.circulardep.scene3_aop_proxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Scene 3: 结算服务, 反向依赖 TradeService。
 * 当 populateBean(settle) 或 populateBean(trade) 触发循环时,
 * settle 拿到的是 trade 的 AOP 代理(从三级缓存工厂产出)。
 */
@Component
public class SettlementService {

	@Autowired
	private TradeService tradeService;

	public SettlementService() {
		System.out.println("  [Scene3] SettlementService 构造");
	}

	public String settle(String orderId) {
		return "Settled[" + orderId + "]";
	}

	public String whoIsMyTrade() {
		return "My trade is: " + tradeService.getClass().getName();
	}
}
