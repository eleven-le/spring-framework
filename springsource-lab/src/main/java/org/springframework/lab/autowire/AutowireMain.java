package org.springframework.lab.autowire;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W12 依赖注入解析 · 练兵场入口
 *
 * 断点抓手:
 * 1. DefaultListableBeanFactory#doResolveDependency  → 注入决策主分支
 * 2. QualifierAnnotationAutowireCandidateResolver#isAutowireCandidate → @Qualifier 过滤
 * 3. DefaultListableBeanFactory#determineAutowireCandidate → @Primary/@Priority/字段名降级
 * 4. DependencyObjectProvider#getIfAvailable → ObjectProvider 可选注入
 *
 * 观察要点:
 * - DependencyDescriptor 中的 field/methodParameter/required/eager/annotations
 * - findAutowireCandidates 返回的 Map 里有几个候选人
 * - determineAutowireCandidate 走了 Primary 还是字段名匹配
 */
public class AutowireMain {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AutowireConfig.class);

		OrderService orderService = ctx.getBean(OrderService.class);

		System.out.println("====== W12 依赖注入解析: autowire / @Qualifier / ObjectProvider ======\n");

		// 场景1: @Qualifier 精确路由
		orderService.payViaAlipay("ORD-001");

		System.out.println();

		// 场景2: @Primary 默认路由
		orderService.payViaDefault("ORD-002");

		System.out.println();

		// 场景3: ObjectProvider 可选注入
		orderService.evaluateRisk("ORD-003");

		System.out.println();

		// 场景4: List 多候选收集
		orderService.listAllChannels();

		System.out.println();

		// 场景5: Map 多候选收集
		orderService.showChannelMap();

		System.out.println();

		// 场景6: ObjectProvider stream
		orderService.streamChannels();

		System.out.println("\n====== 场景7: 手动 resolveDependency 观察 DependencyDescriptor ======");
		DependencyDescriptorInspector.inspect(ctx);

		ctx.close();
	}
}
