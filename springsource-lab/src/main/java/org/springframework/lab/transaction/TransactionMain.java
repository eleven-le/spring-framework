package org.springframework.lab.transaction;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * W17 — @Transactional 挂载全链路：注解 → Advisor → 拦截器
 *
 * <h2>一句话抽象</h2>
 * @Transactional 解决的核心问题是"如何用声明式标注把事务开启/提交/回滚逻辑从业务代码中彻底剥离"；
 * 机制矛盾是"注解只是元数据，必须有人读取它、织入拦截器、并在运行时驱动 TransactionManager 完成真正的事务操作"——
 * Spring 的解法是 @EnableTransactionManagement 注册三件套（Advisor + Interceptor + AttributeSource），
 * 由 InfrastructureAdvisorAutoProxyCreator 在 Bean 创建时自动织入代理，
 * 方法调用时 TransactionInterceptor 读取注解属性，委托 PlatformTransactionManager 执行 begin/commit/rollback。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: 代理结构检查 — 验证事务 Advisor 已挂载、代理类型</li>
 *   <li>实验 2: 正常提交 — 转账后余额变化</li>
 *   <li>实验 3: RuntimeException 回滚 vs CheckedException 不回滚 vs rollbackFor</li>
 *   <li>实验 4: 自调用失效 — this.method() 绕过代理</li>
 *   <li>实验 5: REQUIRES_NEW — 外层回滚不影响内层独立事务</li>
 *   <li>实验 6: TransactionSynchronization — 事务提交后回调（发消息/删缓存）</li>
 * </ul>
 *
 * <h2>核心调用链 (12 步)</h2>
 * <pre>
 *  ① @EnableTransactionManagement → @Import(TransactionManagementConfigurationSelector) : 触发选择器
 *  ② TransactionManagementConfigurationSelector#selectImports : PROXY 模式导入 AutoProxyRegistrar + ProxyTransactionManagementConfiguration
 *  ③ AutoProxyRegistrar#registerBeanDefinitions : 注册 InfrastructureAdvisorAutoProxyCreator (APC)
 *  ④ ProxyTransactionManagementConfiguration : @Bean 注册 Advisor + TransactionInterceptor + AnnotationTransactionAttributeSource
 *  ⑤ InfrastructureAdvisorAutoProxyCreator#postProcessAfterInitialization : Bean 创建完毕后检查
 *  ⑥ AbstractAdvisorAutoProxyCreator#findEligibleAdvisors : 发现 BeanFactoryTransactionAttributeSourceAdvisor
 *  ⑦ TransactionAttributeSourcePointcut#matches : 用 AnnotationTransactionAttributeSource 检查方法上是否有 @Transactional
 *  ⑧ AbstractAutoProxyCreator#createProxy : 命中 → 创建 JDK/CGLIB 代理
 *  ⑨ TransactionInterceptor#invoke : 方法调用时拦截，委托 invokeWithinTransaction
 *  ⑩ TransactionAspectSupport#invokeWithinTransaction : 核心模板 — getTransactionAttribute → determineTransactionManager → createTransactionIfNecessary
 *  ⑪ AbstractPlatformTransactionManager#getTransaction : 根据传播行为决定新建/加入/挂起事务，调 doBegin
 *  ⑫ completeTransactionAfterThrowing / commitTransactionAfterReturning : 异常→回滚或正常→提交
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>TransactionManagementConfigurationSelector#selectImports — 启动期: 看导入了哪些配置类</li>
 *   <li>TransactionAttributeSourcePointcut#matches — 启动期: 看哪些方法被识别为事务方法</li>
 *   <li>TransactionInterceptor#invoke — 运行时: 方法调用进入拦截器的入口</li>
 *   <li>TransactionAspectSupport#invokeWithinTransaction — 运行时: 事务模板核心（开启→执行→提交/回滚）</li>
 *   <li>AbstractPlatformTransactionManager#getTransaction — 运行时: 传播行为分支（REQUIRED/REQUIRES_NEW/NESTED）</li>
 * </ol>
 */
