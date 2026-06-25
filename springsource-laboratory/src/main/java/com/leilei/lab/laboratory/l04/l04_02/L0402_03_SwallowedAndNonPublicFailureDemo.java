package com.leilei.lab.laboratory.l04.l04_02;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 📖 知识点：[[L04-02-事务失效八股的事故现场#2. 🏭 生产怎么用对]]（事务失效八场景之「吞异常」「非 public」⭐）
 * 🎯 作用：用真实可回滚的 HSQLDB 证明两类高频失效——
 *         ① 【吞异常】：异常在事务方法内被 try/catch 吞掉、不再外抛，事务切面看不到异常 → 提交（脏写）。
 *            修复：catch 里调 {@link TransactionAspectSupport#currentTransactionStatus()}.setRollbackOnly() 主动标记回滚。
 *         ② 【非 public】：{@code @Transactional} 标在非 public 方法上，
 *            {@link org.springframework.transaction.interceptor.AbstractFallbackTransactionAttributeSource#computeTransactionAttribute}
 *            因 allowPublicMethodsOnly 过滤掉它，返回空事务属性 → 事务从未开启（方法内 active = false）。
 * 🔗 业务场景：库存回补（异步对账后补扣）与价格刷新批处理。开发图省事在事务方法里把下游异常「吞了打个日志」，
 *         或把批处理子方法写成包级私有——线上表现为「日志报错了，数据却提交了 / 该回滚的没回滚」。
 */
public final class L0402_03_SwallowedAndNonPublicFailureDemo {

	private L0402_03_SwallowedAndNonPublicFailureDemo() {
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
		public InventoryReplenishService inventoryReplenishService(JdbcTemplate jdbcTemplate) {
			return new InventoryReplenishService(jdbcTemplate);
		}

		@Bean
		public PriceRefreshService priceRefreshService(JdbcTemplate jdbcTemplate) {
			return new PriceRefreshService(jdbcTemplate);
		}
	}

	/** 库存回补：演示「吞异常」失效与「主动 setRollbackOnly」修复。 */
	static class InventoryReplenishService {

		private final JdbcTemplate jdbc;

		InventoryReplenishService(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		/** ❌ 吞异常：先写流水，再触发异常并 catch 吞掉——切面看不到异常，事务正常提交（脏写）。 */
		@Transactional
		public void replenishAndSwallow(long skuId) {
			jdbc.update("insert into stock_log(sku_id, op) values (?, ?)", skuId, "REPLENISH");
			try {
				throw new IllegalStateException("下游对账失败，本该回滚");
			}
			catch (RuntimeException ex) {
				System.out.println("  [吞异常] 异常被 catch 吞掉，仅打日志：" + ex.getMessage());
			}
		}

		/** ✅ 修复：catch 里主动 setRollbackOnly()，吞掉异常但不放过事务回滚。 */
		@Transactional
		public void replenishAndMarkRollback(long skuId) {
			jdbc.update("insert into stock_log(sku_id, op) values (?, ?)", skuId, "REPLENISH");
			try {
				throw new IllegalStateException("下游对账失败，需回滚");
			}
			catch (RuntimeException ex) {
				System.out.println("  [修复] catch 中 setRollbackOnly()：" + ex.getMessage());
				TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			}
		}
	}

	/** 价格刷新：演示「非 public 方法」失效（包级私有 vs public 对照）。 */
	static class PriceRefreshService {

		private final JdbcTemplate jdbc;

		PriceRefreshService(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		/** ❌ 非 public（包级私有）：computeTransactionAttribute 被 allowPublicMethodsOnly 过滤，无事务属性 → 不开事务。 */
		@Transactional
		void refreshPackagePrivate(long skuId) {
			System.out.println("  [非 public] 方法内 isActualTransactionActive = "
					+ TransactionSynchronizationManager.isActualTransactionActive());
			jdbc.update("insert into stock_log(sku_id, op) values (?, ?)", skuId, "PRICE");
			throw new IllegalStateException("价格规则冲突，本该回滚");
		}

		/** ✅ public 对照：事务属性正常解析，事务生效，异常回滚。 */
		@Transactional
		public void refreshPublic(long skuId) {
			System.out.println("  [public] 方法内 isActualTransactionActive = "
					+ TransactionSynchronizationManager.isActualTransactionActive());
			jdbc.update("insert into stock_log(sku_id, op) values (?, ?)", skuId, "PRICE");
			throw new IllegalStateException("价格规则冲突，应回滚");
		}
	}

	private static void initSchema(JdbcTemplate jdbc) {
		jdbc.execute("create table stock_log(id identity primary key, sku_id bigint, op varchar(16))");
	}

	private static void truncate(JdbcTemplate jdbc) {
		jdbc.execute("delete from stock_log");
	}

	private static int count(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select count(*) from stock_log", Integer.class);
		return n == null ? 0 : n;
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxConfig.class)) {
			JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
			InventoryReplenishService inv = ctx.getBean(InventoryReplenishService.class);
			PriceRefreshService price = ctx.getBean(PriceRefreshService.class);
			initSchema(jdbc);

			System.out.println("==================== 场景 A：吞异常（不外抛）====================");
			truncate(jdbc);
			inv.replenishAndSwallow(2001L);
			System.out.println("stock_log 行数 = " + count(jdbc)
					+ "   ❌ 期望 0 实得 1：异常被吞，切面无从感知，事务提交脏数据");

			System.out.println();
			System.out.println("==================== 场景 B：吞异常 + setRollbackOnly() 修复 ====================");
			truncate(jdbc);
			inv.replenishAndMarkRollback(2001L);
			System.out.println("stock_log 行数 = " + count(jdbc)
					+ "   ✅ 期望 0：主动标记 rollback-only，吞异常也能回滚");

			System.out.println();
			System.out.println("==================== 场景 C：@Transactional 标在非 public 方法 ====================");
			truncate(jdbc);
			try {
				price.refreshPackagePrivate(3001L);
			}
			catch (IllegalStateException expected) {
				System.out.println("抛出异常：" + expected.getMessage());
			}
			System.out.println("stock_log 行数 = " + count(jdbc)
					+ "   ❌ 期望 0 实得 1：非 public 方法无事务属性，事务未开启，脏数据未回滚");

			System.out.println();
			System.out.println("==================== 场景 D（对照）：public 方法 ====================");
			truncate(jdbc);
			try {
				price.refreshPublic(3001L);
			}
			catch (IllegalStateException expected) {
				System.out.println("抛出异常：" + expected.getMessage());
			}
			System.out.println("stock_log 行数 = " + count(jdbc)
					+ "   ✅ 期望 0：public 方法事务生效，异常回滚");

			System.out.println();
			System.out.println("结论：① 事务方法里 catch 了异常就等于告诉切面「没事发生」，要回滚必须 setRollbackOnly() 或重新抛出；"
					+ "② @Transactional 只认 public 方法（默认 publicMethodsOnly=true），非 public 静默失效。");
		}
	}
}
