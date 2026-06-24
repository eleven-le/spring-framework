package com.leilei.lab.laboratory.l02.l02_09;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.leilei.lab.laboratory.common.domain.Channel;
import com.leilei.lab.laboratory.common.domain.PriceRule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 📖 知识点：[[L02-09-容器基础设施-Resource-SpEL-MessageSource-父子容器#2.2 SpEL：容器内的表达式引擎]]
 * 🎯 作用：演示 SpEL 在容器内/外的两类用法——
 *         ① 容器内：{@code @Value("#{...}")} 由 {@code StandardBeanExpressionResolver} 驱动，能引用其它 Bean 属性、调静态方法 {@code T(...)}、
 *            三元/Elvis、读 {@code systemProperties}、对集合做<b>选择 {@code .?[]}</b>；
 *         ② 容器外：手撸 {@code SpelExpressionParser} + {@code StandardEvaluationContext}，对一批价格规则做选择 {@code .?[]} 与投影 {@code .![]}，
 *            把「规则匹配 + 取价」写成一行表达式。
 * 🔗 业务场景：古茗营销配置常把「秒杀库存阈值 / 灰度比例 / 生效渠道」写成可热改的表达式；价格引擎也常用 SpEL 对规则集合做声明式筛选取价，
 *         避免为每个筛选条件写一坨 if-else。
 */
public final class L0209_02_SpELInContainerDemo {

	public static void main(String[] args) {
		// 给 systemProperties 注入一个促销大区，演示 @Value 读环境属性
		System.setProperty("promo.region", "EAST");

		System.out.println("==================== 1. @Value(\"#{...}\")：容器内 SpEL 的五种典型用法 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(SpelConfig.class)) {
			SeckillPlan plan = ctx.getBean(SeckillPlan.class);
			System.out.println("① 引用 Bean 属性 seckillStockThreshold = " + plan.threshold);
			System.out.println("② 三元表达式选预案 plan = " + plan.planName);
			System.out.println("③ T(Math) 静态方法封顶 capped = " + plan.capped);
			System.out.println("④ 集合选择 .?[] 筛外卖渠道 takeout = " + plan.takeoutChannels);
			System.out.println("⑤ systemProperties + Elvis 读大区 region = " + plan.region);
		}

		System.out.println();
		System.out.println("==================== 2. 容器外手撸 SpEL：对价格规则集合做选择 .?[] + 投影 .![] ====================");
		List<PriceRule> rules = Arrays.asList(
				new PriceRule(1, 1001L, null, null, new BigDecimal("16.00"), 1, null, null),
				new PriceRule(2, 1001L, null, Channel.MINI_PROGRAM, new BigDecimal("13.90"), 5, null, null),
				new PriceRule(3, 1001L, 1001L, Channel.MINI_PROGRAM, new BigDecimal("9.90"), 9, null, null),
				new PriceRule(4, 1001L, 2002L, Channel.TAKEOUT_MEITUAN, new BigDecimal("19.00"), 7, null, null));

		ExpressionParser parser = new SpelExpressionParser();
		StandardEvaluationContext sec = new StandardEvaluationContext();
		sec.setVariable("rules", rules);
		sec.setVariable("storeId", 1001L);
		sec.setVariable("at", LocalDateTime.now());

		// 选择 .?[]：筛出对「门店 1001 + 小程序」此刻生效的规则
		@SuppressWarnings("unchecked")
		List<PriceRule> hit = (List<PriceRule>) parser
				.parseExpression("#rules.?[matches(#storeId, T(com.leilei.lab.laboratory.common.domain.Channel).MINI_PROGRAM, #at)]")
				.getValue(sec);
		System.out.println("选择 .?[] 命中生效规则数 = " + (hit == null ? 0 : hit.size()));

		// 投影 .![]：把命中规则映射成价格列表
		@SuppressWarnings("unchecked")
		List<BigDecimal> prices = (List<BigDecimal>) parser
				.parseExpression("#rules.?[matches(#storeId, T(com.leilei.lab.laboratory.common.domain.Channel).MINI_PROGRAM, #at)].![price]")
				.getValue(sec);
		System.out.println("投影 .![] 取出候选价 = " + prices);

		// selectFirst .^[]：直接选「门店专属规则」（storeId 非空）的第一条价
		Expression storeOnly = parser.parseExpression(
				"#rules.^[storeId == #storeId]?.price");
		System.out.println("selectFirst .^[] 取门店专属价 = " + storeOnly.getValue(sec));
		System.out.println("结论：@Value 与手撸解析共用同一套 SpEL 引擎（SpelExpressionParser），区别只在 EvaluationContext 与 #{} 包裹。");
	}

	@Configuration
	static class SpelConfig {

		/** 营销配置 Bean：被 @Value 的 #{...} 引用。 */
		@Bean
		MarketingConfig marketingConfig() {
			return new MarketingConfig();
		}

		@Bean
		SeckillPlan seckillPlan() {
			return new SeckillPlan();
		}
	}

	/** 可热改的营销配置（真实工程里常来自配置中心）。 */
	static class MarketingConfig {

		public int getSeckillStockThreshold() {
			return 200;
		}

		public List<String> getActiveChannels() {
			return Arrays.asList("MINI_PROGRAM", "APP", "TAKEOUT_MEITUAN", "TAKEOUT_ELEME", "POS");
		}
	}

	/** SpEL 注入靶子：五个字段分别演示一种容器内 SpEL 用法。 */
	static class SeckillPlan {

		@Value("#{marketingConfig.seckillStockThreshold}")
		int threshold;

		@Value("#{marketingConfig.seckillStockThreshold > 100 ? '大促预案' : '常规备货'}")
		String planName;

		@Value("#{T(java.lang.Math).min(marketingConfig.seckillStockThreshold, 150)}")
		int capped;

		@Value("#{marketingConfig.activeChannels.?[#this.startsWith('TAKEOUT')]}")
		List<String> takeoutChannels;

		@Value("#{systemProperties['promo.region'] ?: 'CN'}")
		String region;
	}

}
