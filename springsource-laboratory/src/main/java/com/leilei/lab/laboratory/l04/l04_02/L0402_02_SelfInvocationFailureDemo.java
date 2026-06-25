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
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 📖 知识点：[[L04-02-事务失效八股的事故现场#2. 🏭 生产怎么用对]]（事务失效八场景之「自调用」⭐）
 * 🎯 作用：用真实可回滚的 HSQLDB 证明事务失效头号场景——【自调用】。{@code @Transactional} 靠 AOP 代理生效，
 *         而 {@code this.xxx()} 直接走目标对象、不经过代理，事务切面根本不会被触发：方法体里
 *         {@link TransactionSynchronizationManager#isActualTransactionActive()} 实读 false，抛异常也不会回滚。
 *         对照：把带事务的方法拆到【独立 Bean】跨 Bean 调用 → 走代理 → 事务生效 → 异常正常回滚。
 * 🔗 业务场景：新品批量上架。编排方法循环调用本类的 {@code @Transactional onShelfOne(sku)}，开发以为「每个 SKU 一个事务」，
 *         实际是 this 自调用——事务从未开过，某个 SKU 中途失败时已写入的脏数据不回滚，货架挂出半成品 SKU。
 */
public final class L0402_02_SelfInvocationFailureDemo {

	private L0402_02_SelfInvocationFailureDemo() {
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
		public ShelfItemTxService shelfItemTxService(JdbcTemplate jdbcTemplate) {
			return new ShelfItemTxService(jdbcTemplate);
		}

		@Bean
		public ShelfOrchestrator shelfOrchestrator(JdbcTemplate jdbcTemplate, ShelfItemTxService txService) {
			return new ShelfOrchestrator(jdbcTemplate, txService);
		}
	}

	/** 上架编排器：对照「自调用」与「跨 Bean 调用」两条路径。 */
	static class ShelfOrchestrator {

		private final JdbcTemplate jdbc;
		private final ShelfItemTxService txService;   // 独立 Bean = 代理对象

		ShelfOrchestrator(JdbcTemplate jdbc, ShelfItemTxService txService) {
			this.jdbc = jdbc;
			this.txService = txService;
		}

		/** ❌ 自调用：this.onShelfSelf() 绕过代理，@Transactional 失效，事务从未开启。 */
		public void onShelfBySelfCall(long skuId) {
			onShelfSelf(skuId);   // 等价于 this.onShelfSelf(skuId) —— 不经过代理
		}

		/** 同类内的事务方法：被 this 调用时事务切面不触发。 */
		@Transactional
		public void onShelfSelf(long skuId) {
			System.out.println("  [自调用] 方法内 isActualTransactionActive = "
					+ TransactionSynchronizationManager.isActualTransactionActive());
			jdbc.update("insert into shelf_item(sku_id) values (?)", skuId);
			throw new IllegalStateException("SKU " + skuId + " 价格规则缺失，上架失败");
		}

		/** ✅ 跨 Bean 调用：经代理触发事务切面，异常正常回滚。 */
		public void onShelfByProxyCall(long skuId) {
			txService.onShelf(skuId);
		}
	}

	/** 独立的上架事务服务：作为单独 Bean，被注入后即是代理对象。 */
	static class ShelfItemTxService {

		private final JdbcTemplate jdbc;

		ShelfItemTxService(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		@Transactional
		public void onShelf(long skuId) {
			System.out.println("  [跨 Bean] 方法内 isActualTransactionActive = "
					+ TransactionSynchronizationManager.isActualTransactionActive());
			jdbc.update("insert into shelf_item(sku_id) values (?)", skuId);
			throw new IllegalStateException("SKU " + skuId + " 价格规则缺失，上架失败");
		}
	}

	private static void initSchema(JdbcTemplate jdbc) {
		jdbc.execute("create table shelf_item(sku_id bigint primary key)");
	}

	private static void truncate(JdbcTemplate jdbc) {
		jdbc.execute("delete from shelf_item");
	}

	private static int count(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select count(*) from shelf_item", Integer.class);
		return n == null ? 0 : n;
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxConfig.class)) {
			JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
			ShelfOrchestrator orch = ctx.getBean(ShelfOrchestrator.class);
			initSchema(jdbc);

			System.out.println("==================== 场景 A：自调用 this.onShelfSelf() ====================");
			truncate(jdbc);
			try {
				orch.onShelfBySelfCall(1001L);
			}
			catch (IllegalStateException expected) {
				System.out.println("抛出异常：" + expected.getMessage());
			}
			System.out.println("shelf_item 行数 = " + count(jdbc)
					+ "   ❌ 期望 0 实得 1：自调用绕过代理，事务从未开启，脏数据未回滚");

			System.out.println();
			System.out.println("==================== 场景 B：跨 Bean 调用 txService.onShelf() ====================");
			truncate(jdbc);
			try {
				orch.onShelfByProxyCall(1001L);
			}
			catch (IllegalStateException expected) {
				System.out.println("抛出异常：" + expected.getMessage());
			}
			System.out.println("shelf_item 行数 = " + count(jdbc)
					+ "   ✅ 期望 0：跨 Bean 走代理，事务生效，异常回滚");

			System.out.println();
			System.out.println("结论：@Transactional 只在「经过代理的调用」上生效。同类自调用一律失效——"
					+ "拆独立 Bean 跨 Bean 调用，或 @EnableAspectJAutoProxy(exposeProxy=true) + AopContext.currentProxy()。");
		}
	}
}
