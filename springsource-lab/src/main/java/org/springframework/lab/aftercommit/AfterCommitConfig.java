package org.springframework.lab.aftercommit;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * W47 — 事务后发布与最终一致性：AFTER_COMMIT + 幂等 + 重试
 *
 * <p>基础设施配置，复用 W21 的 H2 + DataSourceTransactionManager 骨架。
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.aftercommit")
public class AfterCommitConfig {

	@Bean
	public DataSource dataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:aftercommit_lab;DB_CLOSE_DELAY=-1");
		ds.setUser("sa");
		ds.setPassword("");
		return ds;
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}
