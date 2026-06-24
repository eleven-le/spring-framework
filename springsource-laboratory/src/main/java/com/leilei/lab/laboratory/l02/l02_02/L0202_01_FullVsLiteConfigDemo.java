package com.leilei.lab.laboratory.l02.l02_02;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.leilei.lab.laboratory.common.domain.Sku;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（@Configuration full vs lite / proxyBeanMethods）
 * 🎯 作用：同一份 @Bean 配置，分别以 full（{@code proxyBeanMethods=true}，默认）与 lite（{@code =false}）运行，
 *         对照「<b>跨 @Bean 方法直调</b>」时的语义差异——full 模式 CGLIB 增强配置类，
 *         把 {@code priceCacheManager()} 的方法调用拦截回容器单例；lite 模式无增强，每次直调都 {@code new} 一个新实例。
 *         实验同时打印「配置类是否被 CGLIB 增强」与「{@link PriceCacheManager} 真实实例化次数」作为铁证。
 * 🔗 业务场景：价格缓存管理器是全局唯一单例，启动后被异步预热。两个服务（报价 / 促销）都依赖它。
 *         若误用 lite 模式，两个服务各自持有 @Bean 方法直调克隆出的「影子缓存」，预热只命中容器单例，
 *         服务侧全是空缓存 → 大促首波请求缓存全 miss、回源打爆 DB。
 */
public final class L0202_01_FullVsLiteConfigDemo {

	private L0202_01_FullVsLiteConfigDemo() {
	}

	public static void main(String[] args) {
		MockDataSet dataSet = MockDataFactory.seed(9, 3);

		System.out.println("==================== 一、full 模式（proxyBeanMethods=true，默认）：跨 @Bean 方法直调命中同一单例 ====================");
		run(FullConfig.class, dataSet);
		System.out.println();
		System.out.println("==================== 二、lite 模式（proxyBeanMethods=false）：跨 @Bean 方法直调每次 new，单例被克隆 ====================");
		run(LiteConfig.class, dataSet);
	}

	private static void run(Class<?> configClass, MockDataSet dataSet) {
		PriceCacheManager.resetCounter();
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(configClass)) {
			Object cfg = ctx.getBean(configClass);
			// CGLIB 增强后的类名形如 FullConfig$$SpringCGLIB$$0，含分隔符 "$$"（ClassUtils.CGLIB_CLASS_SEPARATOR）
			boolean enhanced = cfg.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR);
			System.out.println("[配置类] " + configClass.getSimpleName() + " 是否被 CGLIB 增强 = " + enhanced);

			// 仅对「容器单例」做启动后预热——模拟真实的异步预热只认容器里的那一个 Bean。
			PriceCacheManager containerSingleton = ctx.getBean(PriceCacheManager.class);
			containerSingleton.warmUp(dataSet);

			QuoteService quote = ctx.getBean(QuoteService.class);
			PromotionService promo = ctx.getBean(PromotionService.class);

			System.out.println("[实例] PriceCacheManager 真实实例化次数 = " + PriceCacheManager.instanceCount()
					+ "（full=1 全程同一个；lite=3 容器一个 + 两次 @Bean 直调各 new 一个）");
			System.out.println("[身份] 容器单例 == QuoteService 持有的 = " + (containerSingleton == quote.cache()));
			System.out.println("[身份] QuoteService 持有的 == PromotionService 持有的 = " + (quote.cache() == promo.cache()));
			System.out.println("[预热] 容器单例缓存条数 = " + containerSingleton.size()
					+ "；QuoteService 看到 = " + quote.cache().size()
					+ "；PromotionService 看到 = " + promo.cache().size());
			System.out.println("[后果] 服务侧缓存是否命中预热数据 = "
					+ (quote.cache().size() > 0 && promo.cache().size() > 0)
					+ "（lite 下为 false：预热全白做，请求回源打爆 DB）");
		}
	}

	/** full：默认 proxyBeanMethods=true，配置类被 CGLIB 增强，@Bean 方法直调被拦截回容器单例。 */
	@Configuration
	static class FullConfig {

		@Bean
		PriceCacheManager priceCacheManager() {
			return new PriceCacheManager();
		}

		@Bean
		QuoteService quoteService() {
			// 跨 @Bean 方法直调：full 模式下 priceCacheManager() 被拦截 → 返回容器单例
			return new QuoteService(priceCacheManager());
		}

		@Bean
		PromotionService promotionService() {
			return new PromotionService(priceCacheManager());
		}
	}

	/** lite：proxyBeanMethods=false，配置类不增强，@Bean 方法直调就是普通 Java 调用 → 每次 new。 */
	@Configuration(proxyBeanMethods = false)
	static class LiteConfig {

		@Bean
		PriceCacheManager priceCacheManager() {
			return new PriceCacheManager();
		}

		@Bean
		QuoteService quoteService() {
			// 跨 @Bean 方法直调：lite 模式下就是普通方法调用 → new 出一个「影子缓存」
			return new QuoteService(priceCacheManager());
		}

		@Bean
		PromotionService promotionService() {
			return new PromotionService(priceCacheManager());
		}
	}

	/** 全局价格缓存管理器：本应是单例。静态计数器揭示它到底被 new 了几次。 */
	static final class PriceCacheManager {

		private static final AtomicInteger COUNTER = new AtomicInteger();

		private final ConcurrentHashMap<Long, BigDecimal> priceIndex = new ConcurrentHashMap<>();

		PriceCacheManager() {
			COUNTER.incrementAndGet();
		}

		static void resetCounter() {
			COUNTER.set(0);
		}

		static int instanceCount() {
			return COUNTER.get();
		}

		/** 启动后预热：把沙盘里所有 SKU 的基础价灌进缓存。 */
		void warmUp(MockDataSet dataSet) {
			for (Sku sku : dataSet.getSkus().values()) {
				priceIndex.put(sku.getId(), sku.getBasePrice());
			}
		}

		int size() {
			return priceIndex.size();
		}
	}

	/** 报价服务：依赖全局价格缓存。 */
	static final class QuoteService {

		private final PriceCacheManager cache;

		QuoteService(PriceCacheManager cache) {
			this.cache = cache;
		}

		PriceCacheManager cache() {
			return this.cache;
		}
	}

	/** 促销服务：同样依赖全局价格缓存。 */
	static final class PromotionService {

		private final PriceCacheManager cache;

		PromotionService(PriceCacheManager cache) {
			this.cache = cache;
		}

		PriceCacheManager cache() {
			return this.cache;
		}
	}

}
