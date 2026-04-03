package org.springframework.lab.txresource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * <h2>W68 — 事务资源多形态对照: 多DataSource / AbstractRoutingDataSource / 读写分离</h2>
 *
 * <p>核心问题: 事务到底"绑定"了什么资源? TSM 的 key 是谁? 多种 DataSource 形态下的陷阱在哪?</p>
 *
 * 六个实验, 从资源绑定机制到生产陷阱:
 * <pre>
 * 实验1: 独立多DS+独立TM    — TSM 资源 Map 可并存多个 ConnectionHolder
 * 实验2: routingDS做TM     — TSM key 是 routingDS 对象, 不是底层 targetDS
 * 实验3: 事务内切路由       — 连接已被 TSM 锁定, 切 ThreadLocal 无效
 * 实验4: key 不一致灾难     — JdbcTemplate 用 masterDS 而 TM 用 routingDS → 逃逸事务
 * 实验5: LazyConnection延迟路由 — readOnly 事务正确路由到 slave 的关键时序
 * 实验6: 无事务下切路由     — 无 TSM 保护, 每次 getConnection 拿新连接
 * </pre>
 *
 * @see org.springframework.transaction.support.TransactionSynchronizationManager
 * @see org.springframework.jdbc.datasource.DataSourceTransactionManager
 * @see org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
 * @see org.springframework.jdbc.datasource.DataSourceUtils
 */
public class TxResourceMain {

	public static void main(String[] args) throws Exception {
		System.out.println("══════════════════════════════════════════════════════════════");
		System.out.println("  W68 — 事务资源多形态对照: 多DataSource / RoutingDS / 读写分离");
		System.out.println("══════════════════════════════════════════════════════════════\n");

		exp1_MultiDsMultiTm();
		exp2_RoutingDs_TsmKey();
		exp3_MidTxRouteChange();
		exp4_KeyMismatch();
		exp5_LazyDelaysRouting();
		exp6_NoTxMultiConnection();

		System.out.println("\n══════════════════════════════════════════════════════════════");
		System.out.println("  全部实验完成");
		System.out.println("══════════════════════════════════════════════════════════════");
	}

