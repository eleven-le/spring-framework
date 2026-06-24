package com.leilei.lab.laboratory.l02.l02_07;

import java.math.BigDecimal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;

/**
 * 📖 知识点：[[L02-07-作用域-scope代理与按需注入#2.3 ObjectProvider 按需获取]]（ObjectProvider / ObjectFactory 延迟与按需获取）
 * 🎯 作用：坐实 {@link ObjectProvider} 的四种「按需」姿势，替代「硬注入 + 可能找不到 / 不唯一就启动失败」：
 *         ① {@code getIfAvailable(默认值)}——可选依赖，缺失就用兜底，不报错；
 *         ② {@code getIfUnique()}——多候选时返回 null（避免 NoUniqueBeanDefinitionException）；
 *         ③ {@code orderedStream()}——按 {@code @Order} 遍历全部候选；
 *         ④ {@code getObject()} 每次向容器要——配合 prototype 实现「每次拿全新实例」。
 *         ObjectProvider 继承 {@code ObjectFactory}，本质是把「依赖解析」从注入那一刻推迟到「真正用时」。
 * 🔗 业务场景：下单算价时按需取「营销活动策略」——大促活动可能没配（可选）、可能配了多个（择优遍历）、
 *         每次算价要一份独立的算价上下文（prototype 按需取）。
 */
public final class L0207_03_ObjectProviderDemo {

	private L0207_03_ObjectProviderDemo() {
	}

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ProviderConfig.class);
		OrderPricingService pricing = ctx.getBean(OrderPricingService.class);

		System.out.println("==================== ObjectProvider 四种按需姿势 ====================");

		System.out.println("  ① getIfAvailable(默认)：可选的节日活动未配置 → 用兜底折扣 " + pricing.holidayDiscountOrDefault());

		System.out.println("  ② getIfUnique()       ：营销策略有 2 个候选 → 返回唯一? " + pricing.uniqueStrategyOrNull()
				+ "（非唯一返回 null，不抛 NoUniqueBeanDefinitionException）");

		System.out.println("  ③ orderedStream()     ：按 @Order 遍历全部营销策略并择优 → 最优总折扣 = " + pricing.bestDiscount());

		System.out.println("  ④ getObject() 取 prototype：两次按需获取算价上下文同一实例? " + pricing.twoContextsAreSame()
				+ "（每次都向容器要全新 prototype）");

		System.out.println();
		System.out.println("[小结] ObjectProvider = 推迟到「用时」再解析依赖：可选/非唯一不再启动即炸，prototype 也能每次拿新实例");

		ctx.close();
	}

	@Configuration
	static class ProviderConfig {

		@Bean
		@Order(1)
		MemberDayStrategy memberDayStrategy() {
			return new MemberDayStrategy();
		}

		@Bean
		@Order(2)
		NewUserStrategy newUserStrategy() {
			return new NewUserStrategy();
		}

		/** 算价上下文：prototype，每次按需取一份独立的。 */
		@Bean
		@Scope("prototype")
		PricingContext pricingContext() {
			return new PricingContext();
		}

		@Bean
		OrderPricingService orderPricingService(ObjectProvider<MarketingStrategy> strategies,
				ObjectProvider<HolidayCampaign> holidayCampaign,
				ObjectProvider<PricingContext> contexts) {
			return new OrderPricingService(strategies, holidayCampaign, contexts);
		}
	}

	/**
	 * 下单算价服务：所有营销相关依赖都用 ObjectProvider 按需取，而非硬注入。
	 */
	static class OrderPricingService {

		private final ObjectProvider<MarketingStrategy> strategies;
		private final ObjectProvider<HolidayCampaign> holidayCampaign;
		private final ObjectProvider<PricingContext> contexts;

		OrderPricingService(ObjectProvider<MarketingStrategy> strategies,
				ObjectProvider<HolidayCampaign> holidayCampaign,
				ObjectProvider<PricingContext> contexts) {
			this.strategies = strategies;
			this.holidayCampaign = holidayCampaign;
			this.contexts = contexts;
		}

		/** ① 可选依赖：HolidayCampaign 容器里压根没注册，getIfAvailable 返回 null 就用兜底，不报错。 */
		BigDecimal holidayDiscountOrDefault() {
			HolidayCampaign campaign = holidayCampaign.getIfAvailable();
			return campaign != null ? campaign.discount() : BigDecimal.ZERO;
		}

		/** ② 非唯一依赖：两个 MarketingStrategy 候选，getIfUnique 返回 null（而非抛异常）。 */
		MarketingStrategy uniqueStrategyOrNull() {
			return strategies.getIfUnique();
		}

		/** ③ 全部候选：orderedStream 按 @Order 遍历择优。 */
		BigDecimal bestDiscount() {
			return strategies.orderedStream()
					.map(MarketingStrategy::discount)
					.max(BigDecimal::compareTo)
					.orElse(BigDecimal.ZERO);
		}

		/** ④ prototype 按需：每次 getObject 都是新实例。 */
		boolean twoContextsAreSame() {
			PricingContext c1 = contexts.getObject();
			PricingContext c2 = contexts.getObject();
			return c1 == c2;
		}
	}

	interface MarketingStrategy {

		BigDecimal discount();
	}

	/** 会员日策略：满减折扣。 */
	static class MemberDayStrategy implements MarketingStrategy {

		@Override
		public BigDecimal discount() {
			return new BigDecimal("3.00");
		}
	}

	/** 新客策略：首单折扣。 */
	static class NewUserStrategy implements MarketingStrategy {

		@Override
		public BigDecimal discount() {
			return new BigDecimal("5.00");
		}
	}

	/** 节日活动：演示「可选依赖」，本实验故意不注册它的 Bean。 */
	interface HolidayCampaign {

		BigDecimal discount();
	}

	/** prototype 算价上下文。 */
	static class PricingContext {
	}
}
