package org.springframework.lab.txsync;

import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W22 — TransactionSynchronizationManager 练兵场入口
 *
 * 8 个实验覆盖：
 * 1. 窥探 6 大 ThreadLocal (事务前/中/后)
 * 2. afterCommit 发消息（只有真提交才触发）
 * 3. afterCompletion 回滚告警
 * 4. beforeCommit flush 审计（与主事务同命运）
 * 5. 资源绑定 bindResource/getResource/unbindResource
 * 6. 多 Sync 排序（Ordered 控制执行顺序）
 * 7. REQUIRES_NEW 下 Sync 集合挂起/恢复
 * 8. afterCommit 写 DB 的坑（连接已提交不再 commit）
 */
public class TxSyncMain {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(TxSyncConfig.class);

		OrderService orderService = ctx.getBean(OrderService.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		PlatformTransactionManager tm = ctx.getBean(PlatformTransactionManager.class);
		TransactionTemplate txTemplate = new TransactionTemplate(tm);

		// ═══════════════════════════════════════════════════════════════
		// 实验 1: 窥探 6 大 ThreadLocal — 事务前/中/后对比
		// ═══════════════════════════════════════════════════════════════
		System.out.println("═══ 实验1: 窥探 6 大 ThreadLocal ═══");
		System.out.println("── 事务外 ──");
		printThreadLocalState();

		txTemplate.executeWithoutResult(status -> {
			System.out.println("── 事务内 ──");
			printThreadLocalState();

			// 查看资源绑定: DataSourceTransactionManager 在 doBegin 时绑定了 ConnectionHolder
			Map<Object, Object> resourceMap = TransactionSynchronizationManager.getResourceMap();
			System.out.println("  resources 绑定数量: " + resourceMap.size());
			resourceMap.forEach((key, val) ->
					System.out.println("    key=" + key.getClass().getSimpleName() + ", val=" + val.getClass().getSimpleName()));
		});

		System.out.println("── 事务后 ──");
		printThreadLocalState();

		// ═══════════════════════════════════════════════════════════════
		// 实验 2: afterCommit 发消息 — 只有真提交才触发
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验2: afterCommit 发消息 ═══");
		orderService.placeOrderWithNotification("ORD-001");
		System.out.println("  验证: orders 表行数 = " + jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));

		// ═══════════════════════════════════════════════════════════════
		// 实验 3: 回滚后触发告警
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验3: 回滚后触发告警 ═══");
		try {
			orderService.placeOrderThenFail("ORD-002");
		}
		catch (RuntimeException e) {
			System.out.println("  捕获异常: " + e.getMessage());
		}
		System.out.println("  验证: orders 表行数 = " + jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)
				+ " (ORD-002 应该不存在)");

		// ═══════════════════════════════════════════════════════════════
		// 实验 4: beforeCommit flush 审计
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验4: beforeCommit flush 审计 ═══");
		orderService.placeOrderWithAudit("ORD-003");
		Integer auditCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_log WHERE order_id = ?", Integer.class, "ORD-003");
		System.out.println("  验证: audit_log 行数 = " + auditCount + " (审计与订单同事务)");

		// ═══════════════════════════════════════════════════════════════
		// 实验 5: 资源绑定 bindResource / getResource / unbindResource
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验5: 手动资源绑定 ═══");
		String resourceKey = "myCustomCacheKey";
		String resourceVal = "CachedOrderData-12345";

		txTemplate.executeWithoutResult(status -> {
			// 手动绑定自定义资源
			TransactionSynchronizationManager.bindResource(resourceKey, resourceVal);
			System.out.println("  bindResource: key=" + resourceKey);

			// 读取
			Object retrieved = TransactionSynchronizationManager.getResource(resourceKey);
			System.out.println("  getResource: " + retrieved);

			// 查看 resourceMap
			System.out.println("  resourceMap 总数: " +
					TransactionSynchronizationManager.getResourceMap().size());

			// 注册清理回调
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int st) {
					// afterCompletion 里做自定义资源解绑
					TransactionSynchronizationManager.unbindResourceIfPossible(resourceKey);
					System.out.println("  [Sync-afterCompletion] 自定义资源已解绑");
				}
			});
		});

		boolean stillBound = TransactionSynchronizationManager.hasResource(resourceKey);
		System.out.println("  事务后资源是否仍绑定: " + stillBound + " (应为 false)");

		// ═══════════════════════════════════════════════════════════════
		// 实验 6: 多 Sync 排序
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验6: 多 Sync 排序 (Ordered) ═══");
		orderService.placeOrderWithOrderedSyncs("ORD-004");
		System.out.println("  执行顺序: order=50 → 100 → 200 (值越小越先执行)");

		// ═══════════════════════════════════════════════════════════════
		// 实验 7: REQUIRES_NEW 下 Sync 挂起/恢复
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验7: REQUIRES_NEW 下 Sync 挂起/恢复 ═══");
		orderService.outerWithInnerRequiresNew();

		// ═══════════════════════════════════════════════════════════════
		// 实验 8: afterCommit 写 DB 的坑
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验8: afterCommit 写 DB 的坑 ═══");
		orderService.afterCommitWritePitfall("ORD-005");
		Integer afterCommitAudit = jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, "AFTER_COMMIT_WRITE");
		System.out.println("  验证: AFTER_COMMIT_WRITE 审计行数 = " + afterCommitAudit
				+ " (可能为 0, 因为写入不会被新事务 commit)");

		ctx.close();
	}

	/**
	 * 打印 TransactionSynchronizationManager 的 6 大 ThreadLocal 状态
	 */
	private static void printThreadLocalState() {
		System.out.println("  isSynchronizationActive  = " +
				TransactionSynchronizationManager.isSynchronizationActive());
		System.out.println("  isActualTransactionActive = " +
				TransactionSynchronizationManager.isActualTransactionActive());
		System.out.println("  currentTransactionName    = " +
				TransactionSynchronizationManager.getCurrentTransactionName());
		System.out.println("  currentTransactionReadOnly= " +
				TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		System.out.println("  isolationLevel            = " +
				TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
		System.out.println("  resourceMap.size          = " +
				TransactionSynchronizationManager.getResourceMap().size());
	}
}
