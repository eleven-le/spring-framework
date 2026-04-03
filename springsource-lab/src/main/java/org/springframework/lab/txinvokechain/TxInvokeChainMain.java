package org.springframework.lab.txinvokechain;

import java.io.IOException;
import java.lang.reflect.Method;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * W44 — 事务调用链: @Transactional → TransactionInterceptor
 *
 * <h2>一句话抽象</h2>
 * 事务调用链解决的核心问题是"代理方法被调用时，如何在运行时从注解里读出事务元数据、
 * 选出正确的TransactionManager、用ThreadLocal栈管理嵌套事务上下文、并在异常时用规则引擎决定回滚还是提交"；
 * 机制矛盾是"注解是静态元数据但事务决策必须运行时动态做：元数据缓存vs首次解析、TM单例vs多TM路由、
 * ThreadLocal栈push/pop要保证嵌套安全、异常类继承深度匹配要精确"——
 * Spring的解法是 TransactionInterceptor.invoke() 作为AOP入口，委托 invokeWithinTransaction() 模板:
 * ① TransactionAttributeSource 4级回退解析+缓存 → ② determineTransactionManager qualifier路由
 * → ③ createTransactionIfNecessary → TransactionInfo.bindToThread()栈push
 * → ④ proceed执行业务 → ⑤ completeTransactionAfterThrowing/commitTransactionAfterReturning
 * → ⑥ cleanupTransactionInfo栈pop
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: invoke 入口拆解 — TransactionInterceptor 如何从 MethodInvocation 提取 targetClass</li>
 *   <li>实验 2: TransactionAttributeSource 四级回退 — method → class → interface-method → interface-class</li>
 *   <li>实验 3: TransactionInfo ThreadLocal 栈 — 嵌套 REQUIRES_NEW 时栈的 push/pop</li>
 *   <li>实验 4: rollbackOn 决策引擎 — RuleBasedTransactionAttribute 深度优先匹配</li>
 *   <li>实验 5: determineTransactionManager 路由 — qualifier 选择不同 TM</li>
 * </ul>
 *
 * <h2>核心调用链 (10 步)</h2>
 * <pre>
 *  ① CglibAopProxy$DynamicAdvisedInterceptor#intercept : 代理入口，构造 ReflectiveMethodInvocation
 *  ② TransactionInterceptor#invoke(MethodInvocation) : AOP Alliance 入口，提取 targetClass
 *  ③ TransactionAspectSupport#invokeWithinTransaction : 核心模板方法
 *  ④ AnnotationTransactionAttributeSource#getTransactionAttribute : 带缓存的注解解析
 *  ⑤ AbstractFallbackTransactionAttributeSource#computeTransactionAttribute : 四级回退(method→class→iface-method→iface-class)
 *  ⑥ TransactionAspectSupport#determineTransactionManager : qualifier路由/默认TM/缓存TM
 *  ⑦ TransactionAspectSupport#createTransactionIfNecessary : getTransaction + prepareTransactionInfo
 *  ⑧ TransactionInfo#bindToThread : ThreadLocal栈push(保存oldTransactionInfo)
 *  ⑨ invocation.proceedWithInvocation → 执行真正的业务方法
 *  ⑩ completeTransactionAfterThrowing / commitTransactionAfterReturning + cleanupTransactionInfo(栈pop)
 * </pre>
 *
 * <h2>断点位置 (5 个抓手)</h2>
 * <ol>
 *   <li>TransactionInterceptor#invoke — 运行时入口: 观察 MethodInvocation 里的 targetClass 提取</li>
 *   <li>AbstractFallbackTransactionAttributeSource#computeTransactionAttribute — 四级回退逻辑</li>
 *   <li>TransactionAspectSupport#determineTransactionManager — qualifier路由 vs 默认TM</li>
 *   <li>TransactionInfo#bindToThread — ThreadLocal 栈push，观察 oldTransactionInfo 链</li>
 *   <li>RuleBasedTransactionAttribute#rollbackOn — 规则引擎: 深度匹配+NoRollbackRule优先</li>
 * </ol>
 */
public class TxInvokeChainMain {

