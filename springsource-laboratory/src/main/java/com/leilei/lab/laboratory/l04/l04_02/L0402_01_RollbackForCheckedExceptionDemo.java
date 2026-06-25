package com.leilei.lab.laboratory.l04.l04_02;

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

/**
 * 📖 知识点：[[L04-02-事务失效八股的事故现场#2. 🏭 生产怎么用对]]（rollbackFor 默认只回滚 RuntimeException/Error ⭐）
 * 🎯 作用：用真实可回滚的 HSQLDB 证明事务失效里最隐蔽的一条——{@code @Transactional} 默认的回滚边界由
 *         {@link org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn(Throwable)}
 *         裁决，它只对 {@code RuntimeException} / {@code Error} 回滚；抛出受检异常（checked Exception）时
 *         默认【提交而非回滚】。三场景对照：① 默认 + 受检异常 → 订单脏写提交（事故现场）；
 *         ② {@code rollbackFor = Exception.class} + 受检异常 → 正常回滚；③ 默认 + 运行时异常 → 正常回滚（基线）。
 * 🔗 业务场景：C 端下单扣库存，库存不足抛业务受检异常 {@code InventoryShortageException}（团队规范要求受检以强制调用方处理）。
 *         开发以为「抛异常事务就回滚」，结果默认策略放过了受检异常——订单已落库、库存却没扣，超卖 + 脏单一起上线。
 */
public final class L0402_01_RollbackForCheckedExceptionDemo {

	private L0402_01_RollbackForCheckedExceptionDemo() {
	}

	/** 库存不足业务异常：受检（extends Exception），团队规范要求强制处理——也正因受检，撞上默认回滚边界。 */
	static class InventoryShortageException extends Exception {
		InventoryShortageException(String message) {
			super(message);
		}
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
		public OrderCheckoutService orderCheckoutService(JdbcTemplate jdbcTemplate) {
			return new OrderCheckoutService(jdbcTemplate);
		}
	}

	/** 下单核销服务：每个方法都先落订单、再抛异常，差别只在「异常类型」与「rollbackFor 配置」。 */
	static class OrderCheckoutService {

		private final JdbcTemplate jdbc;

		OrderCheckoutService(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		/** 场景①：默认 @Transactional + 受检异常 → DefaultTransactionAttribute#rollbackOn 返回 false → 提交（事故现场）。 */
		@Transactional
		public void checkoutCheckedDefault(String orderId) throws InventoryShortageException {
			jdbc.update("insert into orders(id, amount) values (?, ?)", orderId, 28);   // 一杯多肉葡萄
			throw new InventoryShortageException("库存不足，本该回滚——但这是受检异常");
		}

		/** 场景②：rollbackFor = Exception.class + 受检异常 → 命中回滚规则 → 回滚。 */
		@Transactional(rollbackFor = Exception.class)
		public void checkoutCheckedRollbackFor(String orderId) throws InventoryShortageException {
			jdbc.update("insert into orders(id, amount) values (?, ?)", orderId, 28);
			throw new InventoryShortageException("库存不足，已配 rollbackFor，应回滚");
		}

		/** 场景③（基线）：默认 @Transactional + 运行时异常 → rollbackOn 返回 true → 回滚。 */
		@Transactional
		public void checkoutRuntime(String orderId) {
			jdbc.update("insert into orders(id, amount) values (?, ?)", orderId, 28);
			throw new IllegalStateException("库存不足（运行时异常），默认就回滚");
		}
	}

	private static void initSchema(JdbcTemplate jdbc) {
		jdbc.execute("create table orders(id varchar(64) primary key, amount int)");
	}

	private static void truncate(JdbcTemplate jdbc) {
		jdbc.execute("delete from orders");
	}

	private static int count(JdbcTemplate jdbc) {
		Integer n = jdbc.queryForObject("select count(*) from orders", Integer.class);
		return n == null ? 0 : n;
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxConfig.class)) {
			JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
			OrderCheckoutService svc = ctx.getBean(OrderCheckoutService.class);
			initSchema(jdbc);

			System.out.println("==================== 场景①：默认 @Transactional + 受检异常 ====================");
			truncate(jdbc);
			try {
				svc.checkoutCheckedDefault("GM-01");
			}
			catch (InventoryShortageException expected) {
				System.out.println("抛出受检异常：" + expected.getMessage());
			}
			System.out.println("orders 行数 = " + count(jdbc)
					+ "   ❌ 期望 0 实得 1：默认 rollbackOn 只回滚 RuntimeException/Error，受检异常被【提交】——脏单上线");

			System.out.println();
			System.out.println("==================== 场景②：rollbackFor=Exception.class + 受检异常 ====================");
			truncate(jdbc);
			try {
				svc.checkoutCheckedRollbackFor("GM-02");
			}
			catch (InventoryShortageException expected) {
				System.out.println("抛出受检异常：" + expected.getMessage());
			}
			System.out.println("orders 行数 = " + count(jdbc)
					+ "   ✅ 期望 0：rollbackFor 把受检异常纳入回滚规则，事务正常回滚");

			System.out.println();
			System.out.println("==================== 场景③（基线）：默认 @Transactional + 运行时异常 ====================");
			truncate(jdbc);
			try {
				svc.checkoutRuntime("GM-03");
			}
			catch (IllegalStateException expected) {
				System.out.println("抛出运行时异常：" + expected.getMessage());
			}
			System.out.println("orders 行数 = " + count(jdbc)
					+ "   ✅ 期望 0：RuntimeException 命中默认回滚边界，无需任何配置");

			System.out.println();
			System.out.println("结论：@Transactional 默认只回滚 RuntimeException/Error；凡是会抛受检异常的写事务，"
					+ "一律显式 rollbackFor = Exception.class（团队红线），别赌默认边界。");
		}
	}
}
