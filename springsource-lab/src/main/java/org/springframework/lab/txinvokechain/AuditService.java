package org.springframework.lab.txinvokechain;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 审计服务 — 演示两个核心场景:
 *
 * <h3>场景 A: TransactionInfo ThreadLocal 栈</h3>
 * 当 OrderServiceImpl(REQUIRED) 调用 AuditService(REQUIRES_NEW) 时，
 * TransactionInfo 形成栈结构:
 * <pre>
 *   transactionInfoHolder ThreadLocal:
 *     push: OrderService.txInfo → AuditService.txInfo(栈顶)
 *     AuditService.txInfo.oldTransactionInfo = OrderService.txInfo
 *     pop:  AuditService 返回后 → restoreThreadLocalStatus() 恢复 OrderService.txInfo
 * </pre>
 *
 * <h3>场景 B: determineTransactionManager 路由</h3>
 * {@code @Transactional("auditTxManager")} 中的 value 就是 qualifier，
 * 在 {@code TransactionAspectSupport#determineTransactionManager} 中:
 * <ol>
 *   <li>读取 {@code txAttr.getQualifier()}</li>
 *   <li>用 BeanFactory.getBean(qualifier, PlatformTransactionManager.class) 查找</li>
 *   <li>缓存到 transactionManagerCache 避免重复查找</li>
 * </ol>
 *
 * <p>断点: {@code TransactionAspectSupport#determineTransactionManager} — 观察 qualifier 路由
 */
@Service
public class AuditService {

	private final JdbcTemplate auditJdbc;

	public AuditService(@Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbc) {
		this.auditJdbc = auditJdbc;
	}

	/**
	 * REQUIRES_NEW + qualifier 路由到 auditTxManager
	 * → TransactionInfo 栈 push + 独立 TM 路由
	 *
	 * <p>在 IDEA 断点 {@code TransactionInfo#bindToThread} 可观察:
	 * <ul>
	 *   <li>this.oldTransactionInfo = 调用方(placeOrder)的 TransactionInfo</li>
	 *   <li>transactionInfoHolder.set(this) → 栈顶切换为当前 AuditService 的 txInfo</li>
	 * </ul>
	 */
	@Transactional(value = "auditTxManager", propagation = Propagation.REQUIRES_NEW)
	public void logAction(String action) {
		OrderServiceImpl.printTxState("auditService.logAction");
		auditJdbc.update("INSERT INTO audit_log(action) VALUES(?)", action);
		System.out.println("  [AuditService] REQUIRES_NEW 审计日志: " + action);

		// 通过 txName 变化观察栈切换:
		// 调用方(placeOrder) 的 txName = OrderServiceImpl.placeOrder
		// 当前(logAction)   的 txName = AuditService.logAction
		// 返回后 txName 会恢复为 OrderServiceImpl.placeOrder
		System.out.println("  [AuditService] 当前 txName=" +
				TransactionSynchronizationManager.getCurrentTransactionName());
		System.out.println("  [AuditService] (断点 TransactionInfo#bindToThread → 观察 oldTransactionInfo 链)");
	}

	/**
	 * 使用默认 TM — 对比 qualifier 路由
	 */
	@Transactional
	public void logActionDefaultTm(String action) {
		OrderServiceImpl.printTxState("auditService.logActionDefaultTm");
		System.out.println("  [AuditService] 默认 TM 审计日志: " + action);
	}

	public int auditLogCount() {
		return auditJdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
	}
}
