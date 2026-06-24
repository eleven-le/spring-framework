package com.leilei.lab.laboratory.l02.l02_07;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 📖 知识点：[[L02-07-作用域-scope代理与按需注入#2.5 @Lazy 延迟注入与代理语义]]（@Lazy 注入产生代理，推迟到首次调用才解析目标）
 * 🎯 作用：坐实 {@code @Lazy} 的代理语义与「两处 @Lazy 缺一不可」：
 *         ① 注入点的 @Lazy 让注入的是一个延迟解析代理（{@code ContextAnnotationAutowireCandidateResolver#buildLazyResolutionProxy}
 *            造的 AOP 代理），而非真实目标，使「依赖它的单例」创建时不触发目标构造；
 *         ② 但单例 Bean 默认会被 {@code preInstantiateSingletons} 在刷新末尾主动实例化——所以还要在 Bean 定义上也加 @Lazy
 *            （lazy-init），刷新期才会跳过它；③ 两处都加，目标才真正推迟到「代理方法首次被调用」时构造（缩短启动 / 破循环依赖）。
 *         对比三种客户端：eager（都不加，刷新期构造）、half-lazy（只注入点加，仍被 preInstantiateSingletons 构造 = 事故二）、
 *         fully-lazy（两处都加，首次调用才构造）。
 * 🔗 业务场景：产品搜索依赖的「ES 搜索客户端」是重资源（建连接池 / 加载词典，构造很贵），冷门后台服务可能整个生命周期都用不到——
 *         用 @Lazy 把初始化推迟到真正第一次搜索时，缩短启动时间、也能打破启动期偶发的循环依赖。
 */
public final class L0207_05_LazyInjectionDemo {

	private L0207_05_LazyInjectionDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 容器刷新阶段（观察哪些重资源 Bean 在此刻构造）====================");
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(LazyConfig.class);
		System.out.println("  ↑ refresh 完成");
		System.out.println("    eager     客户端已构造? " + Probe.eagerBuilt + "（都不加 @Lazy：刷新期构造，符合预期）");
		System.out.println("    half-lazy 客户端已构造? " + Probe.halfLazyBuilt + "（只在注入点加 @Lazy：仍被 preInstantiateSingletons 构造 = 事故二）");
		System.out.println("    fully-lazy客户端已构造? " + Probe.fullyLazyBuilt + "（注入点 + Bean 定义都加 @Lazy：刷新期跳过）");

		ProductSearchFacade facade = ctx.getBean(ProductSearchFacade.class);
		System.out.println();
		System.out.println("  注入的 fullyLazy 依赖是代理? " + AopUtils.isAopProxy(facade.fullyLazyProxy())
				+ "（@Lazy 注入点拿到的是延迟解析代理，不是真实目标）");

		System.out.println();
		System.out.println("==================== 首次调用 fully-lazy 依赖（此刻才真正构造它）====================");
		System.out.println("  调用前——fully-lazy 已构造? " + Probe.fullyLazyBuilt);
		String result = facade.searchByFullyLazy("芝士葡萄");
		System.out.println("  调用后——fully-lazy 已构造? " + Probe.fullyLazyBuilt + "，结果=" + result);

		System.out.println();
		System.out.println("[小结] @Lazy 注入点 = 注入延迟代理；要真正推迟构造，Bean 定义上也得加 @Lazy（否则 preInstantiateSingletons 照样建）");

		ctx.close();
	}

	/** 跨 Bean 共享的构造探针：记录各客户端是否已被真正构造。 */
	static final class Probe {

		static volatile boolean eagerBuilt;
		static volatile boolean halfLazyBuilt;
		static volatile boolean fullyLazyBuilt;

		private Probe() {
		}
	}

	@Configuration
	static class LazyConfig {

		@Bean
		EagerEsClient eagerEsClient() {
			return new EagerEsClient();
		}

		/** 注意：Bean 定义未加 @Lazy → 仍是 eager 单例。 */
		@Bean
		HalfLazyEsClient halfLazyEsClient() {
			return new HalfLazyEsClient();
		}

		/** Bean 定义加 @Lazy（lazy-init）→ preInstantiateSingletons 刷新期跳过它。 */
		@Bean
		@Lazy
		FullyLazyEsClient fullyLazyEsClient() {
			return new FullyLazyEsClient();
		}

		@Bean
		ProductSearchFacade productSearchFacade(EagerEsClient eager,
				@Lazy HalfLazyEsClient halfLazy,
				@Lazy FullyLazyEsClient fullyLazy) {
			return new ProductSearchFacade(eager, halfLazy, fullyLazy);
		}
	}

	/** 都不加 @Lazy：刷新期构造。 */
	static class EagerEsClient {

		EagerEsClient() {
			Probe.eagerBuilt = true;
		}

		String search(String keyword) {
			return "eager-hit:" + keyword;
		}
	}

	/** 只在注入点加 @Lazy、Bean 定义没加：仍被 preInstantiateSingletons 在刷新期构造（事故二）。 */
	static class HalfLazyEsClient {

		HalfLazyEsClient() {
			Probe.halfLazyBuilt = true;
		}

		String search(String keyword) {
			return "half-lazy-hit:" + keyword;
		}
	}

	/** 注入点 + Bean 定义都加 @Lazy：真正推迟到首次调用才构造。 */
	static class FullyLazyEsClient {

		FullyLazyEsClient() {
			Probe.fullyLazyBuilt = true;
		}

		String search(String keyword) {
			return "fully-lazy-hit:" + keyword;
		}
	}

	/** 产品搜索门面：构造器注入一个 eager + 两个 @Lazy 客户端。 */
	static class ProductSearchFacade {

		private final EagerEsClient eagerClient;
		private final HalfLazyEsClient halfLazyClient;
		private final FullyLazyEsClient fullyLazyClient;

		ProductSearchFacade(EagerEsClient eagerClient, HalfLazyEsClient halfLazyClient,
				FullyLazyEsClient fullyLazyClient) {
			this.eagerClient = eagerClient;
			this.halfLazyClient = halfLazyClient;
			this.fullyLazyClient = fullyLazyClient;
		}

		String searchByFullyLazy(String keyword) {
			return fullyLazyClient.search(keyword);
		}

		FullyLazyEsClient fullyLazyProxy() {
			return fullyLazyClient;
		}
	}
}
