package com.leilei.lab.laboratory.l03.l03_02;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

/**
 * 📖 知识点：[[L03-02-Aspect切面工程化#2. 🏭 生产怎么用对]]（多切面排序 @Order ⭐）
 * 🎯 作用：三个独立切面（限流 / 幂等 / 审计）同时切一个秒杀下单方法，用 {@link Order @Order} 决定叠加顺序，
 *         打印「洋葱模型」的进出顺序，坐实规则：
 *         <b>@Order 值越小优先级越高 → 在调用链里越靠外 → 进入时越先跑、返回时越后跑。</b>
 *         <pre>
 *         进入: 限流(1) → 幂等(2) → 审计(3) → [目标]
 *         返回: 目标 → 审计(3) → 幂等(2) → 限流(1)
 *         </pre>
 *         顺序必须是「限流 → 幂等」而非反过来：先限流挡掉超额流量，避免无谓的幂等查重（查 Redis）被打爆；
 *         排序错了，防护就形同虚设。多个 @Order 相同的切面之间顺序未定义，必须显式区分。
 * 🔗 业务场景：大促秒杀下单链路上的「限流 + 幂等 + 审计」三道闸——这是 C 端高并发接口的标准切面叠层，
 *         @Order 决定了它们的拦截先后，是架构层面的「闸门排布」，详细落地在 [[L03-03-切面实战-鉴权日志限流幂等]]。
 */
public final class L0302_03_MultiAspectOrderDemo {

	private L0302_03_MultiAspectOrderDemo() {
	}

	/** 秒杀下单服务：被三道切面叠加包裹的目标。 */
	static class SeckillOrderService {
		String placeOrder(long skuId, long userId) {
			System.out.println("        [目标] placeOrder 真正下单 sku=" + skuId + ", user=" + userId);
			return "ORDER-" + skuId + "-" + userId;
		}
	}

	/** @Order=1 最外层：先限流，把超额流量在最外圈挡掉，省下里层一切开销。 */
	@Aspect
	@Order(1)
	static class RateLimitAspect {
		@Around("execution(* com.leilei.lab.laboratory.l03.l03_02..SeckillOrderService.placeOrder(..))")
		public Object guard(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("  → [1] 限流  进：令牌桶放行");
			Object ret = pjp.proceed();
			System.out.println("  ← [1] 限流  出");
			return ret;
		}
	}

	/** @Order=2 中间层：限流放行后再做幂等查重，避免对被限流流量做无谓的 Redis 查询。 */
	@Aspect
	@Order(2)
	static class IdempotentAspect {
		@Around("execution(* com.leilei.lab.laboratory.l03.l03_02..SeckillOrderService.placeOrder(..))")
		public Object dedup(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("    → [2] 幂等  进：requestId 查重通过");
			Object ret = pjp.proceed();
			System.out.println("    ← [2] 幂等  出：落幂等键");
			return ret;
		}
	}

	/** @Order=3 最内层：贴着目标方法记审计，拿到的是「确实要执行」的有效请求。 */
	@Aspect
	@Order(3)
	static class AuditAspect {
		@Around("execution(* com.leilei.lab.laboratory.l03.l03_02..SeckillOrderService.placeOrder(..))")
		public Object audit(ProceedingJoinPoint pjp) throws Throwable {
			System.out.println("      → [3] 审计  进：记录下单流水");
			Object ret = pjp.proceed();
			System.out.println("      ← [3] 审计  出：写入操作日志");
			return ret;
		}
	}

	@Configuration
	@EnableAspectJAutoProxy   // 默认 JDK 代理；此处目标无接口，容器自动回落 CGLIB
	static class AopConfig {
		@Bean
		SeckillOrderService seckillOrderService() {
			return new SeckillOrderService();
		}

		@Bean
		RateLimitAspect rateLimitAspect() {
			return new RateLimitAspect();
		}

		@Bean
		IdempotentAspect idempotentAspect() {
			return new IdempotentAspect();
		}

		@Bean
		AuditAspect auditAspect() {
			return new AuditAspect();
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			SeckillOrderService service = ctx.getBean(SeckillOrderService.class);

			System.out.println("==================== 三切面叠加：洋葱模型进出顺序 ====================");
			String orderId = service.placeOrder(10000100L, 88001L);
			System.out.println("  下单结果 = " + orderId);
			System.out.println();
			System.out.println("规则：@Order 小 = 优先级高 = 链路靠外 = 进先出后。");
			System.out.println("      限流(1) 在最外圈先挡 → 幂等(2) → 审计(3) 贴着目标；返回时反向收口。");
			System.out.println("      若把限流放到内层，超额流量会先打到幂等查重（Redis），防护就被架空。");
		}
	}
}
