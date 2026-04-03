package org.springframework.lab.txrules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 订单服务 — 交易链路的"主事务入口"
 *
 * <p>真实业务中，下单是一次用户操作的核心写入，通常是 REQUIRED 传播（最外层事务）。
 * 下单内部会依次调用：扣库存(REQUIRED)、冻结优惠券(REQUIRES_NEW)、写审计(NESTED)、发通知(NOT_SUPPORTED)。
 * 每种传播行为对应不同的"事务拆分语义"——这就是本条目的核心。
 *
 * <p>断点:
 * <ul>
 *   <li>{@code AbstractPlatformTransactionManager#getTransaction} — 观察 propagation 分流</li>
 *   <li>{@code AbstractPlatformTransactionManager#handleExistingTransaction} — REQUIRES_NEW/NESTED/NOT_SUPPORTED 三条路</li>
 * </ul>
 */
@Service
public class OrderService {

	private final JdbcTemplate jdbc;
	private final InventoryService inventoryService;
	private final CouponService couponService;
	private final AuditService auditService;
	private final NotifyService notifyService;

	public OrderService(JdbcTemplate jdbc, InventoryService inventoryService,
			CouponService couponService, AuditService auditService, NotifyService notifyService) {
		this.jdbc = jdbc;
		this.inventoryService = inventoryService;
		this.couponService = couponService;
		this.auditService = auditService;
		this.notifyService = notifyService;
	}

	// ===== 实验 1: REQUIRED — 下单 + 扣库存同生共死 =====
	@Transactional
	public void placeOrderWithInventory(String orderId, String sku, int qty) {
		System.out.println("  [OrderService] 写入订单: " + orderId);
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, ?, 'CREATED')", orderId, qty * 100);

		printTxState("OrderService.placeOrderWithInventory");

		// REQUIRED: 加入当前事务 → 库存扣减与订单同一个物理事务
		inventoryService.deduct(sku, qty);
		System.out.println("  [OrderService] 订单+库存 在同一事务，同生共死");
	}

	// ===== 实验 2: REQUIRED 内层抛异常 → 整体回滚 =====
	@Transactional
	public void placeOrderInventoryFail(String orderId, String sku, int qty) {
		System.out.println("  [OrderService] 写入订单: " + orderId);
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, ?, 'CREATED')", orderId, qty * 100);

		try {
			inventoryService.deductAndFail(sku, qty);
		}
		catch (RuntimeException e) {
			System.out.println("  [OrderService] 捕获异常: " + e.getMessage());
			// 注意: 即使捕获了异常，REQUIRED 内层已标记 rollback-only
			// → 外层 commit 时检测到 globalRollbackOnly → UnexpectedRollbackException
			System.out.println("  [OrderService] ⚠ REQUIRED 内层异常已标记 rollback-only，外层无法挽回！");
		}
	}

	// ===== 实验 3: REQUIRES_NEW — 优惠券冻结独立于订单事务 =====
	@Transactional
	public void placeOrderWithCoupon(String orderId, String couponId) {
		System.out.println("  [OrderService] 写入订单: " + orderId);
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'CREATED')", orderId);

		printTxState("OrderService - 调 couponService 前");

		// REQUIRES_NEW: 挂起外层 → 新建独立事务
		couponService.freeze(couponId, orderId);

		printTxState("OrderService - 调 couponService 后(resume)");
		System.out.println("  [OrderService] 优惠券冻结已独立提交，即使订单后续回滚也不影响");
	}

	// ===== 实验 4: REQUIRES_NEW 外层回滚不影响内层 =====
	@Transactional
	public void placeOrderCouponThenFail(String orderId, String couponId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'CREATED')", orderId);
		System.out.println("  [OrderService] 写入订单: " + orderId);

		couponService.freeze(couponId, orderId);  // REQUIRES_NEW 已独立提交
		System.out.println("  [OrderService] 优惠券冻结已提交 → 即将故意抛异常让订单回滚");

		throw new RuntimeException("模拟下单后续步骤失败 → 订单回滚，但优惠券冻结不回滚");
	}

	// ===== 实验 5: NESTED — 审计日志用 savepoint 保护 =====
	@Transactional
	public void placeOrderWithAudit(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'CREATED')", orderId);
		System.out.println("  [OrderService] 写入订单: " + orderId);

		try {
			// NESTED: 创建 savepoint，失败回滚到 savepoint，不影响外层
			auditService.logAndFail(orderId, "下单审计");
		}
		catch (RuntimeException e) {
			System.out.println("  [OrderService] 审计失败(NESTED回滚到savepoint): " + e.getMessage());
			System.out.println("  [OrderService] 外层事务不受影响，继续提交");
		}
	}

	// ===== 实验 6: NOT_SUPPORTED — 发通知不需要事务 =====
	@Transactional
	public void placeOrderWithNotify(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'CREATED')", orderId);
		System.out.println("  [OrderService] 写入订单: " + orderId);

		printTxState("OrderService - 调 notifyService 前");

		// NOT_SUPPORTED: 挂起当前事务，以无事务方式执行
		notifyService.sendNotification(orderId, "订单已创建");

		printTxState("OrderService - 调 notifyService 后(resume)");
	}

	// ===== 实验 7: MANDATORY — 必须在已有事务中调用 =====
	@Transactional
	public void placeOrderWithMandatoryAudit(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'CREATED')", orderId);

		// MANDATORY: 加入当前事务，OK
		auditService.mandatoryLog(orderId, "强制事务审计");
		System.out.println("  [OrderService] MANDATORY 审计成功: 加入了当前事务");
	}

	// ===== 实验 8: 隔离级别冲突检测 =====
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void placeOrderWithIsolationConflict(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'CREATED')", orderId);

		// REQUIRED + SERIALIZABLE → 加入 READ_COMMITTED 的事务时隔离级别不一致
		// validateExistingTransaction=true 时会抛 IllegalTransactionStateException
		inventoryService.deductSerializable("SKU-CONFLICT", 1);
	}

	// ===== 实验 10: 完整交易链路拆分 =====
	@Transactional
	public void placeOrderFullChain(String orderId, String sku, int qty, String couponId) {
		// ① 写订单 (REQUIRED — 主事务)
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, ?, 'CREATED')", orderId, qty * 100);
		System.out.println("  ① [订单] 写入: " + orderId);

		// ② 扣库存 (REQUIRED — 同一事务，同生共死)
		inventoryService.deduct(sku, qty);
		System.out.println("  ② [库存] 扣减: " + sku + " × " + qty);

		// ③ 冻结优惠券 (REQUIRES_NEW — 独立事务，提前锁定)
		couponService.freeze(couponId, orderId);
		System.out.println("  ③ [优惠券] 冻结: " + couponId + " (独立事务已提交)");

		// ④ 写审计日志 (NESTED — savepoint 保护，失败不影响订单)
		try {
			auditService.log(orderId, "下单成功: " + sku + " × " + qty);
			System.out.println("  ④ [审计] 写入成功");
		}
		catch (RuntimeException e) {
			System.out.println("  ④ [审计] 失败但不影响主事务: " + e.getMessage());
		}

		// ⑤ 发通知 (NOT_SUPPORTED — 无事务执行，不阻塞主链路)
		notifyService.sendNotification(orderId, "您的订单已创建");
		System.out.println("  ⑤ [通知] 已发送 (无事务)");

		System.out.println("  [完整链路] 订单提交 → 库存同提交 → 优惠券已独立提交 → 审计savepoint → 通知无事务");
	}

	// ===== 实验 11: NEVER — 确保无事务时调用 =====
	public void callNeverOutsideTx() {
		// 无事务上下文直接调用 NEVER → OK
		notifyService.neverInTx("NEVER-OK");
	}

	@Transactional
	public void callNeverInsideTx() {
		// 在事务上下文中调用 NEVER → 抛 IllegalTransactionStateException
		notifyService.neverInTx("NEVER-FAIL");
	}

	// ===== 实验 12: SUPPORTS — 有事务就加入，没有也行 =====
	public void callSupportsOutsideTx(String orderId) {
		auditService.supportsLog(orderId, "无事务调SUPPORTS");
	}

	@Transactional
	public void callSupportsInsideTx(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'CREATED')", orderId);
		auditService.supportsLog(orderId, "有事务调SUPPORTS");
	}

	static void printTxState(String label) {
		System.out.println("  ── [ThreadLocal] " + label + " ──");
		System.out.println("     syncActive    = " + TransactionSynchronizationManager.isSynchronizationActive());
		System.out.println("     actualTxActive = " + TransactionSynchronizationManager.isActualTransactionActive());
		System.out.println("     txName        = " + TransactionSynchronizationManager.getCurrentTransactionName());
		System.out.println("     resourceCount = " + TransactionSynchronizationManager.getResourceMap().size());
	}
}
