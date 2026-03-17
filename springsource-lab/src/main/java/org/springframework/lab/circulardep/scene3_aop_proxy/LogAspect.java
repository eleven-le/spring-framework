package org.springframework.lab.circulardep.scene3_aop_proxy;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Scene 3: AOP 切面, 切 TradeService 和 SettlementService 的方法,
 * 使它们被 CGLIB 代理包装。
 * 这样循环依赖 + AOP 代理的场景就触发了三级缓存的 getEarlyBeanReference 路径。
 */
@Aspect
@Component
public class LogAspect {

	@Around("execution(* org.springframework.lab.circulardep.scene3_aop_proxy.TradeService.*(..))" +
			" || execution(* org.springframework.lab.circulardep.scene3_aop_proxy.SettlementService.*(..))")
	public Object log(ProceedingJoinPoint pjp) throws Throwable {
		String method = pjp.getSignature().toShortString();
		System.out.println("    [AOP] >>> " + method);
		Object result = pjp.proceed();
		System.out.println("    [AOP] <<< " + method + " = " + result);
		return result;
	}
}
