package com.leilei.lab.laboratory.l04.l04_04;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 📖 知识点：[[L04-04-多数据源与分布式事务边界#2.3 读写分离的路由失效：readOnly 与事务的时序坑]]（路由失效·readOnly 时序坑）
 * 🎯 作用：自证「靠 {@code @Transactional(readOnly=true)} 自动路由到从库」为什么会失效。根因是**时序**：
 *         {@link org.springframework.transaction.support.AbstractPlatformTransactionManager#startTransaction}
 *         先 {@code doBegin}（此刻路由数据源 getConnection → determineCurrentLookupKey 决定走哪个库），
 *         **之后**才 {@code prepareSynchronization} 调
 *         {@code TransactionSynchronizationManager.setCurrentTransactionReadOnly(true)}。
 *         也就是说在路由决策那一刻，{@code isCurrentTransactionReadOnly()} 还是 false——readOnly 标记来晚了，
 *         读请求仍被路由到了主库。修复：把路由 key 用一个「先于事务拦截器执行」的显式上下文（如 @DS 注解 AOP）设好，
 *         不要依赖事务自己的 readOnly 标记。
 * 🔗 业务场景：古茗 C 端读价 / 查库存走读写分离，期望 readOnly 查询全部打到从库给主库减压。结果上线后主库读 QPS
 *         不降反一直很高——所有标了 readOnly 的查询其实都打在主库上，因为路由发生在 readOnly 标记生效之前。
 */
public final class L0404_03_ReadOnlyRoutingPitfallDemo {

	private L0404_03_ReadOnlyRoutingPitfallDemo() {
	}

	private static final String MASTER = "MASTER";
	private static final String SLAVE = "SLAVE";

	/** 显式路由 key：模拟 @DS 注解 AOP 在事务拦截器【之前】设好的上下文（修复路径用）。 */
	private static final ThreadLocal<String> EXPLICIT_KEY = new ThreadLocal<>();

	public static void main(String[] args) {
		EmbeddedDatabase master = newNodeDb(MASTER);
		EmbeddedDatabase slave = newNodeDb(SLAVE);

		Map<Object, Object> targets = new HashMap<>();
		targets.put(MASTER, master);
		targets.put(SLAVE, slave);
		ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
		routing.setTargetDataSources(targets);
		routing.setDefaultTargetDataSource(master);
		routing.afterPropertiesSet();

		JdbcTemplate jdbc = new JdbcTemplate(routing);
		PlatformTransactionManager txManager = new DataSourceTransactionManager(routing);

		System.out.println("==================== 场景 A（坏）：靠 readOnly 自动路由从库 ====================");
		EXPLICIT_KEY.remove();   // 不设显式 key，路由策略回落到「按 isCurrentTransactionReadOnly() 判主从」
		TransactionTemplate readOnlyTx = new TransactionTemplate(txManager);
		readOnlyTx.setReadOnly(true);
		readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		String served = readOnlyTx.execute(readNodeMarker(jdbc));
		System.out.println("readOnly=true 的查询，实际命中节点 = " + served
				+ "  （期望 SLAVE，实际 MASTER：readOnly 标记在 doBegin 之后才设，路由那一刻还读不到它）");

		System.out.println();
		System.out.println("==================== 场景 B（修复）：显式 key 先于事务设好 ====================");
		EXPLICIT_KEY.set(SLAVE);   // 模拟 @DS("slave") 的 AOP 在事务开始前就把 key 放进上下文
		String served2 = readOnlyTx.execute(readNodeMarker(jdbc));
		EXPLICIT_KEY.remove();
		System.out.println("显式 key=SLAVE 的查询，实际命中节点 = " + served2
				+ "  （正确命中从库：路由不再依赖事务自己的 readOnly 标记）");

		System.out.println();
		System.out.println("结论：读写分离不要用 @Transactional(readOnly=true) 去触发从库路由——路由发生在 doBegin，"
				+ "而 readOnly 标记在 prepareSynchronization（doBegin 之后）才生效，时序错位。正确做法是用独立的"
				+ "路由注解 / 上下文（@DS 之类），让 key 在进入事务之前就确定。");

		master.shutdown();
		slave.shutdown();
	}

	private static TransactionCallback<String> readNodeMarker(JdbcTemplate jdbc) {
		return (TransactionStatus status) -> jdbc.queryForObject("select node from node_marker", String.class);
	}

	/** 建一个物理节点库，写入自己的标识，读到哪个标识就证明路由到了哪个节点。 */
	private static EmbeddedDatabase newNodeDb(String node) {
		EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.HSQL)
				.generateUniqueName(true)
				.build();
		JdbcTemplate jdbc = new JdbcTemplate(db);
		jdbc.execute("create table node_marker(node varchar(16))");
		jdbc.update("insert into node_marker(node) values (?)", node);
		return db;
	}

	/**
	 * 读写分离路由：① 若显式 key 已设（@DS 风格，先于事务），用它；② 否则按事务 readOnly 标记判主从——
	 * 而②正是会踩时序坑的「想当然」写法。
	 */
	static final class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

		@Override
		protected Object determineCurrentLookupKey() {
			String explicit = EXPLICIT_KEY.get();
			if (explicit != null) {
				return explicit;
			}
			// 坑：路由这一刻在 doBegin 内，readOnly 标记尚未被 prepareSynchronization 设上 → 这里恒为 false
			return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? SLAVE : MASTER;
		}
	}
}
