package org.springframework.lab.txsync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 业务 Service — 演示在事务方法中注册各种 TransactionSynchronization 回调。
 *
 * 核心思路：业务写完数据后，通过 registerSynchronization 挂载"提交后才做"的副作用，
 * 保证副作用与事务状态一致（只有真提交了才发短信/清缓存/投MQ）。
 */
@Service
public class OrderService {

	private final JdbcTemplate jdbc;

	public OrderService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// ========== 场景 1: 提交后发消息 (afterCommit) ==========
	@Transactional
	public void placeOrderWithNotification(String orderId) {
		jdbc.update("INSERT INTO orders(id, status) VALUES(?, ?)", orderId, "CREATED");
		System.out.println("  [DB] INSERT order: " + orderId);

		// 注册 afterCommit 回调 —— 只有真正 commit 之后才发通知
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				System.out.println("  [Sync-afterCommit] 发送短信通知: 订单 " + orderId + " 已确认");
			}

			@Override
			public void afterCompletion(int status) {
				String desc = (status == STATUS_COMMITTED) ? "COMMITTED" : "ROLLED_BACK";
				System.out.println("  [Sync-afterCompletion] 事务最终状态: " + desc);
			}
		});
	}

	// ========== 场景 2: 回滚后触发报警 (afterCompletion + ROLLED_BACK) ==========
	@Transactional
	public void placeOrderThenFail(String orderId) {
		jdbc.update("INSERT INTO orders(id, status) VALUES(?, ?)", orderId, "CREATED");
		System.out.println("  [DB] INSERT order: " + orderId);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				// 回滚时不会被调用
				System.out.println("  [Sync-afterCommit] 发送通知（不应出现）");
			}

			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) {
					System.out.println("  [Sync-afterCompletion] 事务回滚! 触发告警: 订单 " + orderId + " 下单失败");
				}
			}
		});

		// 模拟业务异常触发回滚
		throw new RuntimeException("模拟下单异常");
	}

	// ========== 场景 3: beforeCommit flush 审计 ==========
	@Transactional
	public void placeOrderWithAudit(String orderId) {
		jdbc.update("INSERT INTO orders(id, status) VALUES(?, ?)", orderId, "CREATED");
		System.out.println("  [DB] INSERT order: " + orderId);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void beforeCommit(boolean readOnly) {
				// 在 doCommit 之前追加审计记录，与主事务同命运
				System.out.println("  [Sync-beforeCommit] 追加审计日志(readOnly=" + readOnly + ")");
				jdbc.update("INSERT INTO audit_log(order_id, action) VALUES(?, ?)", orderId, "CREATE");
			}

			@Override
			public void beforeCompletion() {
				System.out.println("  [Sync-beforeCompletion] 资源清理准备");
			}

			@Override
			public void afterCommit() {
				System.out.println("  [Sync-afterCommit] 审计 + 订单均已持久化");
			}

			@Override
			public void afterCompletion(int status) {
				System.out.println("  [Sync-afterCompletion] status=" + status);
			}
		});
	}

	// ========== 场景 4: 多 Sync 排序 (Ordered) ==========
	@Transactional
	public void placeOrderWithOrderedSyncs(String orderId) {
		jdbc.update("INSERT INTO orders(id, status) VALUES(?, ?)", orderId, "CREATED");

		// 低优先级(order=200): 模拟清缓存
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public int getOrder() { return 200; }

			@Override
			public void afterCommit() {
				System.out.println("  [Sync-order=200] 清除订单缓存");
			}
		});

		// 高优先级(order=100): 模拟发消息
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public int getOrder() { return 100; }

			@Override
			public void afterCommit() {
				System.out.println("  [Sync-order=100] 投递 MQ 消息");
			}
		});

		// 最高优先级(order=50): 模拟事件发布
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public int getOrder() { return 50; }

			@Override
			public void afterCommit() {
				System.out.println("  [Sync-order=50]  发布领域事件");
			}
		});
	}

	// ========== 场景 5: REQUIRES_NEW 下 Sync 集合挂起/恢复 ==========
	@Transactional
	public void outerWithInnerRequiresNew() {
		jdbc.update("INSERT INTO orders(id, status) VALUES(?, ?)", "OUTER-001", "CREATED");
		System.out.println("  [外层] INSERT OUTER-001");

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				System.out.println("  [外层Sync-afterCommit] 外层事务提交完成");
			}
		});

		System.out.println("  [外层] 当前 Sync 数量: " +
				TransactionSynchronizationManager.getSynchronizations().size());

		// 调用内层 REQUIRES_NEW — 外层 Sync 会被挂起
		innerRequiresNew();

		System.out.println("  [外层] 内层结束后, 外层 Sync 数量: " +
				TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void innerRequiresNew() {
		jdbc.update("INSERT INTO orders(id, status) VALUES(?, ?)", "INNER-001", "CREATED");
		System.out.println("  [内层] INSERT INNER-001 (REQUIRES_NEW)");

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				System.out.println("  [内层Sync-afterCommit] 内层事务提交完成");
			}
		});

		System.out.println("  [内层] 当前 Sync 数量: " +
				TransactionSynchronizationManager.getSynchronizations().size());
	}

	// ========== 场景 6: afterCommit 里写 DB 的坑 ==========
	@Transactional
	public void afterCommitWritePitfall(String orderId) {
		jdbc.update("INSERT INTO orders(id, status) VALUES(?, ?)", orderId, "CREATED");
		System.out.println("  [DB] INSERT order: " + orderId);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				// afterCommit 时事务已提交，但连接仍绑定在 ThreadLocal
				// 这里的 DB 操作参与的是已提交事务的残余连接，不会再 commit
				// 正确做法: 用 REQUIRES_NEW 开新事务
				System.out.println("  [Sync-afterCommit] 尝试在 afterCommit 中写 DB...");
				try {
					jdbc.update("INSERT INTO audit_log(order_id, action) VALUES(?, ?)", orderId, "AFTER_COMMIT_WRITE");
					System.out.println("  [Sync-afterCommit] SQL 执行成功（但不会 commit! 因为外层事务已完结）");
				}
				catch (Exception ex) {
					System.out.println("  [Sync-afterCommit] 写入失败: " + ex.getMessage());
				}
			}
		});
	}
}
