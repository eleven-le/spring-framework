package com.leilei.lab.laboratory.l04.l04_04;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 📖 知识点：[[L04-04-多数据源与分布式事务边界#2.2 多数据源下 @Transactional 路由失效]]（路由失效）
 * 🎯 作用：自证「多数据源下事务一开，事务内再切路由 key 也没用」这个最经典的坑。机制：
 *         {@link DataSourceTransactionManager#doBegin} 在事务开始时 **只调一次**
 *         {@code routingDataSource.getConnection()}（此刻 determineCurrentLookupKey 决定走哪个物理库），
 *         拿到连接后用 {@code TransactionSynchronizationManager.bindResource(routingDS, connectionHolder)} 把它
 *         绑死到当前线程；之后事务内所有 SQL 经 {@code DataSourceUtils.getConnection} 拿到的都是这条**已绑定的连接**，
 *         {@code determineCurrentLookupKey} 不会再被调用——所以事务内切 key = 无效，写仍落在 doBegin 时那个库。
 * 🔗 业务场景：开发以为「@Transactional 方法里先 switchRegion(华东) 写一笔，再 switchRegion(华南) 写一笔」能跨两库，
 *         实际两笔都写进了华东库，华南库的数据莫名其妙不见 / 对不上账。结论：路由 key 必须在事务开始前定，
 *         一个本地事务天然绑死一个物理库——这正是「跨库要靠分布式事务 / 消息最终一致」的根因（见 L0404_04）。
 */
public final class L0404_02_RoutingFailsInsideTxDemo {

	private L0404_02_RoutingFailsInsideTxDemo() {
	}

	private static final String EAST = "EAST";
	private static final String SOUTH = "SOUTH";
	private static final long SKU = 200402L;   // 多肉葡萄·大杯

	private static final ThreadLocal<String> REGION = new ThreadLocal<>();

	public static void main(String[] args) {
		EmbeddedDatabase east = newRegionDb();
		EmbeddedDatabase south = newRegionDb();

		Map<Object, Object> targets = new HashMap<>();
		targets.put(EAST, east);
		targets.put(SOUTH, south);
		RoutingDataSource routing = new RoutingDataSource();
		routing.setTargetDataSources(targets);
		routing.setDefaultTargetDataSource(east);
		routing.afterPropertiesSet();

		JdbcTemplate jdbc = new JdbcTemplate(routing);
		// 事务管理器管的是「路由数据源」本身——连接在 doBegin 时按当时的 key 借出并绑定
		PlatformTransactionManager txManager = new DataSourceTransactionManager(routing);
		TransactionTemplate tx = new TransactionTemplate(txManager);

		System.out.println("==================== 场景 A（坏）：事务内切 key，路由失效 ====================");
		REGION.set(EAST);   // 事务开始时 key=EAST → 连接绑定到华东库
		tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				jdbc.update("update inventory set stock = stock - 1 where sku_id = ?", SKU);   // 想写华东
				REGION.set(SOUTH);   // ← 事务内切到华南，开发以为接下来会写华南库
				jdbc.update("update inventory set stock = stock - 10 where sku_id = ?", SKU);  // 实际仍写华东！
			}
		});
		REGION.remove();
		System.out.println("华东库 stock = " + stockOf(east) + "  （期望 999，实际 989：两笔都落在了华东库！）");
		System.out.println("华南库 stock = " + stockOf(south) + "  （期望 990，实际 1000：事务内切 key 完全无效，华南库纹丝不动）");
		System.out.println("→ 根因：连接在 doBegin 借出即绑定到华东库，事务内 getConnection 永远拿这条已绑定连接，"
				+ "determineCurrentLookupKey 不再被调用。");

		// 复位数据
		reset(east);
		reset(south);

		System.out.println();
		System.out.println("==================== 场景 B（对照）：key 在事务开始前定好，路由正常 ====================");
		REGION.set(EAST);
		tx.execute(emptyDeduct(jdbc, 1));     // 整个事务都走华东
		REGION.set(SOUTH);
		tx.execute(emptyDeduct(jdbc, 10));    // 新事务 doBegin 时 key=SOUTH → 这次绑定华南库
		REGION.remove();
		System.out.println("华东库 stock = " + stockOf(east) + "  （-1，正确）");
		System.out.println("华南库 stock = " + stockOf(south) + "  （-10，正确）");

		System.out.println();
		System.out.println("结论：路由 key 必须在事务开始【之前】确定；一个本地事务只能绑定一个物理库，"
				+ "@Transactional 无法靠「事务内切 key」跨库。跨库一致性要么上分布式事务，要么用消息最终一致（见 L0404_04）。");

		east.shutdown();
		south.shutdown();
	}

	private static TransactionCallbackWithoutResult emptyDeduct(JdbcTemplate jdbc, int qty) {
		return new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				jdbc.update("update inventory set stock = stock - ? where sku_id = ?", qty, SKU);
			}
		};
	}

	private static int stockOf(DataSource db) {
		Integer n = new JdbcTemplate(db).queryForObject("select stock from inventory where sku_id = ?", Integer.class, SKU);
		return n == null ? -1 : n;
	}

	private static void reset(DataSource db) {
		new JdbcTemplate(db).update("update inventory set stock = 1000 where sku_id = ?", SKU);
	}

	private static EmbeddedDatabase newRegionDb() {
		EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(db);
		jdbc.execute("create table inventory(sku_id bigint primary key, stock int)");
		jdbc.update("insert into inventory(sku_id, stock) values (?, ?)", SKU, 1000);
		return db;
	}

	static final class RoutingDataSource extends AbstractRoutingDataSource {

		@Override
		protected Object determineCurrentLookupKey() {
			return REGION.get();
		}
	}
}
