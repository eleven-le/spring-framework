package org.springframework.lab.classmigration.risk.frequency;

import org.springframework.lab.classmigration.risk.RiskContext;
import org.springframework.lab.classmigration.risk.RiskResult;
import org.springframework.lab.classmigration.risk.support.AbstractRiskRule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 频率限制规则 —— 滑动窗口限频
 *
 * <p>对标 Spring：
 * <ul>
 *   <li>类比 RateLimiter 思想 —— 时间窗口 + 计数器</li>
 *   <li>supports() 覆盖：只对 "有用户身份" 的请求生效 —— 对标 Pointcut#matches()</li>
 * </ul>
 *
 * <p>这里用简单的 AtomicInteger 模拟，实际场景用 Redis + Lua 滑动窗口
 */
public class FrequencyRule extends AbstractRiskRule {

	private final int maxRequestsPerWindow;
	// 模拟频率计数（实际场景：Redis INCR + TTL 滑动窗口）
	private final Map<String, AtomicInteger> counterMap = new ConcurrentHashMap<>();

	public FrequencyRule(int maxRequestsPerWindow) {
		this.maxRequestsPerWindow = maxRequestsPerWindow;
	}

	@Override
	public String getRuleName() {
		return "频率限制规则";
	}

	@Override
	public int getOrder() {
		return 20;  // 第三优先级
	}

	@Override
	protected boolean supports(RiskContext context) {
		// 只有有用户身份的请求才做频率限制
		return context.getUserId() != null && !context.getUserId().isEmpty();
	}

	@Override
	protected RiskResult doEvaluate(RiskContext context) {
		AtomicInteger counter = counterMap.computeIfAbsent(
				context.getUserId(), k -> new AtomicInteger(0));
		int count = counter.incrementAndGet();

		if (count > maxRequestsPerWindow) {
			return RiskResult.reject(getRuleName(), 80,
					"用户 " + context.getUserId() + " 请求频次 " + count
							+ " 超过限制 " + maxRequestsPerWindow);
		}
		return RiskResult.pass(getRuleName());
	}
}
