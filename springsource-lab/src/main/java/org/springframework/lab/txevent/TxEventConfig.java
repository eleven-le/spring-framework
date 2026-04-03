package org.springframework.lab.txevent;

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
 * W21 — @TransactionalEventListener 四阶段与 fallbackExecution
 *
 * <p>@EnableTransactionManagement 会通过 AbstractTransactionManagementConfiguration 自动注册:
 * 1. InfrastructureAdvisorAutoProxyCreator（AOP 代理）
 * 2. BeanFactoryTransactionAttributeSourceAdvisor（@Transactional 切面）
 * 3. TransactionalEventListenerFactory（order=50，优先于 DefaultEventListenerFactory）
 *
 * <p>关键: TransactionalEventListenerFactory 正是 @TransactionalEventListener 能生效的基础设施。
 * 它在 EventListenerMethodProcessor#postProcessBeanFactory 中被收集（getBeansOfType(EventListenerFactory.class)），
 * 按 Order 排序后排在 DefaultEventListenerFactory 前面，
 * 因此 @TransactionalEventListener 方法会被它优先截获生成 TransactionalApplicationListenerMethodAdapter。
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.txevent")
public class TxEventConfig {

	@Bean
	public DataSource dataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:txevent_lab;DB_CLOSE_DELAY=-1");
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
