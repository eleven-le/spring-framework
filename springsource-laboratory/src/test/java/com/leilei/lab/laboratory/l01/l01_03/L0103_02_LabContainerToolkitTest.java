package com.leilei.lab.laboratory.l01.l01_03;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.leilei.lab.laboratory.common.bench.BenchReport;
import com.leilei.lab.laboratory.common.bench.ConcurrentBench;
import com.leilei.lab.laboratory.common.domain.Channel;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 📖 知识点：[[L01-03-实验室搭建与源码调试姿势#2. 🏭 生产怎么用对]]（实验容器「工具化」/ 容器缓存复用）
 * 🎯 作用：把「@SpringJUnitConfig + 公共注入 + 报价快捷方法」沉淀成可继承的实验基座
 *         {@link AbstractPriceLabSupport}，子类只写断言；并实测 spring-test 的 ContextCache——
 *         同一份配置的容器在整轮测试里只 refresh 一次、被多方法/多类复用，沙盘 seed 不重复构建。
 *         最后用 {@link ConcurrentBench} 直接对容器里的服务跑高并发报价压测，演示「容器装一次、探针复用」。
 * 🔗 业务场景：大促前压测商品详情页报价读路径（多线程齐射），验证价格匹配在高并发下结果稳定、零失败。
 */
class L0103_02_LabContainerToolkitTest extends AbstractPriceLabSupport {

	/** 跨测试方法收集注入到的容器身份；ContextCache 命中则恒为同一实例 */
	private static final Set<Integer> SEEN_CONTEXTS = Collections.synchronizedSet(new HashSet<>());

	@Test
	void 容器被缓存复用_方法一() {
		SEEN_CONTEXTS.add(System.identityHashCode(this.ctx));
		assertThat(this.priceMatchService).isNotNull();
	}

	@Test
	void 容器被缓存复用_方法二且与方法一同一实例() {
		SEEN_CONTEXTS.add(System.identityHashCode(this.ctx));
		// 两个测试方法注入的是同一个 ApplicationContext —— spring-test 不会每方法重建容器
		assertThat(SEEN_CONTEXTS)
				.as("ContextCache 命中：同一配置的容器整轮只造一次")
				.hasSize(1);
	}

	@Test
	void 高并发报价_零失败且结果稳定() {
		long skuId = 1000010;
		long promoStoreId = anyPromoStoreId(skuId);
		BigDecimal expected = quote(skuId, promoStoreId, Channel.MINI_PROGRAM);

		// 32 线程 × 每线程 500 次齐射，全部走容器里的同一个 PriceMatchService 单例
		BenchReport report = ConcurrentBench.run("price-quote", 32, 500,
				() -> {
					BigDecimal q = quote(skuId, promoStoreId, Channel.MINI_PROGRAM);
					if (q.compareTo(expected) != 0) {
						throw new IllegalStateException("并发下报价漂移: " + q + " != " + expected);
					}
				});
		System.out.println(report.prettyPrint());

		assertThat(report.getFailOps()).as("高并发报价应零失败、结果一致").isZero();
		assertThat(report.getSuccessOps()).isEqualTo(report.getTotalOps());
	}

	private long anyPromoStoreId(long skuId) {
		ApplicationContext context = this.ctx;
		return context.getBean(com.leilei.lab.laboratory.common.dao.MockPriceRuleDao.class)
				.listBySkuId(skuId).stream()
				.filter(r -> r.getChannel() == Channel.MINI_PROGRAM && r.getStoreId() != null)
				.map(r -> r.getStoreId())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("沙盘未给 sku=" + skuId + " 生成促销规则"));
	}
}

/**
 * 实验基座（可复用「工具」）：承载 {@code @SpringJUnitConfig} 与公共注入，供本章及后续章节的实验类继承。
 * spring-test 沿类继承链查找 {@code @ContextConfiguration} 元注解，故子类无需重复声明配置即拿到同一缓存容器。
 */
@SpringJUnitConfig(L0103_01_PriceMatchContainerTest.PriceLabConfig.class)
abstract class AbstractPriceLabSupport {

	@Autowired
	protected ApplicationContext ctx;

	@Autowired
	protected L0103_01_PriceMatchContainerTest.PriceMatchService priceMatchService;

	/** 报价快捷方法：默认取当前时刻，免去每个断言重复传 LocalDateTime.now() */
	protected BigDecimal quote(long skuId, long storeId, Channel channel) {
		return this.priceMatchService.quote(skuId, storeId, channel, LocalDateTime.now());
	}
}
