package org.springframework.lab.scanner;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * 条件化服务 —— 只有 feature.enabled=true 时才注册
 * 用于演示 Reader 路径中 ConditionEvaluator 的拦截效果
 *
 * 断点：AnnotatedBeanDefinitionReader#doRegisterBean:298
 *       → conditionEvaluator.shouldSkip(abd.getMetadata())
 */
@Component
@Conditional(OnPropertyCondition.class)
public class ConditionalService {

	public void execute() {
		System.out.println("ConditionalService 已启用");
	}
}
