package org.springframework.lab.classmigration.risk;

/**
 * 风控评估结果
 */
public class RiskResult {

	public enum Action { PASS, REJECT, REVIEW }

	private final Action action;
	private final String ruleName;   // 触发规则名
	private final int score;         // 风险评分 0-100
	private final String reason;

	private RiskResult(Action action, String ruleName, int score, String reason) {
		this.action = action;
		this.ruleName = ruleName;
		this.score = score;
		this.reason = reason;
	}

	public static RiskResult pass(String ruleName) {
		return new RiskResult(Action.PASS, ruleName, 0, "通过");
	}

	public static RiskResult reject(String ruleName, int score, String reason) {
		return new RiskResult(Action.REJECT, ruleName, score, reason);
	}

	public static RiskResult review(String ruleName, int score, String reason) {
		return new RiskResult(Action.REVIEW, ruleName, score, reason);
	}

	public Action getAction() { return action; }
	public String getRuleName() { return ruleName; }
	public int getScore() { return score; }
	public String getReason() { return reason; }
	public boolean isBlocked() { return action == Action.REJECT; }

	@Override
	public String toString() {
		return "RiskResult{" + action + ", rule='" + ruleName + "', score=" + score
				+ ", reason='" + reason + "'}";
	}
}
