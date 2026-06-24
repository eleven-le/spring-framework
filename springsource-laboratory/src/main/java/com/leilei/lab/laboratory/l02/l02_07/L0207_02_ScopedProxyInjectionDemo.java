package com.leilei.lab.laboratory.l02.l02_07;

import com.leilei.lab.laboratory.common.bench.BenchReport;
import com.leilei.lab.laboratory.common.bench.ConcurrentBench;
import com.leilei.lab.laboratory.common.domain.Channel;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.support.SimpleThreadScope;

/**
 * 📖 知识点：[[L02-07-作用域-scope代理与按需注入#2.2 scope 代理 proxyMode]]（单例注入短作用域 Bean 必须走 scope 代理）
 * 🎯 作用：坐实「单例 Bean 注入 request 作用域 Bean，不加 proxyMode 就埋下跨请求串数据的雷」。
 *         单例只在创建时注入一次：直接注入 request Bean，会把「创建那一刻解析到的某一份实例」永久焊死，
 *         所有请求线程共用它 → 串数据；加 {@code @Scope(proxyMode = TARGET_CLASS)} 注入的是 CGLIB 代理，
 *         每次方法调用经 {@code SimpleBeanTargetSource} 重新向当前作用域要 target → 每个请求线程拿到自己的实例。
 *         用 {@link ConcurrentBench} 多线程齐射对比：无代理版大量「读回的门店≠本线程设的门店」，代理版零串号。
 * 🔗 业务场景：单例的「价格报价服务」注入 request 级「下单上下文（门店 / 渠道）」，高并发下单时按当前请求门店匹配价格；
 *         漏写 proxyMode 会导致 A 门店的请求读到 B 门店上下文、价格算错（见 [[L02-07-作用域-scope代理与按需注入#事故一]]）。
 */
public final class L0207_02_ScopedProxyInjectionDemo {

	private L0207_02_ScopedProxyInjectionDemo() {
	}

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		// SimpleThreadScope 等价模拟「一次请求 = 一个线程」；真实 Web 下是 RequestScope。
		ctx.getBeanFactory().registerScope("request", new SimpleThreadScope());
		ctx.register(QuoteConfig.class);
		ctx.refresh();

		int threads = 16;
		int iterations = 500;

		System.out.println("==================== 反例：单例直接注入 request Bean（无 scope 代理）====================");
		BadPriceQuoteService bad = ctx.getBean(BadPriceQuoteService.class);
		BenchReport badReport = ConcurrentBench.run("scope-no-proxy", threads, iterations, () -> {
			long myStore = 8000L + Thread.currentThread().getId() % 1000;
			long readBack = bad.quoteStoreId(myStore, Channel.MINI_PROGRAM);
			if (readBack != myStore) {
				// 读回的门店 != 本线程设置的门店 = 跨请求串数据，计为失败
				throw new IllegalStateException("串号：set=" + myStore + " readBack=" + readBack);
			}
		});
		System.out.println("  注入的上下文是代理? " + bad.contextIsProxy());
		System.out.println("  跨请求串号次数（fail）= " + badReport.getFailOps() + " / " + badReport.getTotalOps()
				+ "（单例焊死了一份上下文，所有线程共用 → 大量串号）");

		System.out.println();
		System.out.println("==================== 正例：proxyMode = TARGET_CLASS（scope 代理）====================");
		GoodPriceQuoteService good = ctx.getBean(GoodPriceQuoteService.class);
		BenchReport goodReport = ConcurrentBench.run("scope-proxy", threads, iterations, () -> {
			long myStore = 8000L + Thread.currentThread().getId() % 1000;
			long readBack = good.quoteStoreId(myStore, Channel.MINI_PROGRAM);
			if (readBack != myStore) {
				throw new IllegalStateException("串号：set=" + myStore + " readBack=" + readBack);
			}
		});
		System.out.println("  注入的上下文是代理? " + good.contextIsProxy());
		System.out.println("  跨请求串号次数（fail）= " + goodReport.getFailOps() + " / " + goodReport.getTotalOps()
				+ "（代理每次方法调用重新向当前请求要 target → 零串号）");

		System.out.println();
		System.out.println("[小结] 单例注入比它短命的作用域 Bean，必须 proxyMode=TARGET_CLASS/INTERFACES，否则跨请求串数据");

		ctx.close();
	}

	@Configuration
	static class QuoteConfig {

		/** 无代理：request 作用域，单例注入它时会被焊死成一份。 */
		@Bean
		@Scope("request")
		PlainOrderContext plainOrderContext() {
			return new PlainOrderContext();
		}

		/** scope 代理：request 作用域 + TARGET_CLASS，单例注入的是 CGLIB 代理。 */
		@Bean
		@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
		ProxiedOrderContext proxiedOrderContext() {
			return new ProxiedOrderContext();
		}

		@Bean
		BadPriceQuoteService badPriceQuoteService(PlainOrderContext context) {
			return new BadPriceQuoteService(context);
		}

		@Bean
		GoodPriceQuoteService goodPriceQuoteService(ProxiedOrderContext context) {
			return new GoodPriceQuoteService(context);
		}
	}

	/** 反例服务：单例，构造期注入一份 request Bean，从此焊死。 */
	static class BadPriceQuoteService {

		private final PlainOrderContext context;

		BadPriceQuoteService(PlainOrderContext context) {
			this.context = context;
		}

		long quoteStoreId(long storeId, Channel channel) {
			context.setStoreId(storeId);
			context.setChannel(channel);
			// 模拟一点点报价计算耗时，放大并发串号
			Thread.yield();
			return context.getStoreId();
		}

		boolean contextIsProxy() {
			return org.springframework.aop.support.AopUtils.isAopProxy(context);
		}
	}

	/** 正例服务：单例，注入的是 scope 代理，每次方法调用按当前请求解析真实上下文。 */
	static class GoodPriceQuoteService {

		private final ProxiedOrderContext context;

		GoodPriceQuoteService(ProxiedOrderContext context) {
			this.context = context;
		}

		long quoteStoreId(long storeId, Channel channel) {
			context.setStoreId(storeId);
			context.setChannel(channel);
			Thread.yield();
			return context.getStoreId();
		}

		boolean contextIsProxy() {
			return org.springframework.aop.support.AopUtils.isAopProxy(context);
		}
	}

	/** request 作用域、无代理的下单上下文。 */
	static class PlainOrderContext {

		private long storeId;
		private Channel channel;

		long getStoreId() {
			return storeId;
		}

		void setStoreId(long storeId) {
			this.storeId = storeId;
		}

		void setChannel(Channel channel) {
			this.channel = channel;
		}
	}

	/** request 作用域、走 CGLIB scope 代理的下单上下文（非 final，可被子类化）。 */
	static class ProxiedOrderContext {

		private long storeId;
		private Channel channel;

		long getStoreId() {
			return storeId;
		}

		void setStoreId(long storeId) {
			this.storeId = storeId;
		}

		void setChannel(Channel channel) {
			this.channel = channel;
		}
	}
}
