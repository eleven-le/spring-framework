package com.leilei.lab.laboratory.l01.l01_03;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.leilei.lab.laboratory.common.dao.MockPriceRuleDao;
import com.leilei.lab.laboratory.common.domain.Channel;
import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 📖 知识点：[[L01-03-实验室搭建与源码调试姿势#2. 🏭 生产怎么用对]]（spring-test 实验容器 / @SpringJUnitConfig）
 * 🎯 作用：用 {@code @SpringJUnitConfig} 把「价格多维匹配」服务连同 Mock DAO 一起拉起成一个真实 Spring 容器，
 *         以「断言驱动」取代 main 方法里的肉眼看 System.out——这是本实验室从 L02 起的标准实验姿势。
 *         同时把本类定义的 {@link PriceLabConfig} / {@link PriceMatchService} 作为后续 L0103_02、L0103_03 复用的公共夹具。
 * 🔗 业务场景：古茗商品详情页报价——同一 SKU 在「命中门店 + 小程序渠道 + 有效期内」走促销价，
 *         否则回落全国基础价；priority 大者胜出，是 C 端最高频的读路径之一。
 */
@SpringJUnitConfig(L0103_01_PriceMatchContainerTest.PriceLabConfig.class)
class L0103_01_PriceMatchContainerTest {

	@Autowired
	PriceMatchService priceMatchService;

	@Autowired
	ApplicationContext applicationContext;

	@Test
	void 命中门店小程序促销价_而非全国基础价() {
		// 沙盘里 productId=100001（p%3==0）挂了一条小程序促销规则（priority=10），命中其促销门店时应取促销价
		long skuId = 1000010; // 100001 号商品的中杯 SKU（productId*10 + 0）
		long promoStoreId = pickPromoStoreId(skuId);

		BigDecimal promoQuote = priceMatchService.quote(skuId, promoStoreId, Channel.MINI_PROGRAM, LocalDateTime.now());
		BigDecimal basePrice = basePriceOf(skuId);

		assertThat(promoQuote)
				.as("命中促销门店 + 小程序渠道，应取 priority=10 的促销价，低于全国基础价")
				.isLessThan(basePrice);
	}

	@Test
	void 未命中促销维度时回落全国基础价() {
		long skuId = 1000010;
		// 故意用一个不在促销规则里的渠道（POS 门店收银）——只剩 priority=0 的全国兜底规则
		BigDecimal quote = priceMatchService.quote(skuId, 1001L, Channel.POS, LocalDateTime.now());
		assertThat(quote).isEqualByComparingTo(basePriceOf(skuId));
	}

	@Test
	void 容器是真实ApplicationContext_饿汉式装好全部夹具单例() {
		// 呼应 L01-02：@SpringJUnitConfig 背后就是一个 GenericApplicationContext，refresh 期已饿汉式实例化
		assertThat(applicationContext.getBean(PriceMatchService.class)).isSameAs(priceMatchService);
		assertThat(applicationContext.getBeanNamesForType(MockPriceRuleDao.class)).hasSize(1);
	}

	private long pickPromoStoreId(long skuId) {
		MockPriceRuleDao dao = applicationContext.getBean(MockPriceRuleDao.class);
		return dao.listBySkuId(skuId).stream()
				.filter(r -> r.getChannel() == Channel.MINI_PROGRAM && r.getStoreId() != null)
				.map(PriceRule::getStoreId)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("沙盘未给 sku=" + skuId + " 生成促销规则"));
	}

	private BigDecimal basePriceOf(long skuId) {
		MockPriceRuleDao dao = applicationContext.getBean(MockPriceRuleDao.class);
		return dao.listBySkuId(skuId).stream()
				.filter(r -> r.getStoreId() == null && r.getChannel() == null)
				.map(PriceRule::getPrice)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("沙盘未给 sku=" + skuId + " 生成全国兜底价"));
	}

	/**
	 * 实验容器的配置类：把「确定性沙盘数据 + Mock DAO + 价格匹配服务」装配成一条可注入的读链路。
	 * 用 {@link MockProfile#inMemory()} 关掉延迟/故障注入，保证 @SpringJUnitConfig 容器秒级 refresh、断言可复现。
	 */
	@Configuration
	static class PriceLabConfig {

		@Bean
		MockDataSet mockDataSet() {
			return MockDataFactory.seed(30, 5);
		}

		@Bean
		MockPriceRuleDao priceRuleDao(MockDataSet mockDataSet) {
			return new MockPriceRuleDao(mockDataSet, MockProfile.inMemory());
		}

		@Bean
		PriceMatchService priceMatchService(MockPriceRuleDao priceRuleDao) {
			return new PriceMatchService(priceRuleDao);
		}
	}

	/**
	 * 价格多维匹配服务（被实验的目标 Bean）：从候选规则里挑「命中当前门店/渠道/时刻、且 priority 最大」的规则；
	 * priority 相同则取价更低者（让利消费者）。被 L0103_02 压测、被 L0103_03 经 MockMvc 调用，故提为公共夹具。
	 */
	static class PriceMatchService {

		private final MockPriceRuleDao priceRuleDao;

		PriceMatchService(MockPriceRuleDao priceRuleDao) {
			this.priceRuleDao = priceRuleDao;
		}

		BigDecimal quote(long skuId, long storeId, Channel channel, LocalDateTime at) {
			List<PriceRule> candidates = priceRuleDao.listBySkuId(skuId);
			return candidates.stream()
					.filter(rule -> rule.matches(storeId, channel, at))
					.max(Comparator.comparingInt(PriceRule::getPriority)
							.thenComparing(Comparator.comparing(PriceRule::getPrice).reversed()))
					.map(PriceRule::getPrice)
					.orElseThrow(() -> new IllegalStateException("sku=" + skuId + " 无任何可用价格规则"));
		}
	}
}
