package org.springframework.lab.scanner;

/**
 * Mock 支付策略 —— 标注了 @MockBean，会被 ExcludeFilter 排除
 */
@MockBean
public class MockPayStrategy implements PayStrategy {

	@Override
	public String channel() {
		return "MOCK";
	}

	@Override
	public boolean pay(String orderId, long amount) {
		System.out.println("    [Mock] 假装支付 " + orderId);
		return true;
	}
}
