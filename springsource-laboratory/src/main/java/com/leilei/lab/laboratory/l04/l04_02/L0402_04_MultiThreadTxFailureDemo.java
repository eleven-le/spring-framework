package com.leilei.lab.laboratory.l04.l04_02;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 📖 知识点：[[L04-02-事务失效八股的事故现场#2. 🏭 生产怎么用对]]（事务失效八场景之「多线程」⭐）
 * 🎯 作用：用真实可回滚的 HSQLDB 证明事务【不跨线程】。Spring 事务靠
 *         {@link TransactionSynchronizationManager} 把连接绑在【当前线程】的 ThreadLocal 上；
 *         在 {@code @Transactional} 方法里新起子线程做 DB 写，子线程拿到的是另一条连接、
 *         {@code isActualTransactionActive()} 实读 false——它【不在主事务里】，会各自独立提交。
 *         于是主线程回滚时，子线程已提交的数据【活了下来】，造成主从数据不一致。
 * 🔗 业务场景：下单后为提速，主事务里 new Thread 并行扣减多仓库存 / 并行写流水。压测看着没问题，
 *         一旦主事务因风控校验回滚，子线程那几笔扣减却已落库——超卖 + 对不平账，典型大促事故。
 */
public final class L0402_04_MultiThreadTxFailureDemo {

	private L0402_04_MultiThreadTxFailureDemo() {
	}

	@Configuration
	@EnableTransactionManagement
	static class TxConfig {

		@Bean
		public DataSource dataSource() {
			return new EmbeddedDatabaseBuilder()
					.setType(EmbeddedDatabaseType.HSQL)
					.generateUniqueName(true)
					.build();
		}

		@Bean
		public PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean
		public JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		public ParallelDeductService parallelDeductService(JdbcTemplate jdbcTemplate) {
			return new ParallelDeductService(jdbcTemplate);
		}
	}

	/** 并行扣减服务：主事务内起子线程写库存流水，最后主事务回滚。 */
	static class ParallelDeductService {

		private final JdbcTemplate jdbc;

		ParallelDeductService(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		/**
		 * 主事务：先在主线程写订单，再开子线程写库存扣减，随后主线程抛异常回滚。
		 * 期望（事故）：orders 回滚 = 0 行；stock_deduct 子线程独立提交 = 1 行（活下来，不一致）。
		 */
		@Transactional
		public void deductInParallelThenFail(String orderId, long skuId) throws InterruptedException {
			System.out.println("  [主线程] isActualTransactionActive = "
					+ TransactionSynchronizationManager.isActualTransactionActive());
			jdbc.update("insert into orders(id) values (?)", orderId);

			CountDownLatch done = new CountDownLatch(1);
			Thread worker = new Thread(() -> {
				try {
					System.out.println("  [子线程] isActualTransactionActive = "
							+ TransactionSynchronizationManager.isActualTransactionActive()
							+ "   ← 子线程不在主事务里");
					jdbc.update("insert into stock_deduct(sku_id) values (?)", skuId);
				}
				finally {
					done.countDown();
				}
			}, "deduct-worker");
			worker.start();
			done.await(5, TimeUnit.SECONDS);   // 等子线程写完，确保它已独立提交

			throw new IllegalStateException("风控校验未过，主事务回滚");
		}
	}

	private static void initSchema(JdbcTemplate jdbc) {
		jdbc.execute("create table orders(id varchar(64) primary key)");
		jdbc.execute("create table stock_deduct(id identity primary key, sku_id bigint)");
	}

	private static int count(JdbcTemplate jdbc, String table) {
		Integer n = jdbc.queryForObject("select count(*) from " + table, Integer.class);
		return n == null ? 0 : n;
	}

	public static void main(String[] args) throws InterruptedException {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxConfig.class)) {
			JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
			ParallelDeductService svc = ctx.getBean(ParallelDeductService.class);
			initSchema(jdbc);

			System.out.println("==================== 主事务内开子线程写库存，随后主事务回滚 ====================");
			try {
				svc.deductInParallelThenFail("GM-T-001", 4001L);
			}
			catch (IllegalStateException expected) {
				System.out.println("主事务按预期抛出并回滚：" + expected.getMessage());
			}
			System.out.println("orders 行数       = " + count(jdbc, "orders")
					+ "   ✅ 期望 0：主线程写入随主事务回滚");
			System.out.println("stock_deduct 行数 = " + count(jdbc, "stock_deduct")
					+ "   ❌ 期望 0 实得 1：子线程另起连接独立提交，不随主事务回滚——数据不一致");

			System.out.println();
			System.out.println("结论：事务上下文绑定在线程 ThreadLocal 上，不跨线程传播。要让并行写也受事务约束，"
					+ "要么不在事务方法里裸开线程，要么改为「主线程串行 + 异步只读」或上分布式事务/Saga 补偿。");
		}
	}
}
