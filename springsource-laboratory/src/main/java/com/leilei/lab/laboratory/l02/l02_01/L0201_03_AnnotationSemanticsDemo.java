package com.leilei.lab.laboratory.l02.l02_01;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedRuntimeException;

/**
 * 📖 知识点：[[L02-01-注入方式选型-构造器Setter字段#2.4 三种注入注解的语义差异]]（@Autowired vs @Resource vs @Inject）
 * 🎯 作用：同一族「折扣策略」放三个实现 Bean，逐一对照三种注入注解在 <b>歧义裁决</b> 上的语义差异：
 *         ① {@code @Autowired} 按类型注入，多候选时歧义报错，靠 {@code @Qualifier}/{@code @Primary} 裁决；
 *         ② {@code @Resource} 默认按「字段名 = Bean 名」注入，天然规避多实现歧义；
 *         ③ {@code @Inject}（JSR-330）行为同 {@code @Autowired}（同样按类型、同样会歧义），配 {@code @Named} 定位；
 *         附带演示 {@code @Autowired(required=false)} 缺 Bean 不报错、而 {@code @Resource} 缺 Bean 直接抛异常。
 * 🔗 业务场景：古茗营销中心常见「一个折扣接口、多套策略实现」（满减/会员/新人），下单链路按场景选其一。
 *         选错注入注解 → 要么启动期 NoUniqueBeanDefinitionException，要么悄悄注错策略导致算错到手价。
 */
public final class L0201_03_AnnotationSemanticsDemo {

	private L0201_03_AnnotationSemanticsDemo() {
	}

