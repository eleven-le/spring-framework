package org.springframework.lab.advisororder;

import org.aopalliance.intercept.MethodInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ===================================================================
 *  W23 · 拦截链排序语义（Spring 容器实验 —— @Aspect + @Bean Advisor）
 * ===================================================================
 *
 * 本 Demo 共 5 个实验：
 *   实验1: 跨 Aspect @Order 排序 → @Order(1) 的 Aspect 在 @Order(2) 外层
 *   实验2: 同 Aspect 内 advice 声明顺序 → before 先声明先执行 / after 先声明后执行
 *   实验3: @Bean Advisor 与 @Aspect 混排 → 都由 sortAdvisors 统一排序
 *   实验4: 事务 Advisor 默认 order=LOWEST_PRECEDENCE → 它在最内层
 *   实验5: 同 Aspect 内 @Before + @After + @Around 的完整排序
 *
 * 关键源码路径:
 *   AspectJAwareAdvisorAutoProxyCreator#sortAdvisors
 *   → 包装成 PartiallyComparableAdvisorHolder
 *   → PartialOrder.sort()
 *   → AspectJPrecedenceComparator#compare
 *     → 先比 AnnotationAwareOrderComparator（跨 Aspect）
 *     → 相等且同 Aspect → comparePrecedenceWithinAspect
 *       → before/around: 先声明=高优先=先执行
 *       → after: 后声明=高优先=后执行（保证对称洋葱）
 */
@Configuration
@ComponentScan("org.springframework.lab.advisororder")
@EnableAspectJAutoProxy
public class AdvisorOrderSpringMain {

	public static void main(String[] args) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println(" W23 · 拦截链排序语义 Demo（Spring 容器实验）");
		System.out.println("═══════════════════════════════════════════════════════════\n");

		exp1_crossAspectOrder();
		exp2_sameAspectAdviceOrder();
		exp3_beanAdvisorMixWithAspect();
		exp4_defaultLowestPrecedence();
		exp5_sameAspectFullAdviceTypes();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验1: 跨 Aspect @Order 排序
	// 要点: @Order 标注在 @Aspect 类上，值越小的 Aspect 在洋葱外层
	//       LogAspect(@Order=1) 在外, SecurityAspect(@Order=2) 在内
	// 源码: AnnotationAwareOrderComparator#findOrder → 读 @Order 注解
	// ─────────────────────────────────────────────────────────────
	static void exp1_crossAspectOrder() {
		System.out.println("【实验1】跨 Aspect @Order 排序 → @Order(1) 在 @Order(2) 外层");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Exp1Config.class);
		OrderService svc = ctx.getBean(OrderService.class);

		System.out.println("\n  >> placeOrder (LogAspect(1) 在外, SecurityAspect(2) 在内):");
		svc.placeOrder("U001", 300);

