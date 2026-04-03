package org.springframework.lab.txfailure;

import java.io.IOException;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W46 — 演示 @Transactional 失效的各种场景
 *
 * <p>每个方法对应一种失效模式，可逐一断点调试观察代理行为。
 */
@Service
public class OrderService {

	private final JdbcTemplate orderJdbc;
	private final JdbcTemplate stockJdbc;

	public OrderService(@Qualifier("orderJdbc") JdbcTemplate orderJdbc,
						@Qualifier("stockJdbc") JdbcTemplate stockJdbc) {
		this.orderJdbc = orderJdbc;
		this.stockJdbc = stockJdbc;
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 1: 自调用 (Self-invocation) — 事务失效
	// ═══════════════════════════════════════════════════════════
	/**
	 * 外层方法无 @Transactional，内部直接 this 调用带 @Transactional 的方法。
	 * 因为 this 指向的是原始 target 而非代理，所以 insertWithTx() 的事务不会生效。
	 *
	 * <p>断点观察：在 TransactionInterceptor#invoke 打断点，
	 *    会发现 insertWithTx() 这次调用根本不会进入拦截器。
	 */
	public void selfInvokeFail(String item) {
		System.out.println("  [自调用] this 的类型 = " + this.getClass().getName());
		// this 是原始对象，不是代理 → @Transactional 无效
		this.insertWithTx(item);
		// 故意抛异常，验证事务是否回滚（预期：不回滚，因为根本没开事务）
		throw new RuntimeException("故意异常 — 验证自调用事务是否回滚");
	}

	@Transactional(transactionManager = "orderTxManager")
	public void insertWithTx(String item) {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
		System.out.println("  [insertWithTx] 插入订单: " + item);
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 2: AopContext.currentProxy() — 修复自调用
	// ═══════════════════════════════════════════════════════════
	/**
	 * 通过 AopContext.currentProxy() 获取代理对象，用代理调用内部方法，事务生效。
	 *
	 * <p>前提：@EnableAspectJAutoProxy(exposeProxy = true)
	 * <p>源码路径：CglibAopProxy.DynamicAdvisedInterceptor#intercept L685-688
	 *    当 exposeProxy=true 时执行 AopContext.setCurrentProxy(proxy)
	 */
	public void selfInvokeFixed(String item) {
		OrderService proxy = (OrderService) AopContext.currentProxy();
		System.out.println("  [修复自调用] proxy 的类型 = " + proxy.getClass().getName());
		proxy.insertWithTx(item);
		throw new RuntimeException("故意异常 — 验证通过代理调用是否回滚");
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 3: final 方法 — CGLIB 无法代理
	// ═══════════════════════════════════════════════════════════
	/**
	 * final 方法不能被 CGLIB 子类覆盖，调用时直接走代理实例上的方法（不经过 intercept）。
	 *
	 * <p>源码验证位置：CglibAopProxy#doValidateClass L260-271
	 *    Modifier.isFinal(mod) → 打 warn 日志但不阻止创建代理
	 *    结果：方法执行在代理实例上，this.orderJdbc 为 null → NPE
	 *
	 * <p>注意：Spring 默认使用 CGLIB 代理（Spring Boot 2.x 后 proxyTargetClass=true），
	 *    所以 final 方法是真实的坑。
	 */
	@Transactional(transactionManager = "orderTxManager")
	public final void finalMethodFail(String item) {
		// CGLIB 无法 override final 方法 → 不走 DynamicAdvisedInterceptor#intercept
		// 方法在代理子类上执行，但字段没有被初始化 → NPE 或无事务
		System.out.println("  [final 方法] 尝试插入: " + item);
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 4: private 方法 — 注解被静默忽略
	// ═══════════════════════════════════════════════════════════
	/**
	 * private 方法上的 @Transactional 被 AbstractFallbackTransactionAttributeSource
	 * 在 computeTransactionAttribute() L167 直接 return null（因为 allowPublicMethodsOnly=true）。
	 *
	 * <p>此方法只能在类内部调用，配合 publicEntryForPrivateTest() 演示。
	 */
	@Transactional(transactionManager = "orderTxManager")
	private void privateMethodFail(String item) {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
		System.out.println("  [private 方法] 插入订单: " + item);
	}

	/**
	 * public 入口方法，通过 this 调用 private 方法。
	 * 双重失效：既是自调用，private 上的 @Transactional 也被忽略。
	 */
	public void publicEntryForPrivateTest(String item) {
		System.out.println("  [public 入口] 调用 private @Transactional 方法");
		this.privateMethodFail(item);
		throw new RuntimeException("故意异常 — private 方法的事务不会回滚");
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 5: 多事务管理器 — 不指定 qualifier 报错
	// ═══════════════════════════════════════════════════════════
	/**
	 * 容器中有两个 PlatformTransactionManager（orderTxManager + stockTxManager），
	 * @Transactional 未指定 transactionManager → determineTransactionManager() L503
	 * 调用 beanFactory.getBean(TransactionManager.class) → NoUniqueBeanDefinitionException
	 */
	@Transactional  // 没指定 transactionManager！
	public void noQualifierFail(String item) {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 6: 多事务管理器 — 正确使用 qualifier
	// ═══════════════════════════════════════════════════════════
	/**
	 * 通过 @Transactional(transactionManager = "orderTxManager") 明确指定，
	 * determineTransactionManager() L491-493 走 qualifier 分支，正确找到对应的 TM。
	 */
	@Transactional(transactionManager = "orderTxManager")
	public void withQualifierSuccess(String item) {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
		System.out.println("  [指定 qualifier] 插入订单: " + item);
	}

	/**
	 * 跨库操作：订单写订单库，库存写库存库，各自有独立事务管理器。
	 */
	@Transactional(transactionManager = "orderTxManager")
	public void crossDbWithQualifier(String item) {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
		// 注意：这里 stockJdbc 走的是 stockDs 的连接，不在 orderTxManager 的事务范围内
		stockJdbc.update("INSERT INTO stock(item, qty) VALUES(?, ?)", item, 50);
		System.out.println("  [跨库] 订单和库存都写入了，但只有订单在事务保护内");
		throw new RuntimeException("故意异常 — 观察哪个库回滚、哪个不回滚");
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 7: Checked Exception 不回滚
	// ═══════════════════════════════════════════════════════════
	/**
	 * 默认 @Transactional 只对 RuntimeException 和 Error 回滚。
	 * 抛出 checked exception (IOException) 时事务会 commit！
	 *
	 * <p>源码：TransactionAspectSupport#completeTransactionAfterThrowing L670
	 *    txInfo.transactionAttribute.rollbackOn(ex) →
	 *    DefaultTransactionAttribute#rollbackOn → (ex instanceof RuntimeException || ex instanceof Error)
	 *    IOException 不匹配 → 走 else 分支 → commit()
	 */
	@Transactional(transactionManager = "orderTxManager")
	public void checkedExceptionNoRollback(String item) throws IOException {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
		System.out.println("  [checked 异常] 插入后抛 IOException");
		throw new IOException("业务异常 — checked exception 不触发回滚");
	}

	/**
	 * 对比：使用 rollbackFor 明确指定 checked exception 也回滚。
	 */
	@Transactional(transactionManager = "orderTxManager", rollbackFor = Exception.class)
	public void checkedExceptionWithRollbackFor(String item) throws IOException {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
		System.out.println("  [rollbackFor] 插入后抛 IOException");
		throw new IOException("业务异常 — 配置了 rollbackFor=Exception.class，会回滚");
	}

	// ═══════════════════════════════════════════════════════════
	// 实验 8: catch 吞异常 — 事务不感知
	// ═══════════════════════════════════════════════════════════
	/**
	 * 在 @Transactional 方法内 catch 了异常，没有 re-throw。
	 * TransactionInterceptor 认为方法正常返回 → 走 commitTransactionAfterReturning → commit
	 * 数据入库了，事务"失效"。
	 *
	 * <p>这不是代理问题，是开发者对事务边界的误解。
	 */
	@Transactional(transactionManager = "orderTxManager")
	public void swallowExceptionFail(String item) {
		orderJdbc.update("INSERT INTO orders(item, price) VALUES(?, ?)", item, 100);
		try {
			// 模拟业务出错
			int x = 1 / 0;
		}
		catch (Exception e) {
			System.out.println("  [吞异常] catch 了异常但没 re-throw: " + e.getMessage());
			// 没有 re-throw → TransactionInterceptor 认为正常返回 → commit
		}
	}
}