	/** 工具方法: 创建 H2 嵌入式数据库 */
	private static DataSource h2(String name) {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.setName(name)
				.addScript("classpath:lab-txresource-schema.sql")
				.build();
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 1: 独立多DS + 独立TM — TSM 资源 Map 直视
	// ═══════════════════════════════════════════════════════════

	/**
	 * 核心证明: TSM 内部 ThreadLocal&lt;Map&lt;Object,Object&gt;&gt; 可以同时持有
	 * 多个 DataSource → ConnectionHolder 映射. 不同 TM 各管各的, 互不干扰.
	 *
	 * <p>断点: TransactionSynchronizationManager#bindResource (line 167)</p>
	 */
	static void exp1_MultiDsMultiTm() {
		System.out.println("━━━ 实验1: 独立多DS+独立TM — TSM 资源 Map 直视 ━━━");

		DataSource orderDs = h2("order-db-exp1");
		DataSource stockDs = h2("stock-db-exp1");
		PlatformTransactionManager orderTm = new DataSourceTransactionManager(orderDs);
		PlatformTransactionManager stockTm = new DataSourceTransactionManager(stockDs);

		// 同一线程, 两个独立 TM, 各自开启事务
		TransactionStatus orderTx = orderTm.getTransaction(new DefaultTransactionDefinition());
		TransactionStatus stockTx = stockTm.getTransaction(new DefaultTransactionDefinition());

		// 直视 TSM 内部资源 Map
		Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
		System.out.println("  TSM 资源 Map 大小: " + resources.size() + " (应为 2)");

		boolean hasOrder = TransactionSynchronizationManager.hasResource(orderDs);
		boolean hasStock = TransactionSynchronizationManager.hasResource(stockDs);
		System.out.println("  hasResource(orderDs): " + hasOrder);
		System.out.println("  hasResource(stockDs): " + hasStock);

		// 获取各自的 ConnectionHolder
		ConnectionHolder orderCh = (ConnectionHolder) TransactionSynchronizationManager.getResource(orderDs);
		ConnectionHolder stockCh = (ConnectionHolder) TransactionSynchronizationManager.getResource(stockDs);
		System.out.println("  orderDs 连接 hash: " + orderCh.getConnection().hashCode());
		System.out.println("  stockDs 连接 hash: " + stockCh.getConnection().hashCode());
		System.out.println("  两个连接相同? " + (orderCh.getConnection() == stockCh.getConnection()) + " (必为 false)");

		// 在各自连接上写数据
		new JdbcTemplate(orderDs).update(
				"INSERT INTO t_account(name, balance) VALUES('order-user', 100)");
		new JdbcTemplate(stockDs).update(
				"INSERT INTO t_account(name, balance) VALUES('stock-user', 200)");

		// commit order, rollback stock → 各自独立
		orderTm.commit(orderTx);
		stockTm.rollback(stockTx);

		// 验证: order 提交成功, stock 被回滚
		int orderCount = new JdbcTemplate(orderDs).queryForObject(
				"SELECT COUNT(*) FROM t_account WHERE name='order-user'", Integer.class);
		int stockCount = new JdbcTemplate(stockDs).queryForObject(
				"SELECT COUNT(*) FROM t_account WHERE name='stock-user'", Integer.class);
		System.out.println("  order commit 后:   " + orderCount + " 条 (应为1)");
		System.out.println("  stock rollback 后: " + stockCount + " 条 (应为0)");
		System.out.println("  ★ 结论: 两个TM各管各的, TSM 按 DataSource 对象引用隔离资源\n");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 2: routingDS 做 TM — TSM key 是 routingDS
	// ═══════════════════════════════════════════════════════════

	/**
	 * 核心证明: DataSourceTransactionManager.doBegin() 用 obtainDataSource()
	 * (即 routingDS) 做 bindResource 的 key. 所以:
	 * <ul>
	 *   <li>TSM.getResource(routingDS) → 找到 ConnectionHolder</li>
	 *   <li>TSM.getResource(masterDS) → null! key 不一致</li>
	 * </ul>
	 *
	 * <p>断点: DataSourceTransactionManager#doBegin line 304
	 *        → TSM.bindResource(obtainDataSource(), ...)</p>
	 */
	static void exp2_RoutingDs_TsmKey() {
		System.out.println("━━━ 实验2: routingDS做TM — TSM key 是 routingDS 而非 targetDS ━━━");

		DataSource masterDs = h2("master-exp2");
		DataSource slaveDs = h2("slave-exp2");
		RwRoutingDataSource routingDs = RwRoutingDataSource.create(masterDs, slaveDs);
		PlatformTransactionManager tm = new DataSourceTransactionManager(routingDs);

		RwRoutingDataSource.set(RwRoutingDataSource.Route.MASTER);
		TransactionStatus tx = tm.getTransaction(new DefaultTransactionDefinition());

		// 查看 TSM 中各 key 的绑定情况
		boolean boundByRouting = TransactionSynchronizationManager.hasResource(routingDs);
		boolean boundByMaster  = TransactionSynchronizationManager.hasResource(masterDs);
		boolean boundBySlave   = TransactionSynchronizationManager.hasResource(slaveDs);

		System.out.println("  TSM.hasResource(routingDS): " + boundByRouting + " ← 绑定在这里!");
		System.out.println("  TSM.hasResource(masterDS):  " + boundByMaster  + " ← null! key 对不上");
		System.out.println("  TSM.hasResource(slaveDS):   " + boundBySlave   + " ← null!");

		// 物理连接确实来自 master
		ConnectionHolder ch = (ConnectionHolder)
				TransactionSynchronizationManager.getResource(routingDs);
		Connection conn = ch.getConnection();
		System.out.println("  物理连接 class: " + conn.getClass().getName() + " (来自 H2 master)");

		// DataSourceUtils 用 routingDS 可以找到事务连接
		Connection fromUtils = DataSourceUtils.getConnection(routingDs);
		System.out.println("  DataSourceUtils.getConnection(routingDS) == 事务连接: "
				+ (fromUtils == conn));
		DataSourceUtils.releaseConnection(fromUtils, routingDs);

		tm.commit(tx);
		RwRoutingDataSource.clear();
		System.out.println("  ★ 结论: TM 绑 routingDS → JdbcTemplate 也必须用 routingDS, 不能用 masterDS\n");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 3: 事务中途切路由 — 连接已被 TSM 锁定
	// ═══════════════════════════════════════════════════════════

	/**
	 * 核心证明: 一旦事务开始, ConnectionHolder 已绑到 TSM.
	 * 后续 DataSourceUtils.getConnection(routingDS) 直接从 TSM 取,
	 * 根本不会再走 routingDS.getConnection() → determineCurrentLookupKey() 被短路!
	 *
	 * <p>断点: DataSourceUtils#doGetConnection line 106-113
	 *        → conHolder != null → 直接 return, 跳过 line 118</p>
	 */
	static void exp3_MidTxRouteChange() {
		System.out.println("━━━ 实验3: 事务内切路由 — 连接已被 TSM 锁定, 切 ThreadLocal 无效 ━━━");

		DataSource masterDs = h2("master-exp3");
		DataSource slaveDs = h2("slave-exp3");
		RwRoutingDataSource routingDs = RwRoutingDataSource.create(masterDs, slaveDs);
		PlatformTransactionManager tm = new DataSourceTransactionManager(routingDs);

		// 预装数据: master 和 slave 各有不同标记
		new JdbcTemplate(masterDs).update(
				"INSERT INTO t_account(name, balance) VALUES('master-mark', 1)");
		new JdbcTemplate(slaveDs).update(
				"INSERT INTO t_account(name, balance) VALUES('slave-mark', 2)");

		// 以 MASTER 路由开启事务
		RwRoutingDataSource.set(RwRoutingDataSource.Route.MASTER);
		TransactionStatus tx = tm.getTransaction(new DefaultTransactionDefinition());

		Connection conn1 = DataSourceUtils.getConnection(routingDs);
		System.out.println("  路由=MASTER, conn hash: " + conn1.hashCode());

		JdbcTemplate jdbc = new JdbcTemplate(routingDs);
		int masterCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM t_account WHERE name='master-mark'", Integer.class);
		System.out.println("  路由=MASTER, 查 master-mark: " + masterCount + " 条");

		// ═══ 中途切路由到 SLAVE ═══
		RwRoutingDataSource.set(RwRoutingDataSource.Route.SLAVE);
		System.out.println("  ⚠ 中途切 ThreadLocal → SLAVE");

		// 再次获取连接 — 仍然是同一个!
		Connection conn2 = DataSourceUtils.getConnection(routingDs);
		System.out.println("  路由=SLAVE, conn hash: " + conn2.hashCode() + " (与上面相同!)");
		System.out.println("  conn1 == conn2: " + (conn1 == conn2) + " ← TSM 短路, 没走 routing!");

		// 查询仍然在 master 上执行
		int masterAgain = jdbc.queryForObject(
				"SELECT COUNT(*) FROM t_account WHERE name='master-mark'", Integer.class);
		int slaveMiss = jdbc.queryForObject(
				"SELECT COUNT(*) FROM t_account WHERE name='slave-mark'", Integer.class);
		System.out.println("  路由=SLAVE 但实际仍在 master: master-mark=" + masterAgain
				+ ", slave-mark=" + slaveMiss + " (slave数据不可见)");

		DataSourceUtils.releaseConnection(conn1, routingDs);
		DataSourceUtils.releaseConnection(conn2, routingDs);
		tm.commit(tx);
		RwRoutingDataSource.clear();
		System.out.println("  ★ 结论: 事务一旦绑定连接, TSM 直接复用, determineCurrentLookupKey 被短路\n");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 4: key 不一致灾难 — JdbcTemplate 用错 DataSource
	// ═══════════════════════════════════════════════════════════

	/**
	 * 生产事故复现:
	 * <ul>
	 *   <li>TM 绑 routingDS → TSM key 是 routingDS</li>
	 *   <li>某 DAO 的 JdbcTemplate 误配了 masterDS</li>
	 *   <li>JdbcTemplate 内部调 DataSourceUtils.getConnection(masterDS)</li>
	 *   <li>TSM.getResource(masterDS) 返回 null → 拿到一条不在事务里的新连接!</li>
	 *   <li>该连接 autoCommit=true → INSERT 立即提交, rollback 无法撤销</li>
	 * </ul>
	 *
	 * <p>断点: DataSourceUtils#doGetConnection line 106 → conHolder == null</p>
	 */
	static void exp4_KeyMismatch() {
		System.out.println("━━━ 实验4: key 不一致灾难 — JdbcTemplate 用 masterDS 而 TM 用 routingDS ━━━");

		DataSource masterDs = h2("master-exp4");
		DataSource slaveDs = h2("slave-exp4");
		RwRoutingDataSource routingDs = RwRoutingDataSource.create(masterDs, slaveDs);

		// TM 绑 routingDS
		PlatformTransactionManager tm = new DataSourceTransactionManager(routingDs);
		// ❌ 错误配置: JdbcTemplate 用了底层的 masterDS!
		JdbcTemplate wrongJdbc = new JdbcTemplate(masterDs);
		// ✅ 正确配置: 应该用 routingDS
		JdbcTemplate rightJdbc = new JdbcTemplate(routingDs);

		RwRoutingDataSource.set(RwRoutingDataSource.Route.MASTER);
		TransactionStatus tx = tm.getTransaction(new DefaultTransactionDefinition());

		// 用错误配置写入 → 逃逸事务
		wrongJdbc.update("INSERT INTO t_account(name, balance) VALUES('wrong-insert', 999)");
		System.out.println("  ❌ wrongJdbc(masterDS) 写入 wrong-insert → autoCommit 立即提交");

		// 用正确配置写入 → 在事务内
		rightJdbc.update("INSERT INTO t_account(name, balance) VALUES('right-insert', 888)");
		System.out.println("  ✓ rightJdbc(routingDS) 写入 right-insert → 在事务内");

		// 回滚事务!
		tm.rollback(tx);
		System.out.println("  事务回滚!");

		// 验证: wrong-insert 仍存在 (逃逸!), right-insert 被回滚
		int wrongCount = new JdbcTemplate(masterDs).queryForObject(
				"SELECT COUNT(*) FROM t_account WHERE name='wrong-insert'", Integer.class);
		int rightCount = new JdbcTemplate(masterDs).queryForObject(
				"SELECT COUNT(*) FROM t_account WHERE name='right-insert'", Integer.class);
		System.out.println("  wrong-insert (逃逸事务): " + wrongCount + " 条 ← rollback 无效!");
		System.out.println("  right-insert (正确回滚): " + rightCount + " 条 ← rollback 生效");
		RwRoutingDataSource.clear();
		System.out.println("  ★ 结论: JdbcTemplate 的 DS 必须与 TM 的 DS 是同一个对象引用!\n");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 5: LazyConnection 延迟路由 — readOnly 正确路由
	// ═══════════════════════════════════════════════════════════

	/**
	 * <h3>核心时序问题:</h3>
	 * <pre>
	 * APTM.getTransaction()
	 *   → doBegin()                  ← 此处 obtainDS().getConnection() 触发路由
	 *     → con.setAutoCommit(false)
	 *     → bindResource()
	 *   → prepareSynchronization()   ← 此处才 setCurrentTransactionReadOnly(true)
	 * </pre>
	 * 不用 LazyConnection: 路由在 doBegin 阶段, readOnly 尚未设置 → 路由到 MASTER (错!)
	 * <br>
	 * 用 LazyConnection: doBegin 拿 lazy 代理 (不触发路由), 等到首条 SQL 创建 Statement 时
	 * 才 getTargetConnection → 此时 readOnly 已设好 → 路由到 SLAVE (对!)
	 *
	 * <p>断点: LazyConnectionInvocationHandler#getTargetConnection (line ~395)</p>
	 * <p>断点: AbstractRoutingDataSource#determineTargetDataSource (line 225)</p>
	 */
	static void exp5_LazyDelaysRouting() throws Exception {
		System.out.println("━━━ 实验5: LazyConnection 延迟路由 — readOnly 正确路由到 slave ━━━");

		DataSource masterDs = h2("master-exp5");
		DataSource slaveDs = h2("slave-exp5");
		ReadOnlyAwareRoutingDataSource routingDs =
				ReadOnlyAwareRoutingDataSource.create(masterDs, slaveDs);

		DefaultTransactionDefinition readOnlyDef = new DefaultTransactionDefinition();
		readOnlyDef.setReadOnly(true);

		// ═══ 方案A: 不用 LazyConnection ═══
		System.out.println("  [方案A — 无 Lazy]");
		PlatformTransactionManager tmDirect = new DataSourceTransactionManager(routingDs);

		TransactionStatus txA = tmDirect.getTransaction(readOnlyDef);
		// doBegin 阶段就调了 getConnection → 路由发生在 readOnly 设置之前
		System.out.println("  方案A: doBegin 阶段路由已完成 (此时 readOnly 还没设到 TSM)");
		boolean readOnlyFlagA = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		System.out.println("  方案A: TSM.isCurrentTransactionReadOnly() = " + readOnlyFlagA
				+ " (现在才是 true, 但路由已经走过了!)");
		tmDirect.commit(txA);

		// ═══ 方案B: 用 LazyConnection 包装 ═══
		System.out.println("\n  [方案B — 有 Lazy]");
		LazyConnectionDataSourceProxy lazyDs = new LazyConnectionDataSourceProxy(routingDs);
		PlatformTransactionManager tmLazy = new DataSourceTransactionManager(lazyDs);

		TransactionStatus txB = tmLazy.getTransaction(readOnlyDef);
		boolean readOnlyFlagB = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		System.out.println("  方案B: TSM.isCurrentTransactionReadOnly() = " + readOnlyFlagB);
		System.out.println("  方案B: doBegin 拿到 lazy 代理, 路由尚未触发");

		// 创建 Statement → 触发 getTargetConnection → 触发路由
		Connection lazyConn = DataSourceUtils.getConnection(lazyDs);
		System.out.println("  方案B: 创建 PreparedStatement → 触发延迟路由:");
		PreparedStatement ps = lazyConn.prepareStatement("SELECT 1");
		ps.close();
		DataSourceUtils.releaseConnection(lazyConn, lazyDs);
		tmLazy.commit(txB);

		System.out.println("  ★ 结论: LazyConnection 把路由决策推迟到首条 SQL,");
		System.out.println("          此时 TSM.isCurrentTransactionReadOnly() 已正确 → 路由准确\n");
	}

	// ═══════════════════════════════════════════════════════════
	//  实验 6: 无事务下切路由 — 每次拿新连接
	// ═══════════════════════════════════════════════════════════

	/**
	 * 对比: 没有 @Transactional 时, 每次 DataSourceUtils.getConnection()
	 * 都穿透到 routingDS.getConnection(), TSM 里无 ConnectionHolder,
	 * 每次拿到的都是新的物理连接.
	 * <br>
	 * 如果中途切 ThreadLocal → 下次拿到的连接来自另一个 DataSource.
	 * → 一段代码内的多次操作分散在不同连接上, 无原子性可言.
	 *
	 * <p>断点: DataSourceUtils#doGetConnection line 106 → conHolder == null (每次)</p>
	 */
	static void exp6_NoTxMultiConnection() {
		System.out.println("━━━ 实验6: 无事务下切路由 — 每次都是新连接, 无原子性 ━━━");

		DataSource masterDs = h2("master-exp6");
		DataSource slaveDs = h2("slave-exp6");
		RwRoutingDataSource routingDs = RwRoutingDataSource.create(masterDs, slaveDs);

		// 无事务: 每次 getConnection 都穿透到 routing
		RwRoutingDataSource.set(RwRoutingDataSource.Route.MASTER);
		Connection c1 = DataSourceUtils.getConnection(routingDs);
		System.out.println("  无事务, 路由=MASTER, conn1 hash: " + c1.hashCode());

		RwRoutingDataSource.set(RwRoutingDataSource.Route.SLAVE);
		Connection c2 = DataSourceUtils.getConnection(routingDs);
		System.out.println("  无事务, 路由=SLAVE,  conn2 hash: " + c2.hashCode());
		System.out.println("  c1 == c2: " + (c1 == c2) + " (不同连接, 来自不同 DataSource!)");

		RwRoutingDataSource.set(RwRoutingDataSource.Route.MASTER);
		Connection c3 = DataSourceUtils.getConnection(routingDs);
		System.out.println("  无事务, 路由=MASTER, conn3 hash: " + c3.hashCode());
		System.out.println("  c1 == c3: " + (c1 == c3) + " (即使同路由, 也可能是新连接)");

		DataSourceUtils.releaseConnection(c1, routingDs);
		DataSourceUtils.releaseConnection(c2, routingDs);
		DataSourceUtils.releaseConnection(c3, routingDs);
		RwRoutingDataSource.clear();
		System.out.println("  ★ 结论: 无事务 = 无 TSM 保护 = 每次 getConnection 穿透 routing");
		System.out.println("          一段代码内多次操作可能分散在不同 DataSource 的不同连接上\n");
	}
}
