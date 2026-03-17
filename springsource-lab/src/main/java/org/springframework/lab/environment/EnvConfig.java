package org.springframework.lab.environment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:lab-common.properties")
public class EnvConfig {

	/**
	 * prod profile 才加载的额外配置源
	 */
	@Configuration
	@Profile("prod")
	@PropertySource("classpath:lab-prod.properties")
	static class ProdConfig {
	}

	@Bean
	public NotificationService notificationService() {
		return new NotificationService();
	}

	@Bean
	@Profile("prod")
	public NotificationService smsNotificationService() {
		return new NotificationService("SMS-PROD");
	}
}
