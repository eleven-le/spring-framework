package org.springframework.lab.txresource;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * <h2>读写分离路由 DataSource — readOnly 感知版</h2>
 *
 * 通过 {@link TransactionSynchronizationManager#isCurrentTransactionReadOnly()} 自动路由:
 * <ul>
 *   <li>readOnly = true → SLAVE (从库)</li>
 *   <li>readOnly = false → MASTER (主库)</li>
 * </ul>
 *
 * <h3>⚠ 必须配合 LazyConnectionDataSourceProxy 使用!</h3>
 * <p>
 * 原因: 不用 LazyConnection 时, doBegin() 阶段就调 getConnection() 触发路由,
 * 但此时 prepareSynchronization() 尚未执行, TSM.isCurrentTransactionReadOnly()
 * 返回上一次事务的残留值(通常为 false) → readOnly 事务也路由到 MASTER.
 * </p>
 * <p>
 * 加 LazyConnection 后: doBegin() 拿到 lazy 代理, 不触发路由.
 * 等到业务代码首次 createStatement 时才调 getConnection() 触发路由,
 * 此时 TSM.isCurrentTransactionReadOnly() 已正确设置.
 * </p>
 */
public class ReadOnlyAwareRoutingDataSource extends AbstractRoutingDataSource {

	public enum Route { MASTER, SLAVE }

	@Override
	protected Object determineCurrentLookupKey() {
		boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		Route route = readOnly ? Route.SLAVE : Route.MASTER;
		System.out.println("    [ReadOnlyAwareRouting] TSM.isCurrentTransactionReadOnly()="
				+ readOnly + " → 路由到 " + route);
		return route;
	}

	/**
	 * 工厂方法: 构建 readOnly 感知的读写路由 DataSource
	 */
	public static ReadOnlyAwareRoutingDataSource create(DataSource master, DataSource slave) {
		ReadOnlyAwareRoutingDataSource ds = new ReadOnlyAwareRoutingDataSource();
		Map<Object, Object> m = new HashMap<>();
		m.put(Route.MASTER, master);
		m.put(Route.SLAVE, slave);
		ds.setTargetDataSources(m);
		ds.setDefaultTargetDataSource(master);
		ds.afterPropertiesSet();
		return ds;
	}
}
