package com.leilei.lab.laboratory.l04.l04_03;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;

import javax.sql.DataSource;

import com.leilei.lab.laboratory.common.bench.BenchReport;
import com.leilei.lab.laboratory.common.bench.ConcurrentBench;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 📖 知识点：[[L04-03-编程式事务与大事务治理#3. 🧨 事故与避坑]]（大事务治理与锁持有时间 ⭐）
 * 🎯 作用：量化「大事务的锁/连接持有时间」如何吃掉系统吞吐。两种实现做同一件事——
 *         「调一次慢营销 RPC（价格/优惠匹配）+ 写一行库存」：
 *         ① 声明式大事务（坏）：把慢 RPC 圈进了事务（等价于 {@code @Transactional} 包住整个方法），
 *            事务从 RPC 前就开始、RPC 全程占着数据库连接（生产里还占着行锁），持有窗口 ≈ RPC + 写；
 *         ② 编程式小事务（好）：用 {@link TransactionTemplate} 只把「写库」一步圈进事务，
 *            RPC 在事务外先跑完，持有窗口 ≈ 写。
 *         用一个 Semaphore 限流的「连接池」(poolSize=4) + {@link ConcurrentBench} 压测，证明：
 *         连接被持有越久，连接池越快被打满、吞吐被锁死在 poolSize / 持有时间 这个天花板上。
 * 🔗 业务场景：古茗大促下单链路里，下单服务在一个事务里同步调营销中心算优惠（跨服务 RPC，p99 几十~上百 ms）。
 *         平时不显，大促瞬时高并发时，每个连接被 RPC 拖着不放，连接池秒满，全站下单排队雪崩——
 *         这就是「大事务 = 长锁持有 = 吞吐天花板」的经典事故，治理第一刀就是把 RPC 移出事务边界。
 */
public final class L0403_02_BigTxLockHoldTimeDemo {

	private L0403_02_BigTxLockHoldTimeDemo() {
	}

	private static final long HOT_SKU = 200302L;     // 杨枝甘露·大杯，热点 SKU
	private static final long RPC_MILLIS = 40;        // 模拟营销中心算优惠的慢 RPC
	private static final int POOL_SIZE = 4;           // 连接池大小（吞吐天花板的分子）
	private static final int THREADS = 16;            // 并发线程
	private static final int ITERS = 4;               // 每线程迭代

	public static void main(String[] args) {
		EmbeddedDatabase realDb = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		// 用 Semaphore 把嵌入式库包成一个「最多 POOL_SIZE 个连接」的连接池——连接被借走多久，许可就被占多久
		BoundedConnectionPoolDataSource pool = new BoundedConnectionPoolDataSource(realDb, POOL_SIZE);
		JdbcTemplate jdbc = new JdbcTemplate(pool);
		PlatformTransactionManager txManager = new DataSourceTransactionManager(pool);
		TransactionTemplate txTemplate = new TransactionTemplate(txManager);

		jdbc.execute("create table inventory(sku_id bigint primary key, stock int)");
		jdbc.update("insert into inventory(sku_id, stock) values (?, ?)", HOT_SKU, 1_000_000);

		OrderService service = new OrderService(jdbc, txTemplate);

		System.out.println("==================== ① 单次：连接（行锁）持有窗口对比 ====================");
		long bigHold = measureHoldMillis(service::placeOrderBigTx);
		service.placeOrderSmallTx();                   // 触发一次以填充 lastTxHoldMillis
		long smallHold = service.lastTxHoldMillis();   // 编程式版内部记录的「事务内」持有窗口
		System.out.println("声明式大事务：连接持有 ≈ " + bigHold + "ms（RPC " + RPC_MILLIS + "ms 全程占着连接 + 写）");
		System.out.println("编程式小事务：连接持有 ≈ " + smallHold + "ms（RPC 在事务外，事务只圈住写）");
		System.out.println("→ 同样一笔下单，持有时间相差一个数量级，差额全是「连接/行锁被慢 RPC 白白占用」的时间。");

		System.out.println();
		System.out.println("==================== ② 并发：连接池(" + POOL_SIZE + ") 下的吞吐对比 ====================");
		System.out.println("吞吐天花板 ≈ 连接池大小 / 单次持有时间。持有越久，天花板越低。");
		System.out.println();

		BenchReport big = ConcurrentBench.run("声明式大事务(RPC在事务内)", THREADS, ITERS, service::placeOrderBigTx);
		System.out.println(big.prettyPrint());
		System.out.println();
		BenchReport small = ConcurrentBench.run("编程式小事务(RPC在事务外)", THREADS, ITERS, service::placeOrderSmallTx);
		System.out.println(small.prettyPrint());

		System.out.println();
		System.out.printf("吞吐提升 ≈ %.1f×（编程式 %.0f ops/s vs 声明式 %.0f ops/s）%n",
				small.getOpsPerSecond() / Math.max(1.0, big.getOpsPerSecond()),
				small.getOpsPerSecond(), big.getOpsPerSecond());
		System.out.println("结论：大事务治理第一刀——把 RPC / HTTP / 远程缓存 / 重计算移出事务边界，"
				+ "用编程式 TransactionTemplate 精确圈住「只有 DB 写」的最小窗口，锁与连接持有时间立刻塌缩。");

		realDb.shutdown();
	}

	/** 测量一段「持有连接」的调用墙钟（毫秒）。 */
	private static long measureHoldMillis(Runnable action) {
		long begin = System.nanoTime();
		action.run();
		return (System.nanoTime() - begin) / 1_000_000;
	}

	/** 下单服务：同一业务（慢 RPC + 写库）的两种事务边界写法。 */
	static final class OrderService {

		private final JdbcTemplate jdbc;
		private final TransactionTemplate txTemplate;
		private volatile long lastTxHoldMillis;

		OrderService(JdbcTemplate jdbc, TransactionTemplate txTemplate) {
			this.jdbc = jdbc;
			this.txTemplate = txTemplate;
		}

		long lastTxHoldMillis() {
			return this.lastTxHoldMillis;
		}

		/**
		 * 坏：声明式大事务的等价物——事务从头开到尾，慢 RPC 全程在事务内，连接被它一直占着。
		 * （这里用 TransactionTemplate 把「RPC + 写」一起圈进回调，行为等同 {@code @Transactional} 包住整个方法。）
		 */
		void placeOrderBigTx() {
			txTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					callMarketingRpc();   // ← 慢 RPC 在事务内：连接/行锁被白白占住 RPC_MILLIS
					jdbc.update("update inventory set stock = stock - 1 where sku_id = ?", HOT_SKU);
				}
			});
		}

		/**
		 * 好：编程式小事务——先把慢 RPC 跑完（事务外，不占连接），再开一个极短事务只写库。
		 */
		void placeOrderSmallTx() {
			callMarketingRpc();   // ← 慢 RPC 在事务外：此刻没有任何连接/行锁被持有
			long begin = System.nanoTime();
			txTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					jdbc.update("update inventory set stock = stock - 1 where sku_id = ?", HOT_SKU);
				}
			});
			this.lastTxHoldMillis = (System.nanoTime() - begin) / 1_000_000;
		}

		/** 模拟营销中心算优惠的慢 RPC（生产里是跨服务调用，p99 几十~上百 ms）。 */
		private void callMarketingRpc() {
			try {
				Thread.sleep(RPC_MILLIS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * 用 Semaphore 把任意 DataSource 包成「固定大小连接池」：getConnection 先抢许可，
	 * 归还连接（close）时释放许可。许可被占用的时长 = 连接被持有的时长 = 事务持有窗口。
	 * 由此「大事务长持连接」→「许可长期被占」→「池被打满、吞吐被锁死」的链路被真实复现。
	 */
	static final class BoundedConnectionPoolDataSource extends DelegatingDataSource {

		private final Semaphore permits;

		BoundedConnectionPoolDataSource(DataSource target, int poolSize) {
			super(target);
			this.permits = new Semaphore(poolSize, true);
		}

		@Override
		public Connection getConnection() throws SQLException {
			acquire();
			return borrow(super.getConnection());
		}

		@Override
		public Connection getConnection(String username, String password) throws SQLException {
			acquire();
			return borrow(super.getConnection(username, password));
		}

		private void acquire() throws SQLException {
			try {
				this.permits.acquire();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new SQLException("等待连接池许可被中断", ex);
			}
		}

		/** 把真实连接包一层动态代理：拦截 close() 时归还连接并释放许可。 */
		private Connection borrow(Connection real) {
			boolean ok = false;
			try {
				Connection wrapped = (Connection) Proxy.newProxyInstance(
						Connection.class.getClassLoader(),
						new Class<?>[] {Connection.class},
						(proxy, method, methodArgs) -> {
							if ("close".equals(method.getName())) {
								try {
									return method.invoke(real, methodArgs);
								}
								finally {
									this.permits.release();   // 连接归还 = 许可释放，事务持有窗口到此为止
								}
							}
							try {
								return method.invoke(real, methodArgs);
							}
							catch (InvocationTargetException ex) {
								throw ex.getTargetException();
							}
						});
				ok = true;
				return wrapped;
			}
			finally {
				if (!ok) {
					this.permits.release();
				}
			}
		}
	}
}
