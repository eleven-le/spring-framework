package org.springframework.lab.naming;

/**
 * 【Decorator 后缀】装饰器：透明增强，包装对象但不改变接口。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>TransactionAwareCacheDecorator — 给 Cache 加事务感知（提交后才写缓存）</li>
 *   <li>HttpServletRequestWrapper — 给 Request 加自定义属性/头</li>
 *   <li>WebSocketSessionDecorator — 给 WebSocket Session 加并发控制/限流</li>
 *   <li>BufferingClientHttpResponseWrapper — 给 Response 加可重复读 body</li>
 * </ul>
 *
 * <p>命名规则：XxxDecorator = "我包装 Xxx，增加额外行为，但接口不变"
 *
 * <p>关键区分：
 * <ul>
 *   <li>vs Processor：Decorator 包装一个对象<b>长期持有</b>，Processor 处理一次就走</li>
 *   <li>vs Adapter：Decorator 接口不变（PayChannel→PayChannel），Adapter 接口变了（ThirdPartySdk→PayChannel）</li>
 *   <li>vs Proxy：Decorator 用组合实现（new Decorator(target)），Proxy 用动态代理（JDK/CGLIB）</li>
 * </ul>
 *
 * <p>设计意图：给已有对象"套一层"行为，可以任意叠加（日志Decorator → 限流Decorator → 实际实现）。
 */
public class LoggingPayChannelDecorator implements PayChannel {

	private final PayChannel delegate;

	public LoggingPayChannelDecorator(PayChannel delegate) {
		this.delegate = delegate;
	}

	@Override
	public PayResult pay(String orderId, long amountInCents) {
		long start = System.currentTimeMillis();
		System.out.println("    [Decorator] >>> 支付开始: orderId=" + orderId
				+ ", amount=" + PayUtils.formatAmount(amountInCents) + "元");

		PayResult result = delegate.pay(orderId, amountInCents);

		long cost = System.currentTimeMillis() - start;
		System.out.println("    [Decorator] <<< 支付完成: "
				+ (result.isSuccess() ? "成功" : "失败") + ", 耗时=" + cost + "ms");
		return result;
	}

	@Override
	public String channelCode() {
		return delegate.channelCode();
	}
}
