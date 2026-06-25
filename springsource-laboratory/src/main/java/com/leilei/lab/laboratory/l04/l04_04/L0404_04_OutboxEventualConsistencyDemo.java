package com.leilei.lab.laboratory.l04.l04_04;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 📖 知识点：[[L04-04-多数据源与分布式事务边界#2.4 分布式事务边界：本地消息表跨过单机事务的天花板]]（分布式事务边界·消息最终一致）
 * 🎯 作用：把「跨两个物理库的一致性」这件事讲透——单机事务（一个 PlatformTransactionManager / 一条连接）
 *         **物理上管不了第二个库**（L0404_02 已证），这就是「分布式事务边界」。两种跨越方式：
 *         ① 强一致：上 Seata（AT/XA）做全局事务，代价是全局锁 + TC 协调 + 侵入，吞吐与复杂度都涨；
 *         ② 最终一致（本类实操）：**本地消息表（outbox）**——把「写订单」与「记一条待办消息」放进**订单库的同一个本地事务**
 *            （这一步是原子的、可靠的），再由 {@code afterCommit} 触发的中继 / 定时补偿任务把消息**投递到库存库**完成扣减，
 *            失败就靠 outbox 里残留的 PENDING 记录重试，直到最终一致。本类用两个物理 HSQLDB + 故障注入跑出全过程。
 * 🔗 业务场景：古茗下单——订单落「订单库」、库存扣「库存库」，两库分属不同集群，一个 @Transactional 盖不住。
 *         裸调两库会「有单无扣减」（超发）；用本地消息表把跨库一致性降级为最终一致，中继失败可重试、不丢不超发。
 */
public final class L0404_04_OutboxEventualConsistencyDemo {

	private L0404_04_OutboxEventualConsistencyDemo() {
	}

	private static final long SKU = 200404L;   // 芝芝芒芒·大杯

	/** 故障注入：让库存库的第一次扣减失败一次，模拟跨库投递抖动，验证 outbox 重试达成最终一致。 */
	private static boolean injectInventoryFailureOnce = false;

	public static void main(String[] args) {
		EmbeddedDatabase orderDb = newOrderDb();
		EmbeddedDatabase inventoryDb = newInventoryDb();

		JdbcTemplate orderJdbc = new JdbcTemplate(orderDb);
		JdbcTemplate invJdbc = new JdbcTemplate(inventoryDb);
		PlatformTransactionManager orderTm = new DataSourceTransactionManager(orderDb);
		PlatformTransactionManager invTm = new DataSourceTransactionManager(inventoryDb);
		TransactionTemplate orderTx = new TransactionTemplate(orderTm);
		TransactionTemplate invTx = new TransactionTemplate(invTm);

		OrderService service = new OrderService(orderJdbc, invJdbc, orderTx, invTx);

		System.out.println("==================== 场景 A（坏）：裸调两库，第二库失败 → 有单无扣减 ====================");
		injectInventoryFailureOnce = true;
		try {
			service.placeOrderNaive("GM-NAIVE-001");
		}
		catch (RuntimeException ex) {
			System.out.println("库存库扣减失败：" + ex.getMessage());
		}
		System.out.println("订单库 orders = " + orderCount(orderJdbc) + "（订单已提交，回滚不了）");
		System.out.println("库存库 stock = " + stock(invJdbc) + "（未扣减）→ ❌ 有单无扣减，跨库不一致 / 超发");
		injectInventoryFailureOnce = false;

		System.out.println();
		System.out.println("==================== 场景 B（最终一致）：本地消息表 + afterCommit 中继 + 重试 ====================");
		invJdbc.update("update inventory set stock = 1000 where sku_id = ?", SKU);   // 复位库存

		injectInventoryFailureOnce = true;   // 第一次中继投递失败
		service.placeOrderWithOutbox("GM-OUTBOX-002");
		System.out.println("[下单提交后] 订单库 orders = " + orderCount(orderJdbc)
				+ "，outbox 待办 = " + pendingOutbox(orderJdbc)
				+ "，库存库 stock = " + stock(invJdbc));
		System.out.println("  → 临时不一致：订单已落、库存未扣，但 outbox 里有一条 PENDING「有据可查」，不会丢。");

		System.out.println();
		System.out.println("[定时补偿任务扫描 PENDING 并重试投递]");
		service.relayPendingOutbox();   // 模拟定时任务再次中继，这次成功
		System.out.println("[补偿后]     订单库 orders = " + orderCount(orderJdbc)
				+ "，outbox 待办 = " + pendingOutbox(orderJdbc)
				+ "，库存库 stock = " + stock(invJdbc));
		System.out.println("  → ✅ 最终一致：库存已扣减到 999，outbox 标记 DONE（待办归零），订单与库存对齐。");

		System.out.println();
		System.out.println("结论：跨物理库无法靠一个本地事务原子完成（分布式事务边界）。本地消息表把跨库一致性降级为"
				+ "最终一致——「写订单 + 记 outbox」用订单库本地事务保证原子，投递失败靠 PENDING 重试兜底；"
				+ "保证「不丢不超发」，代价是放弃强一致（有短暂不一致窗口）。要强一致才上 Seata，代价是全局锁与协调开销。");

		orderDb.shutdown();
		inventoryDb.shutdown();
	}

	static final class OrderService {

		private final JdbcTemplate orderJdbc;
		private final JdbcTemplate invJdbc;
		private final TransactionTemplate orderTx;
		private final TransactionTemplate invTx;

		OrderService(JdbcTemplate orderJdbc, JdbcTemplate invJdbc, TransactionTemplate orderTx, TransactionTemplate invTx) {
			this.orderJdbc = orderJdbc;
			this.invJdbc = invJdbc;
			this.orderTx = orderTx;
			this.invTx = invTx;
		}

		/** 坏：以为「订单库事务 + 库存库事务」能一起成败——其实是两个独立本地事务，第二个失败时第一个早提交了。 */
		void placeOrderNaive(String orderId) {
			this.orderTx.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					orderJdbc.update("insert into orders(id, sku_id, status) values (?, ?, 'PAID')", orderId, SKU);
				}
			});
			// 上面这个事务已经 commit；下面是另一个库的另一个事务，失败也回滚不了上面
			deductInventory(1);
		}

		/**
		 * 好：订单库本地事务里【原子地】写订单 + 记一条 outbox（PENDING）；
		 * 提交成功后用 afterCommit 立刻中继一次（快路径），失败则留给定时补偿任务。
		 */
		void placeOrderWithOutbox(String orderId) {
			this.orderTx.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					orderJdbc.update("insert into orders(id, sku_id, status) values (?, ?, 'PAID')", orderId, SKU);
					orderJdbc.update("insert into outbox(order_id, sku_id, qty, status) values (?, ?, 1, 'PENDING')", orderId, SKU);

					// 提交成功后立刻试投一次（快路径）；失败不抛出去影响下单，等定时任务重试
					if (TransactionSynchronizationManager.isSynchronizationActive()) {
						TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
							@Override
							public void afterCommit() {
								try {
									relayOne(orderId);
								}
								catch (RuntimeException ex) {
									System.out.println("  · afterCommit 中继投递失败（" + ex.getMessage()
											+ "），outbox 保留 PENDING 待补偿任务重试");
								}
							}
						});
					}
				}
			});
		}

		/** 定时补偿任务：扫描所有 PENDING 的 outbox，逐条重试投递到库存库。 */
		void relayPendingOutbox() {
			List<String> pending = this.orderJdbc.queryForList(
					"select order_id from outbox where status = 'PENDING'", String.class);
			for (String orderId : pending) {
				relayOne(orderId);
			}
		}

		/** 单条中继：库存库本地事务扣减 → 成功后把订单库的 outbox 标记 DONE（幂等：按 order_id）。 */
		private void relayOne(String orderId) {
			deductInventory(1);   // 投递到库存库（可能因抖动失败，抛异常则 outbox 维持 PENDING）
			this.orderTx.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					orderJdbc.update("update outbox set status = 'DONE' where order_id = ?", orderId);
				}
			});
		}

		/** 库存库扣减，带一次性故障注入，模拟跨库投递抖动。 */
		private void deductInventory(int qty) {
			this.invTx.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					if (injectInventoryFailureOnce) {
						injectInventoryFailureOnce = false;
						throw new IllegalStateException("库存库连接抖动");
					}
					invJdbc.update("update inventory set stock = stock - ? where sku_id = ?", qty, SKU);
				}
			});
		}
	}

	private static int orderCount(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select count(*) from orders", Integer.class);
		return n == null ? 0 : n;
	}

	private static int pendingOutbox(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select count(*) from outbox where status = 'PENDING'", Integer.class);
		return n == null ? 0 : n;
	}

	private static int stock(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select stock from inventory where sku_id = ?", Integer.class, SKU);
		return n == null ? -1 : n;
	}

	private static EmbeddedDatabase newOrderDb() {
		EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(db);
		jdbc.execute("create table orders(id varchar(64) primary key, sku_id bigint, status varchar(16))");
		jdbc.execute("create table outbox(order_id varchar(64) primary key, sku_id bigint, qty int, status varchar(16))");
		return db;
	}

	private static EmbeddedDatabase newInventoryDb() {
		EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(db);
		jdbc.execute("create table inventory(sku_id bigint primary key, stock int)");
		jdbc.update("insert into inventory(sku_id, stock) values (?, ?)", SKU, 1000);
		return db;
	}
}
