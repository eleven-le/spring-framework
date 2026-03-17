package org.springframework.lab.processor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W05 - Processor 总览：BFPP vs BDRPP vs BPP 调试入口
 *
 * <h2>一句话抽象</h2>
 * Processor 三件套解决"容器可扩展性"问题：改定义(BDRPP/BFPP) + 改实例(BPP)，
 * 核心矛盾是"扩展能力越强 vs 执行时机越早，能看到的信息越少"。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: 完整流程 — 观察 BDRPP → BFPP → BPP 的执行顺序</li>
 *   <li>实验 2: 排序 — 观察 PriorityOrdered → Ordered → Plain 三批执行</li>
 *   <li>实验 3: Scope 修改验证 — 观察 BFPP 把 cartService 改成 prototype 后的效果</li>
 *   <li>实验 4: BPP 包装验证 — 观察 cartService 被 TimingBpp 包装后的计时输出</li>
 *   <li>实验 5: 陷阱 — 过早 getBean 导致 BPP 失效（取消注释运行）</li>
 * </ul>
 *
 * <h2>断点位置（3 个抓手）</h2>
 * <ol>
 *   <li>PostProcessorRegistrationDelegate:112 — invokeBeanDefinitionRegistryPostProcessors (BDRPP 入口)</li>
 *   <li>PostProcessorRegistrationDelegate:198 — invokeBeanFactoryPostProcessors 最后一批普通 BFPP</li>
 *   <li>AbstractAutowireCapableBeanFactory#applyBeanPostProcessorsAfterInitialization — BPP 包装入口</li>
 * </ol>
 *
 * <h2>口述调用链（10 步）</h2>
 * <pre>
 * 1. AbstractApplicationContext#refresh
 * 2.   → invokeBeanFactoryPostProcessors(beanFactory)                    委托给 Delegate
 * 3.     → PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors
 * 4.       → 第一轮：手动注册的 BDRPP.postProcessBeanDefinitionRegistry  硬编码的先跑
 * 5.       → 第二轮：容器中 PriorityOrdered 的 BDRPP (如 ConfigurationClassPostProcessor)
 * 6.       → 第三轮：容器中 Ordered 的 BDRPP
 * 7.       → 第四轮：while 循环兜底剩余 BDRPP（支持 BDRPP 注册新 BDRPP）
 * 8.       → 所有 BDRPP 的 postProcessBeanFactory 回调
 * 9.       → 按 PriorityOrdered/Ordered/普通 三批执行纯 BFPP
 * 10.  → registerBeanPostProcessors(beanFactory)                         只注册不调用
 *        （BPP 的回调在后续 getBean → initializeBean 时才真正触发）
 * </pre>
 */
public class ProcessorMain {

	public static void main(String[] args) {
		System.out.println("========================================");
		System.out.println("  W05 Processor 三件套 练兵场");
		System.out.println("========================================\n");

		// --- 实验 1~4: 正常流程 ---
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(ProcessorConfig.class);

		// 验证 BDRPP 动态注册的 AuditLogger
		System.out.println("\n--- 验证 Scene-1: BDRPP 动态注册 ---");
		AuditLogger audit = ctx.getBean(AuditLogger.class);
		audit.log("用户下单 #10086");

		// 验证 BFPP 把 cartService scope 改成了 prototype
		System.out.println("\n--- 验证 Scene-2: BFPP 修改 scope ---");
		CartService cart1 = ctx.getBean(CartService.class);
		CartService cart2 = ctx.getBean(CartService.class);
		System.out.println("cart1: " + cart1.addItem("iPhone"));
		System.out.println("cart2: " + cart2.addItem("MacBook"));
		System.out.println("cart1 == cart2 ? " + (cart1 == cart2) + " (prototype 应为 false)");

		// 验证 BPP 包装后的计时输出
		System.out.println("\n--- 验证 Scene-3: BPP 计时包装 ---");
		System.out.println("cart1 实际类型: " + cart1.getClass().getSimpleName());

		ctx.close();

		// --- 实验 5: 陷阱演示（取消下面注释即可运行） ---
		// pitfallDemo();
	}

	/**
	 * 实验 5: 过早 getBean 陷阱
	 * 取消 main 方法中的注释即可观察
	 */
	@SuppressWarnings("unused")
	private static void pitfallDemo() {
		System.out.println("\n========================================");
		System.out.println("  实验 5: 过早 getBean 陷阱");
		System.out.println("========================================\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(ProcessorConfig.class);
		// 手动添加陷阱 BFPP
		ctx.addBeanFactoryPostProcessor(
				new org.springframework.lab.processor.scene5_pitfall.EarlyGetBeanBfpp());
		ctx.refresh();

		CartService cart = ctx.getBean(CartService.class);
		System.out.println("cart 类型: " + cart.getClass().getSimpleName());
		System.out.println("↑ 如果是 CartService 而非 CartServiceTimingWrapper，说明 BPP 包装被跳过了");

		ctx.close();
	}
}
