package org.springframework.lab.processortour;

import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * 自定义 Configuration — 对标 ProxyTransactionManagementConfiguration。
 *
 * <p>标准三件套：
 * <ol>
 *   <li>Pointcut — 哪些方法需要拦截（对标 TransactionAttributeSourcePointcut）</li>
 *   <li>MethodInterceptor — 拦截后干什么（对标 TransactionInterceptor）</li>
 *   <li>Advisor — 把 Pointcut 和 Interceptor 绑定（对标 BeanFactoryTransactionAttributeSourceAdvisor）</li>
 * </ol>
 *
 * <p>ROLE_INFRASTRUCTURE 是关键：InfrastructureAdvisorAutoProxyCreator 只认这个角色的 Advisor。
 * 如果不标 ROLE_INFRASTRUCTURE，AutoProxyCreator 不会扫到它。
 *
 * <p>源码对标：ProxyTransactionManagementConfiguration 的 transactionAdvisor/transactionInterceptor/transactionAttributeSource
 */
@Configuration(proxyBeanMethods = false)
public class AuditLogConfiguration {

	/**
	 * Pointcut：匹配所有标了 @AuditLog 的方法。
	 * 对标 TransactionAttributeSourcePointcut（匹配 @Transactional）。
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public Pointcut auditLogPointcut() {
		return AnnotationMatchingPointcut.forMethodAnnotation(AuditLog.class);
	}

	/**
	 * MethodInterceptor：审计日志拦截逻辑。
	 * 对标 TransactionInterceptor（事务拦截逻辑）。
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public AuditLogInterceptor auditLogInterceptor() {
		return new AuditLogInterceptor();
	}

	/**
	 * Advisor：绑定 Pointcut + Interceptor。
	 * 对标 BeanFactoryTransactionAttributeSourceAdvisor。
	 *
	 * <p>必须标 ROLE_INFRASTRUCTURE，否则 InfrastructureAdvisorAutoProxyCreator 不认。
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public DefaultPointcutAdvisor auditLogAdvisor(Pointcut auditLogPointcut,
			AuditLogInterceptor auditLogInterceptor) {
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(auditLogPointcut, auditLogInterceptor);
		advisor.setOrder(0);  // 排在 TX Advisor 前面，先审计再事务
		return advisor;
	}
}
