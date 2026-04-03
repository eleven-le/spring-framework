package org.springframework.lab.threadlocal;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * W49 — ThreadLocal 边界与上下文传播练兵场配置
 *
 * <p>线程池特意设置 corePoolSize=2 + maxPoolSize=2，让线程复用更明显，
 * 方便观察 ThreadLocal 泄漏和 TaskDecorator 清理效果。
 */
@Configuration
@EnableTransactionManagement
public class ThreadLocalBoundaryConfig {

	@Bean
	public DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.addScript("classpath:lab-threadlocal-schema.sql")
				.build();
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource ds) {
		return new JdbcTemplate(ds);
	}

	/**
	 * 无 TaskDecorator 的线程池 — 用于演示上下文丢失
	 */
	@Bean("rawExecutor")
	public ThreadPoolTaskExecutor rawExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(10);
		executor.setThreadNamePrefix("raw-pool-");
		executor.initialize();
		return executor;
	}

	/**
	 * 带 TaskDecorator 的线程池 — 用于演示上下文传播
	 */
	@Bean("decoratedExecutor")
	public ThreadPoolTaskExecutor decoratedExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(10);
		executor.setThreadNamePrefix("ctx-pool-");
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		executor.initialize();
		return executor;
	}
}