	public static void main(String[] args) throws Exception {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(TxInvokeChainConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		JdbcTemplate auditJdbc = ctx.getBean("auditJdbcTemplate", JdbcTemplate.class);

		// 初始化表结构
		jdbc.execute("CREATE TABLE IF NOT EXISTS orders " +
				"(order_id VARCHAR(50) PRIMARY KEY, amount INT, status VARCHAR(20))");
		auditJdbc.execute("CREATE TABLE IF NOT EXISTS audit_log " +
				"(id INT AUTO_INCREMENT PRIMARY KEY, action VARCHAR(200))");

		OrderService orderService = ctx.getBean(OrderService.class);
		AuditService auditService = ctx.getBean(AuditService.class);
		RollbackDemoService rollbackDemo = ctx.getBean(RollbackDemoService.class);

		// ========== 实验 1: invoke 入口拆解 ==========
		experiment1_invokeEntry(orderService);

		// ========== 实验 2: TransactionAttributeSource 四级回退 ==========
		experiment2_attributeFallback(ctx);

		// ========== 实验 3: TransactionInfo ThreadLocal 栈 ==========
		experiment3_transactionInfoStack(orderService, jdbc);

		// ========== 实验 4: rollbackOn 决策引擎 ==========
		experiment4_rollbackRuleEngine(rollbackDemo, jdbc);

		// ========== 实验 5: determineTransactionManager 路由 ==========
		experiment5_tmQualifierRouting(auditService, orderService, jdbc);

		ctx.close();
		System.out.println("\n[完成] W44 所有实验执行完毕");
	}

	/**
	 * 实验 1: invoke 入口拆解
	 *
	 * TransactionInterceptor.invoke() 做了什么:
	 * 1. 从 MethodInvocation.getThis() 取代理的 target
	 * 2. AopUtils.getTargetClass(target) 拿到真实类(非代理类)
	 * 3. 调用父类 invokeWithinTransaction(method, targetClass, callback)
	 */
	private static void experiment1_invokeEntry(OrderService proxy) {
		System.out.println("═══════════════════════════════════════════");
		System.out.println("实验 1: invoke 入口拆解");
		System.out.println("═══════════════════════════════════════════");

		System.out.println("  代理对象类型: " + proxy.getClass().getName());
		System.out.println("  是否 CGLIB: " + AopUtils.isCglibProxy(proxy));
		System.out.println("  是否 JDK:   " + AopUtils.isJdkDynamicProxy(proxy));

		if (proxy instanceof Advised) {
			Advised advised = (Advised) proxy;
			System.out.println("  目标类(targetClass): " +
					advised.getTargetSource().getTargetClass().getName());

			for (Advisor advisor : advised.getAdvisors()) {
				if (advisor instanceof BeanFactoryTransactionAttributeSourceAdvisor) {
					TransactionInterceptor ti =
							(TransactionInterceptor) advisor.getAdvice();
					System.out.println("  TransactionInterceptor: " + ti.getClass().getName());
					System.out.println("  TransactionAttributeSource: " +
							ti.getTransactionAttributeSource().getClass().getSimpleName());
					System.out.println("  → invoke()时: AopUtils.getTargetClass(invocation.getThis())");
					System.out.println("  → 然后委托: invokeWithinTransaction(method, targetClass, callback)");
				}
			}
		}
	}

	/**
	 * 实验 2: TransactionAttributeSource 四级回退解析
	 *
	 * AbstractFallbackTransactionAttributeSource#computeTransactionAttribute:
	 * Try 1: findTransactionAttribute(specificMethod) — 目标方法
	 * Try 2: findTransactionAttribute(specificMethod.getDeclaringClass()) — 目标类
	 * Try 3: findTransactionAttribute(method) — 接口方法(if different)
	 * Try 4: findTransactionAttribute(method.getDeclaringClass()) — 接口类
	 */
	private static void experiment2_attributeFallback(AnnotationConfigApplicationContext ctx)
			throws NoSuchMethodException {
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 2: TransactionAttributeSource 四级回退");
		System.out.println("═══════════════════════════════════════════");

		TransactionAttributeSource tas = ctx.getBean(TransactionAttributeSource.class);

		// placeOrder: 方法级有 @Transactional(rollbackFor) → 命中 Try 1
		Method placeOrder = OrderServiceImpl.class.getMethod("placeOrder", String.class, int.class);
		TransactionAttribute attr1 = tas.getTransactionAttribute(placeOrder, OrderServiceImpl.class);
		System.out.println("  placeOrder → 命中级别: 方法级 (Try 1)");
		System.out.println("    rollbackFor 生效: " + attr1.rollbackOn(new IOException("test")));
		System.out.println("    readOnly: " + attr1.isReadOnly());

		// cancelOrder: 方法级无注解，类级有 @Transactional → 命中 Try 2
		Method cancelOrder = OrderServiceImpl.class.getMethod("cancelOrder", String.class);
		TransactionAttribute attr2 = tas.getTransactionAttribute(cancelOrder, OrderServiceImpl.class);
		System.out.println("  cancelOrder → 命中级别: 类级 (Try 2)");
		System.out.println("    readOnly: " + attr2.isReadOnly() + " (类级 @Transactional 默认 false)");

		// 直接查接口方法 + 接口类 → 命中 Try 4 (接口类级 readOnly=true)
		Method ifaceGetOrder = OrderService.class.getMethod("getOrder", String.class);
		TransactionAttribute attr3 = tas.getTransactionAttribute(ifaceGetOrder, OrderService.class);
		if (attr3 != null) {
			System.out.println("  接口.getOrder (targetClass=OrderService.class):");
			System.out.println("    readOnly: " + attr3.isReadOnly() + " (接口类级 @Transactional(readOnly=true))");
		}

		// getOrder 通过实现类查 → 类级覆盖接口类级
		Method implGetOrder = OrderServiceImpl.class.getMethod("getOrder", String.class);
		TransactionAttribute attr4 = tas.getTransactionAttribute(implGetOrder, OrderServiceImpl.class);
		System.out.println("  实现类.getOrder (targetClass=OrderServiceImpl.class):");
		System.out.println("    readOnly: " + attr4.isReadOnly() +
				" (实现类级 @Transactional 覆盖接口类级 readOnly=true → 变为 false)");

		System.out.println("  [结论] 优先级: 方法 > 类 > 接口方法 > 接口类");
		System.out.println("  [结论] 结果会缓存到 attributeCache(ConcurrentHashMap) → 后续直接命中");
	}

	/**
	 * 实验 3: TransactionInfo ThreadLocal 栈
	 *
	 * OrderServiceImpl.placeOrder (REQUIRED) 内调用 AuditService.logAction (REQUIRES_NEW):
	 * - placeOrder 进入 → TransactionInfo#bindToThread: push到栈顶
	 * - logAction 进入 → TransactionInfo#bindToThread: push到栈顶, oldTransactionInfo = placeOrder的txInfo
	 * - logAction 返回 → cleanupTransactionInfo: pop, restoreThreadLocalStatus恢复 placeOrder的txInfo
	 * - placeOrder 返回 → cleanupTransactionInfo: pop, restoreThreadLocalStatus恢复 null
	 */
	private static void experiment3_transactionInfoStack(OrderService orderService, JdbcTemplate jdbc) {
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 3: TransactionInfo ThreadLocal 栈");
		System.out.println("═══════════════════════════════════════════");
		System.out.println("  调用链: OrderService.placeOrder(REQUIRED) → AuditService.logAction(REQUIRES_NEW)");
		System.out.println("  观察 TransactionInfo 栈的 push/pop:");

		jdbc.execute("DELETE FROM orders");
		orderService.placeOrder("ORD-001", 500);

		System.out.println("  [返回后] TransactionInfo 已弹栈恢复");
		System.out.println("  [断点] TransactionInfo#bindToThread — 观察 oldTransactionInfo 链");
	}

	/**
	 * 实验 4: rollbackOn 决策引擎
	 *
	 * RuleBasedTransactionAttribute#rollbackOn(Throwable):
	 * 1. 遍历所有 rollbackRules (含 RollbackRuleAttribute + NoRollbackRuleAttribute)
	 * 2. 每个规则计算异常的继承 depth (精确匹配 depth=0, 父类 depth=1, ...)
	 * 3. 取 depth 最小(最精确匹配)的规则
	 * 4. 如果是 NoRollbackRule 且 depth ≤ RollbackRule 的 depth → 不回滚
	 * 5. 无匹配规则 → 走默认: RuntimeException/Error 回滚，其它不回滚
	 */
	private static void experiment4_rollbackRuleEngine(RollbackDemoService demo, JdbcTemplate jdbc) {
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 4: rollbackOn 决策引擎");
		System.out.println("═══════════════════════════════════════════");

		// 4a: 默认规则 — RuntimeException 回滚
		jdbc.execute("DELETE FROM orders");
		try {
			demo.defaultRuleRuntime("R-4a");
		}
		catch (RuntimeException e) {
			System.out.println("  [4a] " + e.getMessage());
		}
		System.out.println("  [4a] 订单存在: " + demo.orderExists("R-4a") + " (期望 false, 已回滚)");

		// 4b: 默认规则 — CheckedException 不回滚(提交!)
		try {
			demo.defaultRuleChecked("R-4b");
		}
		catch (Exception e) {
			System.out.println("  [4b] " + e.getMessage());
		}
		System.out.println("  [4b] 订单存在: " + demo.orderExists("R-4b") + " (期望 true, 已提交!)");

		// 4c: rollbackFor=IOException → CheckedException 也回滚
		try {
			demo.rollbackForChecked("R-4c");
		}
		catch (Exception e) {
			System.out.println("  [4c] " + e.getMessage());
		}
		System.out.println("  [4c] 订单存在: " + demo.orderExists("R-4c") + " (期望 false, 已回滚)");

		// 4d: 组合规则 — IOException 不回滚(noRollbackFor depth=0 < rollbackFor depth=1)
		try {
			demo.compositeRule("R-4d");
		}
		catch (Exception e) {
			System.out.println("  [4d] " + e.getMessage());
		}
		System.out.println("  [4d] 订单存在: " + demo.orderExists("R-4d") +
				" (期望 true, noRollbackFor(IOException) depth=0 胜出!)");

		// 4e: 同组合规则 — RuntimeException 回滚(不匹配 noRollbackFor)
		try {
			demo.compositeRuleRuntime("R-4e");
		}
		catch (RuntimeException e) {
			System.out.println("  [4e] " + e.getMessage());
		}
		System.out.println("  [4e] 订单存在: " + demo.orderExists("R-4e") +
				" (期望 false, RuntimeException 不是 IOException 子类 → 只匹配 rollbackFor → 回滚)");
	}

	/**
	 * 实验 5: determineTransactionManager 路由
	 *
	 * TransactionAspectSupport#determineTransactionManager:
	 * 1. txAttr.getQualifier() → @Transactional("auditTxManager") 中的 value
	 * 2. 用 determineQualifiedTransactionManager(qualifier) 从 BeanFactory 查找
	 * 3. 查找结果缓存到 transactionManagerCache(ConcurrentMap)
	 * 4. 如果无 qualifier → 走默认 TM (transactionManager bean)
	 */
	private static void experiment5_tmQualifierRouting(
			AuditService auditService, OrderService orderService, JdbcTemplate jdbc) {
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 5: determineTransactionManager 路由");
		System.out.println("═══════════════════════════════════════════");

		System.out.println("  调用 AuditService.logAction → @Transactional(\"auditTxManager\")");
		System.out.println("  → determineTransactionManager 查找 qualifier=\"auditTxManager\"");
		auditService.logAction("TM_ROUTE_TEST");
		System.out.println("  审计日志条数: " + auditService.auditLogCount() + " (审计库)");

		System.out.println("\n  调用 AuditService.logActionDefaultTm → @Transactional (无 qualifier)");
		System.out.println("  → determineTransactionManager 走默认 TM (transactionManager bean)");
		auditService.logActionDefaultTm("DEFAULT_TM_TEST");

		System.out.println("\n  [结论] qualifier 路由: @Transactional(\"beanName\") → BeanFactory.getBean(\"beanName\", PTM.class)");
		System.out.println("  [结论] 无 qualifier → 默认 PTM bean (by type 查找)");
		System.out.println("  [断点] TransactionAspectSupport#determineTransactionManager — 观察路由分支");
	}
}
