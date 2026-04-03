package org.springframework.lab.bfvsac;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W02 - BeanFactory vs ApplicationContext：边界与角色
 *
 * <h3>实验设计</h3>
 * <pre>
 * 场景一：裸 BeanFactory（DefaultListableBeanFactory）
 *   → 手动注册 BD，手动 getBean
 *   → 观察：BeanFactory 级 Aware 能回调，Context 级 Aware 全部为 null
 *   → 观察：事件发布不可用，资源加载不可用
 *
 * 场景二：ApplicationContext（AnnotationConfigApplicationContext）
 *   → 自动扫描，refresh() 全流程
 *   → 观察：所有 Aware 全部注入，事件发布正常，资源加载正常
 *
 * 场景三：从 ApplicationContext 拿出内部 BeanFactory
 *   → 证明 ApplicationContext 是"组合持有"而非"继承"BeanFactory
 *   → ctx.getBeanFactory() 拿到的就是 DefaultListableBeanFactory
 * </pre>
 *
 * <h3>核心结论</h3>
 * <pre>
 * BeanFactory = 纯粹的 Bean 仓库（存取 Bean + 生命周期回调）
 * ApplicationContext = BeanFactory + 事件 + 资源 + 环境 + 国际化 + Aware 桥接
 * 桥接枢纽 = prepareBeanFactory() 方法
 * </pre>
 */
public class BfVsAcMain {

	public static void main(String[] args) {
		System.out.println("╔══════════════════════════════════════════════════════════╗");
		System.out.println("║   W02 BeanFactory vs ApplicationContext 边界对比实验      ║");
		System.out.println("╚══════════════════════════════════════════════════════════╝");

		// ===================== 场景一：裸 BeanFactory =====================
		System.out.println("\n━━━━━━━━━━ 场景一：裸 BeanFactory ━━━━━━━━━━");
		System.out.println("使用 DefaultListableBeanFactory，手动注册 BeanDefinition\n");
		bareBeanFactory();

		// ===================== 场景二：ApplicationContext =====================
		System.out.println("\n\n━━━━━━━━━━ 场景二：ApplicationContext ━━━━━━━━━━");
		System.out.println("使用 AnnotationConfigApplicationContext，走完整 refresh()\n");
		applicationContext();

		// ===================== 场景三：组合持有关系验证 =====================
		System.out.println("\n\n━━━━━━━━━━ 场景三：组合持有验证 ━━━━━━━━━━");
		compositionProof();
	}

	/**
	 * 场景一：裸 BeanFactory
	 *
	 * <p>断点打在 AbstractAutowireCapableBeanFactory#invokeAwareMethods
	 * 可观察到只有 BeanNameAware / BeanClassLoaderAware / BeanFactoryAware 被调用
	 */
	private static void bareBeanFactory() {
		// 1. 创建裸工厂
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

		// 2. 手动注册 BeanDefinition（没有 @ComponentScan 的扫描能力）
		BeanDefinition bd = new RootBeanDefinition(OrderService.class);
		factory.registerBeanDefinition("orderService", bd);

		// 3. getBean 触发实例化
		System.out.println("[创建 OrderService]");
		OrderService orderService = factory.getBean("orderService", OrderService.class);

		// 4. 查看 Aware 注入状态
		System.out.println("\n[Aware 注入状态]");
		orderService.printAwareStatus();

		// 5. 尝试发布事件 —— 失败
		System.out.println("\n[尝试发布事件]");
		orderService.placeOrder("BF-001");

		// 6. 尝试加载资源 —— 失败
		System.out.println("\n[尝试加载资源]");
		orderService.loadResource("classpath:lab-cachetx-schema.sql");

		factory.destroySingletons();
	}

	/**
	 * 场景二：ApplicationContext
	 *
	 * <p>断点打在 AbstractApplicationContext#prepareBeanFactory 第 804 行
	 * 可观察到 ApplicationContextAwareProcessor 被注册为 BPP
	 */
	private static void applicationContext() {
		// 断点: AnnotationConfigApplicationContext 构造器 → refresh()
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(BfVsAcConfig.class);

		OrderService orderService = ctx.getBean(OrderService.class);

		// 查看 Aware 注入状态 —— 全部注入
		System.out.println("\n[Aware 注入状态]");
		orderService.printAwareStatus();

		// 发布事件 —— 成功，两个监听器都能收到
		System.out.println("\n[发布事件]");
		orderService.placeOrder("AC-001");

		// 加载资源 —— 成功
		System.out.println("\n[加载资源]");
		orderService.loadResource("classpath:lab-cachetx-schema.sql");

		ctx.close();
	}

	/**
	 * 场景三：证明 ApplicationContext 是"组合持有"BeanFactory
	 *
	 * <p>ctx.getBeanFactory() 返回的就是 DefaultListableBeanFactory 实例。
	 * ApplicationContext 自己并不是 BeanFactory 的子类实现，
	 * 而是通过 AbstractRefreshableApplicationContext/GenericApplicationContext
	 * 内部持有一个 DefaultListableBeanFactory 字段，所有 getBean() 都委托给它。
	 */
	private static void compositionProof() {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(BfVsAcConfig.class);

		// 拿出内部 BeanFactory
		DefaultListableBeanFactory internalFactory =
				(DefaultListableBeanFactory) ctx.getBeanFactory();

		System.out.println("ctx 类型                     → " + ctx.getClass().getSimpleName());
		System.out.println("ctx.getBeanFactory() 类型    → " + internalFactory.getClass().getSimpleName());
		//System.out.println("ctx == ctx.getBeanFactory()  → " + (ctx == internalFactory));
		System.out.println("两者是否同一个 BeanFactory?   → 不是！ctx 组合持有 internalFactory");

		// ctx.getBean() 和 internalFactory.getBean() 拿到同一个实例
		OrderService fromCtx = ctx.getBean(OrderService.class);
		OrderService fromFactory = internalFactory.getBean(OrderService.class);
		System.out.println("ctx.getBean() == factory.getBean() → " + (fromCtx == fromFactory) + " (同一个单例)");

		// 注入 @Autowired BeanFactory 时拿到的是 internalFactory 而非 ctx
		// 这就是 prepareBeanFactory 中 registerResolvableDependency 的作用

		System.out.println("\n[关键结论]");
		System.out.println("ApplicationContext 通过 prepareBeanFactory() 把自己注册为");
		System.out.println("ResourceLoader / ApplicationEventPublisher / ApplicationContext 的可解析依赖，");
		System.out.println("而 BeanFactory.class 的可解析依赖指向内部的 DefaultListableBeanFactory。");
		System.out.println("这就是为什么 @Autowired BeanFactory 拿到的不是 ApplicationContext！");

		ctx.close();
	}
}
