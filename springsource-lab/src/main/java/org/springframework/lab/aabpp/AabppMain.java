package org.springframework.lab.aabpp;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W30 AutowiredAnnotationBeanPostProcessor 注入落点与常见坑 · 练兵场入口
 *
 * ═══════════════════════════════════════════════════════════════
 * 核心抽象:
 *   AABPP 解决的矛盾是 "声明式注入的灵活性" vs "元数据扫描的性能" —
 *   它用两阶段扫描(postProcessMergedBeanDefinition 预扫+缓存,
 *   postProcessProperties 真注入)将 O(n) 反射扫描降为 O(1) 缓存命中,
 *   同时通过 AutowiredFieldElement/AutowiredMethodElement 两种 InjectedElement
 *   统一了字段注入和方法注入的多态落点。
 * ═══════════════════════════════════════════════════════════════
 *
 * 断点抓手:
 * 1. AutowiredAnnotationBeanPostProcessor#postProcessProperties
 *    → 所有 @Autowired/@Value 注入的总入口, 从这里 step into 看全流程
 *
 * 2. AutowiredAnnotationBeanPostProcessor#buildAutowiringMetadata
 *    → 看 AABPP 如何一层层扫描字段/方法, 如何跳过 static, 如何递归父类
 *
 * 3. AutowiredFieldElement#resolveFieldValue (内部类)
 *    → 字段注入的真正解析点, 看 DependencyDescriptor 创建 + 缓存 ShortcutDependencyDescriptor
 *
 * 4. AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors
 *    → 构造器选择逻辑, 看 required=true 唯一性校验
 *
 * 5. ContextAnnotationAutowireCandidateResolver#buildLazyResolutionProxy
 *    → @Lazy 注入时代理创建的起点
 */
public class AabppMain {

	public static void main(String[] args) {
		System.out.println("====== W30 AutowiredAnnotationBeanPostProcessor 注入落点与常见坑 ======\n");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AabppConfig.class);

		NotificationService ns = ctx.getBean(NotificationService.class);
		CtorInjectionDemo ctorDemo = ctx.getBean(CtorInjectionDemo.class);
		ChildService childService = ctx.getBean(ChildService.class);

		System.out.println("\n--- 4 大注入落点 ---\n");

		// 落点1: 字段注入
		ns.demoPrimaryFieldInjection();
		System.out.println();

		// 落点2: 方法注入(setter)
		ns.demoSetterInjection();
		System.out.println();

		// 落点3: 构造器注入
		ctorDemo.demo();
		System.out.println();

		// 落点4: @Value 占位符 + SpEL
		ns.demoValueInjection();

		System.out.println("\n--- 6 大常见坑 ---\n");

		// 坑1: static 字段静默跳过
		ns.demoStaticFieldPitfall();
		System.out.println();

		// 坑2: required=false / @Nullable (NotificationService 中 optionalSender 不报错)
		System.out.println("[坑2-required=false] optionalSender 不存在也不报错, 值为null (安全降级)");
		System.out.println();

		// 坑3: @Lazy 代理
		ns.demoLazyProxy();
		System.out.println();

		// 坑4: 集合注入走 resolveMultipleBeans 而非 findBean
		ns.demoCollectionInjection();
		System.out.println();

		// 坑5: Prototype 注入 Singleton → stale reference
		ns.demoPrototypeStalePitfall();
		System.out.println();

		// 坑6: 父类 private 字段也会被注入
		childService.demo();

		// 元数据审查: 直观展示 AABPP 缓存的注入落点
		System.out.println();
		MetadataInspector.inspect(ctx);

		ctx.close();
	}
}
