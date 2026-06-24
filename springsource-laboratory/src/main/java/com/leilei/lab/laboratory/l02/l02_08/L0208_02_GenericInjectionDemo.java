package com.leilei.lab.laboratory.l02.l02_08;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.ResolvableType;

import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.domain.Product;

/**
 * 📖 知识点：[[L02-08-歧义裁决与容器语义-Primary-Qualifier-DependsOn-Bean覆盖#2.2 泛型依赖注入与 ResolvableType]]
 * 🎯 作用：证明「同一裸类型多 Bean 必歧义，但带不同泛型参数时容器能精确裁决」——
 *         {@code CacheLoader<Product>} 与 {@code CacheLoader<PriceRule>} 同为 {@code CacheLoader}，
 *         无需任何 {@code @Qualifier} 即按泛型实参各注各的；其底层是
 *         {@code GenericTypeAwareAutowireCandidateResolver#checkGenericTypeMatch} 借 {@link ResolvableType} 比对泛型签名。
 *         附带：① 直接用 {@code ResolvableType} 还原 Bean 的完整泛型；② 退化为裸 {@code CacheLoader} 注入 → 歧义报错。
 * 🔗 业务场景：古茗 C 端多级缓存有一族泛型加载器（商品详情、价格规则、库存快照…），
 *         统一抽象成 {@code CacheLoader<T>}；靠泛型注入而非手写一堆 @Qualifier，新增缓存类型零裁决成本。
 */
public final class L0208_02_GenericInjectionDemo {

	private L0208_02_GenericInjectionDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 泛型参数即天然限定符：两个 CacheLoader 各注各的 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(LoaderConfig.class, GenericConsumerConfig.class)) {
			GenericConsumer c = ctx.getBean(GenericConsumer.class);
			System.out.println("CacheLoader<Product>   注入的是 = " + c.productLoader.describe());
			System.out.println("CacheLoader<PriceRule> 注入的是 = " + c.priceLoader.describe());
			System.out.println("加载样例：product#" + c.productLoader.load(100001L)
					+ " / priceRule#" + c.priceLoader.load(100001L));
		}

		System.out.println();
		System.out.println("==================== 2. ResolvableType 还原 Bean 的完整泛型签名 ====================");
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(LoaderConfig.class)) {
			for (String name : ctx.getBeanNamesForType(CacheLoader.class)) {
				ResolvableType rt = ctx.getBeanFactory().getMergedBeanDefinition(name)
						.getResolvableType();
				// 工厂方法返回类型上的泛型实参（CacheLoader<X> 里的 X）
				Class<?> generic = rt.as(CacheLoader.class).getGeneric(0).resolve();
				System.out.println("Bean '" + name + "' 的泛型实参 = "
						+ (generic == null ? "未知(裸类型)" : generic.getSimpleName()));
			}
		}

		System.out.println();
		System.out.println("==================== 3. 退化为裸 CacheLoader 注入：泛型信息丢失 → 歧义报错 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(LoaderConfig.class, RawConsumerConfig.class)) {
			System.out.println("不该走到这里：" + ctx);
		}
		catch (NestedRuntimeException ex) {
			System.out.println("裸类型 CacheLoader 注入 → 两候选无法裁决 = "
					+ ex.contains(NoUniqueBeanDefinitionException.class));
		}
	}

	// ===================== 泛型缓存加载器族 =====================

	/** 泛型缓存加载器：按 id 加载领域对象。 */
	interface CacheLoader<T> {

		String load(long id);

		String describe();
	}

	static class ProductCacheLoader implements CacheLoader<Product> {

		@Override
		public String load(long id) {
			return "Product(" + id + ")";
		}

		@Override
		public String describe() {
			return "商品详情加载器";
		}
	}

	static class PriceRuleCacheLoader implements CacheLoader<PriceRule> {

		@Override
		public String load(long id) {
			return "PriceRule(sku=" + id + ")";
		}

		@Override
		public String describe() {
			return "价格规则加载器";
		}
	}

	@Configuration
	static class LoaderConfig {

		// @Bean 工厂方法的返回类型带泛型实参，容器据此为每个 Bean 记下 CacheLoader<Product> / CacheLoader<PriceRule>
		@Bean
		CacheLoader<Product> productCacheLoader() {
			return new ProductCacheLoader();
		}

		@Bean
		CacheLoader<PriceRule> priceRuleCacheLoader() {
			return new PriceRuleCacheLoader();
		}
	}

	@Configuration
	static class GenericConsumerConfig {

		@Bean
		GenericConsumer genericConsumer() {
			return new GenericConsumer();
		}
	}

	/** 两个注入点泛型实参不同——容器靠泛型裁决，无需 @Qualifier。 */
	static class GenericConsumer {

		@Autowired
		CacheLoader<Product> productLoader;

		@Autowired
		CacheLoader<PriceRule> priceLoader;
	}

	@Configuration
	static class RawConsumerConfig {

		@Bean
		RawConsumer rawConsumer() {
			return new RawConsumer();
		}
	}

	/** 注入点用裸类型，丢掉泛型信息 → 两候选歧义。 */
	@SuppressWarnings("rawtypes")
	static class RawConsumer {

		@Autowired
		CacheLoader rawLoader;
	}

}
