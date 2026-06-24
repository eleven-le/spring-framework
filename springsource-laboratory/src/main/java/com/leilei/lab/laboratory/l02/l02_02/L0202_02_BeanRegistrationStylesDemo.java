package com.leilei.lab.laboratory.l02.l02_02;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

import com.leilei.lab.laboratory.l02.l02_02.scan.LegacyComponent;
import com.leilei.lab.laboratory.l02.l02_02.scan.PricingComponent;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（@Bean / @Import / @ComponentScan / 扫描过滤器）
 * 🎯 作用：把 Bean 进容器的「四种姿势」放进同一实验对照：
 *         ① {@code @ComponentScan} 默认过滤器（命中 @Component 元注解的 @CDomainService / @LegacyComponent）；
 *         ② {@code @ComponentScan(excludeFilters=...)} 按注解排除待下线组件；
 *         ③ {@code @Bean} 工厂方法（注册无源码改造权的第三方/独立组件）；
 *         ④ {@code @Import} + {@link ImportBeanDefinitionRegistrar} 编程式注册（活动期动态挂载组件）。
 * 🔗 业务场景：定价组件来源各异——业务组件走扫描、第三方组件走 @Bean、大促秒杀组件按开关编程式注册；
 *         扫描过滤器负责把「待下线的旧组件」挡在容器门外。
 */
public final class L0202_02_BeanRegistrationStylesDemo {

	static final String SCAN_PKG = "com.leilei.lab.laboratory.l02.l02_02.scan";

	private L0202_02_BeanRegistrationStylesDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. @ComponentScan 默认过滤器：@Component 元注解全部命中（含待下线组件） ====================");
		printPricingComponents(ScanAllConfig.class);
		System.out.println();
		System.out.println("==================== 2. @ComponentScan + excludeFilters：按注解排除待下线组件 ====================");
		printPricingComponents(ScanFilteredConfig.class);
		System.out.println();
		System.out.println("==================== 3+4. @Bean 工厂方法 + @Import 编程式注册：与扫描组件汇成完整组件族 ====================");
		printPricingComponents(RegistrationStylesConfig.class);
	}

	private static void printPricingComponents(Class<?> configClass) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(configClass)) {
			Map<String, PricingComponent> beans = new TreeMap<>(ctx.getBeansOfType(PricingComponent.class));
			System.out.println("[" + configClass.getSimpleName() + "] PricingComponent 数量 = " + beans.size());
			beans.forEach((name, bean) -> System.out.println("    - " + name + " → " + bean.label()));
		}
	}

	/** 姿势①：默认过滤器——@CDomainService（满减/会员）与 @LegacyComponent（待下线）都被扫入。 */
	@Configuration
	@ComponentScan(basePackages = SCAN_PKG)
	static class ScanAllConfig {
	}

	/** 姿势②：excludeFilters 按注解排除——待下线的 @LegacyComponent 组件被挡在门外。 */
	@Configuration
	@ComponentScan(basePackages = SCAN_PKG,
			excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = LegacyComponent.class))
	static class ScanFilteredConfig {
	}

	/** 姿势③+④：扫描（带排除）+ @Bean 工厂方法 + @Import 编程式注册，三源汇流。 */
	@Configuration
	@ComponentScan(basePackages = SCAN_PKG,
			excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = LegacyComponent.class))
	@Import({InfraConfig.class, FlashSaleRegistrar.class})
	static class RegistrationStylesConfig {
	}

	/** 姿势③：@Bean 工厂方法注册「无源码改造权」的第三方/独立组件（不能加 @Component 的场景）。 */
	@Configuration
	static class InfraConfig {

		@Bean
		PricingComponent newUserPricingComponent() {
			return new SimplePricingComponent("新人立减", BigDecimal.valueOf(3));
		}
	}

	/** 姿势④：@Import 一个 Registrar，启动期按业务开关编程式注册 Bean 定义（大促秒杀组件动态挂载）。 */
	static final class FlashSaleRegistrar implements ImportBeanDefinitionRegistrar {

		@Override
		public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
			BeanDefinitionBuilder builder = BeanDefinitionBuilder
					.genericBeanDefinition(SimplePricingComponent.class)
					.addConstructorArgValue("秒杀立减")
					.addConstructorArgValue(BigDecimal.valueOf(8));
			registry.registerBeanDefinition("flashSalePricingComponent", builder.getBeanDefinition());
		}
	}

	/** 可复用的定价组件实现：供 @Bean 与编程式注册复用（带显式构造参数）。 */
	public static class SimplePricingComponent implements PricingComponent {

		private final String label;

		private final BigDecimal fixedDiscount;

		public SimplePricingComponent(String label, BigDecimal fixedDiscount) {
			this.label = label;
			this.fixedDiscount = fixedDiscount;
		}

		@Override
		public BigDecimal discount(BigDecimal base) {
			return this.fixedDiscount;
		}

		@Override
		public String label() {
			return this.label;
		}
	}

}
