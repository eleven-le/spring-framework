package org.springframework.lab.createbean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W08 - createBean 主链：实例化→注入→初始化 调试入口
 *
 * <h2>一句话抽象</h2>
 * createBean 解决"把 BeanDefinition 变成可用对象"的问题：
 * 一条模板方法链 createBeanInstance→populateBean→initializeBean 把构造、注入、回调三件事串成流水线，
 * 核心矛盾是"扩展点越多、生命周期越灵活 vs 回调顺序越难预测、循环依赖越难处理"。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: 全生命周期回调顺序 — 构造→Aware→@PostConstruct→afterPropertiesSet→initMethod→BPP.after</li>
 *   <li>实验 2: BPP before/after 触发时机 — 观察 LifecycleTrackerBpp 输出</li>
 *   <li>实验 3: IABPP 五个回调 — beforeInstantiation/afterInstantiation/postProcessProperties</li>
 *   <li>实验 4: 循环依赖三级缓存 — CircularA↔CircularB setter 注入循环</li>
 *   <li>实验 5: 销毁回调顺序 — @PreDestroy→DisposableBean→destroyMethod</li>
 * </ul>
 *
 * <h2>断点位置（3 个抓手）</h2>
 * <ol>
 *   <li>AbstractAutowireCapableBeanFactory#doCreateBean:573 — 总入口, 观察 createBeanInstance→populate→initialize 三步</li>
 *   <li>AbstractAutowireCapableBeanFactory#populateBean:1398 — IABPP.afterInstantiation + postProcessProperties(@Autowired介入)</li>
 *   <li>AbstractAutowireCapableBeanFactory#initializeBean:1783 — Aware→BPP.before(@PostConstruct)→invokeInitMethods→BPP.after</li>
 * </ol>
 *
 * <h2>口述调用链（10 步）</h2>
 * <pre>
 * 1. AbstractBeanFactory#doGetBean          → getSingleton 拿不到, 触发创建
 * 2. AbstractAutowireCapableBeanFactory#createBean     → resolveBeforeInstantiation(IABPP 短路点)
 * 3. #doCreateBean                          → 总编排: 实例化→注入→初始化
 * 4.   → createBeanInstance                 → 反射调构造器(SmartIABPP 可推荐构造器)
 * 5.   → applyMergedBeanDefinitionPostProcessors → 扫描 @Autowired/@PostConstruct 元数据缓存
 * 6.   → addSingletonFactory               → 三级缓存放入 lambda(循环依赖预备)
 * 7.   → populateBean                      → IABPP.afterInstantiation → @Autowired 注入 → applyPropertyValues
 * 8.   → initializeBean                    → invokeAwareMethods(BeanName/BeanFactory)
 * 9.     → BPP.before(@PostConstruct) → afterPropertiesSet + initMethod → BPP.after(AOP 代理)
 * 10.  → registerDisposableBeanIfNecessary  → 注册 destroy 回调(close 时触发)
 * </pre>
 */
public class CreateBeanMain {

	public static void main(String[] args) {
		System.out.println("========================================");
		System.out.println("  W08 createBean 主链 练兵场");
		System.out.println("========================================\n");

		// --- 实验 1~3: 全生命周期 + BPP + IABPP ---
		System.out.println("--- 实验 1~3: createBean 全生命周期回调 ---");
		System.out.println("预期顺序: 构造→Aware→@PostConstruct→afterPropertiesSet→initMethod→BPP.after\n");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(CreateBeanConfig.class);

		// 验证 bean 正常可用
		System.out.println("\n--- 验证: 业务方法调用 ---");
		OrderService order = ctx.getBean(OrderService.class);
		System.out.println(order.placeOrder("iPhone-16"));

		// --- 实验 4: 循环依赖 ---
		System.out.println("\n--- 实验 4: 循环依赖三级缓存 ---");
		CircularA a = ctx.getBean(CircularA.class);
		CircularB b = ctx.getBean(CircularB.class);
		System.out.println(a.whoAmI());
		System.out.println(b.whoAmI());

		// --- 实验 5: 销毁回调顺序 ---
		System.out.println("\n--- 实验 5: 销毁回调顺序 ---");
		ctx.close();
	}
}
