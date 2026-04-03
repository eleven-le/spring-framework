package org.springframework.lab.txresource;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * <h2>读写分离路由 DataSource — ThreadLocal 版</h2>
 *
 * 经典用法: AOP / Filter 在 @Transactional 之前设置 ThreadLocal,
 * 事务开启时 getConnection() → determineCurrentLookupKey() 读取路由.
 *
 * 核心约束:
 * <ul>
 *   <li>ThreadLocal 必须在事务开始前设置</li>
 *   <li>同一事务内不可切换路由 (TSM 已锁定连接)</li>
 * </ul>
 */
public class RwRoutingDataSource extends AbstractRoutingDataSource {

	public enum Route { MASTER, SLAVE }

	private static final ThreadLocal<Route> CTX = new ThreadLocal<>();

	public static void set(Route r) { CTX.set(r); }

	public static Route get() { return CTX.get(); }

	public static void clear() { CTX.remove(); }

	@Override
	protected Object determineCurrentLookupKey() {
		Route r = CTX.get();
		return r != null ? r : Route.MASTER;
	}

	/**
	 * 工厂方法: 构建读写路由 DataSource
	 */
	public static RwRoutingDataSource create(DataSource master, DataSource slave) {
		RwRoutingDataSource ds = new RwRoutingDataSource();
		Map<Object, Object> m = new HashMap<>();
		m.put(Route.MASTER, master);
		m.put(Route.SLAVE, slave);
		ds.setTargetDataSources(m);
		ds.setDefaultTargetDataSource(master);
		ds.afterPropertiesSet();
		return ds;
	}
}
