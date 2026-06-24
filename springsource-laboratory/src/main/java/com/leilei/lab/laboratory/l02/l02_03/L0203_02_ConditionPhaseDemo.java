package com.leilei.lab.laboratory.l02.l02_03;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 📖 知识点：[[L02-03-条件装配-Conditional与Profile#3. 🧨 事故与避坑]]（ConfigurationPhase：PARSE_CONFIGURATION vs REGISTER_BEAN）
 * 🎯 作用：复现「依赖其它 Bean 是否在册的条件用错相位」的经典事故。价格快照预热器只有在主缓存 Bean 已装配时才需要注册（它要往主缓存灌预热数据），
 *         条件即「主缓存是否已在册」。
 *         ① {@link OnPrimaryCachePresentPlainCondition} 是<b>普通 Condition</b>，标在 @Configuration 类上时相位被推断为
 *         {@code PARSE_CONFIGURATION}（{@code ConditionEvaluator#shouldSkip} 第 86~89 行）。此刻所有 @Bean 定义都还没登记，
 *         主缓存「查不到」→ 条件返回 false → <b>整个预热配置类在解析期被丢弃且永不复评</b> → 预热器静默缺位；
 *         ② {@link OnPrimaryCachePresentPhasedCondition} 实现 {@link ConfigurationCondition} 并返回 {@code REGISTER_BEAN}，
 *         解析期跳过评估（{@code shouldSkip} 第 108 行 {@code requiredPhase == phase} 不成立 → 不否决、先保留），
 *         待 Bean 定义全部登记后再评估 → 正确看到主缓存 → 预热器如期注册。
 * 🔗 业务场景：大促前要对 Redis 价格主缓存做全量预热，预热器 Bean 依赖主缓存先就位。条件相位写错，预热器悄悄不进容器，
 *         启动日志毫无报错，等大促开闸才发现缓存全冷、首波流量直击 DB——这类「静默失效」最难排查。
 */
public final class L0203_02_ConditionPhaseDemo {

	/** 主缓存 Bean 名——两个条件都据此判断「主缓存是否已就位」。 */
	static final String PRIMARY_CACHE = "redisPriceCacheManager";

	private L0203_02_ConditionPhaseDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 普通 Condition（相位被推断为 PARSE_CONFIGURATION）：解析期主缓存定义尚未登记 → 误判「主缓存不存在」→ 预热配置类被丢弃且永不复评 ====================");
		runScenario(PlainWarmerConfig.class, "priceSnapshotWarmerPlain");
		System.out.println();
		System.out.println("==================== 2. ConfigurationCondition 指定 REGISTER_BEAN：评估推迟到 Bean 定义全部登记后 → 正确看到主缓存 → 预热器如期注册 ====================");
		runScenario(PhasedWarmerConfig.class, "priceSnapshotWarmerPhased");
	}

	private static void runScenario(Class<?> warmerConfig, String warmerBeanName) {
		// 注册顺序：主缓存配置在前——保证 REGISTER_BEAN 阶段评估预热条件时，主缓存定义已登记
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(RedisCacheConfig.class, warmerConfig)) {
			boolean hasPrimary = ctx.containsBean(PRIMARY_CACHE);
			boolean hasWarmer = ctx.containsBean(warmerBeanName);
			System.out.println("[主缓存] " + PRIMARY_CACHE + " 是否在容器 = " + hasPrimary);
			System.out.println("[预热器] " + warmerBeanName + " 是否在容器 = " + hasWarmer);
			System.out.println("[判定] 主缓存已就位但预热器缺位（静默失效事故态）= " + (hasPrimary && !hasWarmer));
		}
	}

	/** 主缓存配置：始终注册 Redis 价格缓存管理器。 */
	@Configuration
	static class RedisCacheConfig {

		@Bean(PRIMARY_CACHE)
		PriceCacheManager redisPriceCacheManager() {
			return new PriceCacheManager("redis");
		}
	}

	/** 预热配置（错误版）：标普通 Condition，相位被推断为 PARSE_CONFIGURATION → 解析期就被丢弃。 */
	@Configuration
	@ConditionalOnPrimaryCachePresentPlain
	static class PlainWarmerConfig {

		@Bean
		PriceSnapshotWarmer priceSnapshotWarmerPlain() {
			return new PriceSnapshotWarmer();
		}
	}

	/** 预热配置（正确版）：标 ConfigurationCondition，相位 REGISTER_BEAN → 登记后才评估。 */
	@Configuration
	@ConditionalOnPrimaryCachePresentPhased
	static class PhasedWarmerConfig {

		@Bean
		PriceSnapshotWarmer priceSnapshotWarmerPhased() {
			return new PriceSnapshotWarmer();
		}
	}

	// ============ 两个组合注解 + 两个 Condition（差别只在相位）============

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@Conditional(OnPrimaryCachePresentPlainCondition.class)
	@interface ConditionalOnPrimaryCachePresentPlain {
	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@Conditional(OnPrimaryCachePresentPhasedCondition.class)
	@interface ConditionalOnPrimaryCachePresentPhased {
	}

	/** 错误：普通 Condition，标在 @Configuration 类上 → 相位被推断为 PARSE_CONFIGURATION，解析期主缓存定义还没登记。 */
	static final class OnPrimaryCachePresentPlainCondition implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			BeanDefinitionRegistry registry = context.getRegistry();
			// 要求主缓存「已存在」才注册预热器——但解析期主缓存定义还没登记，于是永远查不到、配置类被丢弃
			return registry.containsBeanDefinition(PRIMARY_CACHE);
		}
	}

	/** 正确：ConfigurationCondition 把评估钉死在 REGISTER_BEAN——此时所有 Bean 定义已登记，能真正看到主缓存。 */
	static final class OnPrimaryCachePresentPhasedCondition implements ConfigurationCondition {

		@Override
		public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.REGISTER_BEAN;
		}

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			BeanDefinitionRegistry registry = context.getRegistry();
			return registry.containsBeanDefinition(PRIMARY_CACHE);
		}
	}

	/** 价格缓存管理器：用 backend 标识区分实现。 */
	static final class PriceCacheManager {

		private final String backend;

		PriceCacheManager(String backend) {
			this.backend = backend;
		}

		String backend() {
			return this.backend;
		}
	}

	/** 价格快照预热器：依赖主缓存先就位，启动后把全量价格灌进主缓存。 */
	static final class PriceSnapshotWarmer {
	}

}