public class TransactionMain {

	public static void main(String[] args) throws Exception {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TransactionConfig.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

		// ========== 初始化表结构和数据 ==========
		jdbc.execute("CREATE TABLE IF NOT EXISTS account (name VARCHAR(50) PRIMARY KEY, balance INT)");
		jdbc.execute("CREATE TABLE IF NOT EXISTS tx_log (id INT AUTO_INCREMENT PRIMARY KEY, msg VARCHAR(200))");

		AccountServiceImpl accountService = ctx.getBean(AccountServiceImpl.class);
		LogService logService = ctx.getBean(LogService.class);

		// ========== 实验 1: 代理结构检查 ==========
		System.out.println("═══════════════════════════════════════════");
		System.out.println("实验 1: 代理结构检查");
		System.out.println("═══════════════════════════════════════════");
		inspectProxy(ctx.getBean(AccountService.class));

		// ========== 实验 2: 正常提交 ==========
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 2: 正常事务提交");
		System.out.println("═══════════════════════════════════════════");
		resetData(jdbc);
		accountService.transfer("Alice", "Bob", 100);
		System.out.println("  Alice 余额: " + accountService.balance("Alice") + " (期望 900)");
		System.out.println("  Bob   余额: " + accountService.balance("Bob") + " (期望 1100)");

		// ========== 实验 3: 回滚规则 ==========
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 3: 回滚规则 — RuntimeException vs CheckedException");
		System.out.println("═══════════════════════════════════════════");

		// 3a: RuntimeException → 回滚
		resetData(jdbc);
		try {
			accountService.transferWithError("Alice", "Bob", 200);
		}
		catch (RuntimeException e) {
			System.out.println("  [3a] 捕获 RuntimeException: " + e.getMessage());
		}
		System.out.println("  Alice 余额: " + accountService.balance("Alice") + " (期望 1000, 已回滚)");

		// 3b: CheckedException → 不回滚（默认行为！）
		resetData(jdbc);
		try {
			accountService.transferChecked("Alice", "Bob", 200);
		}
		catch (Exception e) {
			System.out.println("  [3b] 捕获 CheckedException: " + e.getMessage());
		}
		System.out.println("  Alice 余额: " + accountService.balance("Alice") + " (期望 800, 未回滚!)");

		// 3c: CheckedException + rollbackFor → 回滚
		resetData(jdbc);
		try {
			accountService.transferCheckedWithRollback("Alice", "Bob", 200);
		}
		catch (Exception e) {
			System.out.println("  [3c] 捕获 CheckedException (rollbackFor): " + e.getMessage());
		}
		System.out.println("  Alice 余额: " + accountService.balance("Alice") + " (期望 1000, 已回滚)");

		// ========== 实验 4: 自调用失效 ==========
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 4: 自调用(self-invocation)事务失效");
		System.out.println("═══════════════════════════════════════════");
		resetData(jdbc);
		System.out.println("  代理对象调 transfer → 经过 TransactionInterceptor");
		accountService.transfer("Alice", "Bob", 50);
		System.out.println("  直接调 selfInvokeTransfer → this.transfer() 绕过代理");
		accountService.selfInvokeTransfer("Alice", "Bob", 50);
		System.out.println("  (在 TransactionInterceptor#invoke 打断点 → selfInvoke 时不会进入!)");

		// ========== 实验 5: REQUIRES_NEW ==========
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 5: REQUIRES_NEW — 外层回滚不影响内层");
		System.out.println("═══════════════════════════════════════════");
		resetData(jdbc);
		jdbc.execute("DELETE FROM tx_log");
		try {
			accountService.transferWithAudit("Alice", "Bob", 300);
		}
		catch (RuntimeException e) {
			System.out.println("  外层异常: " + e.getMessage());
		}
		System.out.println("  Alice 余额: " + accountService.balance("Alice") + " (期望 1000, 外层已回滚)");
		System.out.println("  日志条数: " + logService.logCount() + " (期望 1, REQUIRES_NEW 日志保留!)");

		// ========== 实验 6: TransactionSynchronization ==========
		System.out.println("\n═══════════════════════════════════════════");
		System.out.println("实验 6: TransactionSynchronization — 事务提交后回调");
		System.out.println("═══════════════════════════════════════════");
		resetData(jdbc);
		demonstrateSynchronization(ctx);

		ctx.close();
		System.out.println("\n[完成] 所有实验执行完毕");
	}

