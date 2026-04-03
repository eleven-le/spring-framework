package org.springframework.lab.processortour;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 桩 TransactionManager — 让 @Transactional 能正常工作（无需真数据库）。
 *
 * <p>仅用于演示代理织入，打印事务开始/提交/回滚。
 */
@Configuration(proxyBeanMethods = false)
public class StubTransactionManager {

	@Bean
	public PlatformTransactionManager transactionManager() {
		return new PlatformTransactionManager() {
			@Override
			public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
				System.out.println("    [StubTxManager] >>> 开启事务");
				return new SimpleTransactionStatus(true);
			}

			@Override
			public void commit(TransactionStatus status) throws TransactionException {
				System.out.println("    [StubTxManager] <<< 提交事务");
			}

			@Override
			public void rollback(TransactionStatus status) throws TransactionException {
				System.out.println("    [StubTxManager] <<< 回滚事务");
			}
		};
	}
}
