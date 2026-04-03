package org.springframework.lab.txrules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回滚规则引擎演示 — 聚焦 rollbackFor/noRollbackFor 的深度匹配
 *
 * <p>回滚决策链:
 * <pre>
 * TransactionAspectSupport#completeTransactionAfterThrowing
 *   → txAttr.rollbackOn(ex)?
 *     → RuleBasedTransactionAttribute#rollbackOn
 *       → 遍历 rollbackRules, 计算每条规则与异常的继承深度(depth)
 *       → 取 depth 最小(最精确匹配)的规则作为 winner
 *       → winner == null? → 走默认: RuntimeException/Error 回滚
 *       → winner instanceof NoRollbackRuleAttribute? → 提交(不回滚)
 *       → 否则 → 回滚
 * </pre>
 *
 * <p>深度计算 (RollbackRuleAttribute#getDepth):
 * <pre>
 *   depth=0: 精确匹配 (ex.getClass().getName().contains(exceptionPattern))
 *   depth=N: 沿继承链上溯 N 层匹配
 *   depth=-1: 不匹配
 * </pre>
 *
 * <p>断点:
 * <ul>
 *   <li>{@code RuleBasedTransactionAttribute#rollbackOn} — winner 选择逻辑</li>
 *   <li>{@code RollbackRuleAttribute#getDepth(Class, int)} — 递归深度计算</li>
 *   <li>{@code TransactionAspectSupport#completeTransactionAfterThrowing} — rollback vs commit 分支</li>
 * </ul>
 */
@Service
public class RollbackRuleService {

	private final JdbcTemplate jdbc;

	public RollbackRuleService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// ===== 场景 A: 默认规则 — RuntimeException 回滚 =====
	@Transactional
	public void defaultRuleRuntime(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new RuntimeException("默认规则: RuntimeException → rollbackOn=true → 回滚");
	}

	// ===== 场景 B: 默认规则 — CheckedException 不回滚(提交!) =====
	@Transactional
	public void defaultRuleChecked(String orderId) throws IOException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new IOException("默认规则: CheckedException → rollbackOn=false → 提交!");
	}

	// ===== 场景 C: rollbackFor=Exception → 所有异常都回滚 =====
	@Transactional(rollbackFor = Exception.class)
	public void rollbackForAll(String orderId) throws IOException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new IOException("rollbackFor=Exception: IOException depth=1 匹配 → 回滚");
	}

	// ===== 场景 D: noRollbackFor 精确匹配 > rollbackFor 模糊匹配 =====
	// rollbackFor=Exception(depth=2对IOException) + noRollbackFor=IOException(depth=0) → 不回滚
	@Transactional(rollbackFor = Exception.class, noRollbackFor = IOException.class)
	public void noRollbackWins(String orderId) throws IOException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new IOException("NoRollback(IOException) depth=0 < Rollback(Exception) depth=1 → 提交!");
	}

	// ===== 场景 E: 子类异常 → noRollbackFor 仍胜出 =====
	// 抛 FileNotFoundException(extends IOException): noRollbackFor=IOException depth=1
	// rollbackFor=Exception depth=2 → noRollback 仍然赢
	@Transactional(rollbackFor = Exception.class, noRollbackFor = IOException.class)
	public void subclassNoRollback(String orderId) throws FileNotFoundException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new FileNotFoundException("FileNotFoundException: NoRollback(IOException) depth=1 < Rollback(Exception) depth=2 → 提交!");
	}

	// ===== 场景 F: SQLException → 不匹配 noRollbackFor(IOException)，只匹配 rollbackFor(Exception) → 回滚 =====
	@Transactional(rollbackFor = Exception.class, noRollbackFor = IOException.class)
	public void sqlExceptionRollback(String orderId) throws SQLException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new SQLException("SQLException: 不匹配NoRollback(IOException)，匹配Rollback(Exception) depth=1 → 回滚");
	}

	// ===== 场景 G: rollbackForClassName 字符串匹配 =====
	@Transactional(rollbackForClassName = "java.io.IOException")
	public void rollbackByClassName(String orderId) throws IOException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new IOException("rollbackForClassName 字符串 contains 匹配 → 回滚");
	}

	// ===== 查询辅助 =====
	public boolean orderExists(String orderId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM orders WHERE order_id = ?", Integer.class, orderId);
		return count != null && count > 0;
	}
}
