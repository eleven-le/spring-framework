package org.springframework.lab.classmigration.risk.engine;

import org.springframework.lab.classmigration.risk.RiskContext;
import org.springframework.lab.classmigration.risk.RiskResult;
import org.springframework.lab.classmigration.risk.RiskRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 风控引擎 —— 规则链编排 + 责任链模式
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 DefaultAdvisorChainFactory —— 收集所有 Advisor，排序后组成拦截链</li>
 *   <li>对标 PostProcessorRegistrationDelegate —— 按 PriorityOrdered → Ordered → 无序 分批执行</li>
 *   <li>对标 ApplicationEventMulticaster —— 收集监听器，按顺序分发事件</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>构造器注入 List&lt;RiskRule&gt; + 按 order 排序 —— 对标 AnnotationAwareOrderComparator</li>
 *   <li>短路语义：REJECT 立即返回 —— 对标拦截链的 invoke() 短路</li>
 *   <li>REVIEW 不短路，继续评估后续规则 —— 业务决策：可能后续规则 REJECT 更严重</li>
 *   <li>最终返回最严重的结果 —— 聚合策略</li>
 * </ul>
 */
public class RiskEngine {

	private final List<RiskRule> sortedRules;

	/**
	 * 构造器注入所有规则 + 排序 —— 对标 PostProcessorRegistrationDelegate 的排序逻辑
	 */
	public RiskEngine(List<RiskRule> rules) {
		this.sortedRules = new ArrayList<>(rules);
		this.sortedRules.sort(Comparator.comparingInt(RiskRule::getOrder));
		System.out.println("    [RiskEngine] 规则链初始化完成，执行顺序:");
		for (RiskRule rule : sortedRules) {
			System.out.println("      order=" + rule.getOrder() + " → " + rule.getRuleName());
		}
	}

	/**
	 * 评估入口 —— 串行执行规则链，REJECT 立即短路
	 *
	 * <p>对标 ReflectiveMethodInvocation#proceed() —— 链式调用，遇到拦截就停
	 */
	public RiskResult evaluate(RiskContext context) {
		System.out.println("    [RiskEngine] 开始风控评估: " + context);
		RiskResult worstResult = null;

		for (RiskRule rule : sortedRules) {
			RiskResult result = rule.evaluate(context);

			// REJECT 立即短路 —— 对标拦截链的提前返回
			if (result.isBlocked()) {
				System.out.println("    [RiskEngine] 规则命中拦截: " + result);
				return result;
			}

			// 记录最严重的非拦截结果（用于 REVIEW 聚合）
			if (worstResult == null || result.getScore() > worstResult.getScore()) {
				worstResult = result;
			}
		}

		RiskResult finalResult = (worstResult != null) ? worstResult : RiskResult.pass("无规则");
		System.out.println("    [RiskEngine] 评估完成，最终结果: " + finalResult);
		return finalResult;
	}
}
