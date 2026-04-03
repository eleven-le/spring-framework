package org.springframework.lab.txevent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 实验 3 + 实验 5 — BEFORE_COMMIT 写 DB vs AFTER_COMMIT 写 DB 对比。
 *
 * <p><b>BEFORE_COMMIT 写 DB</b>: 仍在原事务内，INSERT 会随主事务一起提交或回滚。
 * 如果 BEFORE_COMMIT 抛异常 → 主事务回滚！因此 BEFORE_COMMIT 适合
 * 「必须与主事务同生共死」的操作（审计日志、数据校验补充）。
 *
 * <p><b>AFTER_COMMIT 写 DB</b>: 事务已提交，但事务资源(Connection)仍活跃。
 * 此时执行 INSERT/UPDATE 虽然 SQL 会发出去，但不会被自动 commit，
 * 因为 Connection 仍绑定在已结束的事务上下文中（autoCommit 被 Spring 关闭了）。
 * <b>必须用 REQUIRES_NEW 传播行为开新事务!</b>
 *
 * <p>源码依据: TransactionSynchronization 接口 Javadoc 明确指出:
 * "Use PROPAGATION_REQUIRES_NEW for any transactional operation that is called from here."
 */
@Component
public class BeforeCommitWriteListener {

	private final JdbcTemplate jdbc;

	public BeforeCommitWriteListener(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** BEFORE_COMMIT: 在原事务内追加审计记录 — 随主事务一起提交 */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void auditBeforeCommit(OrderPlacedEvent event) {
		if ("before-commit".equals(event.getScenario())) {
			jdbc.update("INSERT INTO audit_log(order_id, action, phase) VALUES(?, ?, ?)",
					event.getOrderId(), "审计记录(BEFORE_COMMIT追加)", "BEFORE_COMMIT");
			System.out.println("    ★ [BEFORE_COMMIT] 追加审计记录, 仍在原事务内, txActive="
					+ TransactionSynchronizationManager.isActualTransactionActive());
		}
	}

	/**
	 * AFTER_COMMIT: 演示直接写 DB 的陷阱。
	 *
	 * 注意: 这里故意不用 REQUIRES_NEW 来展示问题。
	 * 在 H2 内存数据库中，行为可能因 JDBC 驱动实现而异，
	 * 但在生产级 MySQL/PostgreSQL 中，不开新事务的写操作
	 * 要么不生效（autoCommit=false 且无后续 commit），要么报错。
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void writeAfterCommit(OrderPlacedEvent event) {
		if ("after-commit-write".equals(event.getScenario())) {
			System.out.println("    ★ [AFTER_COMMIT] 尝试写 DB(无 REQUIRES_NEW)");
			System.out.println("      ⚠ 警告: 此时事务已提交, Connection 仍绑定旧事务上下文");
			System.out.println("      ⚠ 生产环境中: 必须用 @Transactional(propagation=REQUIRES_NEW) 的新 Service 方法!");
			// 在 H2 中会自动 commit（因为 H2 驱动特性），但在 MySQL 中可能不会!
			jdbc.update("INSERT INTO audit_log(order_id, action, phase) VALUES(?, ?, ?)",
					event.getOrderId(), "AFTER_COMMIT直接写(有风险)", "AFTER_COMMIT");
		}
	}
}
