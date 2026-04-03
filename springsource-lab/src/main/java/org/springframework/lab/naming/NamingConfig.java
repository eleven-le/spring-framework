package org.springframework.lab.naming;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * #98 命名法则练兵场配置类
 */
@Configuration
@ComponentScan("org.springframework.lab.naming")
public class NamingConfig {

	/**
	 * 注册 PayContext —— PayContextAware 注入的对象
	 */
	@Bean
	public PayContext payContext() {
		return new PayContext("MCH-10086", "SANDBOX");
	}

	/**
	 * 注册自定义 Aware 驱动器（BPP）。
	 * 对照 Spring 的 ApplicationContextAwareProcessor —— 它也是一个 BPP，
	 * 在 prepareBeanFactory 阶段被注册，专门驱动 6 种内置 Aware 回调。
	 */
	@Bean
	public PayContextAwareBeanPostProcessor payContextAwareBeanPostProcessor(PayContext payContext) {
		return new PayContextAwareBeanPostProcessor(payContext);
	}
}
