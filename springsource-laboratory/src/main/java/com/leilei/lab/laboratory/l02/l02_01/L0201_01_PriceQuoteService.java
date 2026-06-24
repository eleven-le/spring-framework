package com.leilei.lab.laboratory.l02.l02_01;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.leilei.lab.laboratory.common.dao.MockPriceRuleDao;
import com.leilei.lab.laboratory.common.domain.Channel;
import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.rpc.MockMarketingRpc;

import org.springframework.util.Assert;

/**
 * 📖 知识点：[[L02-01-注入方式选型-构造器Setter字段#2. 🏭 生产怎么用对]]（构造器注入的生产正确姿势 / 可测性）
 * 🎯 作用：C 端价格试算服务——把「价格多维匹配 + 营销立减」两个协作者用 <b>构造器注入</b> 装进来。
 *         三处刻意为之：① 依赖字段 {@code final}，编译期保证「注完即终态、不可被偷改」；
 *         ② 单构造器，Spring 4.3+ 会隐式自动装配，无需 {@code @Autowired}；
 *         ③ 构造器内 {@link Assert#notNull} 做 fail-fast，漏依赖在「对象诞生那一刻」就炸，而非首个请求才 NPE。
 *         正因为依赖全走构造器入参，这个类能脱离 Spring 容器直接 {@code new} 出来做纯单元测试
 *         （见 L0201_01_ConstructorInjectionTestabilityTest），这正是构造器注入「最可测」的根因。
 * 🔗 业务场景：古茗小程序/外卖下单前的实时报价——读价格规则取最优价，再减去营销最优立减，得到到手价。
 *         这是 C 端最高频读路径之一，必须稳、必须好测、不能因为漏配依赖在大促首个请求才暴雷。
 */
public class L0201_01_PriceQuoteService {

	private final MockPriceRuleDao priceRuleDao;

	private final MockMarketingRpc marketingRpc;

	/**
	 * 唯一构造器：所有依赖在此一次性注入并终态化。
	 * <p>单构造器场景下 Spring 会自动把它当作装配入口（无需 {@code @Autowired}）；
	 * Assert 校验让「漏注入」在实例化阶段就 fail-fast，而不是潜伏到运行期。
	 */
	public L0201_01_PriceQuoteService(MockPriceRuleDao priceRuleDao, MockMarketingRpc marketingRpc) {
		Assert.notNull(priceRuleDao, "priceRuleDao 不能为空：价格规则 DAO 是报价的必备依赖");
		Assert.notNull(marketingRpc, "marketingRpc 不能为空：营销 RPC 是报价的必备依赖");
		this.priceRuleDao = priceRuleDao;
		this.marketingRpc = marketingRpc;
	}

	/**
	 * 实时报价：取命中「门店/渠道/时刻」且 priority 最大的规则价，减去营销最优立减，下限兜底到 0。
	 */
	public BigDecimal quote(long skuId, long storeId, Channel channel, long userId, LocalDateTime at) {
		List<PriceRule> candidates = this.priceRuleDao.listBySkuId(skuId);
		BigDecimal bestPrice = candidates.stream()
				.filter(rule -> rule.matches(storeId, channel, at))
				.max(Comparator.comparingInt(PriceRule::getPriority)
						.thenComparing(Comparator.comparing(PriceRule::getPrice).reversed()))
				.map(PriceRule::getPrice)
				.orElseThrow(() -> new IllegalStateException("sku=" + skuId + " 无任何可用价格规则"));
		BigDecimal discount = this.marketingRpc.queryBestDiscount(userId, skuId);
		BigDecimal payable = bestPrice.subtract(discount);
		return payable.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : payable;
	}

}
