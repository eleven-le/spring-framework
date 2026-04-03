package org.springframework.lab.processortour;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * W33 配置类：同时开启 TX/Async/Scheduling + 自定义 @EnableAuditLog，
 * 用于对比观察三种注解驱动路线的注册和代理差异。
 *
 * <p>注册链路速查：
 * <ul>
 *   <li>@EnableTransactionManagement → AutoProxyRegistrar + ProxyTransactionManagementConfiguration</li>
 *   <li>@EnableAsync → ProxyAsyncConfiguration → AsyncAnnotationBeanPostProcessor</li>
 *   <li>@EnableScheduling → SchedulingConfiguration → ScheduledAnnotationBeanPostProcessor</li>
 *   <li>@EnableAuditLog → AuditLogRegistrar + AuditLogConfiguration (自定义复刻)</li>
 * </ul>
 */
@Configuration
@ComponentScan("org.springframework.lab.processortour")
@EnableTransactionManagement   // Advisor 路线：AutoProxyRegistrar + Advisor + TransactionInterceptor
@EnableAsync                   // BPP+Advisor 路线：AbstractAdvisingBeanPostProcessor
@EnableScheduling              // 纯 BPP 路线：ScheduledAnnotationBeanPostProcessor
@EnableAuditLog                // 自定义复刻 Advisor 路线
public class ProcessorTourConfig {

}
