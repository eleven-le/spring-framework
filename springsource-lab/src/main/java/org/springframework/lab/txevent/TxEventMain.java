package org.springframework.lab.txevent;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W21 — @TransactionalEventListener：四阶段与 fallbackExecution
 *
 * <h2>一句话抽象</h2>
 * <b>@TransactionalEventListener</b> 解决"事件副作用必须等事务真正提交/回滚后才执行"
 * ——核心矛盾是 <i>publishEvent 时事务尚未提交（数据可能回滚），但副作用（发 MQ/发短信）不能撤回</i>，
 * 解法是 <b>不立即处理事件，而是注册 TransactionSynchronization 到当前事务同步链，
 * 延迟到 processCommit/processRollback 的指定阶段才触发 processEvent</b>。
 *
 * <h2>实验列表</h2>
 * <ol>
 *   <li>实验 1: 正常提交 — BEFORE_COMMIT → AFTER_COMMIT → AFTER_COMPLETION 顺序</li>
 *   <li>实验 2: 回滚 — AFTER_ROLLBACK + AFTER_COMPLETION 触发, BEFORE/AFTER_COMMIT 不触发</li>
 *   <li>实验 3: BEFORE_COMMIT 追加审计(仍在事务内) — 随主事务一起提交</li>
 *   <li>实验 4: fallbackExecution — 无事务时 false 丢弃 vs true 立即执行</li>
 *   <li>实验 5: AFTER_COMMIT 写 DB 陷阱 — 必须 REQUIRES_NEW</li>
 *   <li>实验 6: 同一事务多次 publishEvent — 注册多个 Synchronization</li>
 *   <li>实验 7: @EventListener vs @TransactionalEventListener 执行时机对比</li>
 * </ol>
 *
 * <h2>核心调用链 (12 步)</h2>
 * <pre>
 *  ① @EnableTransactionManagement → AbstractTransactionManagementConfiguration : 注册 TransactionalEventListenerFactory (order=50)
 *  ② EventListenerMethodProcessor#postProcessBeanFactory : 收集 EventListenerFactory(按 order 排序: TxFactory=50 优先于 Default=MAX)
 *  ③ EventListenerMethodProcessor#processBean : @TransactionalEventListener 被 TxFactory.supportsMethod 拦截
 *  ④ TransactionalEventListenerFactory#createApplicationListener : new TransactionalApplicationListenerMethodAdapter(beanName, type, method)
 *  ⑤ 构造器解析 @TransactionalEventListener 注解 → 提取 phase + fallbackExecution
 *  ⑥ AbstractApplicationContext#publishEvent : 事件派发到所有监听器(包括 TxAdapter)
 *  ⑦ TransactionalApplicationListenerMethodAdapter#onApplicationEvent : 三路分支判断
 *  ⑧ 有事务分支: TransactionSynchronizationManager.registerSynchronization(new TransactionalApplicationListenerSynchronization)
 *  ⑨ AbstractPlatformTransactionManager#processCommit : triggerBeforeCommit → doCommit → triggerAfterCommit → triggerAfterCompletion
 *  ⑩ TransactionalApplicationListenerSynchronization#beforeCommit : phase==BEFORE_COMMIT → processEventWithCallbacks
 *  ⑪ TransactionalApplicationListenerSynchronization#afterCompletion(status) : 按 status 映射 AFTER_COMMIT/AFTER_ROLLBACK/AFTER_COMPLETION
 *  ⑫ processEventWithCallbacks : callbacks.preProcess → listener.processEvent (反射调用方法体) → callbacks.postProcess
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>TransactionalApplicationListenerMethodAdapter#onApplicationEvent L89 — 三路分支总入口(有事务/fallback/丢弃)</li>
 *   <li>TransactionalApplicationListenerSynchronization#beforeCommit L57 — BEFORE_COMMIT 触发点</li>
 *   <li>TransactionalApplicationListenerSynchronization#afterCompletion L64 — AFTER_COMMIT/ROLLBACK/COMPLETION 触发分支</li>
 *   <li>TransactionalApplicationListenerSynchronization#processEventWithCallbacks L77 — 回调包装 + processEvent 反射调用</li>
 *   <li>AbstractPlatformTransactionManager#processCommit L728 — triggerBeforeCommit → doCommit 之间观察 BEFORE_COMMIT 时机</li>
 * </ol>
 */
public class TxEventMain {

