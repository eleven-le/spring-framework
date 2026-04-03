package org.springframework.lab.naming;

import org.springframework.stereotype.Component;

/**
 * 【Simple 前缀】"最简实现"：无额外功能，最小化。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>SimpleApplicationEventMulticaster — 最简事件广播器（同步、无线程池）</li>
 *   <li>SimpleBeanDefinitionRegistry — 最简 BD 注册表（只存取，无工厂能力）</li>
 *   <li>SimpleInstantiationStrategy — 最简实例化策略（反射调构造器）</li>
 * </ul>
 *
 * <p>命名规则：Simple 前缀 = "我只做最基本的事，多余功能一概没有"
 *
 * <p>与 Default 的区分：
 * Default = 功能完整、生产可用。
 * Simple = 功能最小化、适合测试 stub 或简单场景。
 * 选择标准：能用 Simple 就别用 Default，避免过度设计。
 */
@Component("simplePayChannel")
public class SimplePayChannel implements PayChannel {

	@Override
	public String channelCode() {
		return "SIMPLE";
	}

	/**
	 * 最简实现：直接返回成功，不走任何网关。
	 * 对照 SimpleBeanDefinitionRegistry —— 只有 Map 存取，没有工厂能力。
	 * 用途：单元测试 mock、本地开发环境、Demo 演示。
	 */
	@Override
	public PayResult pay(String orderId, long amountInCents) {
		System.out.println("    [Simple] 直接返回成功 (不走网关, 适合测试)");
		return new PayResult(orderId, true, "SIMPLE_PASS_THROUGH");
	}
}
