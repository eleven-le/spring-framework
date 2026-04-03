package org.springframework.lab.classmigration.pay;

import java.math.BigDecimal;

/**
 * 支付订单 —— 值对象，贯穿整条支付链路
 */
public class PayOrder {

	private final String orderId;
	private final String channelCode;   // alipay / wechat / unionpay
	private final BigDecimal amount;
	private final String subject;       // 商品描述

	public PayOrder(String orderId, String channelCode, BigDecimal amount, String subject) {
		this.orderId = orderId;
		this.channelCode = channelCode;
		this.amount = amount;
		this.subject = subject;
	}

	public String getOrderId() { return orderId; }
	public String getChannelCode() { return channelCode; }
	public BigDecimal getAmount() { return amount; }
	public String getSubject() { return subject; }

	@Override
	public String toString() {
		return "PayOrder{id='" + orderId + "', channel='" + channelCode + "', amount=" + amount + "}";
	}
}
