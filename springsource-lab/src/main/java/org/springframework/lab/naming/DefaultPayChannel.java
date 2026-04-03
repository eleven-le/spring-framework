package org.springframework.lab.naming;

import org.springframework.stereotype.Component;

/**
 * 【Default 前缀】"够用的默认实现"：覆盖 80% 场景，拿来就能用。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>DefaultListableBeanFactory — BeanFactory 的"默认完整实现"</li>
 *   <li>DefaultSingletonBeanRegistry — 单例注册表的默认实现</li>
 *   <li>DefaultLifecycleProcessor — 生命周期处理器的默认实现</li>
 * </ul>
 *
 * <p>命名规则：Default 前缀 = "我是标准实现，直接用就行，不需要继承"
 *
 * <p>为什么不叫 PayChannelImpl？
 * "Impl" 只说明"我实现了接口"（废话），"Default" 说明"我是默认选择，够用就别换"。
 * 当出现第二个实现时，Default 帮你区分谁是主角、谁是特化。
 */
@Component("defaultPayChannel")
public class DefaultPayChannel extends AbstractPayChannel {

	@Override
	public String channelCode() {
		return "DEFAULT";
	}

	/**
	 * 默认实现：走标准支付网关，覆盖大部分支付场景。
	 * 对照 DefaultListableBeanFactory#resolveDependency —— 提供完整的默认解析逻辑。
	 */
	@Override
	protected PayResult doExecute(String orderId, long amountInCents) {
		System.out.println("    [Default] 走标准支付网关, 金额=" + amountInCents + "分");
		return new PayResult(orderId, true, "DEFAULT_GATEWAY_SUCCESS");
	}

	@Override
	protected void onSuccess(String orderId, PayResult result) {
		System.out.println("    [Default] 支付成功, 发送支付成功通知");
	}
}
