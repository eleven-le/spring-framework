package org.springframework.lab.cachetx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * W48 -- 事件驱动删缓存监听器
 *
 * <p>核心机制:
 * @TransactionalEventListener(phase = AFTER_COMMIT) 底层原理:
 * publishEvent 时, TransactionalApplicationListenerMethodAdapter#onApplicationEvent
 * 检测到事务同步活跃, 注册 TransactionalApplicationListenerSynchronization,
 * 等到 processCommit → triggerAfterCommit 才真正调用此方法.
 *
 * <p>与 TransactionAwareCacheDecorator 的区别:
 * Decorator 是缓存层自动延迟, 业务无感知;
 * 事件监听是业务主动发事件, 监听器去清缓存 — 解耦更彻底, 可以同时做多件事.
 *
 * <p>断点: TransactionalApplicationListenerMethodAdapter#onApplicationEvent
 */
@Component
public class CacheEvictListener {

	@Autowired
	private CacheManager rawCacheManager;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCacheEvict(CacheEvictEvent event) {
		Cache cache = rawCacheManager.getCache(event.getCacheName());
		if (cache != null) {
			cache.evict(event.getCacheKey());
			System.out.println("    [EventListener-AFTER_COMMIT] 收到事件 → cache.evict("
					+ event.getCacheName() + ":" + event.getCacheKey() + ") 执行! 事务已提交");
		}
	}

	/**
	 * 回滚时的监听 — 可做告警/打日志, 不清缓存
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
	public void onCacheEvictRollback(CacheEvictEvent event) {
		System.out.println("    [EventListener-AFTER_ROLLBACK] 事务回滚, 不删缓存 → " + event);
	}
}
