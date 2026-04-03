package org.springframework.lab.acactx;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 演示 @Conditional 门控 — register 阶段的条件过滤
 *
 * <p>doRegisterBean 方法中 ConditionEvaluator#shouldSkip 会在
 * BeanDefinition 组装阶段就评估 @Conditional, 不满足则直接跳过注册。
 *
 * <p>注意: Spring Boot 的 @ConditionalOnProperty 等是在更高层实现的,
 * 这里用自定义 Condition 来演示原生机制。
 */
@Configuration
@Conditional(BetaFeatureCondition.class)
public class BetaFeatureConfig {

	@Bean
	public BetaService betaService() {
		return new BetaService();
	}
}
