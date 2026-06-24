package com.leilei.lab.laboratory.l02.l02_07;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * 📖 知识点：[[L02-07-作用域-scope代理与按需注入#2.4 @Lookup 方法注入]]（@Lookup 解决「单例里要 prototype，每次拿新的」）
 * 🎯 作用：坐实「单例 Bean 字段注入 prototype，只会拿到唯一一份（注入那一刻定格）」的经典坑，
 *         以及 {@code @Lookup} 的修法——容器用 CGLIB 子类化 Bean、重写 @Lookup 方法体，
 *         每次调用都现去容器 {@code getBean} 拿一份全新 prototype（方法注入 / lookup-method）。
 *         判定依据：{@code AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors} 扫到 @Lookup 注册成
 *         {@code LookupOverride}，由 {@code CglibSubclassingInstantiationStrategy} 的 LookupOverrideMethodInterceptor 拦截。
 * 🔗 业务场景：单例的「库存扣减派发器」每收到一个扣减请求，要一份全新的、可变的「扣减任务」对象（持有本次 sku/门店/数量、
 *         可被并发安全地独立填充）；若用字段注入 prototype，全局共用一份任务对象 → 并发下相互覆盖。
 */
public final class L0207_04_LookupMethodInjectionDemo {

	private L0207_04_LookupMethodInjectionDemo() {
	}

	public static void main(String[] args) {
		// 按类注册：@Lookup 要求容器亲自实例化该 Bean（才能 CGLIB 子类化重写方法）；@Bean 工厂方法产物无法被重写。
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(StockDeductDispatcher.class, StockDeductTask.class);

		StockDeductDispatcher dispatcher = ctx.getBean(StockDeductDispatcher.class);

		System.out.println("==================== 反例：字段注入 prototype（只定格一份）====================");
		StockDeductTask f1 = dispatcher.fieldInjectedTask();
		StockDeductTask f2 = dispatcher.fieldInjectedTask();
		System.out.println("  两次拿到同一任务实例? " + (f1 == f2) + "（单例注入那一刻定格了一份 prototype，从此不变）");

		System.out.println();
		System.out.println("==================== 正例：@Lookup 方法注入（每次拿新的）====================");
		StockDeductTask l1 = dispatcher.newTask();
		StockDeductTask l2 = dispatcher.newTask();
		System.out.println("  两次拿到同一任务实例? " + (l1 == l2) + "（@Lookup 每次现去容器 getBean，拿全新 prototype）");

		System.out.println();
		System.out.println("  dispatcher 已被 CGLIB 子类化? "
				+ dispatcher.getClass().getName().contains("$$"));

		System.out.println();
		System.out.println("[小结] 单例里要「每次新的 prototype」，别用字段注入（只一份），用 @Lookup 方法注入（每次现拿）");

		ctx.close();
	}

	/**
	 * 单例库存扣减派发器：对比「字段注入 prototype（错）」与「@Lookup 方法注入（对）」。
	 */
	@Component
	static class StockDeductDispatcher {

		/** 反例：字段注入 prototype，单例创建时只注入一份，之后恒为同一实例。 */
		@Autowired
		private StockDeductTask injectedTask;

		StockDeductTask fieldInjectedTask() {
			return injectedTask;
		}

		/**
		 * 正例：@Lookup 方法注入。容器 CGLIB 子类化本类、重写此方法，每次调用都 getBean(StockDeductTask) 拿全新 prototype。
		 * 方法体可随便写（会被覆盖），返回类型即要查找的 Bean 类型。
		 */
		@Lookup
		StockDeductTask newTask() {
			throw new UnsupportedOperationException("方法体会被 CGLIB 覆盖，不会真正执行");
		}
	}

	/** 库存扣减任务：每次扣减请求要一份独立的、可变的任务对象，prototype。 */
	@Component
	@Scope("prototype")
	static class StockDeductTask {

		private long skuId;
		private long storeId;
		private int quantity;

		void setSkuId(long skuId) {
			this.skuId = skuId;
		}

		void setStoreId(long storeId) {
			this.storeId = storeId;
		}

		void setQuantity(int quantity) {
			this.quantity = quantity;
		}
	}
}
