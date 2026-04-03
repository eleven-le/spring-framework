package org.springframework.lab.txskeleton;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 订单服务 — 用于演示事务骨架内部的挂起/恢复/同步回调机制
 *
 * <p>断点抓手:
 * <ul>
 *   <li>{@code AbstractPlatformTransactionManager#suspend} — 观察 SuspendedResourcesHolder 打包过程</li>
 *   <li>{@code AbstractPlatformTransactionManager#resume} — 观察 ThreadLocal 恢复过程</li>
 *   <li>{@code AbstractPlatformTransactionManager#processCommit} — 观察同步回调 4 阶段触发</li>
 * </ul>
 */
@Service
public class OrderService {

	private final JdbcTemplate jdbc;
	private final AuditService auditService;

	public OrderService(JdbcTemplate jdbc, AuditService auditService) {
		this.jdbc = jdbc;
		this.auditService = auditService;
	}

	// ===== 场景1: REQUIRES_NEW 挂起/恢复全流程 =====
	// 外层 REQUIRED → 调 auditService.audit(REQUIRES_NEW)
	// 进入 audit 时: suspend 外层 → doSuspend 解绑 DataSource→Connection
	//                  → SuspendedResourcesHolder 保存同步回调/name/readOnly/isolationLevel
	// audit 提交后: cleanupAfterCompletion → resume 恢复外层 ThreadLocal
	@Transactional
	public void placeOrder(String item, int price) {
		jdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, price);

		// 打印当前线程事务资源，方便对照 suspend 前后
		printThreadTxState("placeOrder - 外层事务内, audit 调用前");

		// REQUIRES_NEW: 会 suspend 当前事务
		auditService.audit("下单: " + item + " ¥" + price);

		// resume 后回到外层事务
		printThreadTxState("placeOrder - 外层事务内, audit 调用后(resume 完成)");

		System.out.println("  [placeOrder] 订单写入完成: " + item);
	}

	// ===== 场景2: NOT_SUPPORTED 挂起（无新事务） =====
	// 与 REQUIRES_NEW 不同: suspend 后不开新事务，以"空事务"状态执行
	@Transactional
	public void placeOrderWithNotSupported(String item, int price) {
		jdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, price);

		printThreadTxState("placeOrderWithNotSupported - suspend 前");

		// NOT_SUPPORTED: suspend 当前事务，但不开新事务
		auditService.auditNotSupported("NOT_SUPPORTED 日志: " + item);

		printThreadTxState("placeOrderWithNotSupported - resume 后");
	}

	// ===== 场景3: 同步回调 4 阶段 =====
	// processCommit 内部: beforeCommit → beforeCompletion → doCommit → afterCommit → afterCompletion
	// 典型场景: afterCommit 里发 MQ / 删缓存，保证"事务已提交才执行副作用"
	@Transactional
	public void placeOrderWithSync(String item, int price) {
		jdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, price);

		// 注册自定义同步回调 — 观察 5 个回调的触发顺序
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void suspend() {
				System.out.println("  [Sync-①] suspend — 事务被挂起");
			}
			@Override
			public void resume() {
				System.out.println("  [Sync-②] resume — 事务被恢复");
			}
			@Override
			public void beforeCommit(boolean readOnly) {
				System.out.println("  [Sync-③] beforeCommit(readOnly=" + readOnly + ") — flush 缓存到 DB");
			}
			@Override
			public void beforeCompletion() {
				System.out.println("  [Sync-④] beforeCompletion — 关闭非托管资源");
			}
			@Override
			public void afterCommit() {
				System.out.println("  [Sync-⑤] afterCommit — 发 MQ 消息 / 删缓存（事务已确认提交!）");
			}
			@Override
			public void afterCompletion(int status) {
				String s = (status == STATUS_COMMITTED) ? "COMMITTED" : "ROLLED_BACK";
				System.out.println("  [Sync-⑥] afterCompletion(" + s + ") — 最终清理");
			}
		});

		System.out.println("  [placeOrderWithSync] 订单已插入，同步回调已注册，等待提交...");
	}

	// ===== 场景4: NESTED — savepoint 而非 suspend =====
	// handleExistingTransaction 发现 NESTED → 不 suspend，创建 savepoint
	// processCommit 时 releaseHeldSavepoint（而非 doCommit）
	// processRollback 时 rollbackToHeldSavepoint（而非 doRollback）
	@Transactional
	public void placeOrderWithNested(String item, int price) {
		jdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, price);
		System.out.println("  [placeOrderWithNested] 主订单写入: " + item);

		try {
			// NESTED: 在同一个物理事务内创建 savepoint
			auditService.auditNested("嵌套日志: " + item);
		}
		catch (RuntimeException e) {
			// 嵌套事务回滚到 savepoint，不影响外层
			System.out.println("  [placeOrderWithNested] 嵌套事务回滚(savepoint): " + e.getMessage());
		}

		System.out.println("  [placeOrderWithNested] 外层事务继续...");
	}

	// ===== 场景5: TransactionInfo ThreadLocal 栈 =====
	// TransactionAspectSupport 用 TransactionInfo.oldTransactionInfo 形成链表栈
	// bindToThread: 保存旧 → 设置新
	// restoreThreadLocalStatus: 恢复旧
	// 这保证了嵌套 @Transactional 调用的 TransactionInfo 不会丢失
	@Transactional
	public void placeOrderChained(String item, int price) {
		jdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, price);
		System.out.println("  [外层] placeOrderChained 事务开启");

		// 调用 REQUIRED 方法 → 加入当前事务，但 TransactionInfo 仍会入栈
		auditService.auditRequired("链式调用日志: " + item);

		System.out.println("  [外层] placeOrderChained — auditRequired 返回，TransactionInfo 已恢复");
	}

	// ===== 场景6: 挂起 + 内层失败 → 外层正常 =====
	@Transactional
	public void placeOrderResilient(String item, int price) {
		jdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, price);
		System.out.println("  [外层] 订单写入: " + item);

		try {
			// REQUIRES_NEW 内层失败 → 不影响外层
			auditService.auditFailInNew("故意失败的日志");
		}
		catch (RuntimeException e) {
			System.out.println("  [外层] 捕获 REQUIRES_NEW 内层异常: " + e.getMessage());
			System.out.println("  [外层] 外层事务不受影响，继续提交");
		}
	}

	static void printThreadTxState(String label) {
		System.out.println("  ── [ThreadLocal] " + label + " ──");
		System.out.println("     synchronizationActive = " +
				TransactionSynchronizationManager.isSynchronizationActive());
		System.out.println("     actualTransactionActive = " +
				TransactionSynchronizationManager.isActualTransactionActive());
		System.out.println("     currentTransactionName  = " +
				TransactionSynchronizationManager.getCurrentTransactionName());
		System.out.println("     resourceMap.size        = " +
				TransactionSynchronizationManager.getResourceMap().size());
	}
}
