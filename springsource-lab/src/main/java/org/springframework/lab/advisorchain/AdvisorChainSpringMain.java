package org.springframework.lab.advisorchain;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * 基于 Spring 容器的 Advisor 注册 Demo
 *
 * 展示在真实项目中如何通过 @Bean 手动注册 Advisor（不依赖 @Aspect 注解）
 * —— 这是框架级治理链（事务/幂等/审计）的标准姿势
 *
 * 知识点：
 *  1. @Bean 返回 Advisor → 被 AbstractAdvisorAutoProxyCreator 自动发现
 *  2. AspectJExpressionPointcut → 支持 execution() 表达式
 *  3. AnnotationMatchingPointcut → 按自定义注解匹配
 *  4. Advisor 实现 Ordered → 参与排序
 */
@Configuration
@EnableAspectJAutoProxy
public class AdvisorChainSpringMain {

	// ─── 自定义注解：标记需要审计的方法 ───
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Auditable {
		String value() default "";
	}

	// ─── 业务 Service ───
	@Service
	public static class PayService {

		@Auditable("支付")
		public String pay(String userId, int amount) {
			System.out.println("  [TARGET] PayService.pay → userId=" + userId + ", amount=" + amount);
			return "PAY-OK-" + amount;
		}

		public String query(String orderId) {
			System.out.println("  [TARGET] PayService.query → orderId=" + orderId);
			return "QUERY-OK";
		}
	}

	// ─── Advisor-1: AspectJ 表达式精确匹配 pay 方法 ───
	@Bean
	public Advisor riskCheckAdvisor() {
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression("execution(* org.springframework.lab.advisorchain.AdvisorChainSpringMain.PayService.pay(..))");

		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut,
				(MethodInterceptor) invocation -> {
					System.out.println("  [风控检查] Advisor-AspectJ → " + invocation.getMethod().getName());
					return invocation.proceed();
				});
		advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
		return advisor;
	}

	// ─── Advisor-2: 按 @Auditable 注解匹配 ───
	@Bean
	public Advisor auditAdvisor() {
		AnnotationMatchingPointcut pointcut = AnnotationMatchingPointcut.forMethodAnnotation(Auditable.class);

		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut,
				(MethodInterceptor) invocation -> {
					Auditable ann = invocation.getMethod().getAnnotation(Auditable.class);
					System.out.println("  [审计日志] Advisor-Annotation → @Auditable(\"" +
							(ann != null ? ann.value() : "") + "\")");
					Object result = invocation.proceed();
					System.out.println("  [审计日志] 执行完毕 → result=" + result);
					return result;
				});
		advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
		return advisor;
	}

	// ─── 入口 ───
	public static void main(String[] args) {
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println(" W16 · Spring 容器内 Advisor 注册 Demo");
		System.out.println("═══════════════════════════════════════════════════════\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AdvisorChainSpringMain.class);
		PayService payService = ctx.getBean(PayService.class);

		System.out.println(">> pay (应触发: 风控 + 审计):");
		String result = payService.pay("U001", 500);
		System.out.println(">> 结果: " + result);

		System.out.println("\n>> query (不触发任何 Advisor):");
		payService.query("ORD-001");

		ctx.close();
	}
}
