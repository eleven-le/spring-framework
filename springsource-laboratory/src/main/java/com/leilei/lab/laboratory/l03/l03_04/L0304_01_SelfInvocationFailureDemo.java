package com.leilei.lab.laboratory.l03.l03_04;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 📖 知识点：[[L03-04-AOP失效场景全集与排查#2. 🏭 生产怎么用对]]（§2.1 自调用失效——失效场景之首 ⭐）
 * 🎯 作用：复现 AOP 头号失效「目标内部 {@code this.method()} 自调用绕过代理 → 挂在被调方法上的切面静默失效」，
 *         并量化三条修复路径的效果：① {@link AopContext#currentProxy()} 拿回代理对象再调；
 *         ② 拆成两个 Bean，由 A Bean 调注入的 B Bean（跨 Bean 调用天然走代理）。
 *         切面只统计 {@code deduct*} 方法「真正进入代理链」的次数：自调用版计数=0（被绕过），两条修复版恢复=调用次数。
 *         根因一句话：Spring AOP 是<b>代理织入</b>，{@code this} 永远指向原始目标实例、不是代理对象，
 *         所以 this 自调用从一开始就没碰到代理（区别于 AspectJ 编译期织入，那是改字节码、self 调用也生效）。
 * 🔗 业务场景：秒杀下单 {@code placeOrder()} 内部调扣库存 {@code deductStock()}——若 deductStock 上挂了
 *         限流 / 幂等 / 库存校验切面，自调用让这些防护全失效，直接超卖、重复扣减。这是失效全集的第 1 类。
 */
public final class L0304_01_SelfInvocationFailureDemo {

	private L0304_01_SelfInvocationFailureDemo() {
	}

	// ==================== 计数切面：统计 deduct* 真正进入代理链的次数 ====================

	@Aspect
	static class DeductCountingAspect {
		final ConcurrentHashMap<String, AtomicInteger> hits = new ConcurrentHashMap<>();

		@Around("execution(* com.leilei.lab.laboratory.l03.l03_04."
				+ "L0304_01_SelfInvocationFailureDemo.OrderService.deductStock(..)) || "
				+ "execution(* com.leilei.lab.laboratory.l03.l03_04."
				+ "L0304_01_SelfInvocationFailureDemo.StockService.deduct(..))")
		public Object count(ProceedingJoinPoint pjp) throws Throwable {
			hits.computeIfAbsent(pjp.getSignature().getName(), k -> new AtomicInteger()).incrementAndGet();
			return pjp.proceed();
		}

		int of(String method) {
			AtomicInteger c = hits.get(method);
			return c == null ? 0 : c.get();
		}
	}

	// ==================== 下单服务：三种内部调用姿势同台对比 ====================

	static class OrderService {

		/** 拆 Bean 解法用：注入独立的库存 Bean，跨 Bean 调用天然走代理。 */
		private final StockService stockService;

		OrderService(StockService stockService) {
			this.stockService = stockService;
		}

		/** ❌ 失效版：this.deductStock() 直接打到目标实例，绕开代理，deductStock 上的切面不生效。 */
		String placeOrderViaThis(String requestId, long skuId, int qty) {
			return deductStock(requestId, skuId, qty);
		}

		/** ✅ 修复一：AopContext.currentProxy() 拿回代理对象再调，deductStock 重新走代理链。 */
		String placeOrderViaProxy(String requestId, long skuId, int qty) {
			return ((OrderService) AopContext.currentProxy()).deductStock(requestId, skuId, qty);
		}

		/** ✅ 修复二：拆 Bean——调注入的 StockService，跨 Bean 调用必经代理（生产首选，无 ThreadLocal 魔法）。 */
		String placeOrderViaSplitBean(String requestId, long skuId, int qty) {
			return stockService.deduct(requestId, skuId, qty);
		}

		/** 被切面盯上的「扣库存」方法（同类内的目标方法，是自调用的靶子）。 */
		String deductStock(String requestId, long skuId, int qty) {
			return "DEDUCT:" + skuId + "x" + qty + "@" + requestId;
		}
	}

	/** 拆 Bean 解法里独立的库存服务：它的 deduct 被同一个计数切面覆盖。 */
	static class StockService {
		String deduct(String requestId, long skuId, int qty) {
			return "DEDUCT:" + skuId + "x" + qty + "@" + requestId;
		}
	}

	@Configuration
	@EnableAspectJAutoProxy(exposeProxy = true)   // exposeProxy=true 才能让 AopContext.currentProxy() 拿到代理
	static class AopConfig {
		@Bean
		DeductCountingAspect deductCountingAspect() {
			return new DeductCountingAspect();
		}

		@Bean
		StockService stockService() {
			return new StockService();
		}

		@Bean
		OrderService orderService(StockService stockService) {
			return new OrderService(stockService);
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			OrderService order = ctx.getBean(OrderService.class);
			DeductCountingAspect aspect = ctx.getBean(DeductCountingAspect.class);
			int rounds = 5;

			System.out.println("==================== 场景 A：this.deductStock() 自调用（失效） ====================");
			for (int i = 0; i < rounds; i++) {
				order.placeOrderViaThis("REQ-A-" + i, 10000100L, 1);
			}
			System.out.println("  下单 " + rounds + " 次，deductStock 进入切面次数 = " + aspect.of("deductStock")
					+ "  ❌ 被自调用绕过——限流/幂等/库存校验切面全部静默失效，超卖隐患");

			System.out.println("\n==================== 场景 B：AopContext.currentProxy().deductStock()（修复一） ====================");
			for (int i = 0; i < rounds; i++) {
				order.placeOrderViaProxy("REQ-B-" + i, 10000100L, 1);
			}
			System.out.println("  下单 " + rounds + " 次，deductStock 进入切面次数 = " + aspect.of("deductStock")
					+ "  ✅ 经代理回调恢复生效（exposeProxy=true 把代理塞进 ThreadLocal）");

			System.out.println("\n==================== 场景 C：拆 Bean，调注入的 StockService.deduct()（修复二，首选） ====================");
			for (int i = 0; i < rounds; i++) {
				order.placeOrderViaSplitBean("REQ-C-" + i, 10000100L, 1);
			}
			System.out.println("  下单 " + rounds + " 次，StockService.deduct 进入切面次数 = " + aspect.of("deduct")
					+ "  ✅ 跨 Bean 调用天然走代理，无需 exposeProxy，可读性最好");

			System.out.println("\n结论：自调用失效 = 「代理对象 ≠ 目标 this」的必然结果。"
					+ "排查口诀『同类自调用？→ 必失效』；修复优先级：拆 Bean > AopContext.currentProxy() > 注入 self。");
		}
	}
}
