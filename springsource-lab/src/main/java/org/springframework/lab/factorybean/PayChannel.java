package org.springframework.lab.factorybean;

/**
 * 支付渠道接口 -- FactoryBean 产物的目标类型
 * 模拟真实场景: RPC Stub / SDK Client / MyBatis Mapper
 */
public interface PayChannel {

	String pay(String orderId, long amountCents);

	String channel();
}
