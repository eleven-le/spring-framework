package org.springframework.lab.processortour;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * 自定义 MethodInterceptor — 对标 TransactionInterceptor / CacheInterceptor。
 *
 * <p>所有注解驱动能力最终都落到 MethodInterceptor#invoke：
 * <ul>
 *   <li>TransactionInterceptor#invoke → 开事务 → proceed → 提交/回滚</li>
 *   <li>CacheInterceptor#invoke → 查缓存 → 命中返回 / miss 则 proceed → 写缓存</li>
 *   <li>AuditLogInterceptor#invoke → 记录审计日志 → proceed → 记录结果</li>
 * </ul>
 *
 * <p>共同骨架：before → proceed() → after/afterThrowing
 */
public class AuditLogInterceptor implements MethodInterceptor {

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Method method = invocation.getMethod();
		AuditLog annotation = method.getAnnotation(AuditLog.class);
		String action = (annotation != null) ? annotation.action() : method.getName();

		System.out.println("    [AuditLog] >>> 开始审计: action=" + action
				+ ", method=" + method.getDeclaringClass().getSimpleName() + "#" + method.getName());

		long start = System.nanoTime();
		try {
			Object result = invocation.proceed();  // 放行到下一个 Interceptor 或真实方法
			long cost = (System.nanoTime() - start) / 1_000_000;
			System.out.println("    [AuditLog] <<< 审计完成: action=" + action
					+ ", result=SUCCESS, cost=" + cost + "ms");
			return result;
		}
		catch (Throwable ex) {
			long cost = (System.nanoTime() - start) / 1_000_000;
			System.out.println("    [AuditLog] <<< 审计完成: action=" + action
					+ ", result=FAIL(" + ex.getClass().getSimpleName() + "), cost=" + cost + "ms");
			throw ex;
		}
	}
}
