package org.springframework.lab.classmigration.risk.blacklist;

import org.springframework.lab.classmigration.risk.RiskContext;
import org.springframework.lab.classmigration.risk.RiskResult;
import org.springframework.lab.classmigration.risk.support.AbstractRiskRule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 黑名单规则 —— 最高优先级，O(1) 查表，成本最低
 *
 * <p>对标 Spring：PriorityOrdered 的 BeanPostProcessor —— 最先执行，快速短路
 * <p>设计理由：黑名单查表是 O(1)，放在第一位可以最快拦截，避免后续昂贵规则的无效计算
 */
public class BlacklistRule extends AbstractRiskRule {

	// 模拟黑名单（实际场景：Redis Set / 布隆过滤器）
	private final Set<String> blacklist = new HashSet<>(Arrays.asList(
			"USER_BANNED_001", "USER_BANNED_002", "USER_FRAUD_003"
	));

	@Override
	public String getRuleName() {
		return "黑名单规则";
	}

	@Override
	public int getOrder() {
		return 0;  // 最高优先级 —— 对标 PriorityOrdered
	}

	@Override
	protected RiskResult doEvaluate(RiskContext context) {
		if (blacklist.contains(context.getUserId())) {
			return RiskResult.reject(getRuleName(), 100, "用户在黑名单中: " + context.getUserId());
		}
		return RiskResult.pass(getRuleName());
	}
}
