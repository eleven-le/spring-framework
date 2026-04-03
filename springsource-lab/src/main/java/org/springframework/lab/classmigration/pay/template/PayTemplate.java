package org.springframework.lab.classmigration.pay.template;

import org.springframework.lab.classmigration.pay.PayChannel;
import org.springframework.lab.classmigration.pay.PayException;
import org.springframework.lab.classmigration.pay.PayOrder;
import org.springframework.lab.classmigration.pay.PayResult;
import org.springframework.lab.classmigration.pay.resolver.PayChannelResolver;

/**
 * 支付门面 —— 业务代码唯一入口
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 JdbcTemplate —— 业务代码只调 jdbcTemplate.query()，不碰 Connection/Statement</li>
 *   <li>对标 TransactionTemplate —— 业务代码只调 transactionTemplate.execute()，不碰 TM</li>
 *   <li>对标 ApplicationContext 本身 —— 门面模式，对外 ctx.getBean()，内部委托 BeanFactory</li>
 * </ul>
 *
 * <h3>门面封装了什么</h3>
 * <ol>
 *   <li>渠道路由 —— 根据 PayOrder.channelCode 自动选渠道</li>
 *   <li>异常兜底 —— catch PayException 后返回统一 fail 结果</li>
 *   <li>业务编排 —— 未来可加：幂等检查、重复下单拦截、结果缓存</li>
 * </ol>
 *
 * <p>业务代码调用示例：
 * <pre>
 *   payTemplate.pay(new PayOrder("ORD001", "alipay", BigDecimal.valueOf(99.9), "iPhone"));
 * </pre>
 * 一行搞定，不关心哪个渠道、怎么签名、怎么重试。
 */
public class PayTemplate {

	private final PayChannelResolver resolver;

	public PayTemplate(PayChannelResolver resolver) {
		this.resolver = resolver;
	}

	/**
	 * 统一支付入口 —— 对标 JdbcTemplate#execute()
	 *
	 * <p>业务代码只调这一个方法，完全屏蔽渠道细节。
	 */
	public PayResult pay(PayOrder order) {
		System.out.println("  [PayTemplate] 开始支付: " + order);
		try {
			// 1. 路由到具体渠道（策略模式）
			PayChannel channel = resolver.resolve(order.getChannelCode());
			// 2. 调用渠道的模板方法（模板方法模式）
			return channel.prepay(order);
		} catch (PayException e) {
			System.out.println("  [PayTemplate] 支付失败: " + e.getMessage());
			return PayResult.fail(e.getMessage());
		}
	}

	/** 统一查询入口 */
	public PayResult query(String channelCode, String orderId) {
		try {
			PayChannel channel = resolver.resolve(channelCode);
			return channel.queryStatus(orderId);
		} catch (PayException e) {
			return PayResult.fail(e.getMessage());
		}
	}
}
