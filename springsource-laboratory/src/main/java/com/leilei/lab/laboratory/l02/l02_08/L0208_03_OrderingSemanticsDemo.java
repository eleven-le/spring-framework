package com.leilei.lab.laboratory.l02.l02_08;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.Order;

/**
 * 📖 知识点：[[L02-08-歧义裁决与容器语义-Primary-Qualifier-DependsOn-Bean覆盖#2.3 @Order vs Ordered vs PriorityOrdered]]
 *         （互链 [[L09-02-BeanFactoryPostProcessor与BeanPostProcessor#执行顺序]]）
 * 🎯 作用：把三种排序手段的语义与边界一次讲透：
 *         ① 注入 {@code List<OrderCheck>} 时，{@code AnnotationAwareOrderComparator} 排序——
 *            {@code PriorityOrdered} 永远排在普通 {@code Ordered} / {@code @Order} 之前，组内再按 order 值升序；
 *         ② {@code @Order} 注解与实现 {@code Ordered} 接口效果等价（值越小越靠前）；
 *         ③ <b>关键边界</b>：{@code @Order} / {@code Ordered} <b>只管集合注入的排序，不参与单注入点的歧义裁决</b>——
 *            单点多候选只认 {@code @Primary} / {@code @Priority}，给候选加 {@code @Order} 照样
 *            {@code NoUniqueBeanDefinitionException}。
 * 🔗 业务场景：古茗下单前置校验链（风控 / 优惠券 / 限购 / 库存）——顺序错了会放过黑产或误杀正常单；
 *         风控必须最先跑（{@code PriorityOrdered}），其余按业务优先级用 {@code @Order} 排定。
 */
public final class L0208_03_OrderingSemanticsDemo {

	private L0208_03_OrderingSemanticsDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. List 注入：PriorityOrdered 优先，组内按 order 值升序 ====================");
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CheckChainConfig.class)) {
			CheckChainConfig.CheckChain chain = ctx.getBean(CheckChainConfig.CheckChain.class);
			List<String> order = chain.checks.stream().map(OrderCheck::name).collect(Collectors.toList());
			System.out.println("校验链执行顺序 = " + order);
			System.out.println("说明：riskCheck 实现 PriorityOrdered 永远第一；其余 couponCheck(@Order 5) "
					+ "< purchaseLimitCheck(@Order 10) < stockCheck(Ordered 20)");
		}

		System.out.println();
		System.out.println("==================== 2. 单注入点歧义：@Order 不参与裁决，照样报错 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(CheckChainConfig.class, SingleInjectConfig.class)) {
			System.out.println("不该走到这里：" + ctx);
		}
		catch (NestedRuntimeException ex) {
			System.out.println("给候选加了 @Order，单点注入仍歧义 = " + ex.contains(NoUniqueBeanDefinitionException.class));
			System.out.println("  结论：@Order 只排序集合注入，单注入点裁决只认 @Primary / @Priority。");
		}
	}

	// ===================== 下单前置校验链 =====================

	/** 下单前置校验：返回校验器名，重在演示排序。 */
	interface OrderCheck {

		String name();
	}

	/** 风控校验：实现 PriorityOrdered —— 无论 order 值如何都排在普通 Ordered 之前。 */
	static class RiskCheck implements OrderCheck, PriorityOrdered {

		@Override
		public String name() {
			return "风控校验";
		}

		@Override
		public int getOrder() {
			return 0;
		}
	}

	/** 库存校验：实现普通 Ordered 接口，order=20。 */
	static class StockCheck implements OrderCheck, Ordered {

		@Override
		public String name() {
			return "库存校验";
		}

		@Override
		public int getOrder() {
			return 20;
		}
	}

	/** 限购校验：用 @Order(10) 注解，等价于实现 Ordered 返回 10。 */
	@Order(10)
	static class PurchaseLimitCheck implements OrderCheck {

		@Override
		public String name() {
			return "限购校验";
		}
	}

	/** 优惠券校验：@Order(5)。 */
	@Order(5)
	static class CouponCheck implements OrderCheck {

		@Override
		public String name() {
			return "优惠券校验";
		}
	}

	@Configuration
	static class CheckChainConfig {

		@Bean
		OrderCheck stockCheck() {
			return new StockCheck();
		}

		@Bean
		OrderCheck riskCheck() {
			return new RiskCheck();
		}

		@Bean
		OrderCheck purchaseLimitCheck() {
			return new PurchaseLimitCheck();
		}

		@Bean
		OrderCheck couponCheck() {
			return new CouponCheck();
		}

		@Bean
		CheckChain checkChain() {
			return new CheckChain();
		}

		/** 注入全部候选为 List —— 由 AnnotationAwareOrderComparator 排序。 */
		static class CheckChain {

			@Autowired
			List<OrderCheck> checks;
		}
	}

	@Configuration
	static class SingleInjectConfig {

		@Bean
		SingleConsumer singleConsumer() {
			return new SingleConsumer();
		}

		/** 单注入点 + 多候选：@Order 在这里完全帮不上忙。 */
		static class SingleConsumer {

			@Autowired
			OrderCheck check;
		}
	}

}
