package org.springframework.lab.aabpp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * W30 AutowiredAnnotationBeanPostProcessor 注入落点与常见坑 · 配置类
 */
@Configuration
@ComponentScan("org.springframework.lab.aabpp")
public class AabppConfig {

	/**
	 * 坑5演示: Prototype bean 注入到 Singleton 中 → 拿到的永远是同一个实例(stale reference)
	 */
	@Bean
	@Scope("prototype")
	public RequestContext requestContext() {
		return new RequestContext("req-" + System.nanoTime());
	}
}
