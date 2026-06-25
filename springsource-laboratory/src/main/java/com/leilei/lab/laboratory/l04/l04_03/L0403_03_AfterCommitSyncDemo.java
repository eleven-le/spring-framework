package com.leilei.lab.laboratory.l04.l04_03;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * 📖 知识点：[[L04-03-编程式事务与大事务治理#3. 🧨 事故与避坑]]（大事务治理：副作用移出事务边界）
 * 🎯 作用：用 {@link TransactionSynchronizationManager#registerSynchronization} 注册
 *         {@link TransactionSynchronization}，把「发 MQ / 清缓存」这类外部副作用挂到
 *         {@link TransactionSynchronization#afterCommit()}——【事务提交成功之后】才执行。一举两得：
 *         ① 治大事务：发 MQ / 删 Redis 这些远程 IO 不再占着数据库连接与行锁（移出持有窗口）；
 *         ② 保一致性：事务若回滚，afterCommit 不触发，绝不会出现「DB 没写成，MQ 却发了 / 缓存却清了」的脏副作用。
 *         对照「在事务内直接发 MQ」的双重恶果：既拉长持有时间，又制造「幽灵消息」。
 * 🔗 业务场景：C 端下单成功后要：发「下单成功」MQ（触发积分/优惠券到账）+ 清商品详情缓存。
 *         若把这两步写在事务内：一旦库存终检失败回滚，用户已收到到账通知、缓存已被击穿，客诉与超发并发。
 *         正确姿势：DB 写在事务内，MQ/清缓存注册到 afterCommit，提交后才发——下单链路里最常用的一招。
 */
public final class L0403_03_AfterCommitSyncDemo {

	private L0403_03_AfterCommitSyncDemo() {
	}

	private static final long SKU = 200303L;   // 多肉杨梅·大杯

	/** 模拟下游 MQ：afterCommit 里投递的「下单成功」消息会落到这里。 */
	private static final List<String> MQ_TOPIC_ORDER_PAID = new CopyOnWriteArrayList<>();

	public static void main(String[] args) {
		EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(db);
		PlatformTransactionManager txManager = new DataSourceTransactionManager(db);
		TransactionTemplate txTemplate = new TransactionTemplate(txManager);

		jdbc.execute("create table orders(id varchar(64) primary key, sku_id bigint)");

		OrderService service = new OrderService(jdbc, txTemplate);

		System.out.println("==================== 场景 A：提交成功 → afterCommit 发 MQ ====================");
		MQ_TOPIC_ORDER_PAID.clear();
		service.placeOrder("GM-OK-001", false);
		System.out.println("orders 行数 = " + count(jdbc) + "（已提交）");
		System.out.println("MQ 收到消息 = " + MQ_TOPIC_ORDER_PAID + "   ✅ 提交后才发，时序正确");

		System.out.println();
		System.out.println("==================== 场景 B：事务回滚 → afterCommit 不触发，无幽灵消息 ====================");
		MQ_TOPIC_ORDER_PAID.clear();
		try {
			service.placeOrder("GM-FAIL-002", true);
		}
		catch (IllegalStateException expected) {
			System.out.println("下单按预期失败回滚：" + expected.getMessage());
		}
		System.out.println("orders 行数 = " + count(jdbc) + "（GM-FAIL-002 未写入）");
		System.out.println("MQ 收到消息 = " + MQ_TOPIC_ORDER_PAID
				+ "   ✅ 期望空：回滚时 afterCommit 不执行，绝不会发出「订单已支付」的幽灵消息");

		System.out.println();
		System.out.println("结论：副作用（发 MQ / 清缓存 / 调下游）一律注册到 afterCommit——"
				+ "既把远程 IO 移出事务持有窗口（治大事务），又天然杜绝「DB 回滚但消息已发」的不一致。");

		db.shutdown();
	}

	private static int count(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select count(*) from orders", Integer.class);
		return n == null ? 0 : n;
	}

	static final class OrderService {

		private final JdbcTemplate jdbc;
		private final TransactionTemplate txTemplate;

		OrderService(JdbcTemplate jdbc, TransactionTemplate txTemplate) {
			this.jdbc = jdbc;
			this.txTemplate = txTemplate;
		}

		/**
		 * 事务内只做 DB 写 + 注册同步；MQ 投递推迟到 afterCommit。
		 *
		 * @param failAfterWrite true=写完后抛异常触发回滚，用于验证 afterCommit 不被触发
		 */
		void placeOrder(String orderId, boolean failAfterWrite) {
			txTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					jdbc.update("insert into orders(id, sku_id) values (?, ?)", orderId, SKU);

					// 把「发 MQ」推迟到事务提交成功之后——只在同步开启时注册（事务上下文内必然开启）
					if (TransactionSynchronizationManager.isSynchronizationActive()) {
						TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
							@Override
							public void afterCommit() {
								MQ_TOPIC_ORDER_PAID.add("ORDER_PAID:" + orderId);   // 提交后才发，不占事务窗口
							}

							@Override
							public void afterCompletion(int statusCode) {
								if (statusCode == STATUS_ROLLED_BACK) {
									System.out.println("  · afterCompletion(ROLLED_BACK)：事务回滚，跳过 MQ 投递（无幽灵消息）");
								}
							}
						});
					}

					if (failAfterWrite) {
						throw new IllegalStateException("库存终检失败，订单 " + orderId + " 回滚");
					}
				}
			});
		}
	}
}
