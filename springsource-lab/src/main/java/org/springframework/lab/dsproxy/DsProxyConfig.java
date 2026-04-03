package org.springframework.lab.dsproxy;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * <h2>W66 — 事务与 DataSource 代理边界 练兵场配置</h2>
 *
 * 代理链结构 (由外到内):
 * <pre>
 *   TransactionAwareDataSourceProxy   ← 最外层, 给原生JDBC代码感知事务
 *       ↓
 *   LazyConnectionDataSourceProxy     ← 中间层, 延迟到首次Statement才拿物理连接
 *       ↓
 *   realDataSource (H2 EmbeddedDB)    ← 最底层, 真实连接池
 * </pre>
 *
 * TransactionManager 必须绑定 realDataSource (不能绑代理),
 * 否则 ThreadLocal 里的 ConnectionHolder key 对不上.
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.dsproxy")
public class DsProxyConfig {

	// ═══════════════════════════════════════════
	//  第一层: 真实 DataSource (H2)
	// ═══════════════════════════════════════════

	@Bean
	public DataSource realDataSource() {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.addScript("classpath:lab-dsproxy-schema.sql")
				.build();
	}

	// ═══════════════════════════════════════════
	//  第二层: LazyConnectionDataSourceProxy
	//  拦截 getConnection(), 直到 createStatement 才真正获取物理连接
	// ═══════════════════════════════════════════

	@Bean
	public LazyConnectionDataSourceProxy lazyDataSource(DataSource realDataSource) {
		return new LazyConnectionDataSourceProxy(realDataSource);
	}

	// ═══════════════════════════════════════════
	//  第三层: TransactionAwareDataSourceProxy
	//  让原生 conn.close() 在事务内变成 "归还" 而非 "关闭"
	// ═══════════════════════════════════════════

	@Bean
	public TransactionAwareDataSourceProxy txAwareDataSource(LazyConnectionDataSourceProxy lazyDataSource) {
		return new TransactionAwareDataSourceProxy(lazyDataSource);
	}

	// ═══════════════════════════════════════════
	//  TransactionManager 必须绑 realDataSource!
	// ═══════════════════════════════════════════

	@Bean
	public PlatformTransactionManager transactionManager(DataSource realDataSource) {
		return new DataSourceTransactionManager(realDataSource);
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource realDataSource) {
		return new JdbcTemplate(realDataSource);
	}
}
