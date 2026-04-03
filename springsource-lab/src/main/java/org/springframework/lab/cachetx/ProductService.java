package org.springframework.lab.cachetx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * W48 -- 商品服务: 演示缓存与事务一致性的各种策略
 *
 * <p>三种删缓存策略:
 * <ol>
 *   <li><b>裸删</b>: 事务内直接删缓存 → 并发窗口风险</li>
 *   <li><b>TransactionAwareCacheDecorator</b>: 自动延迟到 afterCommit</li>
 *   <li><b>手动 TransactionSynchronization</b>: 注册 afterCommit 回调</li>
 * </ol>
 */
@Service
public class ProductService {

	@Autowired
	private JdbcTemplate jdbc;

	// ═══════════════════════════════════════════════════════════════
	// 策略 1: 裸删 — 事务内直接操作原始 Cache (危险)
	// ═══════════════════════════════════════════════════════════════

	/**
	 * 模拟"先写库再裸删缓存"的典型陷阱:
	 * 1. UPDATE DB
	 * 2. cache.evict(key) ← 此时事务还没 commit!
	 * 3. 并发线程读到 cache miss → 查 DB → 读到旧值(因为本事务未提交) → 写回缓存 → 脏缓存!
	 * 4. 本事务 commit → DB 是新值, 但缓存是旧值 → 不一致!
	 */
	@Transactional
	public void updatePriceNaive(Cache rawCache, long productId, int newPrice) {
		System.out.println("    [裸删] UPDATE product SET price=" + newPrice + " WHERE id=" + productId);
		jdbc.update("UPDATE product SET price = ? WHERE id = ?", newPrice, productId);

		// 危险: 事务还没 commit 就删了缓存
		System.out.println("    [裸删] cache.evict(product:" + productId + ") — 事务尚未 commit!");
		rawCache.evict(productId);

		System.out.println("    [裸删] 此刻事务还在, 其他线程看到 cache miss → 查 DB → 拿到旧值 → 写回缓存 → 脏!");
	}

	// ═══════════════════════════════════════════════════════════════
	// 策略 2: TransactionAwareCacheDecorator — 自动延迟到 afterCommit
	// ═══════════════════════════════════════════════════════════════

	/**
	 * 使用 TransactionAwareCacheDecorator 包装的 Cache:
	 * evict() 内部检测到 isSynchronizationActive() == true,
	 * 不会立即删除, 而是注册 TransactionSynchronization#afterCommit 回调.
	 * 事务 commit 后才真正执行 evict.
	 *
	 * <p>断点: TransactionAwareCacheDecorator#evict L116-128
	 */
	@Transactional
	public void updatePriceWithTxAwareCache(Cache txAwareCache, long productId, int newPrice) {
		System.out.println("    [TxAware] UPDATE product SET price=" + newPrice);
		jdbc.update("UPDATE product SET price = ? WHERE id = ?", newPrice, productId);

		System.out.println("    [TxAware] txAwareCache.evict(" + productId + ") — 注册 afterCommit, 不立即执行");
		txAwareCache.evict(productId);

		// 此刻缓存还在! 验证:
		Cache.ValueWrapper cached = txAwareCache.get(productId);
		System.out.println("    [TxAware] 事务内读缓存: " + (cached != null ? cached.get() : "null")
				+ " — 缓存仍存在(evict 被延迟)");
	}

	/**
	 * 演示回滚时 TransactionAwareCacheDecorator 的安全性:
	 * afterCommit 不会被调用, 缓存不会被误删.
	 */
	@Transactional
	public void updatePriceWithTxAwareCacheThenRollback(Cache txAwareCache, long productId, int newPrice) {
		System.out.println("    [TxAware-回滚] UPDATE product SET price=" + newPrice);
		jdbc.update("UPDATE product SET price = ? WHERE id = ?", newPrice, productId);

		System.out.println("    [TxAware-回滚] txAwareCache.evict(" + productId + ") — 注册 afterCommit");
		txAwareCache.evict(productId);

		System.out.println("    [TxAware-回滚] 抛异常触发回滚...");
		throw new RuntimeException("模拟业务异常 → 事务回滚 → afterCommit 不触发 → 缓存不会被删!");
	}

