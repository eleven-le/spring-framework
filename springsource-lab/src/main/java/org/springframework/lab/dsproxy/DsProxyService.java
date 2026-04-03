package org.springframework.lab.dsproxy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>演示事务内的 DataSource 代理行为</h2>
 */
@Service
public class DsProxyService {

	private final JdbcTemplate jdbc;

	public DsProxyService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void insertOrder(String product, int amount) {
		jdbc.update("INSERT INTO t_order(product, amount) VALUES (?, ?)", product, amount);
		System.out.println("    [Service] insertOrder 完成: " + product);
	}

	@Transactional(readOnly = true)
	public int countOrders() {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class);
		System.out.println("    [Service] countOrders = " + count);
		return count != null ? count : 0;
	}
}
