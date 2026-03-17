package org.springframework.lab.hierarchy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * W06 - 子容器配置
 *
 * <p>子容器注册自己的 Controller，同时故意注册同名 sharedService 以验证覆盖语义
 */
@Configuration
public class ChildConfig {

	@Bean
	public OrderController orderController() {
		return new OrderController();
	}

	/** 子容器同名 Bean，getBean 时优先返回子容器的 */
	@Bean
	public SharedService sharedService() {
		return new SharedService("from-CHILD");
	}
}
