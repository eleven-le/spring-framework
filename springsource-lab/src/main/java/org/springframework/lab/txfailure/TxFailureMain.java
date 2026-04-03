package org.springframework.lab.txfailure;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W46 — @Transactional 常见失效与边界：自调用/代理类型/final/private/多事务管理器
 *
 * <h2>一句话抽象</h2>
 * @Transactional 的生效前提是"调用必须穿透代理对象到达 TransactionInterceptor"——
 * 一切失效场景的本质都是"调用链路绕过了代理"或"拦截器内部判定不需要事务"，
 * 核心矛盾是"Java 方法调用的语义（this 直达目标）与 AOP 代理的语义（必须经过包装层）不一致"。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: 自调用失效 — this.method() 绕过代理</li>
 *   <li>实验 2: AopContext.currentProxy() 修复自调用</li>
 *   <li>实验 3: final 方法 — CGLIB 无法覆盖</li>
 *   <li>实验 4: private 方法 — 注解被静默忽略</li>
 *   <li>实验 5: 多事务管理器不指定 qualifier → NoUniqueBeanDefinitionException</li>
 *   <li>实验 6: 多事务管理器指定 qualifier + 跨库事务边界</li>
 *   <li>实验 7: Checked Exception 默认不回滚 vs rollbackFor</li>
 *   <li>实验 8: catch 吞异常导致事务提交</li>
 * </ul>
 *
 * <h2>核心调用链 (10 步)</h2>
 * <pre>
 *  ① Proxy(CGLIB/JDK) 接收方法调用 : 外部调用进入代理对象
 *  ② DynamicAdvisedInterceptor#intercept / JdkDynamicAopProxy#invoke : 获取拦截器链
 *  ③ TransactionInterceptor#invoke : AOP 拦截入口，委托 invokeWithinTransaction
 *  ④ TransactionAspectSupport#invokeWithinTransaction : 获取 TxAttribute + 确定 TxManager
 *  ⑤ AbstractFallbackTransactionAttributeSource#computeTransactionAttribute : 检查方法可见性、查找注解(4级回退)
 *  ⑥ TransactionAspectSupport#determineTransactionManager : qualifier → beanName → getBean(TM.class) 三级查找
 *  ⑦ AbstractPlatformTransactionManager#getTransaction : 开启/参与事务
 *  ⑧ 业务方法执行 : proceed → target.method()
 *  ⑨ TransactionAspectSupport#completeTransactionAfterThrowing : rollbackOn(ex) 判定是否回滚
 *  ⑩ commitTransactionAfterReturning / rollback : 正常返回则提交，rollbackOn=true 则回滚
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>CglibAopProxy.DynamicAdvisedInterceptor#intercept L679 — 代理入口，验证"调用有没有进代理"</li>
 *   <li>AbstractFallbackTransactionAttributeSource#computeTransactionAttribute L167 — 观察 private/非 public 方法被 return null</li>
 *   <li>TransactionAspectSupport#determineTransactionManager L485 — 观察多 TM 时的选择逻辑</li>
 *   <li>TransactionAspectSupport#completeTransactionAfterThrowing L670 — 观察 rollbackOn(ex) 对 checked vs unchecked 的不同判定</li>
 *   <li>CglibAopProxy#doValidateClass L261 — 观察 final 方法的警告日志</li>
 * </ol>
 */