	/**
	 * 检查代理结构：是否为 AOP 代理、挂载了哪些 Advisor
	 */
	private static void inspectProxy(Object bean) {
		System.out.println("  Bean 类型: " + bean.getClass().getName());
		System.out.println("  是否 AOP 代理: " + AopUtils.isAopProxy(bean));
		System.out.println("  是否 JDK 代理: " + AopUtils.isJdkDynamicProxy(bean));
		System.out.println("  是否 CGLIB 代理: " + AopUtils.isCglibProxy(bean));

		if (bean instanceof Advised) {
			Advised advised = (Advised) bean;
			Advisor[] advisors = advised.getAdvisors();
			System.out.println("  Advisor 数量: " + advisors.length);
			for (int i = 0; i < advisors.length; i++) {
				Advisor adv = advisors[i];
				System.out.println("    [" + i + "] " + adv.getClass().getSimpleName());
				if (adv instanceof BeanFactoryTransactionAttributeSourceAdvisor) {
					System.out.println("        → 事务 Advisor！Advice = " +
							adv.getAdvice().getClass().getSimpleName());
					if (adv.getAdvice() instanceof TransactionInterceptor) {
						TransactionInterceptor ti = (TransactionInterceptor) adv.getAdvice();
						System.out.println("        → TransactionManager = " +
								ti.getTransactionManager().getClass().getSimpleName());
					}
				}
			}
		}
	}

	/**
	 * 演示 TransactionSynchronization — 事务提交后发送异步消息/删缓存的标准做法
	 */
	private static void demonstrateSynchronization(AnnotationConfigApplicationContext ctx) {
		AccountServiceImpl svc = ctx.getBean(AccountServiceImpl.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

		// 使用编程式事务 + 同步回调
		org.springframework.transaction.PlatformTransactionManager txMgr =
				ctx.getBean(org.springframework.transaction.PlatformTransactionManager.class);
		org.springframework.transaction.support.TransactionTemplate txTemplate =
				new org.springframework.transaction.support.TransactionTemplate(txMgr);

		txTemplate.execute(status -> {
			jdbc.update("UPDATE account SET balance = balance - 50 WHERE name = 'Alice'");
			jdbc.update("UPDATE account SET balance = balance + 50 WHERE name = 'Bob'");

			// 注册同步回调：在事务提交后执行（典型场景：发 MQ 消息、删缓存）
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					System.out.println("  [Sync] afterCommit → 这里发送 MQ 消息 / 删缓存");
				}

				@Override
				public void afterCompletion(int status) {
					String statusStr = (status == STATUS_COMMITTED) ? "COMMITTED" : "ROLLED_BACK";
					System.out.println("  [Sync] afterCompletion → status=" + statusStr);
				}
			});

			System.out.println("  [Sync] 事务内操作完成，等待提交...");
			return null;
		});
		System.out.println("  Alice 余额: " + svc.balance("Alice") + " (期望 950)");
	}

	private static void resetData(JdbcTemplate jdbc) {
		jdbc.execute("MERGE INTO account(name, balance) KEY(name) VALUES('Alice', 1000)");
		jdbc.execute("MERGE INTO account(name, balance) KEY(name) VALUES('Bob', 1000)");
	}
}
