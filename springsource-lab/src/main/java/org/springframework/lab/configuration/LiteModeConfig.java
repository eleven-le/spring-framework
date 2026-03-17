package org.springframework.lab.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lite 模式: proxyBeanMethods=false.
 *
 * 不会被 CGLIB 增强, @Bean 方法间调用就是普通 Java 方法调用.
 * 适用于:
 *   - 不需要 @Bean 方法间互调的场景
 *   - 追求启动性能 (跳过 CGLIB 字节码生成)
 *
 * 判定规则 (ConfigurationClassUtils#checkConfigurationClassCandidate):
 *   - @Configuration(proxyBeanMethods=false) → lite
 *   - @Component / @ComponentScan / @Import / @ImportResource / 有 @Bean 方法但无 @Configuration → lite
 */
@Configuration(proxyBeanMethods = false)
public class LiteModeConfig {

	@Bean
	public LiteService liteService() {
		System.out.println("  [LiteModeConfig] Creating LiteService...");
		return new LiteService();
	}
}
