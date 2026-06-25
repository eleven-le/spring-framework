package com.leilei.lab.laboratory.l03.l03_02;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * 📖 知识点：[[L03-02-Aspect切面工程化#2. 🏭 生产怎么用对]]（五种通知与执行顺序，5.x 顺序变化 ⭐）
 * 🎯 作用：在「同一个切面类」里挂全五种通知（@Around / @Before / @After / @AfterReturning / @AfterThrowing），
 *         打印真实执行顺序，坐实 Spring 5.2.7 起的关键修正：
 *         <pre>
 *         进入：@Around(前) → @Before → [目标方法]
 *         正常返回：→ @AfterReturning → @After(finally) → @Around(后)
 *         抛异常：  → @AfterThrowing → @After(finally) → @Around 捕获
 *         </pre>
 *         重点：@After 现在**永远在 @AfterReturning/@AfterThrowing 之后**执行（真正的 try/finally 语义）。
 *         5.2.7 之前同一切面内 @After 可能先于 @AfterReturning 跑，把「清理动作」插在「结果处理」之前，
 *         是一类隐蔽的顺序事故。源码出处见 {@code ReflectiveAspectJAdvisorFactory} 的 adviceMethodComparator
 *         与 {@code AspectJAfterAdvice#invoke}（try 里 proceed、finally 里跑 @After）。
 * 🔗 业务场景：商品上下架方法上同时挂「耗时埋点(@Around)、入参审计(@Before)、结果回写缓存(@AfterReturning)、
 *         释放分布式锁(@After)」——若 @After 先于 @AfterReturning 跑，锁会在「结果回写缓存」前被释放，
 *         留出并发窗口。通知顺序不是八股，是事故边界。
 */
public final class L0302_02_FiveAdviceOrderDemo {

	private L0302_02_FiveAdviceOrderDemo() {
	}

	/** 商品上下架服务：onShelf 正常返回，offShelf 故意抛异常，用来分别触发 @AfterReturning / @AfterThrowing。 */
	static class ShelfService {
		String onShelf(long skuId) {
			System.out.println("      [目标] onShelf(" + skuId + ") 执行上架写库");
			return "ON_SHELF:" + skuId;
		}

		String offShelf(long skuId) {
			System.out.println("      [目标] offShelf(" + skuId + ") 执行下架，命中风控拦截 → 抛异常");
			throw new IllegalStateException("sku " + skuId + " 处于活动锁定，禁止下架");
		}
	}

	/** 同一切面挂全五种通知，逐条打印进入时机，把执行顺序可视化。 */
	@Aspect
	static class ShelfAuditAspect {

		@Pointcut("execution(* com.leilei.lab.laboratory.l03.l03_02.L0302_02_FiveAdviceOrderDemo.ShelfService.*(..))")
		public void shelfOps() {
		}

		@Around("shelfOps()")
		public Object around(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("  ① @Around  前置：开始计时");
			try {
				Object ret = pjp.proceed();
				System.out.println("  ⑥ @Around  后置：记录耗时（正常返回路径）");
				return ret;
			}
			catch (Throwable ex) {
				System.out.println("  ⑥ @Around  后置：记录耗时（异常路径），并向上抛");
				throw ex;
			}
		}

		@Before("shelfOps()")
		public void before() {
			System.out.println("  ② @Before  入参审计");
		}

		@AfterReturning("shelfOps()")
		public void afterReturning() {
			System.out.println("  ④ @AfterReturning  结果回写缓存（仅正常返回时跑）");
		}

		@AfterThrowing("shelfOps()")
		public void afterThrowing() {
			System.out.println("  ④ @AfterThrowing   告警上报（仅抛异常时跑）");
		}

		@After("shelfOps()")
		public void after() {
			System.out.println("  ⑤ @After   释放分布式锁（finally：无论成败都在 returning/throwing 之后）");
		}
	}

	private static ShelfService buildProxy() {
		// AspectJProxyFactory：编程式把一个 @Aspect 织到目标上，内部走 ReflectiveAspectJAdvisorFactory，
		// 通知排序规则与容器自动代理完全一致（同一套 adviceMethodComparator）。
		AspectJProxyFactory factory = new AspectJProxyFactory(new ShelfService());
		factory.addAspect(new ShelfAuditAspect());
		factory.setProxyTargetClass(true);   // ShelfService 无接口，走 CGLIB
		return factory.getProxy();
	}

	public static void main(String[] args) {
		ShelfService proxy = buildProxy();

		System.out.println("==================== 正常返回：触发 @AfterReturning ====================");
		String ret = proxy.onShelf(10000100L);
		System.out.println("  返回值 = " + ret);
		System.out.println("顺序：@Around前 → @Before → 目标 → @AfterReturning → @After → @Around后");
		System.out.println("      （5.2.7+：@After 在 @AfterReturning 之后跑 = 真正 finally 语义）\n");

		System.out.println("==================== 抛出异常：触发 @AfterThrowing ====================");
		try {
			proxy.offShelf(10000100L);
		}
		catch (IllegalStateException ex) {
			System.out.println("  捕获到异常：" + ex.getMessage());
		}
		System.out.println("顺序：@Around前 → @Before → 目标(抛) → @AfterThrowing → @After → @Around捕获");
		System.out.println("      （@AfterReturning 不跑；@After 仍在 @AfterThrowing 之后，finally 永远收尾）");
	}
}
