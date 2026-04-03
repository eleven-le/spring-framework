package org.springframework.lab.txinvokechain;

import java.io.IOException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回滚规则引擎演示 — 展示 completeTransactionAfterThrowing 的决策树
 *
 * <p>在 {@code TransactionAspectSupport#completeTransactionAfterThrowing} 中:
 * <pre>
 *   if (txAttr.rollbackOn(ex)) {
 *       txManager.rollback(txStatus);    // 回滚
 *   } else {
 *       txManager.commit(txStatus);      // 即使异常也提交！
 *   }
 * </pre>
 *
 * <p>{@code RuleBasedTransactionAttribute#rollbackOn(Throwable)} 规则匹配:
 * <ol>
 *   <li>遍历 rollbackRules (包含 RollbackRuleAttribute 和 NoRollbackRuleAttribute)</li>
 *   <li>按异常继承深度找最匹配的规则 (depth 越小越优先)</li>
 *   <li>NoRollbackRule 的深度 ≤ RollbackRule 的深度 → 不回滚</li>
 *   <li>无匹配规则 → 默认行为: RuntimeException/Error 回滚，CheckedException 不回滚</li>
 * </ol>
 *
 * <p>断点: {@code RuleBasedTransactionAttribute#rollbackOn}
 * — 观察规则匹配 depth 计算
 */
@Service
public class RollbackDemoService {

	private final JdbcTemplate jdbc;

	public RollbackDemoService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 场景 A: 默认规则 — RuntimeException 回滚, CheckedException 不回滚
	 *
	 * 源码: RuleBasedTransactionAttribute#rollbackOn → 无自定义规则 →
	 * 调 super.rollbackOn(ex) → 即 DefaultTransactionAttribute#rollbackOn
	 * → (ex instanceof RuntimeException || ex instanceof Error)
	 */
	@Transactional
	public void defaultRuleRuntime(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new RuntimeException("默认规则: RuntimeException → 回滚");
	}

	@Transactional
	public void defaultRuleChecked(String orderId) throws IOException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new IOException("默认规则: CheckedException → 提交!");
	}

	/**
	 * 场景 B: rollbackFor 指定 — 覆盖默认规则
	 *
	 * 源码: SpringTransactionAnnotationParser#parseTransactionAnnotation
	 * → new RollbackRuleAttribute(IOException.class)
	 * → RuleBasedTransactionAttribute#rollbackOn: 找到匹配规则 depth=0 → 回滚
	 */
	@Transactional(rollbackFor = IOException.class)
	public void rollbackForChecked(String orderId) throws IOException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new IOException("rollbackFor=IOException → 回滚");
	}

	/**
	 * 场景 C: noRollbackFor + rollbackFor 组合 — 深度优先匹配
	 *
	 * rollbackFor = Exception.class → 所有异常回滚
	 * noRollbackFor = IOException.class → 但 IOException 不回滚
	 *
	 * 当抛 IOException 时:
	 *   - RollbackRule(Exception): depth = 1 (IOException extends Exception)
	 *   - NoRollbackRule(IOException): depth = 0 (精确匹配)
	 *   - NoRollback depth(0) ≤ Rollback depth(1) → 不回滚！
	 */
	@Transactional(rollbackFor = Exception.class, noRollbackFor = IOException.class)
	public void compositeRule(String orderId) throws IOException {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new IOException("组合规则: rollbackFor=Exception + noRollbackFor=IOException → 不回滚!");
	}

	/**
	 * 场景 D: 同组合规则，但抛的是 RuntimeException（不是 IOException）
	 *
	 * 当抛 RuntimeException 时:
	 *   - RollbackRule(Exception): depth = 1
	 *   - NoRollbackRule(IOException): 不匹配 (RuntimeException 不是 IOException 子类)
	 *   - 只有 RollbackRule 匹配 → 回滚
	 */
	@Transactional(rollbackFor = Exception.class, noRollbackFor = IOException.class)
	public void compositeRuleRuntime(String orderId) {
		jdbc.update("INSERT INTO orders(order_id, amount, status) VALUES(?, 100, 'TEST')", orderId);
		throw new RuntimeException("组合规则: RuntimeException 不是 IOException → 回滚!");
	}

	public boolean orderExists(String orderId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM orders WHERE order_id = ?", Integer.class, orderId);
		return count != null && count > 0;
	}
}
