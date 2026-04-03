package org.springframework.lab.jdbctemplate;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class JdbcTemplateConfig {

	@Bean
	public DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.addScript("lab-jdbctemplate-schema.sql")
				.build();
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		JdbcTemplate jt = new JdbcTemplate(dataSource);
		jt.setFetchSize(100);        // 调优: 每次网络往返取100行
		jt.setQueryTimeout(5);       // 调优: 5秒超时保护
		return jt;
	}

	/** 演示自定义 ExceptionTranslator：覆盖 customTranslate 做业务级映射 */
	@Bean
	public JdbcTemplate customTranslatorJdbcTemplate(DataSource dataSource) {
		JdbcTemplate jt = new JdbcTemplate(dataSource);
		jt.setExceptionTranslator(new SQLErrorCodeSQLExceptionTranslator(dataSource) {
			@Override
			protected org.springframework.dao.DataAccessException customTranslate(
					String task, String sql, java.sql.SQLException sqlEx) {
				// 示例: 将 H2 的唯一约束冲突(errorCode=23505) 映射为自定义异常
				if (sqlEx.getErrorCode() == 23505) {
					return new org.springframework.dao.DuplicateKeyException(
							"业务层: 重复数据 — " + sql, sqlEx);
				}
				return null; // 其他异常走默认翻译链
			}
		});
		return jt;
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}
