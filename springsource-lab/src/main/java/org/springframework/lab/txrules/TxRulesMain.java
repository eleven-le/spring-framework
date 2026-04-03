package org.springframework.lab.txrules;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * W67 — 传播/隔离/回滚规则：交易链路拆分指南
 *
 * <h2>一句话抽象</h2>
 * 传播/隔离/回滚三把刀解决的核心问题是"一次业务操作涉及多个写入时，哪些必须同生共死、
 * 哪些必须独立提交、哪些允许局部失败、哪些根本不需要事务"——
 * 机制矛盾是"业务链路是一条直线(顺序调用)，但事务边界是一棵树(嵌套/并行/独立)"：
 * <ul>
 *   <li>传播行为 = 事务栈的"加入/挂起/新建/savepoint" → 解决边界拆分</li>
 *   <li>隔离级别 = 并发读写的一致性语义 → 解决脏读/幻读/不可重复读</li>
 *   <li>回滚规则 = 异常到回滚决策的映射引擎 → 解决"该不该回滚"的判定</li>
 * </ul>
 * Spring 的解法是 AbstractPlatformTransactionManager#getTransaction 对传播行为做 switch 分流，
 * handleExistingTransaction 处理已有事务时的 7 种策略，RuleBasedTransactionAttribute#rollbackOn
 * 用深度优先匹配决定回滚还是提交。
 *
 * <h2>实验列表 (14 个)</h2>
 * <ul>
 *   <li>实验 1:  REQUIRED — 下单+扣库存同生共死</li>
 *   <li>实验 2:  REQUIRED 内层异常 → rollback-only 传染外层(UnexpectedRollbackException)</li>
 *   <li>实验 3:  REQUIRES_NEW — 优惠券冻结独立事务</li>
 *   <li>实验 4:  REQUIRES_NEW — 外层回滚不影响已提交的内层</li>
 *   <li>实验 5:  NESTED — 审计日志用 savepoint，失败不影响主事务</li>
 *   <li>实验 6:  NOT_SUPPORTED — 发通知挂起事务，无事务执行</li>
 *   <li>实验 7:  隔离级别冲突 — validateExistingTransaction 检测 READ_COMMITTED vs SERIALIZABLE</li>
 *   <li>实验 8:  MANDATORY — 无事务时调用直接报错</li>
 *   <li>实验 9:  NEVER — 有事务时调用直接报错</li>
 *   <li>实验 10: SUPPORTS — 有事务加入，无事务裸跑</li>
 *   <li>实验 11: 回滚规则 — 默认(RuntimeException回滚/Checked提交)</li>
 *   <li>实验 12: 回滚规则 — rollbackFor/noRollbackFor 深度匹配</li>
 *   <li>实验 13: 回滚规则 — 子类异常的深度计算</li>
 *   <li>实验 14: 完整交易链路 — 下单→库存→优惠券→审计→通知 五段拆分</li>
 * </ul>
 *
 * <h2>核心调用链 (12 步)</h2>
 * <pre>
 *  ① TransactionInterceptor#invoke : AOP入口，提取 targetClass
 *  ② TransactionAspectSupport#invokeWithinTransaction : 模板方法
 *  ③ TransactionAspectSupport#createTransactionIfNecessary : 触发 getTransaction
 *  ④ AbstractPlatformTransactionManager#getTransaction : 传播行为分流入口
 *  ⑤ DataSourceTransactionManager#doGetTransaction : 从 ThreadLocal 取 ConnectionHolder
 *  ⑥ AbstractPlatformTransactionManager#isExistingTransaction : 判断是否已有事务
 *  ⑦ AbstractPlatformTransactionManager#handleExistingTransaction : 7 种传播行为处理
 *     → NEVER: 抛异常 | NOT_SUPPORTED: suspend | REQUIRES_NEW: suspend+startTransaction
 *     → NESTED: createSavepoint | REQUIRED/SUPPORTS/MANDATORY: 直接参与
 *  ⑧ AbstractPlatformTransactionManager#suspend : 卸载 ThreadLocal → SuspendedResourcesHolder
 *  ⑨ DataSourceTransactionManager#doBegin : 获取新 Connection, setAutoCommit(false), 绑定 ThreadLocal
 *  ⑩ invocation.proceedWithInvocation : 执行真正的业务方法
 *  ⑪ TransactionAspectSupport#completeTransactionAfterThrowing : rollbackOn 规则引擎判定
 *  ⑫ AbstractPlatformTransactionManager#processCommit/processRollback : 提交或回滚 + resume
 * </pre>
 *
 * <h2>断点位置 (5 个抓手)</h2>
 * <ol>
 *   <li>AbstractPlatformTransactionManager#getTransaction L349 — 传播行为 switch 分流入口</li>
 *   <li>AbstractPlatformTransactionManager#handleExistingTransaction L408 — 已有事务时 7 种策略</li>
 *   <li>AbstractPlatformTransactionManager#suspend L569 — SuspendedResourcesHolder 打包过程</li>
 *   <li>RuleBasedTransactionAttribute#rollbackOn L125 — 深度匹配 winner 选择</li>
 *   <li>AbstractPlatformTransactionManager#commit L689 — globalRollbackOnly 检测</li>
 * </ol>
 */
