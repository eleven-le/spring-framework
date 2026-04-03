package org.springframework.lab.aftercommit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟重试状态 — 记录每个 eventId 的消费尝试次数。
 *
 * <p>真实场景中重试由 MQ（RocketMQ/Kafka retry topic）或 Spring Retry 框架管理,
 * 这里用内存 Map 模拟"前 N 次失败、第 N+1 次成功"的行为。
 */
public class RetryState {

	static final RetryState INSTANCE = new RetryState();

	private final Map<String, Integer> attemptMap = new ConcurrentHashMap<>();

	public int getAttempt(String eventId) {
		return attemptMap.getOrDefault(eventId, 0);
	}

	public void incrementAttempt(String eventId) {
		attemptMap.merge(eventId, 1, Integer::sum);
	}

	public void reset() {
		attemptMap.clear();
	}
}
