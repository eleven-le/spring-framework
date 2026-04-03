/*
package org.springframework.lab.aftercommit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

*/
/**
 * W47 — 事务后发布与最终一致性：AFTER_COMMIT + 幂等 + 重试
 *
 * <h2>一句话抽象</h2>
 * <b>最终一致性 = AFTER_COMMIT 确保"只在事务成功后才触发副作用" + 幂等确保"重复消费不产生重复效果"
 * + 重试确保"暂时性失败可恢复"</b>
 * ——核心矛盾是<i>分布式系统中无法同时保证事务原子性与跨服务操作的即时一致性</i>，
 * 解法是<b>本地事务 + 事件驱动 + 至少一次投递 + 消费端幂等</b>构成的最终一致性闭环。
 *
 * <h2>为什么 W21 之后还需要 W47？</h2>
 * W21 解决了"事件什么时候触发"（四阶段），W47 解决"触发之后怎么保证不丢、不重、可恢复"。
 * 只有 AFTER_COMMIT 不够 —— 还差: ① 消息可能丢失 ② 消息可能重复 ③ 消费可能失败。
 *
 * <h2>实验列表</h2>
 * <ol>
 *   <li>实验 1: AFTER_COMMIT 正常发布 — 提交后才触发消费</li>
 *   <li>实验 2: 事务回滚 — AFTER_COMMIT 不触发，事件自然不发送</li>
 *   <li>实验 3: 幂等去重 — 同一 eventId 重复消费被拦截</li>
 *   <li>实验 4: 消费失败 + 重试 — 前 2 次失败，第 3 次成功</li>
 *   <li>实验 5: Outbox 模式 — 业务写 + outbox 写原子提交，AFTER_COMMIT 触发投递</li>
 * </ol>
 *
 * <h2>核心调用链（10 步）</h2>
 * <pre>
 *  ①  OrderProducer#createOrder(@Transactional) : 业务 INSERT + 生成 eventId
 *  ②  ApplicationEventPublisher#publishEvent : 同步派发事件到所有 listener
 *  ③  TransactionalApplicationListenerMethodAdapter#onApplicationEvent : 判断有事务 → 注册 Synchronization
 *  ④  TransactionSynchronizationManager#registerSynchronization : 把 TxAppListenerSync 挂到当前线程事务同步链
 *  ⑤  AbstractPlatformTransactionManager#processCommit : doCommit 成功后进入后置回调
 *  ⑥  triggerAfterCommit → TransactionSynchronizationUtils#triggerAfterCommit : 遍历同步器调 afterCommit（空实现）
 *  ⑦  triggerAfterCompletion(STATUS_COMMITTED) : 遍历同步器调 afterCompletion
 *  ⑧  TransactionalApplicationListenerSynchronization#afterCompletion : phase==AFTER_COMMIT && status==COMMITTED → processEventWithCallbacks
 *  ⑨  processEventWithCallbacks → listener.processEvent : 反射调用 IdempotentConsumer#handleOrderCreated
 *  ⑩  IdempotentConsumer: 去重检查 → 幂等业务处理 → 写去重表（REQUIRES_NEW 独立事务）
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>TransactionalApplicationListenerMethodAdapter#onApplicationEvent L90 — 看注册 Synchronization 时机</li>
 *   <li>AbstractPlatformTransactionManager#processCommit L783 — triggerAfterCommit 入口</li>
 *   <li>TransactionalApplicationListenerSynchronization#afterCompletion L66 — AFTER_COMMIT && STATUS_COMMITTED 分支</li>
 *   <li>IdempotentConsumer#handleOrderCreated — 去重检查入口，观察 REQUIRES_NEW 开启新事务</li>
 *   <li>OutboxPublisher#publishFromOutbox — outbox 投递与状态流转</li>
 * </ol>
 *//*

public class AfterCommitMain {

	public static void main(String[] args) {
		System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
		System.out.println("║  W47 — 事务后发布与最终一致性: AFTER_COMMIT + 幂等 + 重试              ║");
		System.out.println("╚══════════════════════════════════════════════════════════════════════╝\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AfterCommitConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		OrderProducer producer = ctx.getBean(OrderProducer.class);

		initTables(jdbc);

		// ========== 实验 1: AFTER_COMMIT 正常发布 ==========
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 1: AFTER_COMMIT 正常发布 — 事务提交后才触发幂等消费");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  核心: publishEvent 时只注册 Synchronization, 不执行消费逻辑");
		System.out.println("  断点: TxAppListenerMethodAdapter#onApplicationEvent L90\n");
		resetData(jdbc);
		producer.createOrder("ORD-001", "iPhone 16 Pro", 9999);
		System.out.println("  [验证] orders=" + count(jdbc, "orders") + "(期望1)"
				+ ", consumed=" + count(jdbc, "consumed_events") + "(期望1)");

		// ========== 实验 2: 事务回滚 — AFTER_COMMIT 不触发 ==========
		System.out.println("\n═══════════════════════════════════════════════════════════════");
		System.out.println("实验 2: 事务回滚 — AFTER_COMMIT 不触发, 事件自然不发送");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  核心: 这就是 AFTER_COMMIT 的一致性保证 — 只要事务没成功，副作用不会触发");
		System.out.println("  断点: APTM#processRollback → triggerAfterCompletion(STATUS_ROLLED_BACK)\n");
		resetData(jdbc);
		try {
			producer.createOrderThenFail("ORD-002", "MacBook Pro", 14999);
		}
		catch (RuntimeException e) {
			System.out.println("  [捕获异常] " + e.getMessage());
		}
		System.out.println("  [验证] orders=" + count(jdbc, "orders") + "(期望0, 已回滚)"
				+ ", consumed=" + count(jdbc, "consumed_events") + "(期望0, AFTER_COMMIT 未触发)");

		// ========== 实验 3: 幂等去重 — 同一 eventId 重复消费被拦截 ==========
		System.out.println("\n═══════════════════════════════════════════════════════════════");
		System.out.println("实验 3: 幂等去重 — 同一 eventId 重复消费被 consumed_events 表拦截");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  核心: 至少一次投递 + 去重表 = 恰好一次语义");
		System.out.println("  真实场景: MQ 重复投递/Consumer 重启 replay\n");
		resetData(jdbc);
		producer.createOrderDuplicate("ORD-003", "AirPods Max", 4999);
		System.out.println("  [验证] orders=" + count(jdbc, "orders") + "(期望1)"
				+ ", consumed=" + count(jdbc, "consumed_events") + "(期望1, 第二次被去重拦截)");

		// ========== 实验 4: 消费失败 + 重试 ==========
		System.out.println("\n═══════════════════════════════════════════════════════════════");
		System.out.println("实验 4: 消费端失败 + 手动重试 — 模拟 MQ 重试机制");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  核心: 前 2 次失败抛异常, 第 3 次成功 → 重试 + 幂等 = 最终一致");
		System.out.println("  真实场景: RocketMQ 消费失败 → 重试队列 → 指数退避 → DLQ\n");
		resetData(jdbc);
		RetryState.INSTANCE.reset();
		String retryEventId = null;
		try {
			retryEventId = producer.createOrderForRetry("ORD-004", "Vision Pro", 29999);
		}
		catch (RuntimeException e) {
			System.out.println("  [第1次消费失败, 异常冒泡] " + e.getMessage());
		}
		// 模拟 MQ 重新投递: 手动重试（真实中由 MQ broker 触发）
		if (retryEventId != null) {
			System.out.println("  [模拟MQ重试] 第2次投递...");
			IdempotentConsumer consumer = ctx.getBean(IdempotentConsumer.class);
			try {
				consumer.handleOrderCreated(
						new OrderCreatedEvent(this, retryEventId, "ORD-004", "Vision Pro", 29999, "retry"));
			}
			catch (RuntimeException e) {
				System.out.println("  [第2次消费失败] " + e.getMessage());
			}
			System.out.println("  [模拟MQ重试] 第3次投递...");
			try {
				consumer.handleOrderCreated(
						new OrderCreatedEvent(this, retryEventId, "ORD-004", "Vision Pro", 29999, "retry"));
			}
			catch (RuntimeException e) {
				System.out.println("  [第3次消费失败] " + e.getMessage());
			}
		}
		System.out.println("  [验证] consumed=" + count(jdbc, "consumed_events")
				+ "(期望1, 第3次成功并记录去重)");

		// ========== 实验 5: Outbox 模式 ==========
		System.out.println("\n═══════════════════════════════════════════════════════════════");
		System.out.println("实验 5: Outbox 模式 — 业务写 + outbox 写原子提交");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  核心: 解决「事务提交成功但 MQ 发送失败」的经典问题");
		System.out.println("  流程: INSERT orders + INSERT outbox → commit → AFTER_COMMIT 投递 → 更新 outbox 状态");
		System.out.println("  兜底: 定时任务扫描 PENDING 状态的 outbox 记录补偿投递\n");
		resetData(jdbc);
		producer.createOrderWithOutbox("ORD-005", "Mac Studio", 19999);
		String outboxStatus = jdbc.queryForObject(
				"SELECT status FROM outbox WHERE event_id = (SELECT event_id FROM orders WHERE order_id = 'ORD-005')",
				String.class);
		System.out.println("  [验证] orders=" + count(jdbc, "orders") + "(期望1)"
				+ ", outbox.status=" + outboxStatus + "(期望SENT)");

		// ========== 总结 ==========
		System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
		System.out.println("║                        最终一致性闭环总结                              ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
		System.out.println("║  1. AFTER_COMMIT : 保证副作用只在事务成功后触发 (不触发 ≠ 一定触发)     ║");
		System.out.println("║  2. 幂等键+去重表 : 保证重复消费不产生重复效果 (at-least-once → exactly)  ║");
		System.out.println("║  3. 重试+指数退避 : 保证暂时性失败可恢复 (网络抖动/下游超时)              ║");
		System.out.println("║  4. Outbox/补偿   : 保证事件不丢失 (事务提交但回调失败的兜底)             ║");
		System.out.println("║  缺一不可! 只做 AFTER_COMMIT 不做去重/重试/补偿 = 伪最终一致性          ║");
		System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

		ctx.close();
		System.out.println("\n[完成] W47 事务后发布与最终一致性 全部实验执行完毕");
	}

	private static void initTables(JdbcTemplate jdbc) {
		jdbc.execute("CREATE TABLE IF NOT EXISTS orders("
				+ "id INT AUTO_INCREMENT PRIMARY KEY, "
				+ "order_id VARCHAR(50), item VARCHAR(100), price INT, event_id VARCHAR(64))");
		jdbc.execute("CREATE TABLE IF NOT EXISTS consumed_events("
				+ "id INT AUTO_INCREMENT PRIMARY KEY, "
				+ "event_id VARCHAR(64) UNIQUE, order_id VARCHAR(50), status VARCHAR(20))");
		jdbc.execute("CREATE TABLE IF NOT EXISTS outbox("
				+ "id INT AUTO_INCREMENT PRIMARY KEY, "
				+ "event_id VARCHAR(64) UNIQUE, event_type VARCHAR(50), "
				+ "payload TEXT, status VARCHAR(20))");
	}

	private static void resetData(JdbcTemplate jdbc) {
		jdbc.execute("DELETE FROM orders");
		jdbc.execute("DELETE FROM consumed_events");
		jdbc.execute("DELETE FROM outbox");
	}

	private static int count(JdbcTemplate jdbc, String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}
}
*/
