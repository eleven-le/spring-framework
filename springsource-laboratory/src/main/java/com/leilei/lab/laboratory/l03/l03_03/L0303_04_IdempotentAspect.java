package com.leilei.lab.laboratory.l03.l03_03;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import com.leilei.lab.laboratory.common.bench.ConcurrentBench;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

/**
 * 📖 知识点：[[L03-03-切面实战-鉴权日志限流幂等#2. 🏭 生产怎么用对]]（§2.4 幂等切面：注解驱动幂等防重 ⭐）
 * 🎯 作用：用 {@code @Idempotent(keyArgIndex=...)} 注解 + 一个 {@code @Around} 切面做接口幂等防重。
 *         核心是「分布式锁/查重」的原子占位：用 {@link ConcurrentHashMap#putIfAbsent} 模拟 Redis {@code SETNX}，
 *         同一 requestId 只有<b>第一个</b>请求占位成功并真正执行，并发的重复请求一律被 {@link DuplicateRequestException} 拒绝。
 *         通过 {@code @annotation(idempotent)} 把注解实例绑进通知参数，再按 keyArgIndex 取出业务幂等键。
 *         用 {@link ConcurrentBench} 让多线程齐射「同一个 requestId」，坐实「无论并发多少，目标只执行 1 次」。
 * 🔗 业务场景：C 端下单 / 库存扣减接口——用户狂点提交、网络重试、MQ 重投都会产生重复请求，
 *         幂等切面用 requestId 防重，避免重复下单、重复扣库存（超卖）。是高并发写接口的标配第二道闸。
 */
public final class L0303_04_IdempotentAspect {

	private L0303_04_IdempotentAspect() {
	}

	// ==================== 幂等注解 ====================

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Idempotent {
		/** 幂等键在方法参数中的下标（生产里更常用 SpEL 表达式从入参对象取字段，这里用下标保持自包含）。 */
		int keyArgIndex();
	}

	/** 重复请求异常（生产里翻译成「请勿重复提交」或直接返回首次结果，取决于幂等语义）。 */
	static final class DuplicateRequestException extends RuntimeException {
		DuplicateRequestException(String message) {
			super(message);
		}
	}

	// ==================== 业务服务 ====================

	static class StockService {
		private final AtomicLong realDeduct = new AtomicLong();

		/** requestId 在第 0 个参数：同一 requestId 的重复扣减必须被挡掉。 */
		@Idempotent(keyArgIndex = 0)
		String deduct(String requestId, long skuId, int qty) {
			realDeduct.incrementAndGet();   // 真正的库存扣减——重复执行就是超卖
			return "DEDUCT:" + skuId + "x" + qty + "@" + requestId;
		}

		long realDeduct() {
			return realDeduct.get();
		}
	}

	// ==================== 幂等切面 ====================

	@Aspect
	@Order(2)   // 幂等在限流(1)之内、业务之外：超额流量先被限流挡掉，幂等查重只对放行流量做
	static class IdempotentAspect {

		/** 模拟 Redis：value 占位即「已有相同请求在处理/已处理」。生产里带 TTL，避免键无限堆积。 */
		private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();

		@Around(value = "@annotation(idempotent)", argNames = "pjp,idempotent")
		public Object dedup(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
			Object[] args = pjp.getArgs();
			String key = String.valueOf(args[idempotent.keyArgIndex()]);
			// putIfAbsent == SETNX：原子占位。返回非 null 说明键已存在 → 重复请求
			if (seen.putIfAbsent(key, Boolean.TRUE) != null) {
				throw new DuplicateRequestException("重复请求被拦截，requestId=" + key);
			}
			try {
				return pjp.proceed();
			}
			catch (Throwable ex) {
				// 业务失败要放掉占位，允许客户端重试（生产里等价于删除 Redis 键）
				seen.remove(key);
				throw ex;
			}
		}
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class AopConfig {
		@Bean
		StockService stockService() {
			return new StockService();
		}

		@Bean
		IdempotentAspect idempotentAspect() {
			return new IdempotentAspect();
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			StockService service = ctx.getBean(StockService.class);

			System.out.println("==================== 场景一：同一 requestId 并发齐射（重复提交 / 网络重试）====================");
			AtomicLong ok = new AtomicLong();
			AtomicLong rejected = new AtomicLong();
			ConcurrentBench.run("idempotent-same-key", 50, 1, () -> {
				try {
					service.deduct("REQ-SAME-0001", 10000100L, 1);   // 所有线程同一 requestId
					ok.incrementAndGet();
				}
				catch (DuplicateRequestException ex) {
					rejected.incrementAndGet();
				}
			});
			System.out.println("  并发 50 次同 requestId → 放行=" + ok.get() + "  拒绝=" + rejected.get()
					+ "  目标真正扣减次数=" + service.realDeduct());
			System.out.println("  ✅ 无论并发多少，目标只执行 1 次——杜绝重复扣减（超卖）。");

			System.out.println("\n==================== 场景二：不同 requestId（正常的不同请求）→ 全部放行 ====================");
			for (int i = 0; i < 3; i++) {
				String result = service.deduct("REQ-UNIQ-" + i, 10000100L, 1);
				System.out.println("  " + result);
			}
			System.out.println("  目标累计真正扣减次数=" + service.realDeduct() + "（场景一 1 次 + 场景二 3 次 = 4 次）");
			System.out.println("\n结论：幂等 = requestId + 原子占位(SETNX/putIfAbsent)；重复请求 fail-fast，"
					+ "业务失败时释放占位允许重试。@Order(2) 紧贴限流之内。");
		}
	}
}
