package org.springframework.lab.lifecycle;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W09 - Bean 生命周期全景：回调顺序 + 扩展点对照表
 *
 * <h2>一句话抽象</h2>
 * Bean 生命周期解决"对象从无到有再到无的每一步都可插手"的问题：
 * 17 个回调点沿 实例化→属性注入→初始化→使用→销毁 五阶段展开，
 * 核心矛盾是"扩展点覆盖全 vs 顺序心智负担重 + 接口/注解/XML 三套并存"。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: FullLifecycleBean — 实现所有 Aware + 初始化/销毁三连, 验证回调精确顺序</li>
 *   <li>实验 2: LifecycleObserverBpp — IABPP 五阶段观察器, 拦截实例化→初始化全过程</li>
 *   <li>实验 3: SmartInitBean — afterSingletonsInstantiated, 全局单例就绪后回调</li>
 *   <li>实验 4: SmartLifecycleBean — 容器级启停, finishRefresh/doClose 阶段</li>
 * </ul>
 *
 * <h2>预期输出顺序 (fullLifecycleBean)</h2>
 * <pre>
 * ┌─ 实例化阶段 ──────────────────────────────────────────────────┐
 * │ IABPP.postProcessBeforeInstantiation  → resolveBeforeInstantiation │
 * │ 构造器                                 → createBeanInstance         │
 * │ IABPP.postProcessAfterInstantiation   → populateBean 开头          │
 * │ IABPP.postProcessProperties           → populateBean 中段          │
 * ├─ 初始化阶段 ──────────────────────────────────────────────────┤
 * │ BeanNameAware / BeanClassLoaderAware / BeanFactoryAware           │  ← invokeAwareMethods
 * │ EnvironmentAware → ... → ApplicationContextAware                  │  ← ApplicationContextAwareProcessor (BPP.before)
 * │ BPP.postProcessBeforeInitialization (其他 BPP)                     │
 * │ @PostConstruct                                                     │  ← CommonAnnotationBPP (也是 BPP.before)
 * │ InitializingBean.afterPropertiesSet                                │  ← invokeInitMethods
 * │ @Bean(initMethod)                                                  │  ← invokeCustomInitMethod
 * │ BPP.postProcessAfterInitialization                                 │  ← AOP 代理在此生成
 * ├─ 就绪阶段 ────────────────────────────────────────────────────┤
 * │ SmartInitializingSingleton.afterSingletonsInstantiated             │  ← 全部单例创建完毕
 * │ SmartLifecycle.start                                               │  ← finishRefresh
 * ├─ 销毁阶段 ────────────────────────────────────────────────────┤
 * │ SmartLifecycle.stop                                                │  ← doClose
 * │ @PreDestroy                                                        │  ← DestructionAwareBPP
 * │ DisposableBean.destroy                                             │
 * │ @Bean(destroyMethod)                                               │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>断点位置（3 个抓手）</h2>
 * <ol>
 *   <li>AbstractAutowireCapableBeanFactory#initializeBean:1783 — Aware→BPP.before→invokeInitMethods→BPP.after 四步编排</li>
 *   <li>InitDestroyAnnotationBeanPostProcessor#postProcessBeforeInitialization:154 — @PostConstruct 调用入口</li>
 *   <li>DisposableBeanAdapter#destroy:194 — @PreDestroy→DisposableBean→customDestroy 三步销毁链</li>
 * </ol>
 */
public class LifecycleMain {

	public static void main(String[] args) {
		System.out.println("==========================================================");
		System.out.println("  W09 Bean 生命周期全景: 回调顺序 + 扩展点对照表");
		System.out.println("==========================================================\n");

		// --- 实验 1 & 2: 全生命周期 + IABPP 观察 ---
		System.out.println("--- 实验 1 & 2: FullLifecycleBean + IABPP Observer ---\n");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(LifecycleConfig.class);

		// --- 实验 3: SmartInitializingSingleton 已在容器启动中触发 ---

		// --- 实验 4: SmartLifecycle 已在 finishRefresh 中自动 start ---

		System.out.println("\n--- 使用阶段 ---");
		FullLifecycleBean bean = ctx.getBean(FullLifecycleBean.class);
		System.out.println("业务调用: " + bean.sayHello());

		System.out.println("\n--- 销毁阶段 (ctx.close) ---\n");
		ctx.close();

		System.out.println("\n=== 完毕 ===");
	}
}
