package org.springframework.lab.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Full 模式配置类: proxyBeanMethods=true (默认).
 *
 * CGLIB 增强后, @Bean 方法间调用会被 BeanMethodInterceptor 拦截,
 * 转为 beanFactory.getBean(), 保证单例语义.
 *
 * 同时通过 @Import 演示三种导入方式:
 *   1. 普通类 (AuditLogger)
 *   2. ImportSelector (PayChannelImportSelector)
 *   3. ImportBeanDefinitionRegistrar (RpcClientRegistrar)
 * 以及 DeferredImportSelector (AutoConfigDeferredSelector)
 */
@Configuration
@Import({
		AuditLogger.class,                   // 普通类 → 当作 @Configuration 解析
		PayChannelImportSelector.class,      // ImportSelector → selectImports 返回类名
		RpcClientRegistrar.class,            // Registrar → 手动注册 BD
		AutoConfigDeferredSelector.class,    // DeferredImportSelector → 所有普通 parse 结束后才触发
		LiteModeConfig.class                 // Lite 模式对比
})
public class FullModeConfig {

	/**
	 * @Bean 工厂方法: 创建 DataSource.
	 * Full 模式下此方法被 CGLIB 增强, 多次调用只执行一次 new.
	 */
	@Bean
	public DataSource dataSource() {
		System.out.println("  [FullModeConfig] Creating DataSource...");
		return new DataSource("jdbc:mysql://localhost:3306/order");
	}

	/**
	 * @Bean 方法内直接调用 dataSource() — 关键观察点.
	 *
	 * Full 模式: dataSource() 被 CGLIB 拦截 → beanFactory.getBean("dataSource")
	 *            → 返回已有单例, 不会重新 new.
	 *
	 * Lite 模式: dataSource() 是普通 Java 调用 → 每次都 new 一个新实例.
	 */
	@Bean
	public OrderService orderService() {
		System.out.println("  [FullModeConfig] Creating OrderService, calling dataSource()...");
		return new OrderService(dataSource());
	}
}
