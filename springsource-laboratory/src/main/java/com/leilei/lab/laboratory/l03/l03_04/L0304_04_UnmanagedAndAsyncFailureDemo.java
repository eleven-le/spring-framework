package com.leilei.lab.laboratory.l03.l03_04;

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
 * 📖 知识点：[[L03-04-AOP失效场景全集与排查#2. 🏭 生产怎么用对]]（§2.4 容器外失效 + 异步线程 exposeProxy 失效 ⭐）
 * 🎯 作用：收尾两类「容器/线程边界」失效：
 *         ① <b>对象不是从容器拿的</b>——自己 {@code new} 出来的实例根本没经过 {@link org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator}
 *            包装，是裸对象、没有代理，切面 100% 不生效（计数对照：容器 Bean=N，new 出来的=0）。
 *         ② <b>跨线程丢上下文</b>——{@code exposeProxy=true} 把代理存进 <b>ThreadLocal</b>，
 *            一旦在 {@code new Thread} / 线程池里调 {@link AopContext#currentProxy()}，新线程取不到、抛 {@link IllegalStateException}；
 *            同线程内调则正常。坐实 currentProxy() 的 ThreadLocal 语义边界。
 * 🔗 业务场景：缓存预热——有人图省事直接 {@code new CacheWarmService().loadHotSku()} 跑预热，切面（埋点/限并发）全失效；
 *         又有人把 {@code AopContext.currentProxy()} 放进异步预热线程里自调用，线上偶发 IllegalStateException，
 *         查了半天才发现是「代理上下文不跨线程」。
 */
public final class L0304_04_UnmanagedAndAsyncFailureDemo {

	private L0304_04_UnmanagedAndAsyncFailureDemo() {
	}

	// ==================== 计数切面：统计 loadHotSku 进入代理链次数 ====================

	@Aspect
	static class WarmCountingAspect {
		final AtomicInteger loadHits = new AtomicInteger();

		@Around("execution(* com.leilei.lab.laboratory.l03.l03_04."
				+ "L0304_04_UnmanagedAndAsyncFailureDemo.CacheWarmService.loadHotSku(..))")
		public Object count(ProceedingJoinPoint pjp) throws Throwable {
			loadHits.incrementAndGet();
			return pjp.proceed();
		}
	}

	static class CacheWarmService {

		/** 被切面盯上的预热方法。 */
		public String loadHotSku(long skuId) {
			return "WARM:" + skuId;
		}

		/** ✅ 同线程内调 currentProxy()：处于 AOP 调用上下文，能拿到代理。 */
		public String warmupSameThread() {
			Object proxy = AopContext.currentProxy();
			return "same-thread 拿到代理=" + (proxy != null);
		}

		/** ❌ 新线程内调 currentProxy()：ThreadLocal 不跨线程，抛 IllegalStateException。 */
		public String warmupNewThread() throws InterruptedException {
			final String[] box = new String[1];
			Thread t = new Thread(() -> {
				try {
					AopContext.currentProxy();
					box[0] = "new-thread 居然拿到了代理（不应发生）";
				}
				catch (IllegalStateException ex) {
					box[0] = "new-thread 抛 IllegalStateException：" + ex.getMessage();
				}
			});
			t.start();
			t.join();
			return box[0];
		}
	}

	@Configuration
	@EnableAspectJAutoProxy(exposeProxy = true)
	static class AopConfig {
		@Bean
		WarmCountingAspect warmCountingAspect() {
			return new WarmCountingAspect();
		}

		@Bean
		CacheWarmService cacheWarmService() {
			return new CacheWarmService();
		}
	}

	public static void main(String[] args) throws InterruptedException {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			CacheWarmService managed = ctx.getBean(CacheWarmService.class);
			WarmCountingAspect aspect = ctx.getBean(WarmCountingAspect.class);
			int rounds = 5;

			System.out.println("==================== 失效一：对象不是从容器拿的（自己 new） ====================");
			CacheWarmService unmanaged = new CacheWarmService();   // 裸对象，绕过容器，没有代理
			for (int i = 0; i < rounds; i++) {
				unmanaged.loadHotSku(10000100L + i);
			}
			System.out.println("  new 出来调 " + rounds + " 次，loadHotSku 进切面次数 = " + aspect.loadHits.get()
					+ "  ❌ 裸对象无代理，切面 100% 不生效");

			for (int i = 0; i < rounds; i++) {
				managed.loadHotSku(10000100L + i);                 // 容器 Bean，走代理
			}
			System.out.println("  容器 Bean 调 " + rounds + " 次，loadHotSku 进切面累计 = " + aspect.loadHits.get()
					+ "  ✅ 容器托管的 Bean 才被自动代理");

			System.out.println("\n==================== 失效二：exposeProxy 的代理上下文不跨线程 ====================");
			System.out.println("  同线程：" + managed.warmupSameThread() + "  ✅");
			System.out.println("  新线程：" + managed.warmupNewThread() + "  ❌（ThreadLocal 不跨线程）");

			System.out.println("\n结论：① 切面只对「容器托管 + 自动代理」的 Bean 生效，自己 new 的对象永远绕过；"
					+ "② AopContext.currentProxy() 是 ThreadLocal 语义，异步线程里必失效——"
					+ "跨线程要把代理对象作为参数显式传入，别在子线程里调 currentProxy()。");
		}
	}
}
