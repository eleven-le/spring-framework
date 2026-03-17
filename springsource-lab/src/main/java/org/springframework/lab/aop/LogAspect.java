package org.springframework.lab.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 日志切面 — 演示五种 Advice 类型 + 执行顺序
 *
 * <p>单 Aspect 内 Advice 执行顺序 (Spring 5.2.7+ 修正后):
 * <pre>
 *   @Around (前半段 proceed 之前)
 *     → @Before
 *       → 目标方法
 *     → @AfterReturning / @AfterThrowing
 *   → @After (finally 语义, 一定执行)
 *   → @Around (后半段 proceed 之后)
 * </pre>
 *
 * <p>多 Aspect 排序: @Order 值越小优先级越高, 像洋葱层包裹
 *
 * <p>源码对应:
 * ReflectiveAspectJAdvisorFactory#getAdvisorMethods 按 @Around→@Before→@After→@AfterReturning→@AfterThrowing 排序,
 * 最终通过 ExposeInvocationInterceptor + ReflectiveMethodInvocation 形成责任链
 */
@Aspect
@Component
@Order(1)  // 低值 = 高优先级, 先进后出 (洋葱模型外层)
public class LogAspect {

	// ==================== Pointcut 定义 ====================

	/** 匹配 lab.aop 包下所有 public 方法 */
	@Pointcut("execution(* org.springframework.lab.aop.*Service*.*(..))")
	public void servicePointcut() {}

	// ==================== 五种 Advice ====================

	@Around("servicePointcut()")
	public Object around(ProceedingJoinPoint pjp) throws Throwable {
		String method = pjp.getSignature().toShortString();
		System.out.println("[LogAspect][@Around-前] " + method);
		long start = System.nanoTime();
		try {
			Object result = pjp.proceed();
			long cost = (System.nanoTime() - start) / 1_000_000;
			System.out.println("[LogAspect][@Around-后] " + method + " 耗时=" + cost + "ms 返回=" + result);
			return result;
		}
		catch (Throwable ex) {
			System.out.println("[LogAspect][@Around-异常] " + method + " ex=" + ex.getMessage());
			throw ex;
		}
	}

	@Before("servicePointcut()")
	public void before(JoinPoint jp) {
		System.out.println("[LogAspect][@Before] " + jp.getSignature().toShortString());
	}

	@After("servicePointcut()")
	public void after(JoinPoint jp) {
		System.out.println("[LogAspect][@After] " + jp.getSignature().toShortString());
	}

	@AfterReturning(pointcut = "servicePointcut()", returning = "result")
	public void afterReturning(JoinPoint jp, Object result) {
		System.out.println("[LogAspect][@AfterReturning] " + jp.getSignature().toShortString()
				+ " → " + result);
	}

	@AfterThrowing(pointcut = "servicePointcut()", throwing = "ex")
	public void afterThrowing(JoinPoint jp, Throwable ex) {
		System.out.println("[LogAspect][@AfterThrowing] " + jp.getSignature().toShortString()
				+ " → " + ex.getClass().getSimpleName());
	}
}
