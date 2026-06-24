package com.leilei.lab.laboratory.l02.l02_08;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.NestedRuntimeException;

/**
 * 📖 知识点：[[L02-08-歧义裁决与容器语义-Primary-Qualifier-DependsOn-Bean覆盖#2.4 @DependsOn 隐式依赖编排]]
 * 🎯 作用：演示 {@code @DependsOn} 在「没有字段引用、容器无法从依赖图推断顺序」时强制初始化先后：
 *         ① 价格缓存预热 Bean 与字典 / 配置中心之间无注入关系，靠 {@code @DependsOn} 钉死「先字典配置、后预热」；
 *         ② 互相 {@code @DependsOn} 形成环 → {@code AbstractBeanFactory#doGetBean} 经 {@code isDependent} 检测，
 *            启动期抛 {@code BeanCreationException}（Circular depends-on）。
 * 🔗 业务场景：古茗商品中心启动时，价格缓存预热必须在「门店字典 + 营销配置中心」就绪后才能跑，
 *         否则预热出一批错价进缓存；这俩没有代码层引用关系，只能用 @DependsOn 显式编排顺序。
 */
public final class L0208_04_DependsOnDemo {

	/** 记录 Bean 的构造顺序（单线程启动期写入，仅作演示）。 */
	static final List<String> INIT_ORDER = new ArrayList<>();

	private L0208_04_DependsOnDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. @DependsOn 钉死初始化顺序（无字段引用也能编排） ====================");
		INIT_ORDER.clear();
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(WarmupConfig.class)) {
			System.out.println("实际构造顺序 = " + INIT_ORDER);
			System.out.println("说明：priceCacheWarmup 声明在最前，却因 @DependsOn 在 dictLoader / configCenter 之后才构造。");
			ctx.getBean(PriceCacheWarmup.class);
		}

		System.out.println();
		System.out.println("==================== 2. 互相 @DependsOn 成环：启动期 fail-fast ====================");
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CyclicConfig.class)) {
			System.out.println("不该走到这里：" + ctx);
		}
		catch (NestedRuntimeException ex) {
			System.out.println("环形 depends-on 被检测到 = " + ex.contains(BeanCreationException.class));
			System.out.println("  根因：" + rootMessage(ex));
		}
	}

	private static String rootMessage(Throwable ex) {
		Throwable cur = ex;
		while (cur.getCause() != null) {
			cur = cur.getCause();
		}
		return cur.getClass().getSimpleName() + ": " + cur.getMessage();
	}

	// ===================== 启动期基础设施 Bean =====================

	static class DictLoader {

		DictLoader() {
			INIT_ORDER.add("dictLoader(门店字典)");
		}
	}

	static class ConfigCenter {

		ConfigCenter() {
			INIT_ORDER.add("configCenter(营销配置)");
		}
	}

	static class PriceCacheWarmup {

		PriceCacheWarmup() {
			INIT_ORDER.add("priceCacheWarmup(价格预热)");
		}
	}

	@Configuration
	static class WarmupConfig {

		/**
		 * 价格预热与字典 / 配置中心没有任何字段引用，但语义上必须最后跑。
		 * @DependsOn 强制容器先初始化这两个 Bean。
		 */
		@Bean
		@DependsOn({"dictLoader", "configCenter"})
		PriceCacheWarmup priceCacheWarmup() {
			return new PriceCacheWarmup();
		}

		@Bean
		DictLoader dictLoader() {
			return new DictLoader();
		}

		@Bean
		ConfigCenter configCenter() {
			return new ConfigCenter();
		}
	}

	@Configuration
	static class CyclicConfig {

		@Bean
		@DependsOn("beanB")
		Object beanA() {
			return new Object();
		}

		@Bean
		@DependsOn("beanA")
		Object beanB() {
			return new Object();
		}
	}

}