	public static void main(String[] args) {
		System.out.println("╔════════════════════════════════════════════════════════════════╗");
		System.out.println("║  W21 — @TransactionalEventListener: 四阶段与 fallbackExecution  ║");
		System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxEventConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		OrderService orderService = ctx.getBean(OrderService.class);

		// 初始化表
		jdbc.execute("CREATE TABLE IF NOT EXISTS orders(id INT AUTO_INCREMENT PRIMARY KEY, order_id VARCHAR(50), item VARCHAR(100), price INT)");
		jdbc.execute("CREATE TABLE IF NOT EXISTS audit_log(id INT AUTO_INCREMENT PRIMARY KEY, order_id VARCHAR(50), action VARCHAR(200), phase VARCHAR(50))");

		// ========== 实验 1: 正常提交 → 四阶段触发顺序 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 1: 正常提交 → BEFORE_COMMIT → AFTER_COMMIT → AFTER_COMPLETION");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: TransactionalApplicationListenerMethodAdapter#onApplicationEvent L89");
		System.out.println("  观察: isSynchronizationActive=true → registerSynchronization\n");
		resetData(jdbc);
		orderService.placeOrderSuccess("ORD-001", "iPhone", 9999);
		System.out.println("  [结果] orders=" + count(jdbc, "orders")
				+ " (期望1, 事务提交成功)");

		// ========== 实验 2: 回滚 → AFTER_ROLLBACK + AFTER_COMPLETION ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 2: 回滚 → AFTER_ROLLBACK + AFTER_COMPLETION, BEFORE/AFTER_COMMIT 不触发");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: TxAppListenerSync#afterCompletion L64 → status=STATUS_ROLLED_BACK\n");
		resetData(jdbc);
		try {
			orderService.placeOrderFail("ORD-002", "MacBook", 14999);
		}
		catch (RuntimeException e) {
			System.out.println("  [捕获异常] " + e.getMessage());
		}
		System.out.println("  [结果] orders=" + count(jdbc, "orders")
				+ " (期望0, 事务已回滚)");

		// ========== 实验 3: BEFORE_COMMIT 追加审计 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 3: BEFORE_COMMIT — 在事务内追加审计记录");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: TxAppListenerSync#beforeCommit L57 → processEventWithCallbacks\n");
		resetData(jdbc);
		orderService.placeOrderWithBeforeCommit("ORD-003", "AirPods", 1999);
		System.out.println("  [结果] orders=" + count(jdbc, "orders") + ", audit_log="
				+ count(jdbc, "audit_log")
				+ " (期望各1, 审计记录随主事务一起提交)");

		// ========== 实验 4: fallbackExecution ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 4: fallbackExecution — 无事务时 false 丢弃 vs true 执行");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: TxAppListenerMethodAdapter#onApplicationEvent L95 → fallback 分支");
		System.out.println("  断点: TxAppListenerMethodAdapter#onApplicationEvent L101 → 静默丢弃 debug log\n");
		resetData(jdbc);
		orderService.placeOrderNoTx("ORD-004", "Watch", 3999);
		System.out.println("  [结果] fallback=false 的监听器: 静默丢弃(无输出)");
		System.out.println("  [结果] fallback=true 的监听器: 立即执行(见上方输出)");

		// ========== 实验 5: AFTER_COMMIT 写 DB 陷阱 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 5: AFTER_COMMIT 写 DB — 必须 REQUIRES_NEW");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  ⚠ TransactionSynchronization Javadoc 明确警告:");
		System.out.println("  ⚠ 'Use PROPAGATION_REQUIRES_NEW for any transactional operation'\n");
		resetData(jdbc);
		orderService.placeOrderWithAfterCommitWrite("ORD-005", "iPad", 5999);
		System.out.println("  [结果] audit_log=" + count(jdbc, "audit_log")
				+ " (H2 可能写入成功, 但 MySQL/PG 中无 REQUIRES_NEW 不会 commit!)");

		// ========== 实验 6: 同一事务多次 publishEvent ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 6: 同一事务发布多个事件 → 注册多个 Synchronization");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  每次 publishEvent → 注册 1 个 TxAppListenerSync");
		System.out.println("  3 个事件 × 4 种 phase = 最多 12 个 Synchronization\n");
		resetData(jdbc);
		orderService.placeOrderMultiEvents("ORD-006", "批量", 999);

		// ========== 实验 7: @EventListener vs @TransactionalEventListener ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 7: @EventListener vs @TransactionalEventListener 执行时机");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  @EventListener: 在 publishEvent() 调用栈内同步执行");
		System.out.println("  @TransactionalEventListener: 在事务 commit/rollback 调用栈内执行");
		System.out.println("  (已在实验 1/2 中对比展示, 观察输出顺序)\n");

		ctx.close();
		System.out.println("\n[完成] W21 @TransactionalEventListener 全部实验执行完毕");
	}

	private static void resetData(JdbcTemplate jdbc) {
		jdbc.execute("DELETE FROM orders");
		jdbc.execute("DELETE FROM audit_log");
	}

	private static int count(JdbcTemplate jdbc, String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}
}
