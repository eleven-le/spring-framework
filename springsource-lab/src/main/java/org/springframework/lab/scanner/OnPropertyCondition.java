package org.springframework.lab.scanner;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 自定义 Condition：当系统属性 feature.enabled=true 时才满足条件
 * 用于演示 ConditionEvaluator 在注册期的拦截
 *
 * 断点：ConditionEvaluator#shouldSkip:80 → 调到这里的 matches()
 */
public class OnPropertyCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		String value = System.getProperty("feature.enabled");
		boolean result = "true".equalsIgnoreCase(value);
		System.out.println("  [OnPropertyCondition] feature.enabled=" + value + " → matches=" + result);
		return result;
	}
}
