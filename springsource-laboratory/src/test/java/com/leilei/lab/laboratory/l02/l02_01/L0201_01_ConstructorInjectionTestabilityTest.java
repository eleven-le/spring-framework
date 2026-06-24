package com.leilei.lab.laboratory.l02.l02_01;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.leilei.lab.laboratory.common.dao.MockPriceRuleDao;
import com.leilei.lab.laboratory.common.domain.Channel;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.rpc.MockMarketingRpc;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 📖 知识点：[[L02-01-注入方式选型-构造器Setter字段#2. 🏭 生产怎么用对]]（构造器注入的可测性）
 * 🎯 作用：用「断言驱动」坐实本章核心论点——<b>构造器注入让单元测试零仪式</b>：
 *         不起 Spring 容器、不用反射，一行 {@code new} 把 Mock 依赖喂进去即可断言（{@link #构造器注入_脱离容器一行new即可注入Mock_零仪式()}）；
 *         作为反证，字段注入的 Bean 在容器外无注入入口，单测被迫用 {@link ReflectionTestUtils} 反射强塞
 *         （{@link #字段注入_容器外只能靠反射强塞依赖_仪式感拉满()}）。两相对照即「为什么生产默认构造器注入」。
 * 🔗 业务场景：古茗下单前实时报价 {@link L0201_01_PriceQuoteService} 的单元测试——
 *         读路径必须可被廉价、确定性地覆盖，而不是每跑一个用例都拉起一个 Spring 容器。
 */
class L0201_01_ConstructorInjectionTestabilityTest {

	private final MockDataSet dataSet = MockDataFactory.seed(9, 3);

	@Test
	void 构造器注入_脱离容器一行new即可注入Mock_零仪式() {
		// 关键：无 @SpringJUnitConfig、无容器、无反射——构造器入参就是注入点。
		MockPriceRuleDao priceRuleDao = new MockPriceRuleDao(this.dataSet, MockProfile.inMemory());
		MockMarketingRpc marketingRpc = new MockMarketingRpc(MockProfile.inMemory());
		L0201_01_PriceQuoteService service = new L0201_01_PriceQuoteService(priceRuleDao, marketingRpc);

		long skuId = 1000010; // 100001 号商品（p%3==0）的中杯 SKU，沙盘里挂了小程序促销规则
		BigDecimal quote = service.quote(skuId, 1001L, Channel.MINI_PROGRAM, 88L, LocalDateTime.now());

		assertThat(quote)
				.as("报价应为正数：命中规则价后再减营销立减，下限兜底到 0")
				.isGreaterThan(BigDecimal.ZERO);
	}

	@Test
	void 构造器注入_漏依赖在实例化即fail_fast() {
		// 漏传依赖不是潜伏到运行期才 NPE，而是在「对象诞生那一刻」就被 Assert 拦下。
		MockMarketingRpc marketingRpc = new MockMarketingRpc(MockProfile.inMemory());
		assertThatThrownBy(() -> new L0201_01_PriceQuoteService(null, marketingRpc))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("priceRuleDao");
	}

	@Test
	void 字段注入_容器外只能靠反射强塞依赖_仪式感拉满() {
		// 反证：字段注入的 Bean 没有任何注入入口，容器外单测只能反射——这正是它「最难测」的代价。
		L0201_02_InjectionStyleContrastDemo.FieldStyle fieldStyle = new L0201_02_InjectionStyleContrastDemo.FieldStyle();
		assertThat(fieldStyle.daoReady())
				.as("裸 new 之后字段为 null，没有 setter/构造器可注入")
				.isFalse();

		MockPriceRuleDao priceRuleDao = new MockPriceRuleDao(this.dataSet, MockProfile.inMemory());
		ReflectionTestUtils.setField(fieldStyle, "priceRuleDao", priceRuleDao);

		assertThat(fieldStyle.daoReady())
				.as("被迫用 ReflectionTestUtils 反射注入后依赖才就位——对比构造器注入的一行 new")
				.isTrue();
	}

}
