package org.springframework.lab.beanfactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * W27 — DefaultListableBeanFactory 配置类
 */
@Configuration
@ComponentScan("org.springframework.lab.beanfactory")
public class BeanFactoryConfig {

	/** 手动注册的 Bean，会进入 beanDefinitionMap */
	@Bean
	@Primary
	public PayChannel alipay() {
		return new PayChannel("alipay", "支付宝");
	}

	@Bean
	public PayChannel wechat() {
		return new PayChannel("wechat", "微信支付");
	}

	@Bean
	public PayChannel unionpay() {
		return new PayChannel("unionpay", "银联");
	}
}
