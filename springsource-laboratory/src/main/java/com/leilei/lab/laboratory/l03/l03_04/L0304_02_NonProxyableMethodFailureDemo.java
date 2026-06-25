package com.leilei.lab.laboratory.l03.l03_04;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 📖 知识点：[[L03-04-AOP失效场景全集与排查#2. 🏭 生产怎么用对]]（§2.2 方法不可代理：final / static / private ⭐）
 * 🎯 作用：复现「同一个 CGLIB 代理 Bean、同一条切点，仅因目标方法被 {@code final} 修饰，切面就静默失效」。
 *         CGLIB 代理 = 生成目标类的<b>子类</b>并 override 方法插入拦截；{@code final} 方法无法 override，
 *         {@link org.springframework.aop.framework.CglibAopProxy} 的 {@code doValidateClass} 只在<b>日志里</b>
 *         info/debug 警告一句、绝不抛异常——这正是它「静默」的可怕之处：编译过、启动过、跑起来切面就是不进。
 *         本类用计数切面证明：{@code calcNormal}（非 final）进切面=调用次数，{@code calcFinal}（final）进切面=0。
 *         同理 static（无实例可 override）、private（不在子类可见方法集）也不可被 CGLIB 织入，机理一致。
 * 🔗 业务场景：价格中心把核心算价方法 {@code calcFinal()} 顺手标了 final「防篡改」，结果挂在它上面的
 *         价格缓存 / 活动价计算切面全部不生效——线上价格没走活动规则，全是原价，酿成价格事故。
 */
public final class L0304_02_NonProxyableMethodFailureDemo {

	private L0304_02_NonProxyableMethodFailureDemo() {
	}

	// ==================== 计数切面：统计 calc* 真正进入代理链的次数 ====================

	@Aspect
	static class PriceCountingAspect {
		final ConcurrentHashMap<String, AtomicInteger> hits = new ConcurrentHashMap<>();

		@Around("execution(* com.leilei.lab.laboratory.l03.l03_04."
				+ "L0304_02_NonProxyableMethodFailureDemo.PriceService.calc*(..))")
		public Object count(ProceedingJoinPoint pjp) throws Throwable {
			hits.computeIfAbsent(pjp.getSignature().getName(), k -> new AtomicInteger()).incrementAndGet();
			return pjp.proceed();
		}

		int of(String method) {
			AtomicInteger c = hits.get(method);
			return c == null ? 0 : c.get();
		}
	}

	// ==================== 价格服务：同一 Bean 上「非 final」与「final」两个同形方法 ====================

	static class PriceService {

		/** ✅ 可代理：非 final 实例方法，CGLIB 能 override → 切面正常织入。 */
		public BigDecimal calcNormal(BigDecimal base, BigDecimal discount) {
			return base.subtract(discount);
		}

		/**
		 * ❌ 不可代理：final 方法，CGLIB 无法 override → 切面静默失效。
		 * 仅依赖入参、不碰实例字段，避免「final 方法在代理实例上执行命中未初始化字段 NPE」的二次坑
		 * （即 CglibAopProxy#doValidateClass 注释里警告的 NPE 场景）。
		 */
		public final BigDecimal calcFinal(BigDecimal base, BigDecimal discount) {
			return base.subtract(discount);
		}
	}

	@Configuration
	@EnableAspectJAutoProxy(proxyTargetClass = true)   // 强制 CGLIB（无接口本就走 CGLIB，这里显式标明对照点）
	static class AopConfig {
		@Bean
		PriceCountingAspect priceCountingAspect() {
			return new PriceCountingAspect();
		}

		@Bean
		PriceService priceService() {
			return new PriceService();
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			PriceService price = ctx.getBean(PriceService.class);
			PriceCountingAspect aspect = ctx.getBean(PriceCountingAspect.class);
			BigDecimal base = new BigDecimal("18.00");
			BigDecimal off = new BigDecimal("3.00");
			int rounds = 5;

			System.out.println("代理实际类型 = " + price.getClass().getName()
					+ "（含 $$EnhancerBySpringCGLIB$$ 即 CGLIB 子类代理）");

			System.out.println("\n==================== 非 final 方法 calcNormal（可代理） ====================");
			for (int i = 0; i < rounds; i++) {
				price.calcNormal(base, off);
			}
			System.out.println("  调用 " + rounds + " 次，calcNormal 进入切面次数 = " + aspect.of("calcNormal")
					+ "  ✅ CGLIB 能 override，切面正常织入");

			System.out.println("\n==================== final 方法 calcFinal（不可代理，静默失效） ====================");
			for (int i = 0; i < rounds; i++) {
				price.calcFinal(base, off);
			}
			System.out.println("  调用 " + rounds + " 次，calcFinal  进入切面次数 = " + aspect.of("calcFinal")
					+ "  ❌ final 无法 override，切面静默失效（CglibAopProxy 仅日志 info 警告、不报错）");

			System.out.println("\n结论：方法被 final/static/private 修饰 → CGLIB 无法织入，切面静默失效。"
					+ "排查口诀『切面没进？先看目标方法是不是 final/static/private、Bean 是不是 CGLIB 代理』；"
					+ "修复：去掉 final，或把横切逻辑下沉到可代理的 public 实例方法。");
		}
	}
}
