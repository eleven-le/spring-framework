package org.springframework.lab.autowire;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 订单服务 —— 依赖注入解析的核心演练场
 *
 * 场景1: @Qualifier 精确路由 → 注入指定渠道
 * 场景2: @Primary 默认兜底   → 无 Qualifier 时走 WechatChannel
 * 场景3: ObjectProvider      → 灰度风控引擎可选注入
 * 场景4: List/Map 多候选收集 → 聚合所有渠道
 * 场景5: ObjectProvider.stream() → 延迟流式消费
 *
 * 断点建议: 在此类构造/字段注入处打断点, 观察 DependencyDescriptor 内容
 */
@Component
public class OrderService {

	// ========== 场景1: @Qualifier 精确匹配 ==========
	@Autowired
	@Qualifier("alipay")
	private PayChannel alipayChannel;

	// ========== 场景2: 无 Qualifier, @Primary 生效 → WechatChannel ==========
	@Autowired
	private PayChannel defaultChannel;

	// ========== 场景3: ObjectProvider 可选注入(灰度组件) ==========
	// RiskEngine 没有任何实现被注册, getIfAvailable() 返回 null, 不报错
	private final RiskEngine riskEngine;

	// ========== 场景4: List 收集所有候选人 ==========
	@Autowired
	private List<PayChannel> allChannels;

	// ========== 场景5: Map<beanName, instance> 收集 ==========
	@Autowired
	private Map<String, PayChannel> channelMap;

	// ========== 场景6: ObjectProvider 延迟 + stream ==========
	private final ObjectProvider<PayChannel> channelProvider;

	@Autowired
	public OrderService(ObjectProvider<RiskEngine> riskEngineProvider,
						ObjectProvider<PayChannel> channelProvider) {
		// 灰度: 没有 RiskEngine 实现时返回 null, 不会启动失败
		this.riskEngine = riskEngineProvider.getIfAvailable();
		this.channelProvider = channelProvider;
	}

	/** 场景1: 用 @Qualifier 精确指定走支付宝 */
	public void payViaAlipay(String orderId) {
		System.out.println("[Qualifier精确路由] orderId=" + orderId
				+ " → channel=" + alipayChannel.route());
	}

	/** 场景2: 无 Qualifier, @Primary 默认走微信 */
	public void payViaDefault(String orderId) {
		System.out.println("[@Primary默认路由] orderId=" + orderId
				+ " → channel=" + defaultChannel.route() + " (" + defaultChannel.name() + ")");
	}

	/** 场景3: ObjectProvider 可选注入灰度风控 */
	public void evaluateRisk(String orderId) {
		if (riskEngine != null) {
			boolean pass = riskEngine.evaluate(orderId);
			System.out.println("[风控引擎] orderId=" + orderId + " → pass=" + pass);
		} else {
			System.out.println("[风控引擎] 灰度未上线, 跳过风控 (ObjectProvider.getIfAvailable=null)");
		}
	}

	/** 场景4: List 收集所有实现 */
	public void listAllChannels() {
		System.out.println("[List注入] 所有支付渠道:");
		allChannels.forEach(ch ->
				System.out.println("  - " + ch.name() + " → " + ch.route()));
	}

	/** 场景5: Map 收集(beanName → instance) */
	public void showChannelMap() {
		System.out.println("[Map注入] beanName → 渠道:");
		channelMap.forEach((name, ch) ->
				System.out.println("  - " + name + " → " + ch.route()));
	}

	/** 场景6: ObjectProvider.orderedStream() 延迟流式消费 */
	public void streamChannels() {
		String routes = channelProvider.orderedStream()
				.map(PayChannel::name)
				.collect(Collectors.joining(" → "));
		System.out.println("[ObjectProvider.stream] 延迟流式: " + routes);
	}
}
