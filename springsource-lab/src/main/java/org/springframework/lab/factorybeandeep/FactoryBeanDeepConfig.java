package org.springframework.lab.factorybeandeep;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FactoryBeanDeepConfig {

	@Bean
	public CacheDemoFactoryBean cacheConn() {
		return new CacheDemoFactoryBean();
	}

	@Bean
	public TypePredictionFactoryBean typePredictConn() {
		return new TypePredictionFactoryBean();
	}

	@Bean
	public NullTypeFactoryBean nullTypeBean() {
		return new NullTypeFactoryBean();
	}

	@Bean
	public ScopedSessionFactoryBean scopedSession() {
		return new ScopedSessionFactoryBean();
	}

	@Bean
	public EagerRegistryFactoryBean eagerRegistry() {
		return new EagerRegistryFactoryBean();
	}

	@Bean
	public FactoryBeanProductBPP factoryBeanProductBPP() {
		return new FactoryBeanProductBPP();
	}
}
