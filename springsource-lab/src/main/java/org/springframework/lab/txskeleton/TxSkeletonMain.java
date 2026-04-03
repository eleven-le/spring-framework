package org.springframework.lab.txskeleton;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W18 — 事务模板骨架：TransactionAspectSupport + AbstractPlatformTransactionManager
 *
 * <h2>一句话抽象</h2>
 * TransactionAspectSupport 解决"AOP 拦截后如何以模板骨架驱动事务生命周期"（开启→执行→提交/回滚→清理），
 * AbstractPlatformTransactionManager 解决"传播行为如何映射到物理操作"（挂起/恢复连接 + 同步回调序列），
 * 核心矛盾是"一个线程上可能叠加多层逻辑事务，但底层只有一个物理连接"——
 * 解法是 ThreadLocal 栈（TransactionInfo 链表 + SuspendedResourcesHolder 快照）保存/恢复上下文。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: REQUIRES_NEW — 完整的 suspend → doBegin → doCommit → resume 链路</li>
 *   <li>实验 2: NOT_SUPPORTED — suspend 后以"空事务"模式执行</li>
 *   <li>实验 3: 同步回调 4 阶段 — beforeCommit/beforeCompletion/afterCommit/afterCompletion</li>
 *   <li>实验 4: NESTED — savepoint 机制，不 suspend，在同一物理连接上 createAndHoldSavepoint</li>
 *   <li>实验 5: TransactionInfo ThreadLocal 栈 — REQUIRED 嵌套时 bindToThread/restoreThreadLocalStatus</li>
 *   <li>实验 6: REQUIRES_NEW 内层失败 → 外层不受影响（suspend/resume 保护）</li>
 * </ul>
 *
 * <h2>核心调用链 (12 步)</h2>
 * <pre>
 *  ① TransactionInterceptor#invoke : AOP 入口，委托 invokeWithinTransaction
 *  ② TransactionAspectSupport#invokeWithinTransaction : 模板骨架，解析 TxAttribute → 获取 TxManager → 开启 → 执行 → 提交/回滚
 *  ③ TransactionAspectSupport#createTransactionIfNecessary : 包装 TransactionAttribute，调 tm.getTransaction()
 *  ④ AbstractPlatformTransactionManager#getTransaction : 判断是否已有事务 → 走 handleExistingTransaction 或 startTransaction
 *  ⑤ APTM#handleExistingTransaction : 传播行为大分支 — REQUIRES_NEW→suspend+startTransaction / NESTED→savepoint / REQUIRED→参与
 *  ⑥ APTM#suspend : doSuspendSynchronization(逐个回调suspend) → doSuspend(解绑连接) → 清空 ThreadLocal → 打包进 SuspendedResourcesHolder
 *  ⑦ APTM#startTransaction → doBegin : 获取新连接、setAutoCommit(false)、绑定到 ThreadLocal
 *  ⑧ TransactionAspectSupport#prepareTransactionInfo : 构造 TransactionInfo → bindToThread(保存 oldTransactionInfo 形成栈)
 *  ⑨ invocation.proceedWithInvocation : 执行真正的业务方法
 *  ⑩ APTM#processCommit : triggerBeforeCommit → triggerBeforeCompletion → doCommit → triggerAfterCommit → triggerAfterCompletion
 *  ⑪ APTM#cleanupAfterCompletion : 清理同步 → doCleanupAfterCompletion → 如有 suspendedResources 则 resume
 *  ⑫ TransactionAspectSupport#cleanupTransactionInfo : restoreThreadLocalStatus — 把 oldTransactionInfo 弹回 ThreadLocal
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>TransactionAspectSupport#invokeWithinTransaction L382 — 模板骨架总入口</li>
 *   <li>AbstractPlatformTransactionManager#suspend L569 — 观察挂起过程: 同步回调被暂停、连接被解绑、ThreadLocal 被清空</li>
 *   <li>AbstractPlatformTransactionManager#handleExistingTransaction L408 — 传播行为大 if-else 分支入口</li>
 *   <li>AbstractPlatformTransactionManager#processCommit L721 — 同步回调 4 阶段的触发顺序</li>
 *   <li>AbstractPlatformTransactionManager#cleanupAfterCompletion L987 — resume 恢复外层事务的入口</li>
 * </ol>
 */
