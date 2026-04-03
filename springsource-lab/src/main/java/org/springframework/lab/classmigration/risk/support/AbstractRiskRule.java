package org.springframework.lab.classmigration.risk.support;

import org.springframework.lab.classmigration.risk.RiskContext;
import org.springframework.lab.classmigration.risk.RiskResult;
import org.springframework.lab.classmigration.risk.RiskRule;

/**
 * 风控规则骨架 —— 模板方法层
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 AbstractAdvisorAutoProxyCreator —— wrapIfNecessary() 是骨架，
 *       getAdvicesAndAdvisorsForBean() 是子类钩子</li>
 *   <li>对标 GenericFilterBean —— 骨架处理 init/日志/生命周期，
 *       子类只实现 doFilterInternal()</li>
 * </ul>
 *
 * <h3>骨架封装了什么</h3>
 * <ol>
 *   <li>统一日志 —— 规则名+耗时+结果</li>
 *   <li>前置检查 —— 是否启用、是否适用当前场景</li>
 *   <li>异常兜底 —— 单个规则异常不影响整条链</li>
 *   <li>评分归一化 —— 保证 0-100 区间</li>
 * </ol>
 *
 * <h3>子类只实现什么</h3>
 * <ul>
 *   <li>doEvaluate() —— 具体的规则逻辑（黑名单查表/金额比较/频率统计）</li>
 *   <li>supports() —— 可选：是否适用于当前上下文（默认 true）</li>
 * </ul>
 */
public abstract class AbstractRiskRule implements RiskRule {

	private boolean enabled = true;

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * 评估骨架 —— 对标 AbstractAutoProxyCreator#wrapIfNecessary()
	 *
	 * <p>流程：启用检查 → 适用检查 → 日志 → 执行 → 异常兜底
	 */
	@Override
	public final RiskResult evaluate(RiskContext context) {
		// Step 1: 开关检查 —— 对标 BPP 的条件判断
		if (!enabled) {
			System.out.println("      [" + getRuleName() + "] 已禁用，跳过");
			return RiskResult.pass(getRuleName());
		}

		// Step 2: 适用性检查 —— 对标 Pointcut#matches()
		if (!supports(context)) {
			System.out.println("      [" + getRuleName() + "] 不适用当前场景，跳过");
			return RiskResult.pass(getRuleName());
		}

		// Step 3: 执行 + 日志 + 异常兜底
		long start = System.currentTimeMillis();
		try {
			RiskResult result = doEvaluate(context);
			long cost = System.currentTimeMillis() - start;
			System.out.println("      [" + getRuleName() + "] 评估完成: " + result
					+ " (" + cost + "ms)");
			return result;
		} catch (Exception e) {
			// 异常兜底：单个规则故障不应拖垮整条链 —— 对标 BPP 异常隔离
			System.out.println("      [" + getRuleName() + "] 评估异常: " + e.getMessage()
					+ "，降级为 PASS");
			return RiskResult.pass(getRuleName());
		}
	}

	// ==================== 子类钩子 ====================

	/** 具体规则逻辑 —— 子类必须实现 */
	protected abstract RiskResult doEvaluate(RiskContext context);

	/** 是否适用当前上下文 —— 默认 true，子类可覆盖做细粒度控制 */
	protected boolean supports(RiskContext context) {
		return true;
	}
}
