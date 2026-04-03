package org.springframework.lab.classmigration.risk.amount;

import org.springframework.lab.classmigration.risk.RiskContext;
import org.springframework.lab.classmigration.risk.RiskResult;
import org.springframework.lab.classmigration.risk.support.AbstractRiskRule;

import java.math.BigDecimal;

/**
 * 金额限制规则 —— 超过阈值拦截或人工审核
 *
 * <p>对标 Spring：Ordered 的 BeanPostProcessor —— 有明确的优先级排位
 * <p>体现 "策略" 可配：阈值通过构造器注入，可以按环境/租户动态调整
 */
public class AmountLimitRule extends AbstractRiskRule {

	private final BigDecimal rejectThreshold;   // 直接拒绝阈值
	private final BigDecimal reviewThreshold;   // 人工审核阈值

	public AmountLimitRule(BigDecimal reviewThreshold, BigDecimal rejectThreshold) {
		this.reviewThreshold = reviewThreshold;
		this.rejectThreshold = rejectThreshold;
	}

	@Override
	public String getRuleName() {
		return "金额限制规则";
	}

	@Override
	public int getOrder() {
		return 10;  // 第二优先级
	}

	@Override
	protected RiskResult doEvaluate(RiskContext context) {
		BigDecimal amount = context.getAmount();
		if (amount.compareTo(rejectThreshold) > 0) {
			return RiskResult.reject(getRuleName(), 90,
					"金额 " + amount + " 超过拒绝阈值 " + rejectThreshold);
		}
		if (amount.compareTo(reviewThreshold) > 0) {
			return RiskResult.review(getRuleName(), 60,
					"金额 " + amount + " 超过审核阈值 " + reviewThreshold);
		}
		return RiskResult.pass(getRuleName());
	}
}
