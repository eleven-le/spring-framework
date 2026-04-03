package org.springframework.lab.txfailure;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * W46 — @Transactional 常见失效与边界
 *
 * <p>配置要点：
 * <ul>
 *   <li>exposeProxy = true : 允许 AopContext.currentProxy() 修复自调用</li>
 *   <li>两个 DataSource / TransactionManager : 演示多事务管理器场景</li>
 * </ul>
 */
@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy(exposeProxy = true)  // 关键：暴露代理到 ThreadLocal
@ComponentScan("org.springframework.lab.txfailure")
public class TxFailureConfig {

	// ============ 主库 (orderDs) ============
	@Bean
	public DataSource orderDs() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:tx_failure_order;DB_CLOSE_DELAY=-1");
		ds.setUser("sa");
		ds.setPassword("");
		return ds;
	}

	@Bean
	public JdbcTemplate orderJdbc(@Qualifier("orderDs") DataSource ds) {
		return new JdbcTemplate(ds);
	}

	@Bean
	public PlatformTransactionManager orderTxManager(@Qualifier("orderDs") DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}

	// ============ 库存库 (stockDs) ============
	@Bean
	public DataSource stockDs() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:tx_failure_stock;DB_CLOSE_DELAY=-1");
		ds.setUser("sa");
		ds.setPassword("");
		return ds;
	}

	@Bean
	public JdbcTemplate stockJdbc(@Qualifier("stockDs") DataSource ds) {
		return new JdbcTemplate(ds);
	}

	@Bean
	public PlatformTransactionManager stockTxManager(@Qualifier("stockDs") DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}
}
