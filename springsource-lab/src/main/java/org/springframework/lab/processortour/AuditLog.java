package org.springframework.lab.processortour;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义业务注解 — 标记需要审计日志的方法。
 *
 * <p>对标 @Transactional：@Transactional 标记事务方法，@AuditLog 标记审计方法。
 * 两者都通过 Pointcut 匹配 → Advisor 织入 → MethodInterceptor 拦截。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

	/** 审计动作名称，如 "下单"、"支付" */
	String action() default "";
}
