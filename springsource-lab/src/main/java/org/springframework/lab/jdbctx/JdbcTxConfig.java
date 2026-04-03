package org.springframework.lab.jdbctx;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * W45 — JDBC 事务落地练兵场配置
 *
 * H2 内存库 + DataSourceTransactionManager + @EnableTransactionManagement
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.jdbctx")
public class JdbcTxConfig {

	@Bean
	public DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.build();
	}

	@Bean
	public DataSourceTransactionManager transactionManager(DataSource ds) {
		// 注意：返回具体类型，实验中需访问 getDataSource()
		return new DataSourceTransactionManager(ds);
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource ds) {
		return new JdbcTemplate(ds);
	}
}
