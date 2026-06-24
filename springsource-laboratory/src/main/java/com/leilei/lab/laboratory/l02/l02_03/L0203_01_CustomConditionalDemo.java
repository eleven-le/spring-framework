package com.leilei.lab.laboratory.l02.l02_03;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 📖 知识点：[[L02-03-条件装配-Conditional与Profile#2. 🏭 生产怎么用对]]（@Conditional 派生体系与自定义 Condition）
 * 🎯 作用：手写「按开关属性装配」的组合注解——{@link ConditionalOnFlag} 元注解了 {@link Conditional}，
 *         由 {@link OnFlagCondition} 读 {@code Environment} 里的布尔开关裁决 Bean 是否进容器；
 *         再叠加第二个组合注解 {@link ConditionalOnGrayRelease}，演示「多个 @Conditional 派生注解叠加 = AND 语义」
 *         （{@code ConditionEvaluator#shouldSkip} 收集全部 Condition，任一不匹配即 veto）。
 *         这正是 Spring Boot {@code @ConditionalOnProperty}/{@code @ConditionalOnMissingBean} 的同款套路。
 * 🔗 业务场景：秒杀立减组件是把双刃剑——压不住下游就得能一键熔断。用 {@code flashsale.seckill.enabled} 控制秒杀组件装配，
 *         再用 {@code release.gray.enabled} 控制「秒杀灰度增强组件」只在灰度放量时挂载：开关关 → 组件根本不进容器，
 *         零运行期分支、零残留 Bean，比「先注册再 if 判断」干净，也避免了关掉的组件还占着内存 / 还能被误注入。
 */
public final class L0203_01_CustomConditionalDemo {

	private L0203_01_CustomConditionalDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 双开关全关：秒杀组件 / 灰度增强组件都不进容器 ====================");
		run(false, false);
		System.out.println();
		System.out.println("==================== 2. 秒杀开 + 灰度关：秒杀组件装配；灰度增强组件需两开关同开 → 仍缺位 ====================");
		run(true, false);
		System.out.println();
		System.out.println("==================== 3. 双开关全开：秒杀组件 + 灰度增强组件（@ConditionalOnFlag & @ConditionalOnGrayRelease 双条件 AND）一起装配 ====================");
		run(true, true);
	}

	private static void run(boolean seckillEnabled, boolean grayEnabled) {
		Map<String, Object> flags = new HashMap<>();
		flags.put("flashsale.seckill.enabled", seckillEnabled);
		flags.put("release.gray.enabled", grayEnabled);

		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			// 把开关作为最高优先级 PropertySource 注入 Environment，自定义 Condition 从这里读
			ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("flags", flags));
			ctx.register(SeckillConfig.class);
			ctx.refresh();

			boolean hasSeckill = ctx.containsBean("flashSaleDiscountComponent");
			boolean hasGray = ctx.containsBean("graySeckillBoostComponent");
			System.out.println("[开关] flashsale.seckill.enabled=" + seckillEnabled + "，release.gray.enabled=" + grayEnabled);
			System.out.println("[装配] 秒杀立减组件 flashSaleDiscountComponent 是否在容器 = " + hasSeckill);
			System.out.println("[装配] 灰度增强组件 graySeckillBoostComponent 是否在容器 = " + hasGray
					+ "（需 seckill & gray 双开关同开，单开关命中不了）");
		}
	}

	@Configuration
	static class SeckillConfig {

		/** 单条件：仅受秒杀开关控制。 */
		@Bean
		@ConditionalOnFlag("flashsale.seckill.enabled")
		FlashSaleDiscountComponent flashSaleDiscountComponent() {
			return new FlashSaleDiscountComponent();
		}

		/** 双条件叠加：两个派生注解各带一个 @Conditional，AND 语义——秒杀开关 且 灰度开关同时为真才装配。 */
		@Bean
		@ConditionalOnFlag("flashsale.seckill.enabled")
		@ConditionalOnGrayRelease
		GraySeckillBoostComponent graySeckillBoostComponent() {
			return new GraySeckillBoostComponent();
		}
	}

	// ============ 自定义 @Conditional 派生注解（组合注解）============

	/** 派生注解：元注解 {@link Conditional}，按 Environment 里某个布尔开关属性裁决装配。 */
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@Conditional(OnFlagCondition.class)
	@interface ConditionalOnFlag {

		/** 开关属性名，true 才装配（缺省视为 false）。 */
		String value();
	}

	/** 派生注解：固定校验灰度放量开关 {@code release.gray.enabled}。 */
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@Conditional(OnGrayReleaseCondition.class)
	@interface ConditionalOnGrayRelease {
	}

	/** {@link ConditionalOnFlag} 的裁决逻辑：从注解属性取 key，再问 Environment 要布尔值。 */
	static final class OnFlagCondition implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnFlag.class.getName());
			if (attrs == null) {
				return true;
			}
			String key = (String) attrs.get("value");
			return context.getEnvironment().getProperty(key, Boolean.class, false);
		}
	}

	/** {@link ConditionalOnGrayRelease} 的裁决逻辑：灰度开关为真才放行。 */
	static final class OnGrayReleaseCondition implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return context.getEnvironment().getProperty("release.gray.enabled", Boolean.class, false);
		}
	}

	// ============ 被装配的 C 端组件 ============

	/** 秒杀立减组件：大促主力，受开关保护以便随时熔断。 */
	static final class FlashSaleDiscountComponent {
	}

	/** 灰度秒杀增强组件：只在「秒杀开 且 灰度放量」时挂载。 */
	static final class GraySeckillBoostComponent {
	}

}
