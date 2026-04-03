package org.springframework.lab.txinvokechain;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * W44 — 事务调用链练兵场配置
 *
 * <p>配置要点：
 * <ul>
 *   <li>主数据源 + 主 TM (默认)</li>
 *   <li>审计数据源 + 审计 TM (通过 qualifier "auditTxManager" 路由)</li>
 *   <li>{@code @EnableTransactionManagement} 注册三件套:
 *       Advisor + TransactionInterceptor + AnnotationTransactionAttributeSource</li>
 * </ul>
 *
 * <p>断点:
 * {@code ProxyTransactionManagementConfiguration#transactionAdvisor()} — 观察三件套装配
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.txinvokechain")
public class TxInvokeChainConfig {

	// ==================== 主数据源 ====================

	@Bean
	public DataSource dataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:tx_invoke_main;DB_CLOSE_DELAY=-1");
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

	// ==================== 审计数据源 (演示 qualifier 路由) ====================

	@Bean
	public DataSource auditDataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:tx_invoke_audit;DB_CLOSE_DELAY=-1");
		ds.setUser("sa");
		ds.setPassword("");
		return ds;
	}

	@Bean
	public JdbcTemplate auditJdbcTemplate(@Qualifier("auditDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}

	@Bean
	public PlatformTransactionManager auditTxManager(
			@Qualifier("auditDataSource") DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}
}
