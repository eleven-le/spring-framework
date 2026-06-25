package com.leilei.lab.laboratory.l04.l04_01;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 📖 知识点：[[L04-01-声明式事务与七种传播行为#2. 🏭 生产怎么用对]]（隔离级别 / readOnly / timeout 三个属性）
 * 🎯 作用：用真实连接验证 {@code @Transactional} 三个常被忽略的属性如何落地：
 *         ① {@code isolation}：开事务时由 {@code DataSourceUtils.prepareConnectionForTransaction}
 *            调 {@link Connection#setTransactionIsolation} 改物理连接隔离级，事务内 getTransactionIsolation() 即可读出；
 *         ② {@code readOnly}：写进 {@link TransactionSynchronizationManager#isCurrentTransactionReadOnly()}，
 *            供下游（Hibernate FlushMode.MANUAL / MySQL 驱动读写分离路由 / Connection.setReadOnly）做只读优化；
 *         ③ {@code timeout}：超时秒数挂到 ConnectionHolder 的 deadline，JdbcTemplate 执行语句前由
 *            {@code DataSourceUtils.applyTransactionTimeout} 校验，已超时直接抛 {@link TransactionTimedOutException}。
 * 🔗 业务场景：C 端「价格/库存只读查询」标 readOnly 让其走只读库、关闭脏页刷新；「大促对账/秒杀扣减」按需收紧隔离级；
 *         「批量改价」等可能慢的写事务设 timeout 兜底，避免一条大事务长期持有行锁拖垮整库（呼应 L04-03 大事务治理）。
 */
public final class L0401_04_IsolationReadOnlyTimeoutDemo {

	private L0401_04_IsolationReadOnlyTimeoutDemo() {
	}

	@Configuration
	@EnableTransactionManagement
	static class TxConfig {

		@Bean
		public DataSource dataSource() {
			return new EmbeddedDatabaseBuilder()
					.setType(EmbeddedDatabaseType.HSQL)
					.generateUniqueName(true)
					.build();
		}

		@Bean
		public PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean
		public JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		public AttributeProbeService attributeProbeService(DataSource dataSource, JdbcTemplate jdbcTemplate) {
			return new AttributeProbeService(dataSource, jdbcTemplate);
		}
	}

	/** 事务属性探针：在事务内回读真实连接 / 同步管理器状态，证明属性确实生效。 */
	static class AttributeProbeService {

		private final DataSource dataSource;
		private final JdbcTemplate jdbc;

		AttributeProbeService(DataSource dataSource, JdbcTemplate jdbc) {
			this.dataSource = dataSource;
			this.jdbc = jdbc;
		}

		/** 读出事务内物理连接的隔离级（应等于注解里声明的级别）。 */
		@Transactional(isolation = Isolation.READ_COMMITTED)
		public int isolationReadCommitted() {
			return currentIsolation();
		}

		@Transactional(isolation = Isolation.SERIALIZABLE)
		public int isolationSerializable() {
			return currentIsolation();
		}

		/** 读出当前事务是否被标记只读。 */
		@Transactional(readOnly = true)
		public boolean readOnlyFlag() {
			return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		}

		@Transactional
		public boolean defaultReadWriteFlag() {
			return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		}

		/** timeout=1s 的事务里先睡 1.5s，再跑语句——执行前的超时校验应直接抛 TransactionTimedOutException。 */
		@Transactional(timeout = 1)
		public void timeoutAfterSlowWork() {
			sleep(1500);
			jdbc.queryForObject("select count(*) from probe", Integer.class);   // 此处触发超时校验
		}

		private int currentIsolation() {
			Connection con = DataSourceUtils.getConnection(dataSource);
			try {
				return con.getTransactionIsolation();
			}
			catch (java.sql.SQLException ex) {
				throw new IllegalStateException(ex);
			}
			finally {
				DataSourceUtils.releaseConnection(con, dataSource);   // 事务内不会真正关闭，仅归还计数
			}
		}

		private static void sleep(long millis) {
			try {
				Thread.sleep(millis);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static String isoName(int level) {
		switch (level) {
			case Connection.TRANSACTION_READ_UNCOMMITTED: return "READ_UNCOMMITTED(1)";
			case Connection.TRANSACTION_READ_COMMITTED: return "READ_COMMITTED(2)";
			case Connection.TRANSACTION_REPEATABLE_READ: return "REPEATABLE_READ(4)";
			case Connection.TRANSACTION_SERIALIZABLE: return "SERIALIZABLE(8)";
			default: return "UNKNOWN(" + level + ")";
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxConfig.class)) {
			JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
			jdbc.execute("create table probe(id int)");
			AttributeProbeService probe = ctx.getBean(AttributeProbeService.class);

			System.out.println("==================== ① isolation 隔离级别 ====================");
			System.out.println("@Transactional(READ_COMMITTED) → 连接隔离级 = " + isoName(probe.isolationReadCommitted()));
			System.out.println("@Transactional(SERIALIZABLE)   → 连接隔离级 = " + isoName(probe.isolationSerializable()));
			System.out.println("（开事务时由 DataSourceUtils.prepareConnectionForTransaction 改物理连接，提交后会还原）");

			System.out.println();
			System.out.println("==================== ② readOnly 只读标记 ====================");
			System.out.println("@Transactional(readOnly=true) → isCurrentTransactionReadOnly = " + probe.readOnlyFlag()
					+ "   ✅ 下游据此做只读优化（FlushMode.MANUAL / 读写分离路由）");
			System.out.println("@Transactional(默认)          → isCurrentTransactionReadOnly = " + probe.defaultReadWriteFlag());

			System.out.println();
			System.out.println("==================== ③ timeout 事务超时 ====================");
			try {
				probe.timeoutAfterSlowWork();
				System.out.println("（未超时——本不该到这）");
			}
			catch (TransactionTimedOutException ex) {
				System.out.println("@Transactional(timeout=1) + 慢操作 1.5s → 抛 TransactionTimedOutException   ✅");
				System.out.println("  " + ex.getMessage());
			}

			System.out.println();
			System.out.println("结论：isolation/readOnly 只对真正新开的物理事务（REQUIRED/REQUIRES_NEW）生效，"
					+ "加入已有事务时这些属性被忽略；timeout 是大事务的兜底护栏，但它只在「下一条语句执行前」校验，纯 CPU 空转不触发。");
		}
	}
}
