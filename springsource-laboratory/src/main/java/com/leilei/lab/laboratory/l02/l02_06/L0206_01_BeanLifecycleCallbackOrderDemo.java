package com.leilei.lab.laboratory.l02.l02_06;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.leilei.lab.laboratory.common.dao.MockPriceRuleDao;
import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 📖 知识点：[[L02-06-Bean生命周期回调全图#2.1 init/destroy 三种写法与执行顺序]]（init/destroy 三种写法与固定执行顺序）
 * 🎯 作用：把「一个 Bean 同时挂三种 init + 三种 destroy 写法时，谁先谁后」坐实成可运行实验。
 *         初始化固定顺序：构造器 → {@code @PostConstruct} → {@link InitializingBean#afterPropertiesSet()}
 *         → {@code @Bean(initMethod)} 自定义 init；销毁固定顺序：{@code @PreDestroy}
 *         → {@link DisposableBean#destroy()} → {@code @Bean(destroyMethod)} 自定义 destroy。
 *         前者由 {@code AbstractAutowireCapableBeanFactory#initializeBean} 编排（@PostConstruct 在 before-init BPP 阶段，
 *         afterPropertiesSet/initMethod 在其后的 invokeInitMethods 阶段），后者由 {@code DisposableBeanAdapter#destroy} 编排。
 * 🔗 业务场景：产品中心「SKU 热点价格本地缓存预热 Bean」——启动期把基础价灌进本地 Map 削峰，
 *         关停时清空缓存释放堆内存并停止对外提供；三种回调各司其职（体检 / 校验 / 预热重活），
 *         顺序写反（如在 @PostConstruct 里就用还没预热好的缓存）会导致启动期 NPE 或空缓存击穿。
 */
public final class L0206_01_BeanLifecycleCallbackOrderDemo {

	private L0206_01_BeanLifecycleCallbackOrderDemo() {
	}

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CacheConfig.class);
		PriceCacheWarmer warmer = ctx.getBean(PriceCacheWarmer.class);

		System.out.println();
		System.out.println("[运行期] 命中热点价格缓存 sku=1000010 → " + warmer.getHotPrice(1000010L) + " 元");
		System.out.println();

		System.out.println("==================== 关闭容器：触发销毁三连（与初始化逆序）====================");
		ctx.close();
	}

	@Configuration
	static class CacheConfig {

		@Bean
		MockPriceRuleDao priceRuleDao() {
			MockDataSet dataSet = MockDataFactory.seed(3, 3);
			return new MockPriceRuleDao(dataSet, MockProfile.inMemory());
		}

		/** 第三种写法：在 @Bean 上声明 initMethod/destroyMethod，把回调与 Bean 类解耦（连第三方类都能挂）。 */
		@Bean(initMethod = "warmUp", destroyMethod = "evict")
		PriceCacheWarmer priceCacheWarmer(MockPriceRuleDao dao) {
			return new PriceCacheWarmer(dao);
		}
	}

	/**
	 * SKU 热点价格预热 Bean：同一个类同时挂三种 init + 三种 destroy 写法，用全局自增序号打印真实执行顺序。
	 */
	static class PriceCacheWarmer implements InitializingBean, DisposableBean {

		private static final AtomicInteger SEQ = new AtomicInteger();

		private final MockPriceRuleDao dao;
		private final Map<Long, BigDecimal> hotPrice = new ConcurrentHashMap<>();
		private volatile boolean serving;

		PriceCacheWarmer(MockPriceRuleDao dao) {
			this.dao = dao;
			System.out.println("==================== 初始化三连（执行顺序固定）====================");
			System.out.println(step() + "构造器：实例化完成，依赖已由构造器注入（dao 就绪=" + (dao != null) + "）");
		}

		// 写法一：@PostConstruct —— CommonAnnotationBeanPostProcessor 在 before-init 阶段调用，三种 init 里最早
		@PostConstruct
		void checkConfig() {
			System.out.println(step() + "@PostConstruct：体检配置/依赖（最早的 init 回调，处于 before-init BPP 阶段）");
		}

		// 写法二：InitializingBean#afterPropertiesSet —— invokeInitMethods 阶段，晚于 @PostConstruct
		@Override
		public void afterPropertiesSet() {
			System.out.println(step() + "afterPropertiesSet：InitializingBean 回调（invokeInitMethods 阶段，晚于 @PostConstruct）");
		}

		// 写法三：@Bean(initMethod) 自定义 init 方法 —— 同在 invokeInitMethods 阶段，三者中最后，干预热重活
		void warmUp() {
			for (long skuId : new long[] {1000010L, 1000011L, 1000012L}) {
				List<PriceRule> rules = dao.listBySkuId(skuId);
				rules.stream()
						.filter(r -> r.getStoreId() == null && r.getChannel() == null)   // 全国全渠道兜底基础价
						.findFirst()
						.ifPresent(r -> hotPrice.put(skuId, r.getPrice()));
			}
			this.serving = true;
			System.out.println(step() + "warmUp()[自定义 initMethod]：预热 " + hotPrice.size()
					+ " 条热点基础价入本地缓存（三种 init 中最后执行，干重活）");
		}

		BigDecimal getHotPrice(long skuId) {
			if (!serving) {
				throw new IllegalStateException("缓存尚未就绪，serving=false");
			}
			return hotPrice.get(skuId);
		}

		// 写法一：@PreDestroy —— DestructionAwareBeanPostProcessor#postProcessBeforeDestruction，三种 destroy 里最早
		@PreDestroy
		void stopServing() {
			this.serving = false;
			System.out.println(step() + "@PreDestroy：先停止对外提供缓存（最早的 destroy 回调）");
		}

		// 写法二：DisposableBean#destroy —— 晚于 @PreDestroy
		@Override
		public void destroy() {
			System.out.println(step() + "destroy()：DisposableBean 回调（晚于 @PreDestroy）");
		}

		// 写法三：@Bean(destroyMethod) 自定义 destroy 方法 —— 三者中最后，真正释放资源
		void evict() {
			hotPrice.clear();
			System.out.println(step() + "evict()[自定义 destroyMethod]：清空本地缓存释放堆内存（三种 destroy 中最后执行）");
		}

		private static String step() {
			return "  [" + SEQ.incrementAndGet() + "] ";
		}
	}
}
