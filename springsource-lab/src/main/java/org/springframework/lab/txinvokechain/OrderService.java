package org.springframework.lab.txinvokechain;

import org.springframework.transaction.annotation.Transactional;

/**
 * 订单服务接口 — 演示 TransactionAttributeSource 四级回退解析
 *
 * <p>接口级别标注 @Transactional(readOnly = true)。
 * 在 {@code AbstractFallbackTransactionAttributeSource#computeTransactionAttribute}
 * 的回退链中，接口类级别是第 4 优先级（最低）。
 *
 * <p>四级回退顺序:
 * <ol>
 *   <li>目标方法 (specificMethod)</li>
 *   <li>目标类 (specificMethod.getDeclaringClass())</li>
 *   <li>接口方法 (original method)</li>
 *   <li>接口类 (method.getDeclaringClass()) ← 此处</li>
 * </ol>
 */
@Transactional(readOnly = true)
public interface OrderService {

	/** 接口方法无 @Transactional → 回退到接口类级别 → readOnly=true */
	String getOrder(String orderId);

	/** 实现类方法会覆盖此处 → 方法级别优先 */
	void placeOrder(String orderId, int amount);

	/** 实现类类级别覆盖 → 类级别优先于接口类级别 */
	void cancelOrder(String orderId);
}
