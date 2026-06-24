package com.leilei.lab.laboratory.l02.l02_03;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.rpc.MockMarketingRpc;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 📖 知识点：[[L02-03-条件装配-Conditional与Profile#2. 🏭 生产怎么用对]]（@Profile 多环境装配）
 * 🎯 作用：用「营销网关」一族 @Bean 演示 @Profile 的四种典型形态——
 *         ① 简单 profile 名 {@code @Profile("prod")} / {@code @Profile("stress")} 二选一切换；
 *         ② 取反 {@code @Profile("!prod")} 兜底非生产环境；
 *         ③ 表达式 {@code @Profile("prod & gray")} 双条件 AND；
 *         ④ {@code @Profile("default")} 在「无任何激活 profile」时由保留默认 profile 兜底装配
 *         （{@code AbstractEnvironment#isProfileActive} 第 415~417 行：active 为空时回落 default）。
 *         本质上 {@code @Profile} 就是 {@code @Conditional(ProfileCondition.class)} 的一个派生注解。
 * 🔗 业务场景：营销 RPC 在不同环境要切不同实现——生产连真实营销中台；压测（stress）走影子网关只算不打真实下游，
 *         防止把营销系统打挂；灰度（prod & gray）才挂新版分流网关；本地默认环境用桩网关，免依赖外部。
 *         用 @Profile 在「装配期」一次切死，运行期零分支判断，杜绝「压测流量误打生产营销接口」的资损事故。
 */
public final class L0203_03_ProfileMultiEnvDemo {

	private L0203_03_ProfileMultiEnvDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 无激活 profile：active 为空 → 回落保留默认 profile 'default' → 仅 @Profile(\"default\") 与 @Profile(\"!prod\") 命中 ====================");
		run();
		System.out.println();
		System.out.println("==================== 2. active=[stress]：压测影子网关命中；!prod 兜底也命中（prod 未激活）====================");
		run("stress");
		System.out.println();
		System.out.println("==================== 3. active=[prod]：生产网关命中；表达式 prod & gray 因 gray 未激活而不命中 ====================");
		run("prod");
		System.out.println();
		System.out.println("==================== 4. active=[prod, gray]：生产网关 + 灰度分流网关（prod & gray）一起命中 ====================");
		run("prod", "gray");
	}

	private static void run(String... activeProfiles) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			if (activeProfiles.length > 0) {
				ctx.getEnvironment().setActiveProfiles(activeProfiles);
			}
			ctx.register(MarketingGatewayConfig.class);
			ctx.refresh();

			System.out.println("[激活 profile] " + Arrays.toString(activeProfiles.length == 0 ? new String[] {"<空，走 default>"} : activeProfiles));
			// TreeMap 让 Bean 名有序输出，断言稳定
			Map<String, MarketingGateway> gateways = new TreeMap<>(ctx.getBeansOfType(MarketingGateway.class));
			System.out.println("[装配的营销网关] 数量 = " + gateways.size());
			for (Map.Entry<String, MarketingGateway> e : gateways.entrySet()) {
				System.out.println("    - " + e.getKey() + " → " + e.getValue().label());
			}
		}
	}

	@Configuration
	static class MarketingGatewayConfig {

		/** 生产：连真实营销中台。 */
		@Bean
		@Profile("prod")
		MarketingGateway prodMarketingGateway() {
			return new MarketingGateway("生产营销网关(真实中台)", new MockMarketingRpc(MockProfile.rpc()));
		}

		/** 压测：影子网关，只算不打真实下游（零延迟内存桩）。 */
		@Bean
		@Profile("stress")
		MarketingGateway stressMarketingGateway() {
			return new MarketingGateway("压测影子网关(不打真实下游)", new MockMarketingRpc(MockProfile.inMemory()));
		}

		/** 灰度分流：表达式 AND——仅当生产且灰度放量时才挂。 */
		@Bean
		@Profile("prod & gray")
		MarketingGateway grayMarketingGateway() {
			return new MarketingGateway("灰度分流网关(prod & gray)", new MockMarketingRpc(MockProfile.rpc()));
		}

		/** 非生产兜底：取反——只要不是 prod 就挂，覆盖本地 / 压测等所有非生产场景。 */
		@Bean
		@Profile("!prod")
		MarketingGateway nonProdStubGateway() {
			return new MarketingGateway("非生产桩网关(!prod)", new MockMarketingRpc(MockProfile.inMemory()));
		}

		/** 默认兜底：active 全空时由保留默认 profile 'default' 命中（本地裸跑免配置）。 */
		@Bean
		@Profile("default")
		MarketingGateway defaultLocalGateway() {
			return new MarketingGateway("默认本地网关(default)", new MockMarketingRpc(MockProfile.inMemory()));
		}
	}

	/** 营销网关：包一层 {@link MockMarketingRpc}，label 标识当前环境装配的是哪套实现。 */
	static final class MarketingGateway {

		private final String label;
		private final MockMarketingRpc rpc;

		MarketingGateway(String label, MockMarketingRpc rpc) {
			this.label = label;
			this.rpc = rpc;
		}

		String label() {
			return this.label;
		}

		MockMarketingRpc rpc() {
			return this.rpc;
		}
	}

}
