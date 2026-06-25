package com.leilei.lab.laboratory.l04.l04_03;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 📖 知识点：[[L04-03-编程式事务与大事务治理#3. 🧨 事故与避坑]]（大事务治理：超大批量的分片提交）
 * 🎯 作用：用 {@link TransactionTemplate} 实现「分片提交」(chunked commit)，治理超大批量写的大事务问题。
 *         对照两种实现批量改价（N 个 SKU）：
 *         ① 单个大事务：一个事务圈住全部 N 行——持有 N 行锁直到最后、undo log / 回滚段随 N 线性膨胀、
 *            主从复制延迟、且任意一行失败则【全量回滚】，前面的白做；
 *         ② 编程式分片提交：每 CHUNK 行开一个独立小事务提交——锁持有窗口被切成 N/CHUNK 段、
 *            undo 量恒定在一片之内、已提交的分片落库不回滚（失败只损失当前分片，可断点续跑）。
 *         这是声明式 {@code @Transactional} 难以表达的边界控制（它只能整方法一个事务），
 *         编程式事务在「批处理事务粒度」上的掌控力是它的杀手级价值。
 * 🔗 业务场景：大促前运营一次性批量改价上万个 SKU。用一个大事务，改价期间长时间持有大量行锁、
 *         阻塞 C 端正常下单读价；万一第 9000 条触发约束失败，前 8999 条全部回滚，运营崩溃重来。
 *         分片提交（每 500 个一提交）把锁持有切碎、失败只回滚当前 500，已改的价稳稳落库。
 */
public final class L0403_04_ChunkedCommitBatchPriceDemo {

	private L0403_04_ChunkedCommitBatchPriceDemo() {
	}

	private static final int TOTAL_SKU = 5_000;     // 批量改价的 SKU 总数
	private static final int CHUNK = 500;           // 分片大小：每 500 个一提交
	private static final int POISON_INDEX = 3_200;  // 模拟脏数据：第 3200 个 SKU 改价时触发失败

	public static void main(String[] args) {
		EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(db);
		PlatformTransactionManager txManager = new DataSourceTransactionManager(db);
		TransactionTemplate txTemplate = new TransactionTemplate(txManager);

		BatchPriceService service = new BatchPriceService(jdbc, txTemplate);

		List<Long> skuIds = new ArrayList<>(TOTAL_SKU);
		for (int i = 0; i < TOTAL_SKU; i++) {
			skuIds.add(200_400_000L + i);
		}

		System.out.println("==================== 场景 A：单个大事务（一行失败全量回滚）====================");
		initSchema(jdbc, skuIds);
		try {
			service.repriceOneBigTx(skuIds, POISON_INDEX);
			System.out.println("（大事务提交成功——本不该到这）");
		}
		catch (RuntimeException ex) {
			System.out.println("第 " + POISON_INDEX + " 个 SKU 失败 → 大事务回滚：" + ex.getMessage());
		}
		System.out.println("已改价行数 = " + repricedCount(jdbc)
				+ " / " + TOTAL_SKU + "   ❌ 期望 0：一个事务一损俱损，前 " + POISON_INDEX + " 条全白做");

		System.out.println();
		System.out.println("==================== 场景 B：编程式分片提交（每 " + CHUNK + " 个一提交）====================");
		initSchema(jdbc, skuIds);
		int totalChunks = (TOTAL_SKU + CHUNK - 1) / CHUNK;
		int committed = service.repriceChunked(skuIds, CHUNK, POISON_INDEX);
		System.out.println("成功提交分片数 = " + committed + " / " + totalChunks
				+ "，已改价行数 = " + repricedCount(jdbc) + " / " + TOTAL_SKU);
		System.out.println("→ 只有命中脏数据的那一片（[" + (POISON_INDEX / CHUNK * CHUNK) + ","
				+ (POISON_INDEX / CHUNK * CHUNK + CHUNK) + ")）回滚损失 " + CHUNK + " 行，"
				+ "其余 " + committed + " 片（" + repricedCount(jdbc) + " 行）全部落库 ✅ 失败只损失一片，可从断点续跑");

		System.out.println();
		System.out.println("结论：超大批量写别用一个大事务（锁持有 + undo 膨胀 + 全量回滚三宗罪）；"
				+ "编程式 TransactionTemplate 分片提交把事务粒度切到「一片」，"
				+ "锁持有窗口与回滚成本恒定在 CHUNK 量级——这是声明式注解给不了的边界控制力。");

		db.shutdown();
	}

	private static void initSchema(JdbcTemplate jdbc, List<Long> skuIds) {
		jdbc.execute("drop table if exists price");
		jdbc.execute("create table price(sku_id bigint primary key, amount int, repriced boolean default false)");
		jdbc.batchUpdate("insert into price(sku_id, amount) values (?, ?)",
				skuIds.stream().map(id -> new Object[] {id, 18}).collect(java.util.stream.Collectors.toList()));
	}

	private static int repricedCount(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select count(*) from price where repriced = true", Integer.class);
		return n == null ? 0 : n;
	}

	static final class BatchPriceService {

		private final JdbcTemplate jdbc;
		private final TransactionTemplate txTemplate;

		BatchPriceService(JdbcTemplate jdbc, TransactionTemplate txTemplate) {
			this.jdbc = jdbc;
			this.txTemplate = txTemplate;
		}

		/** 坏：一个大事务圈住全部 N 行——锁持有到最后，任意一行失败则全量回滚。 */
		void repriceOneBigTx(List<Long> skuIds, int poisonIndex) {
			txTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					for (int i = 0; i < skuIds.size(); i++) {
						repriceOne(skuIds.get(i), i == poisonIndex);
					}
				}
			});
		}

		/**
		 * 好：分片提交——每 chunk 个 SKU 一个独立小事务。已提交的分片不回滚，失败只损失当前分片。
		 *
		 * @return 成功提交的分片数
		 */
		int repriceChunked(List<Long> skuIds, int chunk, int poisonIndex) {
			int committedChunks = 0;
			for (int from = 0; from < skuIds.size(); from += chunk) {
				int to = Math.min(from + chunk, skuIds.size());
				int chunkFrom = from;
				int chunkTo = to;
				try {
					txTemplate.execute(new TransactionCallbackWithoutResult() {
						@Override
						protected void doInTransactionWithoutResult(TransactionStatus status) {
							for (int i = chunkFrom; i < chunkTo; i++) {
								repriceOne(skuIds.get(i), i == poisonIndex);
							}
						}
					});
					committedChunks++;
				}
				catch (RuntimeException ex) {
					System.out.println("  · 分片 [" + chunkFrom + "," + chunkTo + ") 失败 → 仅本片回滚，已提交分片不受影响："
							+ ex.getMessage());
					// 生产里：记录断点 offset，修脏数据后从该分片续跑
				}
			}
			return committedChunks;
		}

		private void repriceOne(long skuId, boolean poison) {
			if (poison) {
				throw new IllegalStateException("SKU " + skuId + " 价格规则冲突（脏数据）");
			}
			jdbc.update("update price set amount = amount + 2, repriced = true where sku_id = ?", skuId);
		}
	}
}
