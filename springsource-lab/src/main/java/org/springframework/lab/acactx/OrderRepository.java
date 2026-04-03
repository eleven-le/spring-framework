package org.springframework.lab.acactx;

/**
 * 由 @Bean 方法注册的仓储类 — 不带 @Component
 * 验证: register 路径下的 @Bean 方法在 refresh 第 5 步才被 CCPP 解析为 BD
 */
public class OrderRepository {

	@Override
	public String toString() {
		return "OrderRepository@" + Integer.toHexString(hashCode());
	}
}
