package com.leilei.lab.laboratory.l04.l04_04;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 📖 知识点：[[L04-04-多数据源与分布式事务边界#2.1 AbstractRoutingDataSource 动态多数据源]]（动态多数据源 ⭐）
 * 🎯 作用：用 {@link AbstractRoutingDataSource} 把「一个逻辑 DataSource」在运行期按线程上下文的 key 动态
 *         路由到「多个物理库」。核心是子类只实现一个抽象方法 {@code determineCurrentLookupKey()}——返回当前
 *         线程要走哪个库；父类在每次 {@code getConnection()} 时调它，再去 targetDataSources 里 get 出真正的库。
 *         本类自证：同一个 JdbcTemplate、同一条 SQL，仅切换线程上下文里的「区域 key」，读到的就是不同物理库的数据。
 * 🔗 业务场景：古茗 5 万+ 门店按区域分库（华东库 / 华南库各存本区门店的 SKU 库存与价格），C 端读价/查库存时
 *         按门店所属区域路由到对应物理库。AbstractRoutingDataSource 是「分库 / 读写分离 / 多租户」这类
 *         动态多数据源方案（含 dynamic-datasource 的 @DS）的底座。
 */
public final class L0404_01_DynamicRoutingDataSourceDemo {

	private L0404_01_DynamicRoutingDataSourceDemo() {
	}

	static final String REGION_EAST = "EAST";    // 华东库
	static final String REGION_SOUTH = "SOUTH";   // 华南库

	private static final long HOT_SKU = 200401L;  // 杨枝甘露·大杯，同一 SKU 在两库各有独立库存

	/** 线程上下文里的「当前区域」——路由 key 的来源，每个 C 端请求线程绑定自己门店的区域。 */
	static final ThreadLocal<String> CURRENT_REGION = new ThreadLocal<>();

	public static void main(String[] args) {
		EmbeddedDatabase east = newRegionDb("库存=8000（华东仓）", HOT_SKU, 8000);
		EmbeddedDatabase south = newRegionDb("库存=300（华南仓）", HOT_SKU, 300);

		RegionRoutingDataSource routing = buildRoutingDataSource(east, south);
		JdbcTemplate jdbc = new JdbcTemplate(routing);

		System.out.println("==================== 同一个 JdbcTemplate + 同一条 SQL，只切区域 key ====================");

		CURRENT_REGION.set(REGION_EAST);
		System.out.println("区域=EAST  → SKU " + HOT_SKU + " 库存 = " + queryStock(jdbc) + "  （命中华东物理库）");

		CURRENT_REGION.set(REGION_SOUTH);
		System.out.println("区域=SOUTH → SKU " + HOT_SKU + " 库存 = " + queryStock(jdbc) + "  （命中华南物理库）");

		CURRENT_REGION.set(REGION_EAST);
		System.out.println("区域=EAST  → SKU " + HOT_SKU + " 库存 = " + queryStock(jdbc) + "  （切回华东，再次命中华东库）");

		CURRENT_REGION.remove();
		System.out.println("区域=未设置 → SKU " + HOT_SKU + " 库存 = " + queryStock(jdbc) + "  （key=null，回落 default=华东库）");

		System.out.println();
		System.out.println("结论：AbstractRoutingDataSource 把「选哪个库」收敛成一个 determineCurrentLookupKey()，"
				+ "运行期按线程上下文动态路由。注意路由发生在「getConnection 的那一刻」——这埋下了下一课"
				+ "「多数据源下 @Transactional 路由失效」的伏笔（连接一旦在 doBegin 借出并绑定，事务内再切 key 也没用）。");

		east.shutdown();
		south.shutdown();
	}

	/** 装配路由数据源：targetDataSources（key→库）+ defaultTargetDataSource（兜底）+ afterPropertiesSet（解析）。 */
	static RegionRoutingDataSource buildRoutingDataSource(DataSource east, DataSource south) {
		Map<Object, Object> targets = new HashMap<>();
		targets.put(REGION_EAST, east);
		targets.put(REGION_SOUTH, south);

		RegionRoutingDataSource routing = new RegionRoutingDataSource();
		routing.setTargetDataSources(targets);
		routing.setDefaultTargetDataSource(east);   // key 为 null / 未命中时的兜底库
		routing.afterPropertiesSet();               // 必须显式调用：把 targetDataSources 解析进 resolvedDataSources
		return routing;
	}

	private static int queryStock(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select stock from inventory where sku_id = ?", Integer.class, HOT_SKU);
		return n == null ? -1 : n;
	}

	/** 建一个区域物理库，写入该 SKU 在本区的独立库存，方便后续「读到哪个数字」反推「路由到哪个库」。 */
	private static EmbeddedDatabase newRegionDb(String label, long skuId, int stock) {
		EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(db);
		jdbc.execute("create table inventory(sku_id bigint primary key, stock int)");
		jdbc.update("insert into inventory(sku_id, stock) values (?, ?)", skuId, stock);
		return db;
	}

	/**
	 * 区域路由数据源：唯一要实现的就是 determineCurrentLookupKey()——返回当前线程的区域 key。
	 * 父类 {@link AbstractRoutingDataSource#determineTargetDataSource()} 拿这个 key 去 resolvedDataSources 里 get 真正的库。
	 */
	static final class RegionRoutingDataSource extends AbstractRoutingDataSource {

		@Override
		protected Object determineCurrentLookupKey() {
			return CURRENT_REGION.get();   // 返回 null 时父类回落到 defaultTargetDataSource
		}
	}
}
