package org.springframework.lab.cachetx;

import org.springframework.context.ApplicationEvent;

/**
 * W48 -- 缓存失效事件
 *
 * <p>业务场景: 下单/改价成功后, 发布此事件,
 * 由 @TransactionalEventListener(AFTER_COMMIT) 监听并删缓存.
 *
 * <p>这种模式的优点:
 * <ul>
 *   <li>业务代码只关心"发事件", 不关心"谁清缓存"</li>
 *   <li>删缓存、发 MQ、发短信可以各自监听, 互不耦合</li>
 *   <li>@TransactionalEventListener 底层用的也是 TransactionSynchronization</li>
 * </ul>
 */
public class CacheEvictEvent extends ApplicationEvent {

	private final String cacheName;
	private final Object cacheKey;

	public CacheEvictEvent(Object source, String cacheName, Object cacheKey) {
		super(source);
		this.cacheName = cacheName;
		this.cacheKey = cacheKey;
	}

	public String getCacheName() {
		return cacheName;
	}

	public Object getCacheKey() {
		return cacheKey;
	}

	@Override
	public String toString() {
		return "CacheEvictEvent{cacheName='" + cacheName + "', key=" + cacheKey + "}";
	}
}
