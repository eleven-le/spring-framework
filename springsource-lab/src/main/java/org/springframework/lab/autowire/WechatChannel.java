package org.springframework.lab.autowire;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 微信支付渠道 —— @Primary 标记为默认渠道
 * 演示: 当没有 @Qualifier 限定时, @Primary 胜出
 */
@Component
@Primary
@Qualifier("wechat")
public class WechatChannel implements PayChannel {
	@Override
	public String route() {
		return "WECHAT_JSAPI";
	}

	@Override
	public String name() {
		return "WechatPay";
	}
}