	// ═══════════════════════════════════════════════════════════════
	// 策略 3: 手动 TransactionSynchronization#afterCommit
	// ═══════════════════════════════════════════════════════════════

	/**
	 * 不依赖 TransactionAwareCacheDecorator, 自己注册 afterCommit 回调.
	 * 适用于: 需要在删缓存的同时做更多操作(发 MQ、通知下游等).
	 *
	 * <p>断点: AbstractPlatformTransactionManager#processCommit → triggerAfterCommit
	 */
	@Transactional
	public void updatePriceWithManualSync(Cache rawCache, long productId, int newPrice) {
		System.out.println("    [手动Sync] UPDATE product SET price=" + newPrice);
		jdbc.update("UPDATE product SET price = ? WHERE id = ?", newPrice, productId);

		System.out.println("    [手动Sync] 注册 afterCommit 回调 → 提交后删缓存 + 发通知");
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				rawCache.evict(productId);
				System.out.println("    [手动Sync-afterCommit] cache.evict(" + productId + ") 执行! 事务已提交");
				System.out.println("    [手动Sync-afterCommit] 可在此追加: sendMQ / notifyDownstream...");
			}
		});

		System.out.println("    [手动Sync] 此刻事务未提交, 缓存仍在: "
				+ (rawCache.get(productId) != null ? rawCache.get(productId).get() : "null"));
	}

	// ═══════════════════════════════════════════════════════════════
	// 策略 4: put 也要延迟 — 更新缓存而非删除
	// ═══════════════════════════════════════════════════════════════

	/**
	 * TransactionAwareCacheDecorator#put 也会延迟到 afterCommit.
	 * 适合"更新缓存"策略: DB commit → 用新值刷缓存.
	 */
	@Transactional
	public void updatePriceAndRefreshCache(Cache txAwareCache, long productId, int newPrice) {
		System.out.println("    [更新缓存] UPDATE product SET price=" + newPrice);
		jdbc.update("UPDATE product SET price = ? WHERE id = ?", newPrice, productId);

		System.out.println("    [更新缓存] txAwareCache.put(" + productId + ", " + newPrice + ") — 延迟到 afterCommit");
		txAwareCache.put(productId, newPrice);

		// 事务内读缓存: 仍然是旧值, 因为 put 还没执行
		Cache.ValueWrapper cached = txAwareCache.get(productId);
		System.out.println("    [更新缓存] 事务内读缓存: " + (cached != null ? cached.get() : "null") + " — 旧值(put 被延迟)");
	}

	// ═══════════════════════════════════════════════════════════════
	// 辅助: putIfAbsent / evictIfPresent — 不可延迟
	// ═══════════════════════════════════════════════════════════════

	/**
	 * putIfAbsent 和 evictIfPresent 是"立即操作", 无法延迟.
	 * TransactionAwareCacheDecorator 对这两个方法直接透传, 不注册 Sync.
	 *
	 * <p>这是源码的明确设计选择:
	 * putIfAbsent 需要返回旧值 → 必须立即执行;
	 * evictIfPresent 需要返回 boolean → 必须立即执行.
	 */
	@Transactional
	public void demonstrateImmediateOps(Cache txAwareCache, long productId) {
		System.out.println("    [立即操作] evictIfPresent → 直接透传, 不延迟");
		boolean removed = txAwareCache.evictIfPresent(productId);
		System.out.println("    [立即操作] evictIfPresent 返回: " + removed + " (立即执行!)");

		System.out.println("    [立即操作] putIfAbsent → 直接透传, 不延迟");
		Cache.ValueWrapper prev = txAwareCache.putIfAbsent(productId, 99999);
		System.out.println("    [立即操作] putIfAbsent 返回旧值: " + (prev != null ? prev.get() : "null") + " (立即执行!)");
	}

	// ─── DB 辅助 ───

	public void insertProduct(long id, String name, int price) {
		jdbc.update("INSERT INTO product(id, name, price) VALUES(?,?,?)", id, name, price);
	}

	public int getDbPrice(long productId) {
		return jdbc.queryForObject("SELECT price FROM product WHERE id = ?", Integer.class, productId);
	}
}
