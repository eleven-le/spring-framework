package org.springframework.lab.classmigration.pay.support;

import org.springframework.lab.classmigration.pay.PayChannel;
import org.springframework.lab.classmigration.pay.PayException;
import org.springframework.lab.classmigration.pay.PayOrder;
import org.springframework.lab.classmigration.pay.PayResult;

/**
 * 支付渠道骨架类 —— 模板方法层，定义 "怎么做的流程"
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 AbstractPlatformTransactionManager —— getTransaction() 是 final 骨架，
 *       doGetTransaction()/doBegin()/doCommit() 是子类钩子</li>
 *   <li>对标 AbstractBeanFactory —— doGetBean() 是骨架，
 *       createBean()/getBeanDefinition() 是子类钩子</li>
 * </ul>
 *
 * <h3>骨架封装了什么（60%+ 公共逻辑）</h3>
 * <ol>
 *   <li>参数校验 —— 所有渠道都需要</li>
 *   <li>签名验签模板 —— 调用子类的 doSign()，但签名时机由骨架控制</li>
 *   <li>统一日志 —— 请求/响应/耗时，子类不用关心</li>
 *   <li>异常翻译 —— catch 各渠道原始异常 → 包装为 PayException</li>
 *   <li>耗时统计 —— 性能监控埋点</li>
 * </ol>
 *
 * <h3>子类只实现什么（40% 差异逻辑）</h3>
 * <ul>
 *   <li>doSign() —— 各渠道签名算法不同（RSA2 vs HMAC-SHA256）</li>
 *   <li>doExecutePrepay() —— 各渠道 HTTP 调用和响应解析不同</li>
 *   <li>doExecuteQuery() —— 各渠道查询接口不同</li>
 * </ul>
 *
 * <p>断点建议：在 {@link #prepay(PayOrder)} 打断点，F7 跟入 doSign() / doExecutePrepay()
 * 观察模板方法如何调度子类钩子 —— 与 APTM#getTransaction() 调度 doBegin() 完全同构。
 */
public abstract class AbstractPayChannel implements PayChannel {

	// ==================== 模板方法（final 锁流程） ====================

	/**
	 * 预下单骨架 —— 对标 AbstractPlatformTransactionManager#getTransaction()
	 *
	 * <p>流程：校验 → 签名 → 日志 → 调用 → 异常翻译
	 * <p>★ 断点位置：这里打断点，F7 进入 doSign() 和 doExecutePrepay() 观察多态分派
	 */
	@Override
	public final PayResult prepay(PayOrder order) {
		// Step 1: 参数校验 —— 骨架统一做，子类不用操心
		validateOrder(order);

		// Step 2: 签名 —— 调用子类钩子
		String signature = doSign(order);

		// Step 3: 统一日志（请求）
		long start = System.currentTimeMillis();
		System.out.println("    [" + getChannelCode() + "] 开始预下单: " + order.getOrderId()
				+ ", 签名=" + signature);

		try {
			// Step 4: 调用子类的实际执行逻辑 —— 这就是 "钩子"
			PayResult result = doExecutePrepay(order, signature);

			// Step 5: 统一日志（响应 + 耗时）
			long cost = System.currentTimeMillis() - start;
			System.out.println("    [" + getChannelCode() + "] 预下单完成: " + result
					+ " (" + cost + "ms)");
			return result;

		} catch (PayException e) {
			throw e;  // 已翻译的异常直接抛
		} catch (Exception e) {
			// Step 6: 异常翻译 —— 对标 Spring 的 SQLExceptionTranslator
			throw new PayException(getChannelCode(), "CHANNEL_ERROR",
					"渠道调用失败: " + e.getMessage(), e);
		}
	}

	@Override
	public final PayResult queryStatus(String orderId) {
		System.out.println("    [" + getChannelCode() + "] 查询订单状态: " + orderId);
		try {
			return doExecuteQuery(orderId);
		} catch (Exception e) {
			throw new PayException(getChannelCode(), "QUERY_ERROR",
					"查询失败: " + e.getMessage(), e);
		}
	}

	// ==================== 子类钩子（abstract/protected） ====================

	/** 签名算法 —— 各渠道不同：支付宝=RSA2, 微信=HMAC-SHA256 */
	protected abstract String doSign(PayOrder order);

	/** 实际预下单 HTTP 调用 —— 各渠道 API 不同 */
	protected abstract PayResult doExecutePrepay(PayOrder order, String signature);

	/** 实际查询 HTTP 调用 */
	protected abstract PayResult doExecuteQuery(String orderId);

	// ==================== 骨架内部工具方法 ====================

	private void validateOrder(PayOrder order) {
		if (order == null) {
			throw new PayException(getChannelCode(), "参数不能为空");
		}
		if (order.getAmount() == null || order.getAmount().signum() <= 0) {
			throw new PayException(getChannelCode(), "金额必须大于0");
		}
	}
}
