package com.leilei.lab.laboratory.l02.l02_08;

import javax.annotation.Priority;

import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.NestedRuntimeException;

/**
 * 📖 知识点：[[L02-08-歧义裁决与容器语义-Primary-Qualifier-DependsOn-Bean覆盖#2.1 歧义裁决的优先级链]]
 *         （@Primary / @Qualifier / @Priority 的裁决顺序）
 * 🎯 作用：把 {@code DefaultListableBeanFactory#determineAutowireCandidate} 的裁决优先级链
 *         「@Qualifier 收窄候选 → @Primary 全局默认 → @javax.annotation.Priority 兜底 → 按名字」用一族库存扣减策略跑出来：
 *         ① 裸 {@code @Autowired} 多候选 → {@code NoUniqueBeanDefinitionException}；
 *         ② {@code @Qualifier} 精确点名；
 *         ③ 无限定符时命中 {@code @Primary} 全局默认；
 *         ④ {@code @Qualifier} 与 {@code @Primary} 同在时，{@code @Qualifier} 胜出（注入点级 &gt; Bean 级）；
 *         ⑤ 无 Primary、无 Qualifier 时，{@code @Priority}（值小者优先）兜底裁决；
 *         ⑥ 两个本地 {@code @Primary} → {@code NoUniqueBeanDefinitionException}（more than one 'primary'）。
 * 🔗 业务场景：古茗下单链路的库存扣减策略族——普通扣减 / 秒杀预扣 / 区域仓扣减并存，
 *         不同入口该走哪套必须显式可裁决，裁错策略轻则超卖、重则把秒杀流量打穿常规库存。
 */
public final class L0208_01_StrategyResolutionDemo {