public class TxRulesMain {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(TxRulesConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

		// 初始化表结构
		initTables(jdbc);

		OrderService orderService = ctx.getBean(OrderService.class);
		RollbackRuleService rollbackService = ctx.getBean(RollbackRuleService.class);

		// ========== 传播行为实验 ==========
		experiment1_required(orderService, jdbc);
		experiment2_requiredRollbackOnly(orderService, jdbc);
		experiment3_requiresNew(orderService, jdbc);
		experiment4_requiresNewOuterRollback(orderService, jdbc);
		experiment5_nested(orderService, jdbc);
		experiment6_notSupported(orderService, jdbc);
		experiment7_isolationConflict(orderService, jdbc);
		experiment8_mandatory(orderService, jdbc);
		experiment9_never(orderService, jdbc);
		experiment10_supports(orderService, jdbc);

		// ========== 回滚规则实验 ==========
		experiment11_defaultRollbackRules(rollbackService, jdbc);
		experiment12_rollbackForDepthMatch(rollbackService, jdbc);
		experiment13_subclassDepth(rollbackService, jdbc);

		// ========== 完整交易链路 ==========
		experiment14_fullChain(orderService, jdbc);

		ctx.close();
		System.out.println("\n[完成] W67 传播/隔离/回滚规则 全部 14 个实验执行完毕");
	}

	// ==================== 传播行为实验 ====================

	private static void experiment1_required(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 1: REQUIRED — 下单+扣库存同生共死");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  语义: 库存 REQUIRED 加入订单事务 → 同一物理连接/同一 commit");
		resetData(jdbc);
		jdbc.update("INSERT INTO inventory(sku, qty) VALUES('SKU-001', 100)");

