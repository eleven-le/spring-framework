package org.springframework.lab.naming;

/**
 * 【Adapter 后缀】适配器：将不兼容的接口转换为目标接口。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>HandlerAdapter — 将各种 Handler（Controller/@RequestMapping/HttpRequestHandler）适配为统一调用接口</li>
 *   <li>WebMvcConfigurerAdapter — 提供 default 空实现，让子类选择性覆盖（Java 8 前的适配器写法）</li>
 *   <li>SourceFilteringListener — 适配 ApplicationListener，只对特定源的事件生效</li>
 *   <li>ApplicationListenerMethodAdapter — 把 @EventListener 标注的方法适配为 ApplicationListener</li>
 * </ul>
 *
 * <p>命名规则：XxxAdapter = "我把 Xxx 的接口翻译成你需要的接口"
 *
 * <p>关键区分：
 * <ul>
 *   <li>vs Resolver：Resolver 翻译<b>数据</b>（channelCode→PayChannel），Adapter 翻译<b>接口</b>（ThirdPartySdk→PayChannel）</li>
 *   <li>vs Decorator：Decorator 接口不变加行为（PayChannel→PayChannel），Adapter 接口变了做桥接（异构→统一）</li>
 *   <li>vs Converter：Converter 翻译<b>值类型</b>（String→Integer），Adapter 翻译<b>行为契约</b></li>
 * </ul>
 *
 * <p>典型场景：接入第三方 SDK 时，第三方接口和你的标准接口不一致，用 Adapter 桥接。
 */
public class ThirdPartyPayAdapter implements PayChannel {

	private final ThirdPartySdk sdk;

	public ThirdPartyPayAdapter(ThirdPartySdk sdk) {
		this.sdk = sdk;
	}

	@Override
	public PayResult pay(String orderId, long amountInCents) {
		// 适配：我们的 PayChannel 接口 → 第三方 SDK 的异构接口
		// 单位适配：分 → 元
		// 参数适配：增加币种参数
		String thirdPartyResult = sdk.sendPayment(
				orderId,
				amountInCents / 100.0,
				"CNY"
		);

		// 返回值适配：第三方 String → 我们的 PayResult
		boolean success = "OK".equals(thirdPartyResult);
		return new PayResult(orderId, success,
				success ? "第三方支付成功" : "第三方返回: " + thirdPartyResult);
	}

	@Override
	public String channelCode() {
		return "THIRD_PARTY";
	}

	// ─── 模拟第三方 SDK ──────────────────────────────────────────────────────
	// 接口风格和 PayChannel 完全不同：参数类型不同(double vs long)、多了币种参数、返回 String 而非 VO

	/**
	 * 模拟第三方支付 SDK —— 接口和我们的 PayChannel 完全不兼容
	 */
	public static class ThirdPartySdk {
		public String sendPayment(String orderNo, double amountInYuan, String currency) {
			System.out.println("    [ThirdPartySDK] sendPayment(orderNo=" + orderNo
					+ ", amount=" + amountInYuan + currency + ")");
			return "OK";
		}
	}
}
