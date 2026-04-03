package org.springframework.lab.txevent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 业务服务 — 在事务中发布事件，配合各实验场景。
 *
 * <p>关键理解点:
 * publishEvent() 是同步调用，事件立刻派发到所有监听器。
 * 普通 @EventListener 在 publishEvent 内部就已执行完毕。
 * @TransactionalEventListener 的 onApplicationEvent 则只是「注册一个 TransactionSynchronization」
 * 到 TransactionSynchronizationManager，真正的 processEvent 延迟到事务提交/回滚阶段才执行。
 *
 * <p>所以: publishEvent 返回时，@TransactionalEventListener 的方法体还没执行！
 */
@Service
public class OrderService {

	private final JdbcTemplate jdbc;
	private final ApplicationEventPublisher publisher;

	public OrderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
		this.jdbc = jdbc;
		this.publisher = publisher;
	}

	// ─── 实验 1: 正常提交 → 四阶段触发顺序 ───
	@Transactional
	public void placeOrderSuccess(String orderId, String item, int price) {
		jdbc.update("INSERT INTO orders(order_id, item, price) VALUES(?, ?, ?)", orderId, item, price);
		System.out.println("    [OrderService] INSERT 完成, txActive="
				+ TransactionSynchronizationManager.isActualTransactionActive());
		publisher.publishEvent(new OrderPlacedEvent(orderId, item, price, "commit"));
		System.out.println("    [OrderService] publishEvent 返回(注意: AFTER_COMMIT 监听器此时还没执行!)");
	}

	// ─── 实验 2: 回滚 → AFTER_ROLLBACK/AFTER_COMPLETION 触发 ───
	@Transactional
	public void placeOrderFail(String orderId, String item, int price) {
		jdbc.update("INSERT INTO orders(order_id, item, price) VALUES(?, ?, ?)", orderId, item, price);
		System.out.println("    [OrderService] INSERT 完成, 即将抛异常触发回滚...");
		publisher.publishEvent(new OrderPlacedEvent(orderId, item, price, "rollback"));
		throw new RuntimeException("模拟业务异常 → 触发事务回滚");
	}

	// ─── 实验 3: BEFORE_COMMIT 追加操作（仍在事务内） ───
	@Transactional
	public void placeOrderWithBeforeCommit(String orderId, String item, int price) {
		jdbc.update("INSERT INTO orders(order_id, item, price) VALUES(?, ?, ?)", orderId, item, price);
		publisher.publishEvent(new OrderPlacedEvent(orderId, item, price, "before-commit"));
		System.out.println("    [OrderService] 事务即将提交(BEFORE_COMMIT 监听器将在提交前触发)");
	}

	// ─── 实验 4: 无事务场景 → fallbackExecution ───
	// 注意: 没有 @Transactional
	public void placeOrderNoTx(String orderId, String item, int price) {
		jdbc.update("INSERT INTO orders(order_id, item, price) VALUES(?, ?, ?)", orderId, item, price);
		System.out.println("    [OrderService] INSERT 完成(无事务), txActive="
				+ TransactionSynchronizationManager.isActualTransactionActive());
		publisher.publishEvent(new OrderPlacedEvent(orderId, item, price, "no-tx"));
	}

	// ─── 实验 5: AFTER_COMMIT 中写 DB — 必须 REQUIRES_NEW ───
	@Transactional
	public void placeOrderWithAfterCommitWrite(String orderId, String item, int price) {
		jdbc.update("INSERT INTO orders(order_id, item, price) VALUES(?, ?, ?)", orderId, item, price);
		publisher.publishEvent(new OrderPlacedEvent(orderId, item, price, "after-commit-write"));
	}

	// ─── 实验 6: 一次事务发布多个事件 → 多个 Synchronization 注册 ───
	@Transactional
	public void placeOrderMultiEvents(String orderId, String item, int price) {
		jdbc.update("INSERT INTO orders(order_id, item, price) VALUES(?, ?, ?)", orderId, item, price);
		System.out.println("    [OrderService] 在同一事务中发布 3 个事件...");
		publisher.publishEvent(new OrderPlacedEvent(orderId, item + "-事件1", price, "multi-1"));
		publisher.publishEvent(new OrderPlacedEvent(orderId, item + "-事件2", price, "multi-2"));
		publisher.publishEvent(new OrderPlacedEvent(orderId, item + "-事件3", price, "multi-3"));
		System.out.println("    [OrderService] 3 个 publishEvent 均已返回(注册了 3 个 Synchronization)");
	}
}
