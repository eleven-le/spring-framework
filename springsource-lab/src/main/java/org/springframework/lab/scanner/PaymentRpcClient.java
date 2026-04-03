package org.springframework.lab.scanner;

/**
 * 支付 RPC 客户端 —— 标注了 @RpcClient，会被自定义 TypeFilter 扫描到
 */
@RpcClient("payment-service")
public interface PaymentRpcClient {

	boolean pay(String orderId, long amount);
}
