package org.springframework.lab.txrules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 优惠券服务 — 演示 REQUIRES_NEW 传播的"挂起/新建/独立提交"语义
 *
 * <p>核心认知：REQUIRES_NEW 会 suspend 外层事务（ThreadLocal 全部卸载），
 * 在全新的物理连接上开启独立事务。内层提交后 resume 恢复外层 ThreadLocal。
 *
 * <p>业务语义：优惠券冻结必须"先锁后用"——即使后续下单步骤失败，
 * 优惠券也不能释放（否则会被其他订单抢用），需要单独走补偿解冻流程。
 *
 * <p>源码路径:
 * handleExistingTransaction → REQUIRES_NEW
 * → suspend(transaction) → SuspendedResourcesHolder 保存 sync/name/readOnly/isolationLevel
 * → startTransaction(definition, transaction, ..., suspendedResources)
 * → doBegin(新Connection) → 独立 commit/rollback
 * → cleanupAfterCompletion → resume(transaction, suspendedResources)
 *
 * <p>断点:
 * {@code AbstractPlatformTransactionManager#suspend} — 观察 SuspendedResourcesHolder 打包内容
 */
@Service
public class CouponService {

	private final JdbcTemplate jdbc;

	public CouponService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * REQUIRES_NEW — 挂起外层，独立事务冻结优惠券
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void freeze(String couponId, String orderId) {
		jdbc.update("UPDATE coupons SET status = 'FROZEN', order_id = ? WHERE coupon_id = ?",
				orderId, couponId);
		System.out.println("  [CouponService] 冻结优惠券: " + couponId + " → 订单: " + orderId);
		System.out.println("  [CouponService] 独立事务 txName=" +
				TransactionSynchronizationManager.getCurrentTransactionName());
		System.out.println("  [CouponService] resourceCount=" +
				TransactionSynchronizationManager.getResourceMap().size() +
				" (新Connection，外层Connection已suspend)");
	}

	/**
	 * REQUIRES_NEW — 内层失败，外层不受影响
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void freezeAndFail(String couponId, String orderId) {
		jdbc.update("UPDATE coupons SET status = 'FROZEN', order_id = ? WHERE coupon_id = ?",
				orderId, couponId);
		throw new RuntimeException("优惠券冻结失败 → REQUIRES_NEW 内层独立回滚，外层不受影响");
	}
}
