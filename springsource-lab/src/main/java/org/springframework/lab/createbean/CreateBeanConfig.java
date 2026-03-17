package org.springframework.lab.createbean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.springframework.lab.createbean")
public class CreateBeanConfig {

	/**
	 * 用 @Bean(initMethod) 演示 custom init-method 回调，
	 * 与 @PostConstruct / InitializingBean 形成三重初始化对照。
	 */
	@Bean(initMethod = "customInit", destroyMethod = "customDestroy")
	public OrderService orderService() {
		return new OrderService();
	}
}
