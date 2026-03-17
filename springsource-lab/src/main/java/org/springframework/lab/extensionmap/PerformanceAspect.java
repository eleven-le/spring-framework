package org.springframework.lab.extensionmap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 运行期 —— AOP 切面 (底层为 MethodInterceptor 拦截链)
 * <p>
 * 代理在 BPP.postProcessAfterInitialization 阶段由 AbstractAutoProxyCreator 创建
 * 方法调用时经过: CglibAopProxy -> ReflectiveMethodInvocation.proceed -> Advice chain
 * <p>
 * 业务场景: 性能监控 / 日志 / 限流 / 熔断 / 事务
 */
@Aspect
public class PerformanceAspect {

	@Around("execution(* org.springframework.lab.extensionmap.OrderService.placeOrder(..))")
	public Object around(ProceedingJoinPoint pjp) throws Throwable {
		String method = pjp.getSignature().toShortString();
		TimelineTracker.record("运行",
				"AOP MethodInterceptor (enter)",
				method + " -> 进入拦截链");
		try {
			return pjp.proceed();
		} finally {
			TimelineTracker.record("运行",
					"AOP MethodInterceptor (exit)",
					method + " -> 拦截链返回");
		}
	}
}
