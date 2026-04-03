package org.springframework.lab.naming;

import org.springframework.stereotype.Component;

/**
 * 【Template 后缀】封装 打开→操作→关闭 的资源操作骨架。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>JdbcTemplate — getConnection → executeCallback → releaseConnection（封装 JDBC 生命周期）</li>
 *   <li>TransactionTemplate — getTransaction → executeCallback → commit/rollback</li>
 *   <li>RestTemplate — createRequest → executeCallback → handleResponse</li>
 * </ul>
 *
 * <p>命名规则：Template 后缀 = "我封装了 try/catch/finally，你只写核心逻辑"
 *
 * <p>与 Abstract 的区分（关键！）：
 * Abstract 用 继承 实现扩展（子类覆盖 doXxx 方法）→ 编译期绑定。
 * Template 用 回调 实现扩展（调用方传 lambda/Callback）→ 运行时绑定。
 * Spring 倾向 Template：JdbcTemplate 不需要你继承，传个 RowMapper 就行。
 *
 * <pre>
 * 断点：JdbcTemplate#execute(StatementCallback) 第 397 行
 *   → 观察 try/catch/finally 骨架：获取连接 → 执行回调 → 释放连接
 * </pre>
 */
@Component
public class PayTemplate {

	/**
	 * 模板方法：打开连接 → 执行回调 → 关闭连接。
	 * 调用方只需传入 PayCallback，资源管理完全交给 Template。
	 *
	 * <p>对照 JdbcTemplate#execute(ConnectionCallback):
	 * <pre>
	 *   Connection con = DataSourceUtils.getConnection(dataSource);   // 打开
	 *   try {
	 *       return action.doInConnection(con);                         // 回调
	 *   } finally {
	 *       DataSourceUtils.releaseConnection(con, dataSource);        // 关闭
	 *   }
	 * </pre>
	 */
	public <T> T execute(String channelCode, PayCallback<T> callback) {
		// Step 1: 打开连接（对照 DataSourceUtils.getConnection）
		String connection = openChannel(channelCode);
		System.out.println("    [Template] Step1 打开渠道连接: " + connection);

		try {
			// Step 2: 执行回调（对照 action.doInConnection）
			T result = callback.doInConnection(connection);
			System.out.println("    [Template] Step2 回调执行完成");
			return result;
		}
		catch (Exception e) {
			// Step 3: 异常转换（对照 translateException）
			System.out.println("    [Template] Step3 异常转换: " + e.getMessage());
			throw new RuntimeException("支付执行失败: " + e.getMessage(), e);
		}
		finally {
			// Step 4: 关闭连接（对照 DataSourceUtils.releaseConnection）
			closeChannel(connection);
			System.out.println("    [Template] Step4 关闭渠道连接");
		}
	}

	private String openChannel(String channelCode) {
		return channelCode + "-CONN-" + System.currentTimeMillis() % 10000;
	}

	private void closeChannel(String connection) {
		// 释放资源
	}

	/**
	 * 回调接口——对照 JdbcTemplate 的 ConnectionCallback。
	 * 使用方只需实现这一个方法，不需要关心连接的打开和关闭。
	 */
	@FunctionalInterface
	public interface PayCallback<T> {
		T doInConnection(String connection) throws Exception;
	}
}
