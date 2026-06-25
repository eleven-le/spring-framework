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
import org.aspectj.lang.reflect.MethodSignature;

import com.leilei.lab.laboratory.common.bench.BenchReport;
import com.leilei.lab.laboratory.common.bench.ConcurrentBench;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

/**
 * 📖 知识点：[[L03-03-切面实战-鉴权日志限流幂等#2. 🏭 生产怎么用对]]（§2.3 限流切面：注解驱动限流 ⭐）
 * 🎯 作用：用 {@code @RateLimit(permitsPerSecond=...)} 注解 + 一个 {@code @Around} 切面做单机令牌桶限流。
 *         技术要点：用 <b>{@code @annotation(rateLimit)} 把注解实例绑进通知参数</b>（而非反射再读一遍），
 *         这是 Spring AOP 注解驱动切面的标准写法；令牌桶 {@link TokenBucket} 按「每秒匀速补桶 + 允许突发」放行，
 *         超额请求 fail-fast 抛 {@link RateLimitException}，把过载在最外圈挡掉、不打到下游。
 *         配合 {@link ConcurrentBench} 齐射压测，用「成功/失败」计数量化限流效果。
 * 🔗 业务场景：大促秒杀下单接口——开场瞬时 QPS 远超容量，限流把超额流量在切面层快速拒绝，
 *         保护库存/订单/Redis 等下游，是 C 端高并发接口的第一道闸（@Order 最外层）。
 */
public final class L0303_03_RateLimitAspect {

	private L0303_03_RateLimitAspect() {
	}

	// ==================== 限流注解 ====================

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface RateLimit {
		/** 每秒放行许可数（令牌生成速率 = 桶容量，允许 1 秒的突发）。 */
		double permitsPerSecond();
	}

	/** 限流拒绝异常（生产里全局异常处理器翻译成 429 Too Many Requests）。 */
	static final class RateLimitException extends RuntimeException {
		RateLimitException(String message) {
			super(message);
		}
	}

	// ==================== 令牌桶（单机、无锁 CAS 匀速补桶）====================

	/**
	 * 简化令牌桶：以纳秒为时钟，按 permitsPerSecond 匀速生成令牌，桶容量 = 速率（容忍 1 秒突发）。
	 * tryAcquire 用 CAS 推进「下一个可用令牌时间戳」，无锁、可在高并发下安全计数。
	 */
	static final class TokenBucket {
		private final double permitsPerSecond;
		private final long capacityNanos;
		private final AtomicLong nextFreeNanos;

		TokenBucket(double permitsPerSecond) {
			this.permitsPerSecond = permitsPerSecond;
			this.capacityNanos = 1_000_000_000L;   // 桶容量 = 1 秒额度（允许冷启动时一次性突发 permitsPerSecond 个）
			// 初始化为「桶已满」：把 nextFree 回拨一个容量窗口，冷启动即可放行约 permitsPerSecond 个突发请求
			this.nextFreeNanos = new AtomicLong(System.nanoTime() - capacityNanos);
		}

		boolean tryAcquire() {
			long intervalNanos = (long) (1_000_000_000L / permitsPerSecond);   // 单个令牌的时间成本
			while (true) {
				long now = System.nanoTime();
				long next = nextFreeNanos.get();
				// 令牌存量上限：now 之前最多回填一桶（capacityNanos），避免长期空闲后瞬时无限突发
				long base = Math.max(next, now - capacityNanos);
				if (base > now) {
					return false;   // 桶已空（下一个令牌在未来），拒绝
				}
				if (nextFreeNanos.compareAndSet(next, base + intervalNanos)) {
					return true;    // 抢到一个令牌
				}
				// CAS 失败说明并发争用，重试
			}
		}
	}

	// ==================== 业务服务 ====================

	static class SeckillService {
		private final AtomicLong realExecuted = new AtomicLong();

		@RateLimit(permitsPerSecond = 200)
		String placeOrder(long skuId, long userId) {
			realExecuted.incrementAndGet();
			// 模拟下单真实成本（写订单/扣库存），限流的意义就是别让这段被超额调用打爆
			return "ORDER-" + skuId + "-" + userId;
		}

		long realExecuted() {
			return realExecuted.get();
		}
	}

	// ==================== 限流切面 ====================

	@Aspect
	@Order(1)   // 限流最外层：超额流量在这里 fail-fast，省下里层幂等/库存/Redis 的一切开销
	static class RateLimitAspect {

		/** 一个被限流方法一个桶，按「类#方法」做 key（生产里可扩展为 key = 注解里的 SpEL，按用户/门店维度限流）。 */
		private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

		// @annotation(rateLimit)：Spring 把命中的注解实例绑定到名为 rateLimit 的通知参数（argNames 显式声明更稳）
		@Around(value = "@annotation(rateLimit)", argNames = "pjp,rateLimit")
		public Object limit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
			String key = ((MethodSignature) pjp.getSignature()).getMethod().toString();
			TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rateLimit.permitsPerSecond()));
			if (!bucket.tryAcquire()) {
				throw new RateLimitException("限流触发（" + rateLimit.permitsPerSecond() + " QPS）：" + key);
			}
			return pjp.proceed();
		}
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class AopConfig {
		@Bean
		SeckillService seckillService() {
			return new SeckillService();
		}

		@Bean
		RateLimitAspect rateLimitAspect() {
			return new RateLimitAspect();
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			SeckillService service = ctx.getBean(SeckillService.class);

			System.out.println("==================== 秒杀下单：@RateLimit(200 QPS) 齐射压测 ====================");
			System.out.println("  50 线程 × 每线程 200 次 = 10000 次瞬时请求，闸门设 200 QPS");
			BenchReport report = ConcurrentBench.run("seckill-ratelimit", 50, 200,
					() -> service.placeOrder(10000100L, 88001L));   // 抛 RateLimitException 即计「失败」

			System.out.println();
			System.out.println(report.prettyPrint());
			System.out.println();
			System.out.println("  放行(成功)=" + report.getSuccessOps() + "  被限流(失败)=" + report.getFailOps()
					+ "  目标真正执行次数=" + service.realExecuted());
			System.out.println("  说明：放行数 ≈ 桶容量（瞬时突发额度），其余被切面 fail-fast 拒绝，未打到下单逻辑。");
			System.out.println("  结论：限流切面把过载挡在最外圈（@Order(1)），下游只承接「确定要执行」的流量。");
		}
	}
}
