package com.leilei.lab.laboratory.l04.l04_03;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 📖 知识点：[[L04-03-编程式事务与大事务治理#2. 🏭 生产怎么用对]]（TransactionTemplate 编程式事务）
 * 🎯 作用：把 {@link TransactionTemplate} 的三种基本姿势用真实可回滚的 HSQLDB 跑出来：
 *         ① {@code execute(TransactionCallback<T>)} 有返回值——回调里写库、返回业务结果，模板负责提交/回滚；
 *         ② {@code execute(TransactionCallbackWithoutResult)} 无返回值——纯写操作的语义糖；
 *         ③ 回调里 {@link TransactionStatus#setRollbackOnly()} ——【不抛异常】也能让事务回滚，
 *            这是编程式相对声明式 {@code @Transactional}（默认必须抛 RuntimeException/Error 才回滚）的关键差异：
 *            业务上「库存不足」是正常分支而非异常，编程式可以「打标回滚 + 正常返回失败结果」，不必为了回滚强行抛异常。
 *         模板的传播/隔离/超时都通过 setter 编程式设置（TransactionTemplate 本身就是一个 TransactionDefinition）。
 * 🔗 业务场景：C 端下单扣库存——扣减是写、结果要回传（剩余库存/是否成功）。库存不足是高频正常分支，
 *         用编程式 setRollbackOnly 让这条「失败但不异常」的路径干净回滚，避免为流程控制滥用异常（异常昂贵且污染监控）。
 */
public final class L0403_01_TransactionTemplateBasicsDemo {

	private L0403_01_TransactionTemplateBasicsDemo() {
	}

	private static final long HOT_SKU = 200301L;   // 多肉葡萄·大杯，秒杀热点 SKU

	public static void main(String[] args) {
		EmbeddedDatabase dataSource = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		PlatformTransactionManager txManager = new DataSourceTransactionManager(dataSource);

		// 编程式事务的入口：手动 new 出来、显式设置事务定义（这就是「编程式」——边界全在你手里）
		TransactionTemplate txTemplate = new TransactionTemplate(txManager);
		txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		txTemplate.setTimeout(3);

		jdbc.execute("create table inventory(sku_id bigint primary key, stock int)");
		jdbc.update("insert into inventory(sku_id, stock) values (?, ?)", HOT_SKU, 5);

		InventoryService inventory = new InventoryService(jdbc, txTemplate);

		System.out.println("初始库存 = " + currentStock(jdbc) + "（SKU=" + HOT_SKU + "）");

		System.out.println();
		System.out.println("==================== ① execute(TransactionCallback<T>)：有返回值 ====================");
		int remain = inventory.deductReturningRemain(2);
		System.out.println("扣减 2 → 返回剩余库存 = " + remain + "，DB 实读 = " + currentStock(jdbc) + "   ✅ 已提交");

		System.out.println();
		System.out.println("==================== ② TransactionCallbackWithoutResult：无返回值（纯写）====================");
		inventory.deductWithoutResult(1);
		System.out.println("扣减 1 → DB 实读 = " + currentStock(jdbc) + "   ✅ 已提交");

		System.out.println();
		System.out.println("==================== ③ setRollbackOnly()：不抛异常也回滚 ====================");
		System.out.println("此刻库存 = " + currentStock(jdbc) + "，请求扣减 10（超卖）");
		DeductResult result = inventory.deductOrMarkRollback(10);
		System.out.println("返回业务结果 = " + result + "（注意：没有抛任何异常）");
		System.out.println("DB 实读 = " + currentStock(jdbc) + "   ✅ 期望不变：setRollbackOnly 让本次写回滚，但流程正常返回失败");

		System.out.println();
		System.out.println("结论：编程式事务把「提交/回滚」的决策权交回你手里——"
				+ "库存不足这种【正常业务失败】用 setRollbackOnly 干净回滚，不必为了触发回滚而强行抛异常。");

		dataSource.shutdown();
	}

	private static int currentStock(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select stock from inventory where sku_id = ?", Integer.class, HOT_SKU);
		return n == null ? 0 : n;
	}

	/** 编程式事务版库存服务：构造时注入 TransactionTemplate，事务边界完全由代码圈定。 */
	static final class InventoryService {

		private final JdbcTemplate jdbc;
		private final TransactionTemplate txTemplate;

		InventoryService(JdbcTemplate jdbc, TransactionTemplate txTemplate) {
			this.jdbc = jdbc;
			this.txTemplate = txTemplate;
		}

		/** ① 有返回值：回调里扣减并回读剩余库存，由模板统一提交。 */
		int deductReturningRemain(int qty) {
			return txTemplate.execute(new TransactionCallback<Integer>() {
				@Override
				public Integer doInTransaction(TransactionStatus status) {
					jdbc.update("update inventory set stock = stock - ? where sku_id = ?", qty, HOT_SKU);
					return jdbc.queryForObject("select stock from inventory where sku_id = ?", Integer.class, HOT_SKU);
				}
			});
		}

		/** ② 无返回值：纯写操作用 TransactionCallbackWithoutResult，省掉 return null 的噪音。 */
		void deductWithoutResult(int qty) {
			txTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					jdbc.update("update inventory set stock = stock - ? where sku_id = ?", qty, HOT_SKU);
				}
			});
		}

		/**
		 * ③ 不抛异常的回滚：先乐观扣减，发现库存为负（超卖）就 setRollbackOnly，
		 * 让本次写回滚，同时把「失败」作为正常结果返回——不污染异常通道。
		 */
		DeductResult deductOrMarkRollback(int qty) {
			return txTemplate.execute(new TransactionCallback<DeductResult>() {
				@Override
				public DeductResult doInTransaction(TransactionStatus status) {
					jdbc.update("update inventory set stock = stock - ? where sku_id = ?", qty, HOT_SKU);
					Integer after = jdbc.queryForObject("select stock from inventory where sku_id = ?", Integer.class, HOT_SKU);
					if (after == null || after < 0) {
						status.setRollbackOnly();   // 不抛异常，仅标记回滚
						return DeductResult.INSUFFICIENT;
					}
					return DeductResult.SUCCESS;
				}
			});
		}
	}

	enum DeductResult {
		SUCCESS, INSUFFICIENT
	}
}
