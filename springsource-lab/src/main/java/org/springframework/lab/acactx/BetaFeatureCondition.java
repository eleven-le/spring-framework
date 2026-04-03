package org.springframework.lab.acactx;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 自定义 Condition: 读取 feature.beta 属性决定是否注册
 *
 * <p>断点: 此处 matches() 方法 — 在 AnnotatedBeanDefinitionReader#doRegisterBean
 * 的 shouldSkip 环节被回调
 */
public class BetaFeatureCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		String value = context.getEnvironment().getProperty("feature.beta", "false");
		boolean result = "true".equalsIgnoreCase(value);
		System.out.println("  [BetaFeatureCondition] feature.beta=" + value + " → matches=" + result);
		return result;
	}
}
