package org.springframework.lab.transaction;

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
 * 事务实验配置类
 *
 * <p>三件套注册链路:
 * <ol>
 *   <li>{@code @EnableTransactionManagement}
 *       → @Import(TransactionManagementConfigurationSelector)</li>
 *   <li>Selector 导入: AutoProxyRegistrar + ProxyTransactionManagementConfiguration</li>
 *   <li>ProxyTransactionManagementConfiguration 注册:
 *       BeanFactoryTransactionAttributeSourceAdvisor
 *       + AnnotationTransactionAttributeSource
 *       + TransactionInterceptor</li>
 * </ol>
 *
 * <p>断点:
 * {@code TransactionManagementConfigurationSelector#selectImports} — 观察导入了哪些类
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.transaction")
public class TransactionConfig {

	@Bean
	public DataSource dataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:tx_lab;DB_CLOSE_DELAY=-1");
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
