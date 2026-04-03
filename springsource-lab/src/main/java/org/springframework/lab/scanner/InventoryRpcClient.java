package org.springframework.lab.scanner;

/**
 * 库存 RPC 客户端 —— 标注了 @RpcClient，会被自定义 TypeFilter 扫描到
 */
@RpcClient("inventory-service")
public interface InventoryRpcClient {

	int queryStock(String productId);
}