public class TxSkeletonMain {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxSkeletonConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

		// 初始化表
		jdbc.execute("CREATE TABLE IF NOT EXISTS orders(id INT AUTO_INCREMENT PRIMARY KEY, item VARCHAR(100), price INT)");
		jdbc.execute("CREATE TABLE IF NOT EXISTS audit_log(id INT AUTO_INCREMENT PRIMARY KEY, msg VARCHAR(200))");

		OrderService orderService = ctx.getBean(OrderService.class);
		AuditService auditService = ctx.getBean(AuditService.class);

		// ========== 实验 1: REQUIRES_NEW — suspend/resume 全流程 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 1: REQUIRES_NEW — suspend → doBegin → doCommit → resume");
		System.out.println("═══════════════════════════════════════════════════════");
		resetData(jdbc);
		orderService.placeOrder("iPhone", 9999);
		printTableCounts(jdbc, "实验1");

		// ========== 实验 2: NOT_SUPPORTED — suspend 但不开新事务 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 2: NOT_SUPPORTED — suspend 后以空事务执行");
		System.out.println("═══════════════════════════════════════════════════════");
		resetData(jdbc);
		orderService.placeOrderWithNotSupported("MacBook", 14999);
		printTableCounts(jdbc, "实验2");

		// ========== 实验 3: 同步回调 4 阶段 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 3: TransactionSynchronization 回调顺序");
		System.out.println("═══════════════════════════════════════════════════════");
		resetData(jdbc);
		orderService.placeOrderWithSync("AirPods", 1999);
		printTableCounts(jdbc, "实验3");

		// ========== 实验 4: NESTED — savepoint ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 4: NESTED — savepoint 回滚不影响外层");
		System.out.println("═══════════════════════════════════════════════════════");
		resetData(jdbc);
		orderService.placeOrderWithNested("iPad", 5999);
		int orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		int logCount = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
		System.out.println("  orders=" + orderCount + " (期望1, 外层提交)");
		System.out.println("  audit_log=" + logCount + " (期望0, 嵌套回滚到 savepoint)");

		// ========== 实验 5: TransactionInfo ThreadLocal 栈 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 5: TransactionInfo 栈 — REQUIRED 嵌套 bindToThread/restoreThreadLocalStatus");
		System.out.println("═══════════════════════════════════════════════════════");
		resetData(jdbc);
		orderService.placeOrderChained("Watch", 3999);
		printTableCounts(jdbc, "实验5");

		// ========== 实验 6: REQUIRES_NEW 内层失败 → 外层不受影响 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 6: REQUIRES_NEW 内层失败 → suspend/resume 保护外层");
		System.out.println("═══════════════════════════════════════════════════════");
		resetData(jdbc);
		orderService.placeOrderResilient("Vision Pro", 29999);
		orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		logCount = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
		System.out.println("  orders=" + orderCount + " (期望1, 外层正常提交)");
		System.out.println("  audit_log=" + logCount + " (期望0, REQUIRES_NEW 内层回滚)");

		ctx.close();
		System.out.println("\n[完成] W18 事务模板骨架全部实验执行完毕");
	}

	private static void resetData(JdbcTemplate jdbc) {
		jdbc.execute("DELETE FROM orders");
		jdbc.execute("DELETE FROM audit_log");
	}

	private static void printTableCounts(JdbcTemplate jdbc, String label) {
		int orders = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		int logs = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
		System.out.println("  [" + label + " 结果] orders=" + orders + ", audit_log=" + logs);
	}
}
