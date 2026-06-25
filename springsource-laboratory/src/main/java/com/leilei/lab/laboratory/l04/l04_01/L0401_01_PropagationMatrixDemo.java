package com.leilei.lab.laboratory.l04.l04_01;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 📖 知识点：[[L04-01-声明式事务与七种传播行为#2. 🏭 生产怎么用对]]（7 种传播行为的用例矩阵）
 * 🎯 作用：把 7 种传播行为 ×「无外层事务 / 有外层事务」两种上下文跑成一张真值矩阵。
 *         用一台 {@link LoggingDataSourceTransactionManager}（在真实 DataSourceTransactionManager 上
 *         埋点打印 begin/suspend/resume/commit/rollback）直接暴露 Spring 传播引擎
 *         {@code AbstractPlatformTransactionManager#getTransaction / handleExistingTransaction} 的真实动作：
 *         哪些会「新开事务」、哪些「加入当前事务」、哪些「挂起当前事务」、哪些直接抛异常。
 *         矩阵用 {@link TransactionSynchronizationManager#isActualTransactionActive()} 判定内层是否真的处在事务里。
 * 🔗 业务场景：C 端下单主流程（REQUIRED）里嵌套调用「写操作流水（REQUIRES_NEW）」「批量上架子项（NESTED）」
 *         「只读查营销价（SUPPORTS/NOT_SUPPORTED）」「强约束的对账方法（MANDATORY/NEVER）」——
 *         选错传播行为，要么该独立的没独立（主单回滚把流水也带走），要么该一起回滚的各回各的（超卖）。
 */
public final class L0401_01_PropagationMatrixDemo {

	private L0401_01_PropagationMatrixDemo() {
	}

	/** 真实 DataSourceTransactionManager + 生命周期埋点：每个钩子打印一行，肉眼看清传播引擎的动作。 */
	static class LoggingDataSourceTransactionManager extends DataSourceTransactionManager {

		LoggingDataSourceTransactionManager(DataSource dataSource) {
			super(dataSource);
			// DataSourceTransactionManager 构造器已默认 setNestedTransactionAllowed(true)，NESTED 才能走 savepoint
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			System.out.println("      └─[doBegin]   物理开启新事务  " + describe(definition));
			super.doBegin(transaction, definition);
		}

		@Override
		protected Object doSuspend(Object transaction) {
			System.out.println("      └─[doSuspend] 挂起当前事务（连接解绑入栈）");
			return super.doSuspend(transaction);
		}

		@Override
		protected void doResume(Object transaction, Object suspendedResources) {
			System.out.println("      └─[doResume]  恢复被挂起的事务");
			super.doResume(transaction, suspendedResources);
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
			System.out.println("      └─[doCommit]  提交物理事务");
			super.doCommit(status);
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			System.out.println("      └─[doRollback]回滚物理事务");
			super.doRollback(status);
		}

		private static String describe(TransactionDefinition def) {
			return "(propagation=" + def.getPropagationBehavior()
					+ ", readOnly=" + def.isReadOnly() + ")";
		}
	}

	/** 把传播常量翻译成可读名，矩阵打印用。 */
	private static String name(int propagation) {
		switch (propagation) {
			case TransactionDefinition.PROPAGATION_REQUIRED: return "REQUIRED     ";
			case TransactionDefinition.PROPAGATION_SUPPORTS: return "SUPPORTS     ";
			case TransactionDefinition.PROPAGATION_MANDATORY: return "MANDATORY    ";
			case TransactionDefinition.PROPAGATION_REQUIRES_NEW: return "REQUIRES_NEW ";
			case TransactionDefinition.PROPAGATION_NOT_SUPPORTED: return "NOT_SUPPORTED";
			case TransactionDefinition.PROPAGATION_NEVER: return "NEVER        ";
			case TransactionDefinition.PROPAGATION_NESTED: return "NESTED       ";
			default: return "?";
		}
	}

	/** 在「内层」用指定传播行为跑一次，返回它实际落到的事务状态；异常被捕获转成结论文案。 */
	private static String runInner(TransactionTemplate tm, int propagation) {
		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setPropagationBehavior(propagation);
		TransactionTemplate inner = new TransactionTemplate(tm.getTransactionManager(), def);
		try {
			return inner.execute(status -> {
				boolean active = TransactionSynchronizationManager.isActualTransactionActive();
				boolean newTx = status.isNewTransaction();
				boolean savepoint = (status instanceof DefaultTransactionStatus)
						&& ((DefaultTransactionStatus) status).hasSavepoint();
				if (!active) {
					return "无事务运行 active=false（非事务方式执行）";
				}
				if (savepoint) {
					return "处于事务中 active=true，在外层事务内打了 savepoint";
				}
				return "处于事务中 active=true，" + (newTx ? "且是新开的事务" : "加入了已存在事务");
			});
		}
		catch (RuntimeException ex) {
			return "❌ 抛 " + ex.getClass().getSimpleName();
		}
	}

	public static void main(String[] args) {
		DataSource ds = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		LoggingDataSourceTransactionManager tm = new LoggingDataSourceTransactionManager(ds);
		TransactionTemplate root = new TransactionTemplate(tm);

		int[] propagations = {
				TransactionDefinition.PROPAGATION_REQUIRED,
				TransactionDefinition.PROPAGATION_SUPPORTS,
				TransactionDefinition.PROPAGATION_MANDATORY,
				TransactionDefinition.PROPAGATION_REQUIRES_NEW,
				TransactionDefinition.PROPAGATION_NOT_SUPPORTED,
				TransactionDefinition.PROPAGATION_NEVER,
				TransactionDefinition.PROPAGATION_NESTED,
		};

		System.out.println("==================== 列 ①：调用时【无外层事务】====================");
		String[] noOuter = new String[propagations.length];
		for (int i = 0; i < propagations.length; i++) {
			System.out.println("· " + name(propagations[i]).trim() + "：");
			noOuter[i] = runInner(root, propagations[i]);
		}

		System.out.println();
		System.out.println("==================== 列 ②：调用时【已有外层 REQUIRED 事务】====================");
		String[] withOuter = new String[propagations.length];
		for (int i = 0; i < propagations.length; i++) {
			final int idx = i;
			System.out.println("· 外层 REQUIRED 开启，内层 " + name(propagations[i]).trim() + "：");
			try {
				root.execute(new TransactionCallbackWithoutResult() {
					@Override
					protected void doInTransactionWithoutResult(org.springframework.transaction.TransactionStatus s) {
						withOuter[idx] = runInner(root, propagations[idx]);
					}
				});
			}
			catch (RuntimeException ex) {
				// NEVER 在外层事务中会抛异常，连带外层 execute 抛出
				if (withOuter[idx] == null) {
					withOuter[idx] = "❌ 抛 " + ex.getClass().getSimpleName();
				}
			}
		}

		System.out.println();
		System.out.println("==================== 七种传播行为 · 真值矩阵 ====================");
		System.out.printf("%-14s | %-40s | %-40s%n", "传播行为", "① 无外层事务", "② 有外层 REQUIRED 事务");
		System.out.println("---------------+------------------------------------------+------------------------------------------");
		for (int i = 0; i < propagations.length; i++) {
			System.out.printf("%-14s | %-40s | %-40s%n", name(propagations[i]).trim(), noOuter[i], withOuter[i]);
		}
		System.out.println();
		System.out.println("一句话收口：REQUIRED 加入或新建（默认）；REQUIRES_NEW 永远挂起外层另起炉灶；"
				+ "NESTED 在外层内打 savepoint；SUPPORTS/NOT_SUPPORTED 顺势而为；MANDATORY/NEVER 是断言（缺/有事务即抛）。");

		((org.springframework.jdbc.datasource.embedded.EmbeddedDatabase) ds).shutdown();
	}
}
