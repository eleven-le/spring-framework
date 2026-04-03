package org.springframework.lab.processortour;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * 自定义 @EnableAuditLog — 完整复刻 @EnableTransactionManagement 的 Advisor 路线。
 *
 * <p>对比：
 * <pre>
 * @EnableTransactionManagement
 *   → @Import(TransactionManagementConfigurationSelector)
 *     → [AutoProxyRegistrar, ProxyTransactionManagementConfiguration]
 *
 * @EnableAuditLog
 *   → @Import({AuditLogRegistrar, AuditLogConfiguration})
 *     → AuditLogRegistrar 注册 AutoProxyCreator
 *     → AuditLogConfiguration 注册 Advisor + Interceptor
 * </pre>
 *
 * <p>业务映射：电商交易链路中，幂等校验/风控拦截/灰度路由等治理组件，
 * 都可以复刻这套 @Enable + Advisor + MethodInterceptor 的标准套路。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({AuditLogRegistrar.class, AuditLogConfiguration.class})
public @interface EnableAuditLog {

}
