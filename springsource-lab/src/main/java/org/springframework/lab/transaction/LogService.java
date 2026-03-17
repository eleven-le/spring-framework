package org.springframework.lab.transaction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 日志服务 — 演示 REQUIRES_NEW 场景
 *
 * <p>独立 Bean，避免 self-invocation 问题。
 * 外层事务回滚时，REQUIRES_NEW 的日志记录不受影响。
 */
@Service
public class LogService {

	private final JdbcTemplate jdbc;

	public LogService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void insertLog(String msg) {
		jdbc.update("INSERT INTO tx_log(msg) VALUES(?)", msg);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertLogRequiresNew(String msg) {
		jdbc.update("INSERT INTO tx_log(msg) VALUES(?)", msg);
		System.out.println("[LogService] REQUIRES_NEW 日志已提交: " + msg);
	}

	public int logCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM tx_log", Integer.class);
	}
}
