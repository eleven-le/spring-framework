package org.springframework.lab.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 模拟事务切面 — 演示多切面叠加时的洋葱模型
 *
 * <p>@Order(2) 比 LogAspect(@Order(1)) 后进先出:
 * <pre>
 *   LogAspect.around-前   ← 外层 (Order=1, 先进)
 *     TxAspect.around-前  ← 内层 (Order=2, 后进)
 *       目标方法
 *     TxAspect.around-后  ← 内层 (先出)
 *   LogAspect.around-后   ← 外层 (后出)
 * </pre>
 *
 * <p>排序源码: AspectJAwareAdvisorAutoProxyCreator#sortAdvisors
 * → 使用 AspectJPrecedenceComparator + PartialOrder.sort()
 */
@Aspect
@Component
@Order(2)  // 内层, 后进先出
public class TxAspect {

	@Around("execution(* org.springframework.lab.aop.*Service*.create*(..))" +
			" || execution(* org.springframework.lab.aop.*Service*.pay*(..))")
	public Object simulateTx(ProceedingJoinPoint pjp) throws Throwable {
		String method = pjp.getSignature().toShortString();
		System.out.println("[TxAspect] 开启事务 → " + method);
		try {
			Object result = pjp.proceed();
			System.out.println("[TxAspect] 提交事务 → " + method);
			return result;
		}
		catch (Throwable ex) {
			System.out.println("[TxAspect] 回滚事务 → " + method + " cause=" + ex.getMessage());
			throw ex;
		}
	}
}
