package com.leilei.lab.laboratory.l03.l03_02;

import java.util.concurrent.atomic.AtomicLong;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 📖 知识点：[[L03-02-Aspect切面工程化#4. 🧠 设计思想]]（引介增强 @DeclareParents 🧊）
 * 🎯 作用：演示「引介/混入（Introduction / Mixin）」——不改一行业务代码、不动继承结构，
 *         用 {@link DeclareParents @DeclareParents} 给已有的 {@code ProductCommandService}
 *         凭空「长出」一个全新接口 {@link SaleMonitor} 及其默认实现。
 *         代理对象因此同时 {@code instanceof} 业务类型与被引介接口；每个目标 Bean 各自持有一份
 *         默认实现实例（Spring 走 {@code DelegatePerTargetObjectIntroductionInterceptor} / {@code DeclareParentsAdvisor}）。
 *         与前几种「方法级通知（增强行为）」不同，引介是「类型级增强（增强能力/状态）」——给对象加新接口。
 * 🔗 业务场景：要给一批老的商品写服务统一加「销量计数 / 健康探针」能力，又不想侵入每个 service 改签名、
 *         加字段——@DeclareParents 把这份横切「状态能力」混入进去，监控面板按 {@code instanceof SaleMonitor}
 *         统一采集。属冷门但面试爱问的「AOP 不只是拦方法，还能改类型」考点。
 */
public final class L0302_04_DeclareParentsIntroductionDemo {

	private L0302_04_DeclareParentsIntroductionDemo() {
	}

	/** 被引介的新接口：销量计数能力（业务类原本完全不知道它的存在）。 */
	public interface SaleMonitor {
		long incrAndGet();

		long current();
	}

	/** 引介接口的默认实现：每个被增强的目标 Bean 各持有一份，状态互相隔离。 */
	public static class DefaultSaleMonitor implements SaleMonitor {
		private final AtomicLong sales = new AtomicLong();

		@Override
		public long incrAndGet() {
			return sales.incrementAndGet();
		}

		@Override
		public long current() {
			return sales.get();
		}
	}

	/** 老的商品写服务：只有纯业务方法，不实现 SaleMonitor，也不想为监控改它。 */
	public static class ProductCommandService {
		String createSku(long skuId) {
			return "CREATED:" + skuId;
		}
	}

	/** 引介切面：value 选中要增强的目标类型，defaultImpl 指定混入实现。 */
	@Aspect
	static class SaleMonitorIntroductionAspect {
		// 把 SaleMonitor 接口「引介」到所有 ProductCommandService（及子类，+ 号）上，
		// 默认实现 DefaultSaleMonitor —— 等价于让目标对象凭空实现了一个新接口。
		@DeclareParents(value = "com.leilei.lab.laboratory.l03.l03_02..ProductCommandService+",
				defaultImpl = DefaultSaleMonitor.class)
		private SaleMonitor saleMonitor;
	}

	@Configuration
	@EnableAspectJAutoProxy(proxyTargetClass = true)   // 目标无接口又要保留 instanceof 业务类，强制 CGLIB
	static class AopConfig {
		@Bean
		ProductCommandService productCommandService() {
			return new ProductCommandService();
		}

		@Bean
		SaleMonitorIntroductionAspect saleMonitorIntroductionAspect() {
			return new SaleMonitorIntroductionAspect();
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			ProductCommandService service = ctx.getBean(ProductCommandService.class);

			System.out.println("==================== @DeclareParents 引介增强（类型级混入）====================");
			System.out.println("业务调用 createSku = " + service.createSku(10000100L));
			System.out.println("代理 instanceof ProductCommandService = " + (service instanceof ProductCommandService)
					+ "（原业务类型仍在）");
			System.out.println("代理 instanceof SaleMonitor            = " + (service instanceof SaleMonitor)
					+ "（凭空长出的新接口）");

			// 把代理当成新接口用：业务类压根没实现这个接口，能力完全由引介注入
			SaleMonitor monitor = (SaleMonitor) service;
			monitor.incrAndGet();
			monitor.incrAndGet();
			System.out.println("通过引介接口累加销量 current = " + monitor.current()
					+ "（每个目标 Bean 独享一份 DefaultSaleMonitor 状态）");
			System.out.println();
			System.out.println("结论：通知（@Around 等）是「方法级·增强行为」；引介 @DeclareParents 是「类型级·增强能力」——");
			System.out.println("      不改业务代码就给一批对象统一加新接口/新状态，是 AOP 横切能力的另一维度。");
		}
	}
}
