package com.leilei.lab.laboratory.l02.l02_02;

import java.util.Arrays;

import com.leilei.lab.laboratory.l02.l02_02.naming.ChannelPriceService;
import com.leilei.lab.laboratory.l02.l02_02.naming.mini.PriceService;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（BeanNameGenerator 与 Bean 命名规则）
 * 🎯 作用：把 Bean 命名规则做成可运行实验：
 *         ① 默认 {@link org.springframework.context.annotation.AnnotationBeanNameGenerator}——
 *            组件名 = 短类名首字母小写，@Bean 名 = 方法名；
 *         ② 跨包同名类用默认命名器 → 撞名，{@code ClassPathBeanDefinitionScanner} 抛
 *            {@code ConflictingBeanDefinitionException}；
 *         ③ 换 {@link FullyQualifiedAnnotationBeanNameGenerator}（名 = 全限定类名）→ 两个同名类和平共存。
 * 🔗 业务场景：小程序与 App 各有一套 {@code PriceService}，模块拆分后落到不同包，短类名相同；
 *         默认命名器下二者撞名，要么启动报错、要么静默互相覆盖；全限定名命名器是大型多模块工程的解法。
 */
public final class L0202_03_BeanNameGeneratorDemo {

	static final String MINI_PKG = "com.leilei.lab.laboratory.l02.l02_02.naming.mini";

	static final String APP_PKG = "com.leilei.lab.laboratory.l02.l02_02.naming.app";

	private L0202_03_BeanNameGeneratorDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 默认命名规则：组件名 = 短类名首字母小写，@Bean 名 = 方法名 ====================");
		defaultNamingRule();
		System.out.println();
		System.out.println("==================== 2. 默认命名器 + 跨包同名类：撞名抛 ConflictingBeanDefinitionException ====================");
		defaultGeneratorCollision();
		System.out.println();
		System.out.println("==================== 3. FullyQualifiedAnnotationBeanNameGenerator：全限定名命名 → 同名类和平共存 ====================");
		fullyQualifiedGeneratorResolvesCollision();
	}

	private static void defaultNamingRule() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ctx.register(NamingRuleConfig.class, PriceService.class);
			ctx.refresh();
			System.out.println("[组件] " + PriceService.class.getName() + " 默认 Bean 名 = "
					+ Arrays.toString(ctx.getBeanNamesForType(ChannelPriceService.class))
					+ "（短类名 PriceService → 首字母小写 priceService）");
			System.out.println("[@Bean] PriceTag 的 Bean 名 = "
					+ Arrays.toString(ctx.getBeanNamesForType(PriceTag.class))
					+ "（= @Bean 方法名 flagshipPriceTag，不是类名）");
		}
	}

	private static void defaultGeneratorCollision() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		try {
			ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(ctx);
			// 默认命名器：两个包下的 PriceService 都被命名为 priceService
			scanner.scan(MINI_PKG, APP_PKG);
			System.out.println("[默认命名器] 居然没冲突？bean 名 = "
					+ Arrays.toString(ctx.getBeanNamesForType(ChannelPriceService.class)));
		}
		catch (RuntimeException ex) {
			System.out.println("[默认命名器] 跨包同名类撞名，scan 阶段直接抛错：" + ex.getClass().getSimpleName());
			System.out.println("    根因：" + ex.getMessage());
		}
		finally {
			ctx.close();
		}
	}

	private static void fullyQualifiedGeneratorResolvesCollision() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(ctx);
			scanner.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
			scanner.scan(MINI_PKG, APP_PKG);
			ctx.refresh();
			String[] names = ctx.getBeanNamesForType(ChannelPriceService.class);
			Arrays.sort(names);
			System.out.println("[全限定名命名器] ChannelPriceService Bean 数量 = " + names.length + "（两个同名类共存）");
			for (String name : names) {
				System.out.println("    - " + name + " → 渠道=" + ctx.getBean(name, ChannelPriceService.class).channel());
			}
		}
	}

	/** 演示 @Bean 命名规则：Bean 名 = 方法名（flagshipPriceTag），与返回类型无关。 */
	@Configuration
	static class NamingRuleConfig {

		@Bean
		PriceTag flagshipPriceTag() {
			return new PriceTag("旗舰店价签");
		}
	}

	/** 简单价签，仅用于演示 @Bean 方法名即 Bean 名。 */
	static final class PriceTag {

		private final String desc;

		PriceTag(String desc) {
			this.desc = desc;
		}

		@Override
		public String toString() {
			return "PriceTag{" + this.desc + "}";
		}
	}

}
