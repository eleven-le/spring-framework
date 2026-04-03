package org.springframework.lab.dsproxy;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * <h2>读写分离路由 DataSource</h2>
 *
 * 核心机制:
 * <ol>
 *   <li>继承 AbstractRoutingDataSource, 实现 determineCurrentLookupKey()</li>
 *   <li>用 ThreadLocal 存当前线程的路由 key (WRITE / READ)</li>
 *   <li>业务层可结合 @Transactional(readOnly=true) + AOP 自动切换</li>
 * </ol>
 *
 * 生产中的典型堆叠:
 * <pre>
 *   TransactionAwareDataSourceProxy
 *       ↓
 *   LazyConnectionDataSourceProxy    ← readOnly事务若命中缓存, 不拿物理连接
 *       ↓
 *   ReadWriteRoutingDataSource       ← 按 ThreadLocal 路由到 master/slave
 *       ↓
 *   masterPool / slavePool
 * </pre>
 */
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

	public enum RouteType { WRITE, READ }

	private static final ThreadLocal<RouteType> CURRENT_ROUTE = new ThreadLocal<>();

	public static void setRoute(RouteType type) {
		CURRENT_ROUTE.set(type);
	}

	public static RouteType getRoute() {
		return CURRENT_ROUTE.get();
	}

	public static void clearRoute() {
		CURRENT_ROUTE.remove();
	}

	@Override
	protected Object determineCurrentLookupKey() {
		RouteType route = CURRENT_ROUTE.get();
		// 默认走 WRITE (master)
		return route != null ? route : RouteType.WRITE;
	}

	/**
	 * 工厂方法: 用两个 DataSource 构建读写路由
	 */
	public static ReadWriteRoutingDataSource create(DataSource master, DataSource slave) {
		ReadWriteRoutingDataSource ds = new ReadWriteRoutingDataSource();
		Map<Object, Object> targets = new HashMap<>();
		targets.put(RouteType.WRITE, master);
		targets.put(RouteType.READ, slave);
		ds.setTargetDataSources(targets);
		ds.setDefaultTargetDataSource(master);
		ds.afterPropertiesSet();
		return ds;
	}
}
