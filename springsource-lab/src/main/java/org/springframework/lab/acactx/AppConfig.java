package org.springframework.lab.acactx;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 主配置类 — 演示 register 路径的入口
 *
 * <p>@Configuration 让本类成为 Full 模式配置 (CGLIB 增强)
 * <p>@ComponentScan 在 refresh 第 5 步由 ConfigurationClassPostProcessor 触发扫描
 */
@Configuration
@ComponentScan("org.springframework.lab.acactx")
public class AppConfig {

	/**
	 * 手动 @Bean 注册 — 展示 register 路径下 @Bean 方法的处理时机:
	 * 不是在 register() 时, 而是在 refresh() 第 5 步 CCPP 解析 @Bean 方法时
	 */
	@Bean
	public OrderRepository orderRepository() {
		System.out.println("  [AppConfig] @Bean orderRepository() invoked");
		return new OrderRepository();
	}
}
