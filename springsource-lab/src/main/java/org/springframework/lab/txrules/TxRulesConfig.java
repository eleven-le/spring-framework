package org.springframework.lab.txrules;

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
 * W67 — 传播/隔离/回滚规则：交易链路拆分指南 · 练兵场配置
 *
 * <p>配置要点：
 * <ul>
 *   <li>H2 内存库 — 支持 savepoint（NESTED 实验必需）</li>
 *   <li>{@code setNestedTransactionAllowed(true)} — 开启 NESTED 传播支持</li>
 *   <li>{@code setValidateExistingTransaction(true)} — 开启隔离级别冲突校验（实验 7）</li>
 *   <li>单数据源 + 单 TM 聚焦传播/隔离/回滚本身，不引入多 TM 路由干扰</li>
 * </ul>
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.txrules")
public class TxRulesConfig {

	@Bean
	public DataSource dataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:tx_rules;DB_CLOSE_DELAY=-1");
		ds.setUser("sa");
		ds.setPassword("");
		return ds;
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	/**
	 * 开启 nestedTransactionAllowed + validateExistingTransaction
	 * <ul>
	 *   <li>nestedTransactionAllowed → handleExistingTransaction 中 NESTED 分支不抛 NestedTransactionNotSupportedException</li>
	 *   <li>validateExistingTransaction → 加入已有事务时校验隔离级别一致性，不一致抛 IllegalTransactionStateException</li>
	 * </ul>
	 */
	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		DataSourceTransactionManager tm = new DataSourceTransactionManager(dataSource);
		tm.setNestedTransactionAllowed(true);
		tm.setValidateExistingTransaction(true);
		return tm;
	}
}
