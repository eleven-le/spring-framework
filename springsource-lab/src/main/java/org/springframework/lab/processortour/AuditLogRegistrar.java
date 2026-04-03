package org.springframework.lab.processortour;

import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 自定义 ImportBeanDefinitionRegistrar — 对标 AutoProxyRegistrar。
 *
 * <p>职责：往容器注册 InfrastructureAdvisorAutoProxyCreator（如果还没注册的话）。
 * 这个 BPP 在 initializeBean 阶段扫描所有 Advisor，匹配就建代理。
 *
 * <p>源码对标：
 * {@code org.springframework.context.annotation.AutoProxyRegistrar#registerBeanDefinitions}
 *
 * <p>断点：AutoProxyRegistrar#registerBeanDefinitions:70 — 看 InfrastructureAdvisorAutoProxyCreator 注册
 */
public class AuditLogRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
			BeanDefinitionRegistry registry) {

		// 和 @EnableTransactionManagement 的 AutoProxyRegistrar 调用的是同一个方法
		// 幂等的：如果已经注册了更高级的 AutoProxyCreator（如 AnnotationAwareAspectJAutoProxyCreator），
		// 这个调用不会降级覆盖，只会保留最高级的那个
		AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);

		System.out.println("[AuditLogRegistrar] 已注册 InfrastructureAdvisorAutoProxyCreator");
	}
}
