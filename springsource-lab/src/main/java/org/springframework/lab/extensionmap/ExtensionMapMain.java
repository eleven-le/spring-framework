package org.springframework.lab.extensionmap;

import java.util.Collections;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 扩展点全景地图 —— 总入口
 * <p>
 * 实验 1: 完整时间线 — 从容器启动 → 方法调用 → 容器关闭, 展示全部扩展点触发顺序
 * 实验 2: IABPP 短路 — 展示 postProcessBeforeInstantiation 返回非 null 可跳过 createBean
 *
 * <pre>
 * 三阶段扩展点全景:
 *
 * 定义期 (refresh 的 invokeBeanFactoryPostProcessors):
 *   ImportSelector → Registrar → BDRPP → BFPP
 *
 * 实例期 (refresh 的 finishBeanFactoryInitialization → getBean → createBean):
 *   IABPP.beforeInstantiation → Constructor → MergedBDPP → IABPP.afterInstantiation
 *   → IABPP.postProcessProperties → invokeAwareMethods(BeanName/BeanFactory)
 *   → BPP.before(含 ApplicationContextAware + @PostConstruct)
 *   → InitializingBean → init-method → BPP.after(含 AOP 代理)
 *   → SmartInitializingSingleton
 *
 * 运行期 (业务方法调用):
 *   AOP MethodInterceptor 拦截链 → 业务逻辑 → Event 发布/监听
 *
 * 销毁期 (context.close):
 *   @PreDestroy → DisposableBean → destroy-method
 * </pre>
 */
public class ExtensionMapMain {

	public static void main(String[] args) {
		experiment1_fullTimeline();
		System.out.println("\n\n");
		experiment2_iabppShortCircuit();
	}

	/**
	 * 实验 1: 完整时间线
	 * 展示一个 Bean 从定义→实例→运行→销毁的全部扩展点触发顺序
	 */
	static void experiment1_fullTimeline() {
		String banner = String.join("", Collections.nCopies(80, "="));
		System.out.println(banner);
		System.out.println("  实验 1: 扩展点全景时间线 (Full Extension Points Timeline)");
		System.out.println(banner);

		TimelineTracker.reset();

		// Phase 1 (定义期) + Phase 2 (实例期) 在容器启动过程中自动触发
		TimelineTracker.separator("容器启动 (定义期 + 实例期)");
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(ExtensionMapConfig.class);

		// Phase 3: 运行期 (AOP + Event)
		TimelineTracker.separator("触发运行期扩展点 (AOP + Event)");
		OrderService orderService = ctx.getBean(OrderService.class);
		String result = orderService.placeOrder("TX-20260313-001");
		System.out.println("  >> placeOrder result: " + result);

		// 验证 BDRPP 动态注册的 Bean
		GrayService gray = ctx.getBean(GrayService.class);
		System.out.println("  >> grayService.route: " + gray.route("user-123"));

		// Shutdown: 销毁回调
		TimelineTracker.separator("容器关闭 (销毁回调)");
		ctx.close();

		// 打印完整时间线汇总
		TimelineTracker.printTimeline();
	}

	/**
	 * 实验 2: IABPP 短路能力
	 * 当 postProcessBeforeInstantiation 返回非 null 时, 跳过整个 doCreateBean 流程,
	 * 直接执行 postProcessAfterInitialization (连初始化回调都不走)
	 */
	static void experiment2_iabppShortCircuit() {
		String banner = String.join("", Collections.nCopies(80, "="));
		System.out.println(banner);
		System.out.println("  实验 2: IABPP 短路能力 (Short-Circuit via postProcessBeforeInstantiation)");
		System.out.println(banner);

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 注册一个会短路的 IABPP
		ctx.getBeanFactory().addBeanPostProcessor(
			new org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor() {
				@Override
				public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
					if ("shortCircuitBean".equals(beanName)) {
						System.out.println("  [SHORT-CIRCUIT] IABPP.postProcessBeforeInstantiation -> 返回替代对象!");
						System.out.println("  [SHORT-CIRCUIT] 跳过: 构造器/MergedBDPP/属性注入/Aware/初始化三连");
						return "I am a short-circuit replacement!";
					}
					return null;
				}

				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName)
						throws org.springframework.beans.BeansException {
					if ("shortCircuitBean".equals(beanName)) {
						System.out.println("  [SHORT-CIRCUIT] BPP.postProcessAfterInitialization -> 仍会执行!");
						System.out.println("  [SHORT-CIRCUIT] bean type = " + bean.getClass().getSimpleName());
					}
					return bean;
				}
			}
		);

		// 注册一个目标 BD
		org.springframework.beans.factory.support.GenericBeanDefinition bd =
				new org.springframework.beans.factory.support.GenericBeanDefinition();
		bd.setBeanClass(TargetBean.class);
		ctx.registerBeanDefinition("shortCircuitBean", bd);

		ctx.refresh();

		// 获取被短路的 Bean
		Object bean = ctx.getBean("shortCircuitBean");
		System.out.println("  >> shortCircuitBean 类型: " + bean.getClass().getSimpleName());
		System.out.println("  >> shortCircuitBean 值: " + bean);
		System.out.println("  >> 结论: TargetBean 的构造器和所有生命周期回调都被跳过了!");

		ctx.close();
	}
}
