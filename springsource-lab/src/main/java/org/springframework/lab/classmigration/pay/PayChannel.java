package org.springframework.lab.classmigration.pay;

/**
 * 支付渠道接口 —— 契约层，定义 "能做什么"
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 PlatformTransactionManager —— 只定义 getTransaction/commit/rollback 三个能力</li>
 *   <li>对标 BeanFactory —— 只定义 getBean/containsBean 等只读能力</li>
 * </ul>
 *
 * <h3>设计决策</h3>
 * <ul>
 *   <li>接口只有 3 个方法，严格遵循 ISP（接口隔离原则）</li>
 *   <li>入参/出参都是领域对象（PayOrder/PayResult），不暴露渠道细节</li>
 *   <li>不定义签名/日志/异常处理 —— 这些是骨架的事</li>
 * </ul>
 */
public interface PayChannel {

	/** 渠道编码，用于路由匹配 */
	String getChannelCode();

	/** 预下单：创建支付订单，返回渠道流水号 */
	PayResult prepay(PayOrder order);

	/** 查询支付状态 */
	PayResult queryStatus(String orderId);
}
