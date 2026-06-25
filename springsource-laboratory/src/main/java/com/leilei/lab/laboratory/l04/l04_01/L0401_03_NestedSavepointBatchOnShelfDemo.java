package com.leilei.lab.laboratory.l04.l04_01;

import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 📖 知识点：[[L04-01-声明式事务与七种传播行为#3. 🧨 事故与避坑]]（🧊 NESTED 与 SAVEPOINT）
 * 🎯 作用：对照 NESTED 与 REQUIRED 在「批处理中单条失败」时的回滚边界——
 *         NESTED 通过 {@code DefaultTransactionStatus#createAndHoldSavepoint}（底层 JDBC Savepoint）
 *         只回滚到该子项的保存点、外层可捕获异常后继续提交其余子项（局部回滚）；
 *         REQUIRED 子项与外层同一物理事务，子项抛异常会把整个事务标记 rollback-only，
 *         外层即便 try/catch 吞掉异常，提交时仍抛 {@link UnexpectedRollbackException}（一损俱损、全部回滚）。
 * 🔗 业务场景：新品批量上架到门店货架，一批 N 个 SKU，其中某个 SKU 因价格规则未配置（脏数据）上架失败。
 *         产品诉求：坏的那条跳过、好的继续上架（NESTED）；若误用 REQUIRED 的「子方法独立事务」幻觉，
 *         实际会全批回滚——运营以为上了 99 个，结果一个都没上，大促前夜的典型翻车。
 */
public final class L0401_03_NestedSavepointBatchOnShelfDemo {

	private L0401_03_NestedSavepointBatchOnShelfDemo() {
	}

	/** 价格规则缺失的「脏数据」SKU：上架时必失败，用来制造批处理中的单条异常。 */
	private static final long DIRTY_SKU = 100103L;

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
			// DataSourceTransactionManager 默认 nestedTransactionAllowed=true，NESTED 才会走 savepoint 分支
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean
		public JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		public ShelfItemService shelfItemService(JdbcTemplate jdbcTemplate) {
			return new ShelfItemService(jdbcTemplate);
		}

		@Bean
		public BatchOnShelfService batchOnShelfService(JdbcTemplate jdbcTemplate, ShelfItemService item) {
			return new BatchOnShelfService(jdbcTemplate, item);
		}
	}

	/** 单个 SKU 上架：独立 Bean，提供 NESTED / REQUIRED 两种传播策略对照。 */
	static class ShelfItemService {

		private final JdbcTemplate jdbc;

		ShelfItemService(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		/** NESTED：进入即在外层事务里打一个 savepoint，失败只回滚到此 savepoint。 */
		@Transactional(propagation = Propagation.NESTED)
		public void onShelfNested(long skuId) {
			doOnShelf(skuId);
		}

		/** REQUIRED：与外层同一物理事务，失败会把整个事务标记为 rollback-only。 */
		@Transactional(propagation = Propagation.REQUIRED)
		public void onShelfRequired(long skuId) {
			doOnShelf(skuId);
		}

		private void doOnShelf(long skuId) {
			jdbc.update("insert into shelf_item(sku_id) values (?)", skuId);
			if (skuId == DIRTY_SKU) {
				throw new IllegalStateException("SKU " + skuId + " 缺少价格规则，上架失败");
			}
		}
	}

	/** 批量上架编排：外层一个事务，逐个 SKU 调子项上架。 */
	static class BatchOnShelfService {

		private final JdbcTemplate jdbc;
		private final ShelfItemService item;

		BatchOnShelfService(JdbcTemplate jdbc, ShelfItemService item) {
			this.jdbc = jdbc;
			this.item = item;
		}

		/** NESTED 版：坏项回滚到 savepoint 后跳过，好项随外层一起提交。 */
		@Transactional
		public void batchNested(List<Long> skuIds) {
			for (long skuId : skuIds) {
				try {
					item.onShelfNested(skuId);
				}
				catch (RuntimeException ex) {
					System.out.println("  · SKU " + skuId + " 失败 → 回滚到 savepoint，跳过：" + ex.getMessage());
				}
			}
		}

		/** REQUIRED 版：坏项已把共享事务标记 rollback-only，外层吞异常也救不回来。 */
		@Transactional
		public void batchRequired(List<Long> skuIds) {
			for (long skuId : skuIds) {
				try {
					item.onShelfRequired(skuId);
				}
				catch (RuntimeException ex) {
					System.out.println("  · SKU " + skuId + " 失败 → 已 catch（但事务已被标记 rollback-only）：" + ex.getMessage());
				}
			}
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
			BatchOnShelfService batch = ctx.getBean(BatchOnShelfService.class);
			initSchema(jdbc);

			// 5 个 SKU，第 3 个是脏数据（缺价格规则）
			List<Long> skus = Arrays.asList(100101L, 100102L, DIRTY_SKU, 100104L, 100105L);

			System.out.println("==================== 场景 A：子项 NESTED（局部回滚）====================");
			truncate(jdbc);
			batch.batchNested(skus);
			System.out.println("shelf_item 行数 = " + count(jdbc)
					+ "   ✅ 期望 4：坏项回滚到 savepoint，其余 4 个正常上架并提交");

			System.out.println();
			System.out.println("==================== 场景 B：子项 REQUIRED（一损俱损）====================");
			truncate(jdbc);
			try {
				batch.batchRequired(skus);
				System.out.println("（外层提交成功——本不该到这）");
			}
			catch (UnexpectedRollbackException ex) {
				System.out.println("外层提交时抛 UnexpectedRollbackException：" + ex.getMessage());
			}
			System.out.println("shelf_item 行数 = " + count(jdbc)
					+ "   ❌ 期望 0：坏项把共享事务标记 rollback-only，外层吞了异常也全批回滚");

			System.out.println();
			System.out.println("结论：批处理要「坏项跳过、好项保留」用 NESTED（savepoint 局部回滚）；"
					+ "REQUIRED 子方法的失败是「核弹级」的——会污染整个外层事务，try/catch 吞不掉 rollback-only 标记。");
		}
	}
}
