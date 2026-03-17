package org.springframework.lab.factorybean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FactoryBeanConfig {

	/**
	 * 注意: @Bean 方法返回类型是 FactoryBean, 但容器暴露的 bean 类型是 PayChannel。
	 * 这就是 FactoryBean 的"二阶抽象"——注册的是工厂, 暴露的是产物。
	 */
	@Bean
	public PayChannelFactoryBean payChannel() {
		return new PayChannelFactoryBean("Alipay");
	}

	@Bean
	public PrototypeTokenFactoryBean tokenGenerator() {
		return new PrototypeTokenFactoryBean();
	}

	@Bean
	public EagerMetricsFactoryBean metricsClient() {
		return new EagerMetricsFactoryBean();
	}
}
