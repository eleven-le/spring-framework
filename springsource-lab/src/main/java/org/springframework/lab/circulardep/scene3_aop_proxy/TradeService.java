package org.springframework.lab.circulardep.scene3_aop_proxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Scene 3: AOP 代理 + 循环依赖 — 三级缓存的真正价值。
 *
 * 当 TradeService 被 AOP 代理 (LogAspect 切面):
 *   1. createBeanInstance(trade) → 空壳 trade
 *   2. addSingletonFactory("tradeService", () -> getEarlyBeanReference(trade))
 *      → 这个 lambda 里 SmartInstantiationAwareBeanPostProcessor 会提前创建代理
 *   3. populateBean(trade) → getBean("settlementService") → settle 需要 trade
 *   4. getSingleton("tradeService", true) → 调用工厂 → getEarlyBeanReference
 *      → AbstractAutoProxyCreator#getEarlyBeanReference 创建 AOP 代理
 *      → 代理放入二级缓存 earlySingletonObjects, 三级缓存删除
 *   5. settle 注入的是代理后的 trade
 *   6. initializeBean(trade) 的 postProcessAfterInitialization 中
 *      AbstractAutoProxyCreator 检查 earlyProxyReferences 缓存, 发现已提前代理, 跳过
 *   7. doCreateBean 末尾: exposedObject == bean? → 用 earlySingletonReference 替换
 *
 * 这就是为什么需要三级缓存而不是两级:
 * 如果只有两级, 那么所有 Bean 都要提前创建代理(不管有没有循环依赖),
 * 三级缓存延迟到「真正被循环依赖时才创建代理」。
 *
 * 断点: AbstractAutoProxyCreator#getEarlyBeanReference
 */
@Component
public class TradeService {

	@Autowired
	private SettlementService settlementService;

	public TradeService() {
		System.out.println("  [Scene3] TradeService 构造");
	}

	public String executeTrade(String orderId) {
		return "Trade[" + orderId + "] -> " + settlementService.settle(orderId);
	}
}
