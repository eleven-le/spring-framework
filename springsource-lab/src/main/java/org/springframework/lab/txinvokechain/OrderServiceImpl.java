package org.springframework.lab.txinvokechain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 订单服务实现 — 演示 TransactionAttributeSource 四级回退 + TransactionInfo 栈
 *
 * <p>类级别标注 @Transactional → 优先于接口类级别的 readOnly=true。
 * 方法级别标注（如 placeOrder）→ 优先于类级别。
 *
 * <p>断点:
 * {@code AbstractFallbackTransactionAttributeSource#computeTransactionAttribute}
 * — 逐步观察 4 级回退逻辑
 */
@Service
@Transactional  // 类级别: propagation=REQUIRED, readOnly=false
public class OrderServiceImpl implements OrderService {

	private final JdbcTemplate jdbc;
	private final AuditService auditService;

	public OrderServiceImpl(JdbcTemplate jdbc, AuditService auditService) {
		this.jdbc = jdbc;
		this.auditService = auditService;
	}

	/**
	 * 无方法级 @Transactional → 回退到类级别(REQUIRED, readOnly=false)
	 * 而非接口类级别(readOnly=true)
	 */
	@Override
	public String getOrder(String orderId) {
		printTxState("getOrder");
		return jdbc.queryForObject(
				"SELECT order_id FROM orders WHERE order_id = ?", String.class, orderId);
	}

	/**
	 * 方法级 @Transactional(rollbackFor) → 优先级最高，覆盖类级别
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void placeOrder(String orderId, int amount) {
		printTxState("placeOrder");
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, ?, 'CREATED')",
				orderId, amount);
		// 调用另一个 Bean 的 @Transactional(REQUIRES_NEW) → TransactionInfo 栈 push
		auditService.logAction("PLACE_ORDER:" + orderId);
		// 返回后 TransactionInfo 栈已弹回 placeOrder 的层级
		printTxState("placeOrder(审计调用后)");
		System.out.println("  [placeOrder] 订单已创建: " + orderId);
	}

	@Override
	public void cancelOrder(String orderId) {
		printTxState("cancelOrder");
		jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE order_id = ?", orderId);
		System.out.println("  [cancelOrder] 订单已取消: " + orderId);
	}

	/**
	 * 打印当前事务状态 — 通过公开API观察 TransactionSynchronizationManager 绑定的线程上下文
	 *
	 * <p>底层实际是 TransactionInfo ThreadLocal 栈驱动的:
	 * <ul>
	 *   <li>TransactionInfo#bindToThread() → push 到栈顶</li>
	 *   <li>cleanupTransactionInfo() → restoreThreadLocalStatus() 弹栈</li>
	 * </ul>
	 * 在 IDEA 断点 TransactionInfo#bindToThread 可直接观察 oldTransactionInfo 链。
	 */
	static void printTxState(String label) {
		String txName = TransactionSynchronizationManager.getCurrentTransactionName();
		boolean active = TransactionSynchronizationManager.isActualTransactionActive();
		boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
		System.out.println("  [" + label + "] txName=" + txName +
				", active=" + active + ", readOnly=" + readOnly +
				", syncActive=" + syncActive);
	}
}
