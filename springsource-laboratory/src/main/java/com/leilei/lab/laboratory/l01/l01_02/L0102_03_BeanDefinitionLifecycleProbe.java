package com.leilei.lab.laboratory.l01.l01_02;

import java.math.BigDecimal;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * 📖 知识点：[[L01-02-容器心智模型与核心抽象#4. 🧠 设计思想]]（BeanDefinition 心智模型 · 一生全景）
 * 🎯 作用：用一条「父模板 + 多渠道子定义」的动态注册链路，把 BeanDefinition 的一生走一遍——
 *         定义（元数据）→ 注册（registry 按名登记）→ 合并（父子合成 RootBeanDefinition）→ 实例化（getBean），
 *         每一步打印元数据快照，让「BeanDefinition 是 Bean 的图纸、不是 Bean 本身」这件事看得见。
 * 🔗 业务场景：古茗多渠道价格策略（小程序立减 / 外卖加价 / 堂食原价）共享一份「价格策略模板」父定义，
 *         运营每上一个渠道就动态注册一个子定义——这正是 Dubbo ReferenceBean、MyBatis MapperBean
 *         等「按需动态注册 Bean」套路的最小内核。
 */
public final class L0102_03_BeanDefinitionLifecycleProbe {

	private static final String PARENT_TEMPLATE = "channelPriceStrategyTemplate";

	private L0102_03_BeanDefinitionLifecycleProbe() {
	}

	public static void main(String[] args) {
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

		// —— 阶段一 · 定义：父模板是一张「抽象图纸」，只放共享元数据，自己永不被实例化 ——
		BeanDefinition parent = BeanDefinitionBuilder.genericBeanDefinition(ChannelPriceStrategy.class)
				.setScope(BeanDefinition.SCOPE_SINGLETON)
				.setLazyInit(false)
				.setAbstract(true)
				.addPropertyValue("basePrice", new BigDecimal("12.00"))
				.getBeanDefinition();
		factory.registerBeanDefinition(PARENT_TEMPLATE, parent);
		printStage("阶段一·定义(父模板)", PARENT_TEMPLATE, parent);

		// —— 阶段二 · 注册：子定义不写 class、不写 basePrice，全靠 parentName 继承；只补渠道差异 ——
		BeanDefinition miniProgram = BeanDefinitionBuilder.genericBeanDefinition()
				.setParentName(PARENT_TEMPLATE)
				.addPropertyValue("channel", "MINI_PROGRAM")
				.addPropertyValue("adjustment", new BigDecimal("-2.00")) // 小程序立减 2 元
				.getBeanDefinition();
		factory.registerBeanDefinition("miniProgramPriceStrategy", miniProgram);
		printStage("阶段二·注册(子定义/合并前)", "miniProgramPriceStrategy", miniProgram);
		System.out.println("    ↑ 注意：合并前子定义 beanClassName=" + miniProgram.getBeanClassName()
				+ "（继承自父，尚未补全），registry 共登记 " + factory.getBeanDefinitionCount() + " 个定义");

		// —— 阶段三 · 合并：容器把「父定义 + 子定义」合成一份扁平的 RootBeanDefinition，class/scope/属性全部补齐 ——
		BeanDefinition merged = factory.getMergedBeanDefinition("miniProgramPriceStrategy");
		printStage("阶段三·合并(RootBeanDefinition)", "miniProgramPriceStrategy", merged);
		System.out.println("    ↑ 合并后 beanClassName 补全为 " + merged.getBeanClassName()
				+ "，basePrice 从父继承、channel/adjustment 来自子——这份合成图纸才是实例化真正依据");

		// —— 阶段四 · 实例化：getBean 才把图纸变成对象（之前所有阶段都没有任何 ChannelPriceStrategy 实例）——
		ChannelPriceStrategy strategy = factory.getBean("miniProgramPriceStrategy", ChannelPriceStrategy.class);
		System.out.println("阶段四·实例化(getBean) -> " + strategy.describe());

		// 同一张父模板再派生一个外卖加价子定义，证明「一张图纸 N 个实例化分支」
		BeanDefinition takeaway = BeanDefinitionBuilder.genericBeanDefinition()
				.setParentName(PARENT_TEMPLATE)
				.addPropertyValue("channel", "TAKEAWAY")
				.addPropertyValue("adjustment", new BigDecimal("1.50")) // 外卖渠道加价 1.5 元
				.getBeanDefinition();
		factory.registerBeanDefinition("takeawayPriceStrategy", takeaway);
		System.out.println("阶段四·实例化(getBean) -> "
				+ factory.getBean("takeawayPriceStrategy", ChannelPriceStrategy.class).describe());
	}

	private static void printStage(String stage, String beanName, BeanDefinition bd) {
		System.out.printf("%s  name=%s  class=%s  scope=%s  lazy=%s  abstract=%s%n",
				stage, beanName,
				bd.getBeanClassName() == null ? "<继承父>" : bd.getBeanClassName(),
				bd.getScope() == null || bd.getScope().isEmpty() ? "<继承父>" : bd.getScope(),
				bd.isLazyInit(), bd.isAbstract());
	}

	/**
	 * 渠道价格策略 Bean：父模板提供 basePrice，子定义注入 channel 与 adjustment，
	 * 最终价 = basePrice + adjustment。属性经 setter 注入（BeanDefinition.propertyValues 的落点）。
	 */
	public static class ChannelPriceStrategy {

		private String channel;
		private BigDecimal basePrice;
		private BigDecimal adjustment = BigDecimal.ZERO;

		public void setChannel(String channel) {
			this.channel = channel;
		}

		public void setBasePrice(BigDecimal basePrice) {
			this.basePrice = basePrice;
		}

		public void setAdjustment(BigDecimal adjustment) {
			this.adjustment = adjustment;
		}

		public BigDecimal finalPrice() {
			return this.basePrice.add(this.adjustment);
		}

		public String describe() {
			return "渠道[" + this.channel + "] 基础价=" + this.basePrice
					+ " 调整=" + this.adjustment + " 最终价=" + finalPrice();
		}

	}

}
