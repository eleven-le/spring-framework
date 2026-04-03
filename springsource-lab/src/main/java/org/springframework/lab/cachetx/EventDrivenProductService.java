package org.springframework.lab.cachetx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W48 -- 事件驱动方式: 业务只发事件, 缓存清理交给监听器
 *
 * <p>C 端典型场景:
 * 改价服务 → publishEvent(CacheEvictEvent) → 事务提交后:
 *   - CacheEvictListener: 删本地缓存
 *   - MqListener: 发 MQ 通知其他节点删缓存
 *   - AuditListener: 记审计日志
 *
 * <p>业务代码完全不感知缓存的存在 — 职责分离.
 */
@Service
public class EventDrivenProductService {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationEventPublisher publisher;

	/**
	 * 改价 + 发事件 — 提交后由监听器清缓存
	 */
	@Transactional
	public void updatePriceAndPublishEvent(long productId, int newPrice) {
		System.out.println("    [事件驱动] UPDATE product SET price=" + newPrice);
		jdbc.update("UPDATE product SET price = ? WHERE id = ?", newPrice, productId);

		System.out.println("    [事件驱动] publishEvent(CacheEvictEvent) — 此刻事务未提交, 事件被挂起");
		publisher.publishEvent(new CacheEvictEvent(this, "product", productId));

		System.out.println("    [事件驱动] 方法即将返回 → 事务准备 commit → triggerAfterCommit → 监听器执行");
	}

	/**
	 * 改价 + 发事件 + 回滚
	 */
	@Transactional
	public void updatePriceThenFail(long productId, int newPrice) {
		jdbc.update("UPDATE product SET price = ? WHERE id = ?", newPrice, productId);
		publisher.publishEvent(new CacheEvictEvent(this, "product", productId));
		throw new RuntimeException("模拟失败 → 回滚 → AFTER_COMMIT 监听器不触发, AFTER_ROLLBACK 触发");
	}
}
