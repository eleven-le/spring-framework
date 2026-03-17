package org.springframework.lab.extensionmap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 扩展点全景地图 —— 主配置类
 * <p>
 * 注册三阶段的全部扩展点实现:
 * - 定义期: BDRPP + BFPP (static @Bean, 避免过早实例化)
 * - 实例期: LifecycleObserver(IABPP+MergedBDPP+BPP) + TargetBean + ReadyHook
 * - 运行期: AOP Aspect + EventListener
 */
@Configuration
@EnableAspectJAutoProxy
public class ExtensionMapConfig {

	// ====== 定义期: BFPP/BDRPP 必须 static, 否则会导致 @Configuration 类过早实例化 ======

	@Bean
	public static FeatureBdrpp featureBdrpp() {
		return new FeatureBdrpp();
	}

	@Bean
	public static ConfigBfpp configBfpp() {
		return new ConfigBfpp();
	}

	// ====== 实例期: BPP 也建议 static, 确保在业务 Bean 之前注册 ======

	@Bean
	public static LifecycleObserver lifecycleObserver() {
		return new LifecycleObserver();
	}

	@Bean(initMethod = "customInit", destroyMethod = "customDestroy")
	public TargetBean targetBean() {
		return new TargetBean();
	}

	@Bean
	public ReadyHook readyHook() {
		return new ReadyHook();
	}

	// ====== 运行期 ======

	@Bean
	public OrderService orderService() {
		return new OrderService();
	}

	@Bean
	public OrderEventListener orderEventListener() {
		return new OrderEventListener();
	}

	@Bean
	public PerformanceAspect performanceAspect() {
		return new PerformanceAspect();
	}
}
