package org.springframework.lab.txskeleton;

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
 * W18 — 事务模板骨架：TransactionAspectSupport + AbstractPlatformTransactionManager
 *
 * <p>本配置复用 H2 内存数据库 + DataSourceTransactionManager，
 * 重点不在于"如何挂载"（W17 已讲），而在于方法调用进入拦截器之后，
 * 事务骨架内部的模板流程：挂起/恢复/同步回调。
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.txskeleton")
public class TxSkeletonConfig {

	@Bean
	public DataSource dataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:tx_skeleton;DB_CLOSE_DELAY=-1");
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
		DataSourceTransactionManager tm = new DataSourceTransactionManager(dataSource);
		// 开启嵌套事务支持 (savepoint)
		tm.setNestedTransactionAllowed(true);
		return tm;
	}
}