		ctx.close();
		System.out.println();
	}

	// ─── 实验1 配置 ───
	@Configuration
	@EnableAspectJAutoProxy
	static class Exp1Config {
		@Bean
		public OrderService orderService() { return new OrderServiceImpl(); }
		@Bean
		public LogAspect logAspect() { return new LogAspect(); }
		@Bean
		public SecurityAspect securityAspect() { return new SecurityAspect(); }
	}

	@Aspect
	@Order(1)
	static class LogAspect {
		@Around("execution(* org.springframework.lab.advisororder.OrderService.*(..))")
		public Object log(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("    [LogAspect @Order(1)] BEFORE → " + pjp.getSignature().getName());
			try {
				Object result = pjp.proceed();
				System.out.println("    [LogAspect @Order(1)] AFTER  → result=" + result);
				return result;
			} catch (Throwable t) {
				System.out.println("    [LogAspect @Order(1)] ERROR  → " + t.getMessage());
				throw t;
			}
		}
	}

	@Aspect
	@Order(2)
	static class SecurityAspect {
		@Around("execution(* org.springframework.lab.advisororder.OrderService.*(..))")
		public Object check(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("    [SecurityAspect @Order(2)] BEFORE → 安全检查");
			try {
				Object result = pjp.proceed();
				System.out.println("    [SecurityAspect @Order(2)] AFTER  → 安全检查通过");
				return result;
			} catch (Throwable t) {
				System.out.println("    [SecurityAspect @Order(2)] ERROR  → 安全异常");
				throw t;
			}
		}
	}

	// ─────────────────────────────────────────────────────────────
	// 实验2: 同 Aspect 内 advice 声明顺序
	// 要点: 同一个 @Aspect 类中的多个 advice，order 值相同
	//       排序规则（AspectJPrecedenceComparator#comparePrecedenceWithinAspect）：
	//       - before/around: 先声明 = 高优先 = 先执行
	//       - after: 后声明 = 高优先 = 后执行（保证洋葱对称）
	// 源码: AbstractAspectJAdvisorFactory#getAdvisorMethods
	//       → 按方法名排序（排序依据是 ConvertingComparator）
	// 注意: Spring 5.x 中同 Aspect 内同类型 advice 的声明顺序
	//       由方法名字母序决定（因为反射获取方法的顺序不保证）
	// ─────────────────────────────────────────────────────────────
	static void exp2_sameAspectAdviceOrder() {
		System.out.println("【实验2】同 Aspect 内 advice 声明顺序");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Exp2Config.class);
		OrderService svc = ctx.getBean(OrderService.class);

		System.out.println("\n  >> placeOrder (观察同 Aspect 内 @Before×2 + @After×2 的顺序):");
		svc.placeOrder("U001", 200);

		ctx.close();
		System.out.println();
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class Exp2Config {
		@Bean
		public OrderService orderService() { return new OrderServiceImpl(); }
		@Bean
		public MultiAdviceAspect multiAdviceAspect() { return new MultiAdviceAspect(); }
	}

	@Aspect
	@Order(1)
	static class MultiAdviceAspect {
		// 两个 @Before —— 按方法名字母序: aaa < bbb → aaa 先执行
		@Before("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void aaa_beforeFirst() {
			System.out.println("    [@Before] aaa_beforeFirst（方法名字母序靠前 → 先执行）");
		}

		@Before("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void bbb_beforeSecond() {
			System.out.println("    [@Before] bbb_beforeSecond（方法名字母序靠后 → 后执行）");
		}

		// 两个 @After —— after 语义: 后声明 = 高优先 = 后执行
		// 但实际排序依然受方法名字母序影响
		@After("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void ccc_afterFirst() {
			System.out.println("    [@After]  ccc_afterFirst");
		}

		@After("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void ddd_afterSecond() {
			System.out.println("    [@After]  ddd_afterSecond");
		}
	}

	// ─────────────────────────────────────────────────────────────
	// 实验3: @Bean Advisor 与 @Aspect 混排
	// 要点: @Bean 注册的 Advisor 和 @Aspect 生成的 Advisor
	//       都由 AbstractAdvisorAutoProxyCreator#sortAdvisors 统一排序
	//       排序规则相同: AnnotationAwareOrderComparator
	// ─────────────────────────────────────────────────────────────
	static void exp3_beanAdvisorMixWithAspect() {
		System.out.println("【实验3】@Bean Advisor 与 @Aspect 混排 → 统一排序");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Exp3Config.class);
		OrderService svc = ctx.getBean(OrderService.class);

		System.out.println("\n  >> placeOrder (@Bean Advisor order=1 在外, @Aspect order=2 在内):");
		svc.placeOrder("U001", 400);

		ctx.close();
		System.out.println();
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class Exp3Config {
		@Bean
		public OrderService orderService() { return new OrderServiceImpl(); }

		// @Bean 注册的 Advisor，order=1（最外层）
		@Bean
		public Advisor idempotentAdvisor() {
			AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
			pointcut.setExpression("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))");
			DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut,
					(MethodInterceptor) invocation -> {
						System.out.println("    [@Bean Advisor order=1] 幂等检查 BEFORE");
						Object result = invocation.proceed();
						System.out.println("    [@Bean Advisor order=1] 幂等检查 AFTER");
						return result;
					});
			advisor.setOrder(1);
			return advisor;
		}

		@Bean
		public MixedAspect mixedAspect() { return new MixedAspect(); }
	}

	@Aspect
	@Order(2)
	static class MixedAspect {
		@Around("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public Object around(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("    [@Aspect @Order(2)] 事务 BEFORE");
			try {
				Object result = pjp.proceed();
				System.out.println("    [@Aspect @Order(2)] 事务 AFTER (提交)");
				return result;
			} catch (Throwable t) {
				System.out.println("    [@Aspect @Order(2)] 事务 AFTER (回滚)");
				throw t;
			}
		}
	}

	// ─────────────────────────────────────────────────────────────
	// 实验4: 事务 Advisor 默认 order=LOWEST_PRECEDENCE
	// 要点: BeanFactoryTransactionAttributeSourceAdvisor 的默认 order
	//       = Ordered.LOWEST_PRECEDENCE = Integer.MAX_VALUE
	//       意味着事务拦截器默认在最内层 → 你的自定义拦截器都在事务外
	//       除非你的自定义拦截器也不设 order（也是 LOWEST_PRECEDENCE）
	//       → 两者顺序不确定 → 危险！
	// ─────────────────────────────────────────────────────────────
	static void exp4_defaultLowestPrecedence() {
		System.out.println("【实验4】未声明 order 的 Advisor 兜底 LOWEST_PRECEDENCE → 最内层");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Exp4Config.class);
		OrderService svc = ctx.getBean(OrderService.class);

		System.out.println("\n  >> placeOrder:");
		System.out.println("     显式 order=100 的一定在外层");
		System.out.println("     未设 order（兜底 LOWEST_PRECEDENCE）的在最内层");
		svc.placeOrder("U001", 500);

		ctx.close();
		System.out.println();
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class Exp4Config {
		@Bean
		public OrderService orderService() { return new OrderServiceImpl(); }

		@Bean
		public Advisor explicitOrderAdvisor() {
			AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
			pc.setExpression("execution(* org.springframework.lab.advisororder.OrderService.*(..))");
			DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pc,
					(MethodInterceptor) invocation -> {
						System.out.println("    [显式order=100] BEFORE → 我有明确 order，在外层");
						Object result = invocation.proceed();
						System.out.println("    [显式order=100] AFTER");
						return result;
					});
			advisor.setOrder(100);
			return advisor;
		}

		// 不设 order → 默认 LOWEST_PRECEDENCE
		@Bean
		public NoOrderAspect noOrderAspect() { return new NoOrderAspect(); }
	}

	@Aspect
	// 注意: 没有 @Order 注解 → AnnotationAwareOrderComparator 找不到 → 兜底 LOWEST_PRECEDENCE
	static class NoOrderAspect {
		@Around("execution(* org.springframework.lab.advisororder.OrderService.*(..))")
		public Object around(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("    [无@Order Aspect] BEFORE → 兜底 LOWEST_PRECEDENCE，在最内层");
			Object result = pjp.proceed();
			System.out.println("    [无@Order Aspect] AFTER");
			return result;
		}
	}

	// ─────────────────────────────────────────────────────────────
	// 实验5: 同 Aspect 内 @Before + @After + @Around 完整排序
	// 要点: 同一个 @Aspect 内不同类型 advice 的排序规则
	//       AspectJPrecedenceComparator#comparePrecedenceWithinAspect:
	//       - 如果有 after 类 advice: 后声明=高优先（最后执行）
	//       - 如果都是 before/around: 先声明=高优先（最先执行）
	//       实际排序中方法名字母序决定了 declarationOrder
	// ─────────────────────────────────────────────────────────────
	static void exp5_sameAspectFullAdviceTypes() {
		System.out.println("【实验5】同 Aspect 内 @Before + @AfterReturning + @Around 完整排序");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Exp5Config.class);
		OrderService svc = ctx.getBean(OrderService.class);

		System.out.println("\n  >> placeOrder (观察同 Aspect 内不同类型 advice 的执行顺序):");
		svc.placeOrder("U001", 600);

		ctx.close();
		System.out.println();
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class Exp5Config {
		@Bean
		public OrderService orderService() { return new OrderServiceImpl(); }
		@Bean
		public FullAdviceAspect fullAdviceAspect() { return new FullAdviceAspect(); }
	}

	@Aspect
	@Order(1)
	static class FullAdviceAspect {
		@Around("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public Object aaa_around(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("    [@Around]          BEFORE → aaa_around");
			Object result = pjp.proceed();
			System.out.println("    [@Around]          AFTER  → aaa_around");
			return result;
		}

		@Before("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void bbb_before() {
			System.out.println("    [@Before]          → bbb_before");
		}

		@After("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void ccc_after() {
			System.out.println("    [@After]           → ccc_after (finally 语义)");
		}

		@AfterReturning("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void ddd_afterReturning() {
			System.out.println("    [@AfterReturning]  → ddd_afterReturning");
		}

		@AfterThrowing("execution(* org.springframework.lab.advisororder.OrderService.placeOrder(..))")
		public void eee_afterThrowing() {
			System.out.println("    [@AfterThrowing]   → eee_afterThrowing (仅异常时执行)");
		}
	}
}
