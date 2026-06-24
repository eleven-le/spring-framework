package com.leilei.lab.laboratory.l01.l01_03;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.leilei.lab.laboratory.common.dao.MockPriceRuleDao;
import com.leilei.lab.laboratory.common.domain.Channel;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 📖 知识点：[[L01-03-实验室搭建与源码调试姿势#2. 🏭 生产怎么用对]]（MockMvc 工具化）
 * 🎯 作用：用 {@code MockMvcBuilders.standaloneSetup} 单独拉起一个 C 端报价 Controller，
 *         不启动 Tomcat、不 refresh 整个容器，就能对「请求映射 / 参数绑定 / 响应体 / 状态码」做断言——
 *         这是验证 Web 层契约最轻量的实验工具，与 L0103_01 的「整容器」形成快慢两档对照。
 * 🔗 业务场景：商品详情页报价接口 GET /c/price，校验「命中促销门店返回促销价」与「缺必填参数返回 400」两条契约。
 */
class L0103_03_PriceQueryMockMvcTest {

	private MockMvc mockMvc;

	private L0103_01_PriceMatchContainerTest.PriceMatchService priceMatchService;

	private long promoStoreId;

	@BeforeEach
	void setUp() {
		MockDataSet dataSet = MockDataFactory.seed(30, 5);
		MockPriceRuleDao priceRuleDao = new MockPriceRuleDao(dataSet, MockProfile.inMemory());
		this.priceMatchService = new L0103_01_PriceMatchContainerTest.PriceMatchService(priceRuleDao);
		this.promoStoreId = priceRuleDao.listBySkuId(1000010L).stream()
				.filter(r -> r.getChannel() == Channel.MINI_PROGRAM && r.getStoreId() != null)
				.map(r -> r.getStoreId())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("沙盘未生成促销规则"));
		// standaloneSetup：只注册被测 Controller，毫秒级搭起一个 Mock 的 Servlet 环境
		this.mockMvc = MockMvcBuilders.standaloneSetup(new PriceQueryController(this.priceMatchService)).build();
	}

	@Test
	void 命中促销门店_返回促销价JSON() throws Exception {
		BigDecimal expected = this.priceMatchService.quote(1000010L, this.promoStoreId,
				Channel.MINI_PROGRAM, LocalDateTime.now());
		String expectedBody = "{\"skuId\":1000010,\"price\":" + expected.toPlainString() + "}";

		this.mockMvc.perform(get("/c/price")
						.param("skuId", "1000010")
						.param("storeId", String.valueOf(this.promoStoreId))
						.param("channel", "MINI_PROGRAM"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().string(expectedBody));
	}

	@Test
	void 缺必填参数_返回400() throws Exception {
		// 不传 channel：@RequestParam 必填校验由 MockMvc 在 Web 层拦下，返回 400
		this.mockMvc.perform(get("/c/price")
						.param("skuId", "1000010")
						.param("storeId", String.valueOf(this.promoStoreId)))
				.andExpect(status().isBadRequest());
	}

	/**
	 * C 端报价接口：极简手写 JSON 响应（仅用 StringHttpMessageConverter，无需 Jackson），
	 * 把价格匹配服务暴露成一个可被 MockMvc 探测的 HTTP 契约。
	 */
	@RestController
	static class PriceQueryController {

		private final L0103_01_PriceMatchContainerTest.PriceMatchService priceMatchService;

		PriceQueryController(L0103_01_PriceMatchContainerTest.PriceMatchService priceMatchService) {
			this.priceMatchService = priceMatchService;
		}

		@GetMapping(value = "/c/price", produces = MediaType.APPLICATION_JSON_VALUE)
		String quote(@RequestParam long skuId, @RequestParam long storeId, @RequestParam Channel channel) {
			BigDecimal price = this.priceMatchService.quote(skuId, storeId, channel, LocalDateTime.now());
			return "{\"skuId\":" + skuId + ",\"price\":" + price.toPlainString() + "}";
		}
	}
}
