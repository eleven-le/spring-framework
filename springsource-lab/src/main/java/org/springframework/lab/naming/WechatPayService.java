package org.springframework.lab.naming;

import org.springframework.stereotype.Component;

/**
 * 微信支付服务——继承 PayChannelSupport 复用工具方法。
 * 展示 Support 后缀的使用方式：继承获得工具，自己只写业务逻辑。
 *
 * <p>对照 Spring：
 * WebApplicationObjectSupport extends ApplicationObjectSupport
 *   → 继承了 getApplicationContext()、getMessageSourceAccessor()
 *   → 自己只加了 getServletContext()、getWebApplicationContext()
 */
@Component
public class WechatPayService extends PayChannelSupport {

	public PayResult executePayment(String orderId, long amountInCents) {
		// 使用 Support 提供的日志工具
		logPayment("发起微信支付", orderId, amountInCents);

		// 使用 Support 提供的重试工具
		return retryOnFailure(3, () -> {
			// 使用 Support 提供的配置读取工具
			String appId = getConfigValue("wechat.appId", "wx_default_appid");
			System.out.println("    [Wechat] 使用 appId=" + appId + " 调用微信统一下单");
			return new PayResult(orderId, true, "WECHAT_SUCCESS");
		});
	}
}