public class TxFailureMain {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxFailureConfig.class);
		JdbcTemplate orderJdbc = ctx.getBean("orderJdbc", JdbcTemplate.class);
		JdbcTemplate stockJdbc = ctx.getBean("stockJdbc", JdbcTemplate.class);

		// 初始化表
		orderJdbc.execute("CREATE TABLE IF NOT EXISTS orders(id INT AUTO_INCREMENT PRIMARY KEY, item VARCHAR(100), price INT)");
		stockJdbc.execute("CREATE TABLE IF NOT EXISTS stock(id INT AUTO_INCREMENT PRIMARY KEY, item VARCHAR(100), qty INT)");

		OrderService svc = ctx.getBean(OrderService.class);
		System.out.println("代理类型: " + svc.getClass().getName());
		System.out.println();

		// ========== 实验 1: 自调用失效 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 1: 自调用 — this.insertWithTx() 绕过代理，事务不生效");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		try {
			svc.selfInvokeFail("自调用商品");
		}
		catch (RuntimeException e) {
			System.out.println("  捕获异常: " + e.getMessage());
		}
		int count = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		System.out.println("  orders 行数 = " + count + " (期望 1, 因为无事务保护，数据没被回滚)\n");

		// ========== 实验 2: AopContext.currentProxy() 修复自调用 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 2: AopContext.currentProxy() 修复自调用");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		try {
			svc.selfInvokeFixed("修复商品");
		}
		catch (RuntimeException e) {
			System.out.println("  捕获异常: " + e.getMessage());
		}
		count = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		System.out.println("  orders 行数 = " + count + " (期望 0, 代理调用 → 事务生效 → 异常回滚)\n");

		// ========== 实验 3: final 方法 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 3: final 方法 — CGLIB 无法代理");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		try {
			svc.finalMethodFail("final商品");
			count = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
			System.out.println("  orders 行数 = " + count);
		}
		catch (NullPointerException e) {
			System.out.println("  捕获 NPE: CGLIB 代理实例上的字段未初始化 (orderJdbc == null)");
			System.out.println("  原因: final 方法不走 intercept，直接在代理子类实例上执行");
		}
		System.out.println();

		// ========== 实验 4: private 方法 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 4: private 方法 — @Transactional 被静默忽略");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		try {
			svc.publicEntryForPrivateTest("private商品");
		}
		catch (RuntimeException e) {
			System.out.println("  捕获异常: " + e.getMessage());
		}
		count = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		System.out.println("  orders 行数 = " + count + " (期望 1, 事务未生效，数据没回滚)\n");

		// ========== 实验 5: 多事务管理器不指定 qualifier ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 5: 多事务管理器 — 未指定 qualifier");
		System.out.println("═══════════════════════════════════════════════════════");
		try {
			svc.noQualifierFail("无qualifier商品");
			System.out.println("  未抛异常 (可能某个 TM 被 @Primary 或先注册)");
		}
		catch (Exception e) {
			System.out.println("  捕获异常: " + e.getClass().getSimpleName());
			System.out.println("  原因: 容器中有多个 TransactionManager，未指定 qualifier");
			System.out.println("  源码: TransactionAspectSupport#determineTransactionManager L503");
		}
		System.out.println();

		// ========== 实验 6: 多事务管理器指定 qualifier + 跨库 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 6: 多事务管理器 — 指定 qualifier + 跨库事务边界");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		stockJdbc.execute("DELETE FROM stock");
		try {
			svc.crossDbWithQualifier("跨库商品");
		}
		catch (RuntimeException e) {
			System.out.println("  捕获异常: " + e.getMessage());
		}
		int orderCount = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		int stockCount = stockJdbc.queryForObject("SELECT COUNT(*) FROM stock", Integer.class);
		System.out.println("  orders 行数 = " + orderCount + " (期望 0, orderTxManager 回滚了)");
		System.out.println("  stock  行数 = " + stockCount + " (期望 1, stockDs 不在 orderTxManager 管辖内，自动提交了)");
		System.out.println();

		// ========== 实验 7: Checked Exception 不回滚 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 7a: Checked Exception 默认不回滚");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		try {
			svc.checkedExceptionNoRollback("checked商品");
		}
		catch (Exception e) {
			System.out.println("  捕获异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		count = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		System.out.println("  orders 行数 = " + count + " (期望 1, IOException 不触发回滚 → 事务提交了)");

		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 7b: rollbackFor = Exception.class → checked 也回滚");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		try {
			svc.checkedExceptionWithRollbackFor("rollbackFor商品");
		}
		catch (Exception e) {
			System.out.println("  捕获异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		count = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		System.out.println("  orders 行数 = " + count + " (期望 0, 配置了 rollbackFor → 回滚了)\n");

		// ========== 实验 8: catch 吞异常 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 8: catch 吞异常 — 事务正常提交");
		System.out.println("═══════════════════════════════════════════════════════");
		resetOrders(orderJdbc);
		svc.swallowExceptionFail("吞异常商品");
		count = orderJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
		System.out.println("  orders 行数 = " + count + " (期望 1, catch 吞掉异常 → TransactionInterceptor 认为正常返回 → commit)\n");

		ctx.close();
		System.out.println("[完成] W46 @Transactional 失效边界全部实验执行完毕");
	}

	private static void resetOrders(JdbcTemplate jdbc) {
		jdbc.execute("DELETE FROM orders");
	}
}