	public static void main(String[] args) {
		BigDecimal order = new BigDecimal("30.00");
		long userId = 88L;

		System.out.println("==================== 1. @Autowired 按类型：多候选 → 启动期歧义报错 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(StrategyConfig.class, AutowiredByTypeConfig.class)) {
			System.out.println("不该走到这里：" + ctx);
		}
		catch (NestedRuntimeException ex) {
			boolean ambiguous = ex.contains(NoUniqueBeanDefinitionException.class);
			System.out.println("@Autowired 裸按类型注入失败，是否因『候选不唯一』= " + ambiguous);
			System.out.println("  根因：" + rootMessage(ex));
		}

		System.out.println();
		System.out.println("==================== 2. @Autowired + @Qualifier / @Primary：按类型 + 名字裁决 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(StrategyConfig.class, AutowiredResolvedConfig.class)) {
			AutowiredResolvedConfig.QualifierConsumer q = ctx.getBean(AutowiredResolvedConfig.QualifierConsumer.class);
			AutowiredResolvedConfig.PrimaryConsumer p = ctx.getBean(AutowiredResolvedConfig.PrimaryConsumer.class);
			System.out.println("@Autowired @Qualifier(\"memberDiscount\") 选中 = " + q.strategyName()
					+ "  立减=" + q.reduce(userId, order));
			System.out.println("@Autowired（无 Qualifier）命中 @Primary 的 = " + p.strategyName()
					+ "  立减=" + p.reduce(userId, order));
		}

		System.out.println();
		System.out.println("==================== 3. @Resource：默认按『字段名 = Bean 名』注入，天然无歧义 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(StrategyConfig.class, ResourceConfig.class)) {
			ResourceConfig.ResourceConsumer r = ctx.getBean(ResourceConfig.ResourceConsumer.class);
			System.out.println("@Resource 字段名 memberDiscount → byName 选中 = " + r.strategyName()
					+ "  立减=" + r.reduce(userId, order) + "（三候选并存却不报歧义）");
		}

		System.out.println();
		System.out.println("==================== 4. @Inject + @Named（JSR-330）：本质同 @Autowired ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(StrategyConfig.class, InjectConfig.class)) {
			InjectConfig.InjectConsumer i = ctx.getBean(InjectConfig.InjectConsumer.class);
			System.out.println("@Inject @Named(\"newUserDiscount\") 选中 = " + i.strategyName()
					+ "  立减=" + i.reduce(userId, order) + "（@Inject 无 required，定位靠 @Named）");
		}

		System.out.println();
		System.out.println("==================== 5. 缺 Bean 时：@Autowired(required=false) 容忍 vs @Resource 抛错 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(OptionalConfig.class)) {
			OptionalConfig.OptionalAutowiredConsumer c = ctx.getBean(OptionalConfig.OptionalAutowiredConsumer.class);
			System.out.println("@Autowired(required=false) 找不到 Bean → 字段保持 null，不阻断启动：strategy=" + c.hasStrategy());
		}
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(MissingResourceConfig.class)) {
			System.out.println("不该走到这里：" + ctx);
		}
		catch (NestedRuntimeException ex) {
			System.out.println("@Resource 找不到 Bean → 直接 NoSuchBeanDefinition 阻断启动，是否如此 = "
					+ ex.contains(NoSuchBeanDefinitionException.class));
		}
	}

	private static String rootMessage(Throwable ex) {
		Throwable cur = ex;
		while (cur.getCause() != null) {
			cur = cur.getCause();
		}
		return cur.getClass().getSimpleName() + ": " + cur.getMessage();
	}

	// ===================== 折扣策略族：一个接口、三个实现 =====================

	/** 折扣策略：给定用户与订单金额，返回立减金额。 */
	interface DiscountStrategy {

		BigDecimal reduce(long userId, BigDecimal orderAmount);

		String name();
	}

	/** 满 30 减 5。 */
	static class FullReductionDiscount implements DiscountStrategy {

		@Override
		public BigDecimal reduce(long userId, BigDecimal orderAmount) {
			return orderAmount.compareTo(new BigDecimal("30")) >= 0 ? new BigDecimal("5.00") : BigDecimal.ZERO;
		}

		@Override
		public String name() {
			return "满减";
		}
	}

	/** 会员 95 折：立减 = 订单额 × 5%。 */
	static class MemberDiscount implements DiscountStrategy {

		@Override
		public BigDecimal reduce(long userId, BigDecimal orderAmount) {
			return orderAmount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
		}

		@Override
		public String name() {
			return "会员折扣";
		}
	}

	/** 新人立减 3。 */
	static class NewUserDiscount implements DiscountStrategy {

		@Override
		public BigDecimal reduce(long userId, BigDecimal orderAmount) {
			return new BigDecimal("3.00");
		}

		@Override
		public String name() {
			return "新人立减";
		}
	}

	/** 三个策略以确定 Bean 名注册：fullReductionDiscount / memberDiscount / newUserDiscount。 */
	@Configuration
	static class StrategyConfig {

		@Bean
		DiscountStrategy fullReductionDiscount() {
			return new FullReductionDiscount();
		}

		@Bean
		DiscountStrategy memberDiscount() {
			return new MemberDiscount();
		}

		@Bean
		DiscountStrategy newUserDiscount() {
			return new NewUserDiscount();
		}
	}

	/** 场景 1：裸 @Autowired 按类型注入，三候选 → NoUniqueBeanDefinitionException。 */
	@Configuration
	static class AutowiredByTypeConfig {

		@Bean
		AmbiguousConsumer ambiguousConsumer() {
			return new AmbiguousConsumer();
		}

		static class AmbiguousConsumer {

			@Autowired
			private DiscountStrategy discountStrategy;
		}
	}

	/** 场景 2：@Qualifier 精确点名 + @Primary 兜底裁决。 */
	@Configuration
	static class AutowiredResolvedConfig {

		@Bean
		QualifierConsumer qualifierConsumer() {
			return new QualifierConsumer();
		}

		@Bean
		PrimaryConsumer primaryConsumer() {
			return new PrimaryConsumer();
		}

		/** @Qualifier 把「按类型」收窄成「按类型 + 指定 Bean 名」。 */
		static class QualifierConsumer {

			@Autowired
			@Qualifier("memberDiscount")
			private DiscountStrategy strategy;

			String strategyName() {
				return this.strategy.name();
			}

			BigDecimal reduce(long userId, BigDecimal order) {
				return this.strategy.reduce(userId, order);
			}
		}

		/** 无 @Qualifier，但容器里有标了 @Primary 的候选（见下方 PrimaryStrategyConfig）。 */
		static class PrimaryConsumer {

			@Autowired
			private DiscountStrategy strategy;

			String strategyName() {
				return this.strategy.name();
			}

			BigDecimal reduce(long userId, BigDecimal order) {
				return this.strategy.reduce(userId, order);
			}
		}

		/** 用一个标了 @Primary 的满减策略覆盖默认裁决（与 StrategyConfig 同名 fullReductionDiscount，演示 @Primary 胜出）。 */
		@Bean
		@org.springframework.context.annotation.Primary
		DiscountStrategy primaryFullReductionDiscount() {
			return new FullReductionDiscount();
		}
	}

	/** 场景 3：@Resource 默认按字段名（= Bean 名 memberDiscount）注入。 */
	@Configuration
	static class ResourceConfig {

		@Bean
		ResourceConsumer resourceConsumer() {
			return new ResourceConsumer();
		}

		static class ResourceConsumer {

			/** 字段名 memberDiscount 即 byName 的 key——三候选并存也不歧义。 */
			@Resource
			private DiscountStrategy memberDiscount;

			String strategyName() {
				return this.memberDiscount.name();
			}

			BigDecimal reduce(long userId, BigDecimal order) {
				return this.memberDiscount.reduce(userId, order);
			}
		}
	}

	/** 场景 4：@Inject + @Named（JSR-330），语义同 @Autowired 按类型 + 名字定位。 */
	@Configuration
	static class InjectConfig {

		@Bean
		InjectConsumer injectConsumer() {
			return new InjectConsumer();
		}

		static class InjectConsumer {

			@Inject
			@Named("newUserDiscount")
			private DiscountStrategy strategy;

			String strategyName() {
				return this.strategy.name();
			}

			BigDecimal reduce(long userId, BigDecimal order) {
				return this.strategy.reduce(userId, order);
			}
		}
	}

	/** 场景 5a：@Autowired(required=false) 缺 Bean 容忍。 */
	@Configuration
	static class OptionalConfig {

		@Bean
		OptionalAutowiredConsumer optionalAutowiredConsumer() {
			return new OptionalAutowiredConsumer();
		}

		static class OptionalAutowiredConsumer {

			@Autowired(required = false)
			private DiscountStrategy strategy;

			boolean hasStrategy() {
				return this.strategy != null;
			}
		}
	}

	/** 场景 5b：@Resource 缺 Bean 直接抛 NoSuchBeanDefinitionException。 */
	@Configuration
	static class MissingResourceConfig {

		@Bean
		MissingResourceConsumer missingResourceConsumer() {
			return new MissingResourceConsumer();
		}

		static class MissingResourceConsumer {

			@Resource
			private DiscountStrategy notExistDiscount;
		}
	}

}
