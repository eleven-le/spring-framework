package org.springframework.lab.transaction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账户服务实现 — 标注 @Transactional，让 Spring 生成事务代理
 *
 * <p>关键调试入口:
 * <ul>
 *   <li>在 {@link #transfer} 上打断点，可观察代理拦截链</li>
 *   <li>在 {@link #selfInvokeTransfer} 中，内部调用绕过代理导致事务失效</li>
 * </ul>
 */
@Service
public class AccountServiceImpl implements AccountService {

	private final JdbcTemplate jdbc;
	private final LogService logService;

	public AccountServiceImpl(JdbcTemplate jdbc, LogService logService) {
		this.jdbc = jdbc;
		this.logService = logService;
	}

	// ===== 场景1: 基本事务 — REQUIRED（默认） =====
	@Override
	@Transactional
	public void transfer(String from, String to, int amount) {
		jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
		jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
		System.out.println("[transfer] " + from + " -> " + to + " : " + amount);
	}

	@Override
	public int balance(String name) {
		return jdbc.queryForObject("SELECT balance FROM account WHERE name = ?", Integer.class, name);
	}

	// ===== 场景2: 异常触发回滚 =====
	@Transactional
	public void transferWithError(String from, String to, int amount) {
		jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
		jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
		// 模拟业务异常 → RuntimeException 默认触发回滚
		throw new RuntimeException("模拟转账失败 — 触发回滚");
	}

	// ===== 场景3: Checked Exception 默认不回滚 =====
	@Transactional
	public void transferChecked(String from, String to, int amount) throws Exception {
		jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
		jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
		// Checked Exception 默认不触发回滚（除非 rollbackFor 指定）
		throw new Exception("Checked 异常 — 默认提交！");
	}

	// ===== 场景3b: rollbackFor 显式指定回滚 Checked Exception =====
	@Transactional(rollbackFor = Exception.class)
	public void transferCheckedWithRollback(String from, String to, int amount) throws Exception {
		jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
		jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
		throw new Exception("Checked 异常 + rollbackFor → 触发回滚");
	}

	// ===== 场景4: 自调用失效 =====
	public void selfInvokeTransfer(String from, String to, int amount) {
		// 这里 this.transfer() 是直接调用，绕过代理，@Transactional 不生效！
		this.transfer(from, to, amount);
		System.out.println("[selfInvoke] 内部调用 transfer — 事务注解被绕过");
	}

	// ===== 场景5: REQUIRES_NEW — 独立事务 =====
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void auditLog(String msg) {
		logService.insertLog(msg);
		System.out.println("[auditLog] REQUIRES_NEW 独立事务记录: " + msg);
	}

	// ===== 场景5b: 外层事务 + REQUIRES_NEW 内层事务 =====
	@Transactional
	public void transferWithAudit(String from, String to, int amount) {
		jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
		jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
		// 通过注入的代理调用 REQUIRES_NEW 方法（注意：不能 self-invoke）
		logService.insertLogRequiresNew("转账: " + from + " -> " + to + " : " + amount);
		// 外层故意抛异常回滚 — 但 REQUIRES_NEW 的日志不会被回滚
		throw new RuntimeException("外层回滚 — 验证 REQUIRES_NEW 日志是否保留");
	}
}
