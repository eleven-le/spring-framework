package com.leilei.lab.laboratory.l02.l02_09;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 📖 知识点：[[L02-09-容器基础设施-Resource-SpEL-MessageSource-父子容器#2.4 父子容器：层级 BeanFactory]]
 * 🎯 作用：演示层级容器（HierarchicalBeanFactory）的四条铁律——
 *         ① 可见性<b>单向</b>：子容器能拿/能注入父容器的 Bean（{@code getBean}/{@code findAutowireCandidates} 沿 {@code getParentBeanFactory} 上溯），父容器看不到子容器的 Bean；
 *         ② {@code containsLocalBean} 只看本层，{@code containsBean} 含父层——区分「本容器定义」与「沿父可见」；
 *         ③ 同名 Bean <b>子遮蔽父</b>：子容器按自己的定义解析，父容器仍用自己的，互不覆盖；
 *         ④ {@code getBeanFactory().getParentBeanFactory()} 暴露层级链。
 * 🔗 业务场景：古茗把「价格引擎 / 库存 client」等公共基础设施放父容器，各业务线（小程序下单 / 外卖下单）各起子容器复用父 Bean；
 *         这正是 Spring MVC「Root WebApplicationContext（父，放 Service/DAO）+ DispatcherServlet 容器（子，放 Controller）」的同构模型（详见 L05-01）。
 */
public final class L0209_04_ParentChildContextDemo {

	public static void main(String[] args) {
		// 父容器：平台基础设施（价格引擎 / 库存 client / 平台默认渠道配置）
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(PlatformInfraConfig.class);

		// 子容器：小程序业务线，setParent 挂上父容器后再 refresh
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.setParent(parent);
		child.register(MiniAppBizConfig.class);
		child.refresh();

		try {
			System.out.println("==================== 1. 可见性单向：子能拿父的 Bean，父拿不到子的 Bean ====================");
			PriceEngine fromChild = child.getBean("priceEngine", PriceEngine.class);
			System.out.println("子容器拿父容器的 priceEngine = " + fromChild.name());
			try {
				parent.getBean("miniOrderService");
				System.out.println("不该走到这里");
			}
			catch (NoSuchBeanDefinitionException ex) {
				System.out.println("父容器拿子容器的 miniOrderService → 抛 = " + ex.getClass().getSimpleName());
			}

			System.out.println();
			System.out.println("==================== 2. 子容器注入点沿父层裁决：priceEngine 注进子的 orderService ====================");
			MiniOrderService orderService = child.getBean(MiniOrderService.class);
			System.out.println("子 orderService 注入到的 priceEngine 来自父 = "
					+ (orderService.priceEngine == parent.getBean("priceEngine")));

			System.out.println();
			System.out.println("==================== 3. containsLocalBean 只看本层，containsBean 含父层 ====================");
			System.out.println("child.containsLocalBean('priceEngine') = " + child.containsLocalBean("priceEngine")
					+ "（本层无定义）");
			System.out.println("child.containsBean('priceEngine')      = " + child.containsBean("priceEngine")
					+ "（沿父可见）");

			System.out.println();
			System.out.println("==================== 4. 同名 Bean 子遮蔽父：各取各的，互不覆盖 ====================");
			String parentChannel = parent.getBean("channelConfig", String.class);
			String childChannel = child.getBean("channelConfig", String.class);
			System.out.println("父容器 channelConfig = " + parentChannel);
			System.out.println("子容器 channelConfig = " + childChannel + "（子定义遮蔽父，父仍用自己的）");

			System.out.println();
			System.out.println("==================== 5. 层级链：getParentBeanFactory 暴露父 BeanFactory ====================");
			System.out.println("child.getBeanFactory().getParentBeanFactory() != null = "
					+ (child.getBeanFactory().getParentBeanFactory() != null));
			System.out.println("parent.getBeanFactory().getParentBeanFactory() == null = "
					+ (parent.getBeanFactory().getParentBeanFactory() == null));
		}
		finally {
			child.close();
			parent.close();
		}
	}

	// ===================== 父容器：平台基础设施 =====================

	@Configuration
	static class PlatformInfraConfig {

		@Bean
		PriceEngine priceEngine() {
			return new PriceEngine("平台价格引擎");
		}

		@Bean
		String channelConfig() {
			return "平台默认渠道配置";
		}
	}

	// ===================== 子容器：小程序业务线 =====================

	@Configuration
	static class MiniAppBizConfig {

		@Bean
		MiniOrderService miniOrderService() {
			return new MiniOrderService();
		}

		/** 与父容器同名：子遮蔽父。 */
		@Bean
		String channelConfig() {
			return "小程序专属渠道配置";
		}
	}

	static class PriceEngine {

		private final String name;

		PriceEngine(String name) {
			this.name = name;
		}

		String name() {
			return name;
		}
	}

	/** 子容器 Bean，依赖父容器的 PriceEngine（按类型沿父层注入）。 */
	static class MiniOrderService {

		@Autowired
		PriceEngine priceEngine;
	}

}