	private L0208_01_StrategyResolutionDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 裸 @Autowired 按类型：三候选 → 歧义报错 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(StrategyConfig.class, AmbiguousConfig.class)) {
			System.out.println("不该走到这里：" + ctx);
		}
		catch (NestedRuntimeException ex) {
			System.out.println("裸 @Autowired 多候选无法裁决 = " + ex.contains(NoUniqueBeanDefinitionException.class));
			System.out.println("  根因：" + rootMessage(ex));
		}

		System.out.println();
		System.out.println("==================== 2. @Qualifier 精确点名（注入点级裁决） ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(StrategyConfig.class, QualifierConfig.class)) {
			QualifierConsumer c = ctx.getBean(QualifierConsumer.class);
			System.out.println("@Qualifier(\"seckillDeduct\") 命中 = " + c.strategy.name());
		}

		System.out.println();
		System.out.println("==================== 3. @Primary 全局默认（无限定符时生效） ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(PrimaryStrategyConfig.class, PrimaryConfig.class)) {
			PrimaryConsumer c = ctx.getBean(PrimaryConsumer.class);
			System.out.println("无 @Qualifier → 命中 @Primary 的 = " + c.strategy.name());
		}

		System.out.println();
		System.out.println("==================== 4. @Qualifier vs @Primary：注入点级胜过 Bean 级 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(PrimaryStrategyConfig.class, QualifierBeatsPrimaryConfig.class)) {
			QualifierBeatsPrimaryConsumer c = ctx.getBean(QualifierBeatsPrimaryConsumer.class);
			System.out.println("容器里 standardDeduct 是 @Primary，但注入点写了 @Qualifier(\"seckillDeduct\")");
			System.out.println("  最终命中 = " + c.strategy.name() + "（@Qualifier 胜出，@Primary 被覆盖）");
		}

		System.out.println();
		System.out.println("==================== 5. 无 Primary 无 Qualifier：@Priority（值小者优先）兜底 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(PriorityStrategyConfig.class, PriorityConfig.class)) {
			PriorityConsumer c = ctx.getBean(PriorityConsumer.class);
			System.out.println("seckill=@Priority(1) / standard=@Priority(2) / area=@Priority(3)");
			System.out.println("  值小者优先 → 命中 = " + c.strategy.name());
		}

		System.out.println();
		System.out.println("==================== 6. 两个本地 @Primary：裁决失败（more than one primary） ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(TwoPrimaryConfig.class, PrimaryConfig.class)) {
			System.out.println("不该走到这里：" + ctx);
		}
		catch (NestedRuntimeException ex) {
			System.out.println("两个 @Primary 互相打架 = " + ex.contains(NoUniqueBeanDefinitionException.class));
			System.out.println("  根因：" + rootMessage(ex));
		}
	}

	private static String rootMessage(Throwable ex) {
		Throwable cur = ex;
		while (cur.getCause() != null) {
			cur = cur.getCause();
		}
		String msg = cur.getMessage();
		if (msg != null && msg.length() > 140) {
			msg = msg.substring(0, 140) + "...";
		}
		return cur.getClass().getSimpleName() + ": " + msg;
	}

	// ===================== 库存扣减策略族：一个接口、三个实现 =====================

	/** 库存扣减策略：返回策略名即可，重在演示裁决而非扣减细节。 */
	interface InventoryDeductStrategy {

		String name();
	}

	static class StandardDeduct implements InventoryDeductStrategy {

		@Override
		public String name() {
			return "普通扣减(行锁)";
		}
	}

	static class SeckillDeduct implements InventoryDeductStrategy {

		@Override
		public String name() {
			return "秒杀预扣(Redis 原子)";
		}
	}

	static class AreaWarehouseDeduct implements InventoryDeductStrategy {

		@Override
		public String name() {
			return "区域仓扣减(就近路由)";
		}
	}

	// @Priority 的 @Target 仅 TYPE，只能标在类上（不能标 @Bean 方法），故用带注解的子类承载优先级。
	@Priority(2)
	static class PriorityStandardDeduct extends StandardDeduct {
	}

	@Priority(1)
	static class PrioritySeckillDeduct extends SeckillDeduct {
	}

	@Priority(3)
	static class PriorityAreaWarehouseDeduct extends AreaWarehouseDeduct {
	}

	/** 三个策略以确定 Bean 名注册，均无 @Primary。 */
	@Configuration
	static class StrategyConfig {

		@Bean
		InventoryDeductStrategy standardDeduct() {
			return new StandardDeduct();
		}

		@Bean
		InventoryDeductStrategy seckillDeduct() {
			return new SeckillDeduct();
		}

		@Bean
		InventoryDeductStrategy areaWarehouseDeduct() {
			return new AreaWarehouseDeduct();
		}
	}

	/** 同上三策略，但 standardDeduct 标 @Primary，作为全局默认。 */
	@Configuration
	static class PrimaryStrategyConfig {

		@Bean
		@Primary
		InventoryDeductStrategy standardDeduct() {
			return new StandardDeduct();
		}

		@Bean
		InventoryDeductStrategy seckillDeduct() {
			return new SeckillDeduct();
		}

		@Bean
		InventoryDeductStrategy areaWarehouseDeduct() {
			return new AreaWarehouseDeduct();
		}
	}

	/** 两个 @Primary：故意制造「多个 primary」裁决失败。 */
	@Configuration
	static class TwoPrimaryConfig {

		@Bean
		@Primary
		InventoryDeductStrategy standardDeduct() {
			return new StandardDeduct();
		}

		@Bean
		@Primary
		InventoryDeductStrategy seckillDeduct() {
			return new SeckillDeduct();
		}
	}

	/** 三策略用 @Priority 标定优先级（值小者优先），不靠 @Primary。 */
	@Configuration
	static class PriorityStrategyConfig {

		@Bean
		InventoryDeductStrategy standardDeduct() {
			return new PriorityStandardDeduct();
		}

		@Bean
		InventoryDeductStrategy seckillDeduct() {
			return new PrioritySeckillDeduct();
		}

		@Bean
		InventoryDeductStrategy areaWarehouseDeduct() {
			return new PriorityAreaWarehouseDeduct();
		}
	}

	// ===================== 各场景的消费者 =====================

	@Configuration
	static class AmbiguousConfig {

		@Bean
		AmbiguousConsumer ambiguousConsumer() {
			return new AmbiguousConsumer();
		}
	}

	static class AmbiguousConsumer {

		@Autowired
		InventoryDeductStrategy strategy;
	}

	@Configuration
	static class QualifierConfig {

		@Bean
		QualifierConsumer qualifierConsumer() {
			return new QualifierConsumer();
		}
	}

	static class QualifierConsumer {

		@Autowired
		@Qualifier("seckillDeduct")
		InventoryDeductStrategy strategy;
	}

	@Configuration
	static class PrimaryConfig {

		@Bean
		PrimaryConsumer primaryConsumer() {
			return new PrimaryConsumer();
		}
	}

	static class PrimaryConsumer {

		@Autowired
		InventoryDeductStrategy strategy;
	}

	@Configuration
	static class QualifierBeatsPrimaryConfig {

		@Bean
		QualifierBeatsPrimaryConsumer qualifierBeatsPrimaryConsumer() {
			return new QualifierBeatsPrimaryConsumer();
		}
	}

	static class QualifierBeatsPrimaryConsumer {

		@Autowired
		@Qualifier("seckillDeduct")
		InventoryDeductStrategy strategy;
	}

	@Configuration
	static class PriorityConfig {

		@Bean
		PriorityConsumer priorityConsumer() {
			return new PriorityConsumer();
		}
	}

	static class PriorityConsumer {

		@Autowired
		InventoryDeductStrategy strategy;
	}

}