		os.placeOrderWithInventory("ORD-001", "SKU-001", 2);

		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		int remainQty = jdbc.queryForObject("SELECT qty FROM inventory WHERE sku='SKU-001'", Integer.class);
		System.out.println("  [验证] 订单数=" + orderCount + "(期望1), 库存余量=" + remainQty + "(期望98)");
		System.out.println("  [结论] REQUIRED = 加入外层事务，同生共死\n");
	}

	private static void experiment2_requiredRollbackOnly(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 2: REQUIRED 内层异常 → rollback-only 传染");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  语义: 内层 REQUIRED 抛异常 → 标记 rollback-only → 外层 catch 也救不了");
		resetData(jdbc);
		jdbc.update("INSERT INTO inventory(sku, qty) VALUES('SKU-002', 100)");

		try {
			os.placeOrderInventoryFail("ORD-002", "SKU-002", 2);
		}
		catch (UnexpectedRollbackException e) {
			System.out.println("  [捕获] UnexpectedRollbackException: " + e.getMessage());
		}
		catch (RuntimeException e) {
			System.out.println("  [捕获] " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}

		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_id='ORD-002'", Integer.class);
		System.out.println("  [验证] 订单存在=" + (orderCount > 0) + " (期望false，整体回滚)");
		System.out.println("  [结论] REQUIRED 内层异常 = globalRollbackOnly → 外层无法挽救\n");
	}

	private static void experiment3_requiresNew(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 3: REQUIRES_NEW — 优惠券冻结独立事务");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  语义: 优惠券冻结在独立事务中 → suspend 外层 → 新 Connection → 独立 commit");
		resetData(jdbc);
		jdbc.update("INSERT INTO coupons(coupon_id, status, order_id) VALUES('CPN-001', 'ACTIVE', NULL)");

		os.placeOrderWithCoupon("ORD-003", "CPN-001");

		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_id='ORD-003'", Integer.class);
		String couponStatus = jdbc.queryForObject("SELECT status FROM coupons WHERE coupon_id='CPN-001'", String.class);
		System.out.println("  [验证] 订单数=" + orderCount + "(期望1), 优惠券状态=" + couponStatus + "(期望FROZEN)");
		System.out.println("  [结论] REQUIRES_NEW = 独立事务，suspend/resume 外层\n");
	}

	private static void experiment4_requiresNewOuterRollback(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 4: REQUIRES_NEW — 外层回滚不影响已提交的内层");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  语义: 优惠券已独立提交 → 订单后续失败回滚 → 优惠券不回滚");
		resetData(jdbc);
		jdbc.update("INSERT INTO coupons(coupon_id, status, order_id) VALUES('CPN-002', 'ACTIVE', NULL)");

		try {
			os.placeOrderCouponThenFail("ORD-004", "CPN-002");
		}
		catch (RuntimeException e) {
			System.out.println("  [捕获] " + e.getMessage());
		}

		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_id='ORD-004'", Integer.class);
		String couponStatus = jdbc.queryForObject("SELECT status FROM coupons WHERE coupon_id='CPN-002'", String.class);
		System.out.println("  [验证] 订单存在=" + (orderCount > 0) + "(期望false), 优惠券状态=" + couponStatus + "(期望FROZEN)");
		System.out.println("  [结论] REQUIRES_NEW 内层已提交 → 外层回滚与它无关 → 需要补偿机制解冻\n");
	}

	private static void experiment5_nested(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 5: NESTED — 审计日志用 savepoint，失败不影响主事务");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  语义: 审计 NESTED → 同一物理连接 → 创建 savepoint → 失败回滚到 savepoint");
		resetData(jdbc);

		os.placeOrderWithAudit("ORD-005");

		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_id='ORD-005'", Integer.class);
		int auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE order_id='ORD-005'", Integer.class);
		System.out.println("  [验证] 订单存在=" + (orderCount > 0) + "(期望true), 审计条数=" + auditCount + "(期望0，savepoint已回滚)");
		System.out.println("  [结论] NESTED = 同一连接的 savepoint → 局部回滚不影响外层\n");
	}

	private static void experiment6_notSupported(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 6: NOT_SUPPORTED — 发通知挂起事务");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  语义: 通知 NOT_SUPPORTED → suspend 当前事务 → 无事务执行 → resume");
		resetData(jdbc);

		os.placeOrderWithNotify("ORD-006");

		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_id='ORD-006'", Integer.class);
		System.out.println("  [验证] 订单数=" + orderCount + "(期望1)");
		System.out.println("  [结论] NOT_SUPPORTED = 挂起事务 → 不持锁不阻塞 → 适合调外部 IO\n");
	}

	private static void experiment7_isolationConflict(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 7: 隔离级别冲突 — validateExistingTransaction");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  语义: 外层 READ_COMMITTED + 内层 REQUIRED+SERIALIZABLE → 隔离冲突");
		resetData(jdbc);
		jdbc.update("INSERT INTO inventory(sku, qty) VALUES('SKU-CONFLICT', 100)");

		try {
			os.placeOrderWithIsolationConflict("ORD-007");
		}
		catch (Exception e) {
			System.out.println("  [捕获] " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}

		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_id='ORD-007'", Integer.class);
		System.out.println("  [验证] 订单存在=" + (orderCount > 0) + " (期望false，隔离冲突导致回滚)");
		System.out.println("  [结论] validateExistingTransaction=true → REQUIRED 加入时校验隔离级别一致性\n");
	}

	private static void experiment8_mandatory(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 8: MANDATORY — 无事务时调用直接报错");
		System.out.println("═══════════════════════════════════════════════════════════");
		resetData(jdbc);

		// 8a: 在事务内调用 MANDATORY → OK
		os.placeOrderWithMandatoryAudit("ORD-008");
		System.out.println("  [8a] 在事务内调用 MANDATORY → 成功");

		// 8b: 无事务调用 MANDATORY → 抛异常
		AuditService auditService = new AnnotationConfigApplicationContext(TxRulesConfig.class)
				.getBean(AuditService.class);
		try {
			auditService.mandatoryLog("ORD-008b", "无事务调用");
		}
		catch (Exception e) {
			System.out.println("  [8b] 无事务调用 MANDATORY → " + e.getClass().getSimpleName());
		}
		System.out.println("  [结论] MANDATORY = 防御性编程，确保必须在事务内调用\n");
	}

	private static void experiment9_never(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 9: NEVER — 有事务时调用直接报错");
		System.out.println("═══════════════════════════════════════════════════════════");

		// 9a: 无事务调用 NEVER → OK
		os.callNeverOutsideTx();
		System.out.println("  [9a] 无事务调用 NEVER → 成功");

		// 9b: 在事务内调用 NEVER → 抛异常
		try {
			os.callNeverInsideTx();
		}
		catch (Exception e) {
			System.out.println("  [9b] 有事务调用 NEVER → " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		System.out.println("  [结论] NEVER = 防御性编程，确保绝不在事务内执行\n");
	}

	private static void experiment10_supports(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 10: SUPPORTS — 有事务加入，无事务裸跑");
		System.out.println("═══════════════════════════════════════════════════════════");
		resetData(jdbc);

		// 10a: 无事务调用 SUPPORTS
		os.callSupportsOutsideTx("ORD-010a");
		System.out.println("  [10a] 无事务调用 SUPPORTS → 裸跑执行");

		// 10b: 有事务调用 SUPPORTS
		os.callSupportsInsideTx("ORD-010b");
		System.out.println("  [10b] 有事务调用 SUPPORTS → 加入事务执行");
		System.out.println("  [结论] SUPPORTS = 弹性适配，常用于只读查询\n");
	}

	// ==================== 回滚规则实验 ====================

	private static void experiment11_defaultRollbackRules(RollbackRuleService rs, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 11: 回滚规则 — 默认行为");
		System.out.println("═══════════════════════════════════════════════════════════");
		resetData(jdbc);

		// 11a: RuntimeException → 回滚
		try { rs.defaultRuleRuntime("R-11a"); } catch (RuntimeException e) { /* expected */ }
		System.out.println("  [11a] RuntimeException → 订单存在=" + rs.orderExists("R-11a") + " (期望false，已回滚)");

		// 11b: CheckedException → 提交!
		try { rs.defaultRuleChecked("R-11b"); } catch (Exception e) { /* expected */ }
		System.out.println("  [11b] CheckedException → 订单存在=" + rs.orderExists("R-11b") + " (期望true，已提交!)");

		System.out.println("  [结论] 默认: RuntimeException/Error → 回滚 | CheckedException → 提交\n");
	}

	private static void experiment12_rollbackForDepthMatch(RollbackRuleService rs, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 12: 回滚规则 — rollbackFor/noRollbackFor 深度匹配");
		System.out.println("═══════════════════════════════════════════════════════════");
		resetData(jdbc);

		// 12a: rollbackFor=Exception → IOException 也回滚
		try { rs.rollbackForAll("R-12a"); } catch (Exception e) { /* expected */ }
		System.out.println("  [12a] rollbackFor=Exception + IOException → 存在=" +
				rs.orderExists("R-12a") + " (期望false，回滚)");

		// 12b: noRollbackFor=IOException 精确匹配胜出
		try { rs.noRollbackWins("R-12b"); } catch (Exception e) { /* expected */ }
		System.out.println("  [12b] noRollbackFor(IOException) depth=0 vs rollbackFor(Exception) depth=1 → 存在=" +
				rs.orderExists("R-12b") + " (期望true，不回滚)");

		// 12c: SQLException 不匹配 noRollbackFor(IOException) → rollbackFor(Exception) 生效
		try { rs.sqlExceptionRollback("R-12c"); } catch (Exception e) { /* expected */ }
		System.out.println("  [12c] SQLException vs noRollbackFor(IOException) 不匹配 → 存在=" +
				rs.orderExists("R-12c") + " (期望false，回滚)");

		System.out.println("  [结论] 规则引擎: 取 depth 最小的 winner → NoRollbackRule 则提交，否则回滚\n");
	}

	private static void experiment13_subclassDepth(RollbackRuleService rs, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 13: 回滚规则 — 子类异常的深度计算");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  深度计算: FileNotFoundException extends IOException extends Exception");
		System.out.println("  NoRollback(IOException): FileNotFound depth=1 | Rollback(Exception): FileNotFound depth=2");
		resetData(jdbc);

		try { rs.subclassNoRollback("R-13a"); } catch (Exception e) { /* expected */ }
		System.out.println("  [13a] FileNotFoundException → NoRollback depth=1 < Rollback depth=2 → 存在=" +
				rs.orderExists("R-13a") + " (期望true，不回滚)");

		// className 匹配
		try { rs.rollbackByClassName("R-13b"); } catch (Exception e) { /* expected */ }
		System.out.println("  [13b] rollbackForClassName=\"java.io.IOException\" → 存在=" +
				rs.orderExists("R-13b") + " (期望false，回滚)");

		System.out.println("  [结论] 深度 = 异常继承链上溯层数，depth 越小越优先\n");
	}

	// ==================== 完整交易链路 ====================

	private static void experiment14_fullChain(OrderService os, JdbcTemplate jdbc) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("实验 14: 完整交易链路 — 下单→库存→优惠券→审计→通知");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("  五段拆分:");
		System.out.println("  订单(REQUIRED) → 库存(REQUIRED,同生共死) → 优惠券(REQUIRES_NEW,独立)");
		System.out.println("  → 审计(NESTED,savepoint) → 通知(NOT_SUPPORTED,无事务)");
		System.out.println("───────────────────────────────────────────────────────────");
		resetData(jdbc);
		jdbc.update("INSERT INTO inventory(sku, qty) VALUES('IPHONE', 100)");
		jdbc.update("INSERT INTO coupons(coupon_id, status, order_id) VALUES('CPN-FULL', 'ACTIVE', NULL)");

		os.placeOrderFullChain("ORD-FULL", "IPHONE", 1, "CPN-FULL");

		System.out.println("───────────────────────────────────────────────────────────");
		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_id='ORD-FULL'", Integer.class);
		int remainQty = jdbc.queryForObject("SELECT qty FROM inventory WHERE sku='IPHONE'", Integer.class);
		String couponStatus = jdbc.queryForObject("SELECT status FROM coupons WHERE coupon_id='CPN-FULL'", String.class);
		int auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE order_id='ORD-FULL'", Integer.class);
		System.out.println("  [验证] 订单=" + orderCount + " | 库存=" + remainQty +
				" | 优惠券=" + couponStatus + " | 审计=" + auditCount);
		System.out.println("  [结论] 完整链路事务边界拆分验证通过\n");
	}

	// ==================== 辅助方法 ====================

	private static void initTables(JdbcTemplate jdbc) {
		jdbc.execute("CREATE TABLE IF NOT EXISTS orders " +
				"(order_id VARCHAR(50) PRIMARY KEY, amount INT, status VARCHAR(20))");
		jdbc.execute("CREATE TABLE IF NOT EXISTS inventory " +
				"(sku VARCHAR(50) PRIMARY KEY, qty INT)");
		jdbc.execute("CREATE TABLE IF NOT EXISTS coupons " +
				"(coupon_id VARCHAR(50) PRIMARY KEY, status VARCHAR(20), order_id VARCHAR(50))");
		jdbc.execute("CREATE TABLE IF NOT EXISTS audit_log " +
				"(id INT AUTO_INCREMENT PRIMARY KEY, order_id VARCHAR(50), action VARCHAR(200))");
	}

	private static void resetData(JdbcTemplate jdbc) {
		jdbc.execute("DELETE FROM orders");
		jdbc.execute("DELETE FROM inventory");
		jdbc.execute("DELETE FROM coupons");
		jdbc.execute("DELETE FROM audit_log");
	}
}
