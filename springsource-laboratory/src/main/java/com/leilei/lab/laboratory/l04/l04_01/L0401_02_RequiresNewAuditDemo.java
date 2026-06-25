package com.leilei.lab.laboratory.l04.l04_01;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 📖 知识点：[[L04-01-声明式事务与七种传播行为#2. 🏭 生产怎么用对]]（REQUIRES_NEW 的生产语义）
 * 🎯 作用：用真实可回滚的 HSQLDB 证明 {@code @Transactional(propagation = REQUIRES_NEW)} 的核心契约——
 *         内层方法挂起外层事务、另起一个物理事务并独立提交，因此「即便外层主事务最终回滚，
 *         REQUIRES_NEW 写入的数据依然存活」。对照 REQUIRED：内层与外层是同一个物理事务，外层回滚把它一并带走。
 *         注意：审计方法必须是【独立 Bean】被跨 Bean 调用，否则自调用绕过代理、@Transactional 根本不生效（见 [[L03-01-代理机制与选型-JDK与CGLIB]]）。
 * 🔗 业务场景：C 端下单主事务里要落「操作流水 / 风控埋点 / 扣减审计」。主单因库存不足/营销校验失败回滚，
 *         但这条流水必须留痕（事后对账、客诉举证、风控复盘）——这就是 REQUIRES_NEW 最典型的生产用例：
 *         「业务可回滚，但留痕不可回滚」。把它误写成 REQUIRED，流水会随主单一起消失，线上排查时死无对证。
 */
public final class L0401_02_RequiresNewAuditDemo {

	private L0401_02_RequiresNewAuditDemo() {
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
		public OperationAuditService operationAuditService(JdbcTemplate jdbcTemplate) {
			return new OperationAuditService(jdbcTemplate);
		}

		@Bean
		public OrderPlacementService orderPlacementService(JdbcTemplate jdbcTemplate, OperationAuditService audit) {
			return new OrderPlacementService(jdbcTemplate, audit);
		}
	}

	/** 操作审计服务：独立 Bean，提供两种传播策略下的「写流水」方法供对照。 */
	static class OperationAuditService {

		private final JdbcTemplate jdbc;

		OperationAuditService(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		/** REQUIRES_NEW：挂起主事务、独立提交——主单回滚也带不走这条流水。 */
		@Transactional(propagation = Propagation.REQUIRES_NEW)
		public void recordRequiresNew(String orderId, String action) {
			jdbc.update("insert into audit_log(order_id, action) values (?, ?)", orderId, action);
		}

		/** REQUIRED：与主事务同一物理事务——主单回滚，这条流水也一起没。 */
		@Transactional(propagation = Propagation.REQUIRED)
		public void recordRequired(String orderId, String action) {
			jdbc.update("insert into audit_log(order_id, action) values (?, ?)", orderId, action);
		}
	}

	/** 下单服务：主事务里写订单 + 写流水，再人为抛异常触发主事务回滚。 */
	static class OrderPlacementService {

		private final JdbcTemplate jdbc;
		private final OperationAuditService audit;

		OrderPlacementService(JdbcTemplate jdbc, OperationAuditService audit) {
			this.jdbc = jdbc;
			this.audit = audit;
		}

		/**
		 * @param auditRequiresNew true=流水用 REQUIRES_NEW（应存活），false=用 REQUIRED（应一起回滚）
		 */
		@Transactional
		public void placeOrderThenFail(String orderId, boolean auditRequiresNew) {
			jdbc.update("insert into orders(id, amount) values (?, ?)", orderId, 28);   // 主单：一杯多肉葡萄
			if (auditRequiresNew) {
				audit.recordRequiresNew(orderId, "CREATE");
			}
			else {
				audit.recordRequired(orderId, "CREATE");
			}
			throw new IllegalStateException("库存不足，主单回滚（模拟营销/库存校验失败）");
		}
	}

	private static void initSchema(JdbcTemplate jdbc) {
		jdbc.execute("create table orders(id varchar(64) primary key, amount int)");
		jdbc.execute("create table audit_log(id identity primary key, order_id varchar(64), action varchar(32))");
	}

	private static void truncate(JdbcTemplate jdbc) {
		jdbc.execute("delete from orders");
		jdbc.execute("delete from audit_log");
	}

	private static int count(JdbcTemplate jdbc, String table) {
		Integer n = jdbc.queryForObject("select count(*) from " + table, Integer.class);
		return n == null ? 0 : n;
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxConfig.class)) {
			JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
			OrderPlacementService orders = ctx.getBean(OrderPlacementService.class);
			initSchema(jdbc);

			System.out.println("==================== 场景 A：流水用 REQUIRES_NEW ====================");
			truncate(jdbc);
			try {
				orders.placeOrderThenFail("GM-A-001", true);
			}
			catch (IllegalStateException expected) {
				System.out.println("主单按预期抛出并回滚：" + expected.getMessage());
			}
			System.out.println("orders 行数    = " + count(jdbc, "orders") + "   （期望 0：主单已回滚）");
			System.out.println("audit_log 行数 = " + count(jdbc, "audit_log") + "   ✅ 期望 1：REQUIRES_NEW 独立提交，主单回滚带不走流水");

			System.out.println();
			System.out.println("==================== 场景 B：流水用 REQUIRED（错误示范）====================");
			truncate(jdbc);
			try {
				orders.placeOrderThenFail("GM-B-001", false);
			}
			catch (IllegalStateException expected) {
				System.out.println("主单按预期抛出并回滚：" + expected.getMessage());
			}
			System.out.println("orders 行数    = " + count(jdbc, "orders") + "   （期望 0）");
			System.out.println("audit_log 行数 = " + count(jdbc, "audit_log") + "   ❌ 期望 0：REQUIRED 与主单同体，流水随主单一起回滚——线上死无对证");

			System.out.println();
			System.out.println("结论：要「业务可回滚但留痕不可回滚」，流水/审计/风控埋点必须 REQUIRES_NEW；"
					+ "且审计方法须独立 Bean 跨 Bean 调用，否则自调用绕过代理，连事务都不会开。");
		}
	}
}
