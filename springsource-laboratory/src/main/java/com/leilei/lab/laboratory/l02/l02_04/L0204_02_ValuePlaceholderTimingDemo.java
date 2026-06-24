package com.leilei.lab.laboratory.l02.l02_04;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

/**
 * 📖 知识点：[[L02-04-Environment与属性绑定#2.2 @Value 占位符解析时机]]（@Value 占位符解析时机与失效坑）
 * 🎯 作用：坐实「@Value 占位符到底什么时候、被谁解析」——
 *         占位符 {@code ${...}} 不是编译期常量，而是在 **Bean 创建期、依赖注入那一刻**由
 *         {@code AutowiredAnnotationBeanPostProcessor} 调
 *         {@code DefaultListableBeanFactory#resolveDependency} →
 *         {@code AbstractBeanFactory#resolveEmbeddedValue}（遍历容器里的 {@code StringValueResolver}）解析。
 *         在 {@code AnnotationConfigApplicationContext} 里，**即使不显式配置
 *         {@code PropertySourcesPlaceholderConfigurer}**，{@code @Value} 也能解析——因为
 *         {@code AbstractApplicationContext#finishBeanFactoryInitialization} 会兜底注册一个
 *         「委托 {@code Environment#resolvePlaceholders}」的默认值解析器（源码见章节 §5.2）。
 *         本类同时演示 {@code ${key:default}} 缺省语法：key 不存在时回落默认值、**不抛异常**。
 * 🔗 业务场景：秒杀限流 QPS、价格缓存 TTL、降级文案这类参数，绝大多数 Bean 用 {@code @Value} 注入就够了；
 *         关键是给每个 {@code @Value} 都带上 {@code :默认值} 兜底，避免「配置中心某 key 漏配 → 启动直接报错
 *         IllegalArgumentException: Could not resolve placeholder」把整个应用拖挂。
 */
public final class L0204_02_ValuePlaceholderTimingDemo {

	public static void main(String[] args) {
		System.out.println("==================== 1. 配置中心提供了阈值：@Value 在 Bean 创建期(注入时)解析占位符 ====================");
		runWithConfig(true);
		System.out.println();
		System.out.println("==================== 2. 配置中心缺失：@Value ${k:default} 走默认值，不抛异常 ====================");
		runWithConfig(false);
	}

	private static void runWithConfig(boolean nacosPresent) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			if (nacosPresent) {
				Map<String, Object> nacos = new LinkedHashMap<>();
				nacos.put("ratelimit.seckill.qps", 8000);
				nacos.put("cache.price.ttlSeconds", 600);
				nacos.put("seckill.degrade.message", "活动太火爆，请稍后再试");
				ctx.getEnvironment().getPropertySources().addLast(new MapPropertySource("nacosConfig", nacos));
			}
			// 注意：全程没有注册 PropertySourcesPlaceholderConfigurer，@Value 照样解析（容器兜底注册了默认解析器）
			ctx.register(GuardConfig.class);
			ctx.refresh();

			SeckillGuard guard = ctx.getBean(SeckillGuard.class);
			System.out.println("[SeckillGuard] " + guard);
		}
	}

	@Configuration
	static class GuardConfig {

		@Bean
		SeckillGuard seckillGuard() {
			return new SeckillGuard();
		}
	}

	/** 秒杀限流守卫：三个高危阈值全部用 @Value 注入，且都带 {@code :默认值} 兜底。 */
	static class SeckillGuard {

		/** 缺省 2000：配置中心没给就按 2000 QPS 限流，绝不裸奔。 */
		@Value("${ratelimit.seckill.qps:2000}")
		private int qps;

		/** 缺省 300s 缓存 TTL。 */
		@Value("${cache.price.ttlSeconds:300}")
		private int ttlSeconds;

		/** 缺省降级文案。 */
		@Value("${seckill.degrade.message:系统繁忙，请稍后重试}")
		private String degradeMessage;

		@Override
		public String toString() {
			boolean qpsDefault = (this.qps == 2000);
			boolean ttlDefault = (this.ttlSeconds == 300);
			return "qps=" + this.qps + (qpsDefault ? "(默认)" : "(来自 nacos)")
					+ ", ttlSeconds=" + this.ttlSeconds + (ttlDefault ? "(默认)" : "(来自 nacos)")
					+ ", degradeMessage=" + this.degradeMessage;
		}
	}

}
