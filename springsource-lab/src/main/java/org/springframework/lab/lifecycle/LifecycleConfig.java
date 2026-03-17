package org.springframework.lab.lifecycle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.springframework.lab.lifecycle")
public class LifecycleConfig {

	@Bean(initMethod = "customInit", destroyMethod = "customDestroy")
	public FullLifecycleBean fullLifecycleBean() {
		return new FullLifecycleBean();
	}
}
