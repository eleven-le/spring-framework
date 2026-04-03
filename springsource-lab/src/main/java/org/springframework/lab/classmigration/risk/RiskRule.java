package org.springframework.lab.classmigration.risk;

/**
 * 风控规则接口 —— 契约层
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 BeanPostProcessor —— 只定义 postProcessBeforeInitialization/After 两个钩子</li>
 *   <li>对标 Advisor/MethodInterceptor —— 拦截链中的单个节点</li>
 *   <li>对标 Ordered —— 支持排序，责任链中的顺序即语义</li>
 * </ul>
 *
 * <h3>设计决策</h3>
 * <ul>
 *   <li>evaluate() 返回 RiskResult 而非 boolean —— 携带评分+原因，支持灰度（REVIEW）</li>
 *   <li>getOrder() 控制评估顺序 —— 对标 Ordered 接口，低成本规则先执行</li>
 *   <li>getRuleName() 用于日志和监控 —— 对标 BeanPostProcessor 的身份标识</li>
 * </ul>
 */
public interface RiskRule {

	/** 规则名称，用于日志和监控 */
	String getRuleName();

	/** 执行顺序，数值小优先 —— 对标 Ordered#getOrder() */
	int getOrder();

	/** 评估风控 —— 对标 BeanPostProcessor#postProcessBeforeInitialization() */
	RiskResult evaluate(RiskContext context);
}
