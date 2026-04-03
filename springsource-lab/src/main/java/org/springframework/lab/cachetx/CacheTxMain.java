/*
package org.springframework.lab.cachetx;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

*/
/**
 * W48 -- 缓存与事务一致性：提交后删缓存 / 更新缓存
 *
 * <h2>一句话抽象</h2>
 * <b>缓存与事务一致性</b>解决的核心矛盾是:
 * <i>缓存写操作(evict/put)在事务 commit 之前执行, 而并发线程可能在"缓存已删、事务未提交"
 * 的窗口期用旧 DB 数据回填缓存, 导致事务提交后缓存永久脏</i>.
 * 解法: <b>把缓存写操作延迟到 afterCommit 阶段</b>, 确保"DB 写成功 → 缓存才变更".
 *
 * <h2>实验列表</h2>
 * <ol>
 *   <li>实验 1: 裸删缓存的时序陷阱 — 事务内直接 evict, 并发窗口导致脏缓存</li>
 *   <li>实验 2: TransactionAwareCacheDecorator — evict 延迟到 afterCommit</li>
 *   <li>实验 3: TransactionAwareCacheDecorator 回滚安全 — afterCommit 不触发, 缓存不删</li>
 *   <li>实验 4: 手动 TransactionSynchronization#afterCommit — 更精细的提交后操作</li>
 *   <li>实验 5: @TransactionalEventListener(AFTER_COMMIT) 事件驱动删缓存 — 解耦最彻底</li>
 *   <li>实验 6: put 也要延迟 — "更新缓存"策略同样适用</li>
 *   <li>实验 7: putIfAbsent / evictIfPresent 不可延迟 — 立即操作的陷阱</li>
 * </ol>
 *
 * <h2>核心调用链 (10 步)</h2>
 * <pre>
 *  ① @Transactional 方法入口 → TransactionInterceptor#invoke : 开启事务, initSynchronization
 *  ② 业务代码执行 UPDATE SQL → Connection 缓冲, 事务未提交
 *  ③ TransactionAwareCacheDecorator#evict : 检测 isSynchronizationActive() == true
 *  ④ TransactionSynchronizationManager#registerSynchronization : 注册匿名 Sync(afterCommit → evict)
 *  ⑤ 方法正常返回 → TransactionInterceptor#commitTransactionAfterReturning
 *  ⑥ AbstractPlatformTransactionManager#commit → processCommit
 *  ⑦ processCommit#triggerBeforeCommit : 调用所有 Sync#beforeCommit (此处无操作)
 *  ⑧ processCommit#doCommit : Connection.commit() — DB 数据真正持久化
 *  ⑨ processCommit#triggerAfterCommit : 调用所有 Sync#afterCommit → 此时才执行 cache.evict!
 *  ⑩ processCommit#triggerAfterCompletion : 清理 Sync 列表 + 解绑 ThreadLocal 资源
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>TransactionAwareCacheDecorator#evict L117 — if(isSynchronizationActive) 分支入口</li>
 *   <li>TransactionSynchronizationManager#registerSynchronization — 观察 Sync 列表增长</li>
 *   <li>AbstractPlatformTransactionManager#processCommit L752 — triggerAfterCommit 调用点</li>
 *   <li>TransactionSynchronizationUtils#triggerAfterCommit — 遍历 Sync 列表, 逐个调 afterCommit</li>
 *   <li>TransactionalApplicationListenerMethodAdapter#onApplicationEvent L89 — 事件驱动方式的 Sync 注册入口</li>
 * </ol>
 *//*

public class CacheTxMain {

	public static void main(String[] args) {
		System.out.println("╔══════════════════════════════════════════════════════════════════╗");
		System.out.println("║  W48 — 缓存与事务一致性: 提交后删缓存 / 更新缓存                    ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		System.out.println("║  核心矛盾: 缓存写在 commit 前执行 → 并发回填脏数据                   ║");
		System.out.println("║  解法: 把 evict/put 延迟到 afterCommit                            ║");
		System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CacheTxConfig.class);
		ProductService productService = ctx.getBean(ProductService.class);
		EventDrivenProductService eventService = ctx.getBean(EventDrivenProductService.class);
		CacheManager rawCacheManager = ctx.getBean("rawCacheManager", CacheManager.class);
		Cache txAwareCache = ctx.getBean("txAwareProductCache", Cache.class);
		Cache rawCache = rawCacheManager.getCache("product");

		// ═════════════════════════════════════════════════════════════
		// 实验 1: 裸删缓存的时序陷阱
		// ═════════════════════════════════════════════════════════════
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 1: 裸删缓存的时序陷阱 — 事务内直接 evict");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  场景: 商品改价, 先写 DB 再裸删缓存 → 并发线程可能在窗口期用旧值回填缓存");
		System.out.println("  断点: ProductService#updatePriceNaive 内观察 evict 执行时机\n");

		resetProductCache(rawCache);
		productService.insertProduct(1L, "iPhone", 9999);
		rawCache.put(1L, 9999); // 预热缓存
		System.out.println("  [初始] DB price=9999, cache=" + rawCache.get(1L).get());

		productService.updatePriceNaive(rawCache, 1L, 7999);
		System.out.println("  [结果] DB price=" + productService.getDbPrice(1L)
				+ ", cache=" + (rawCache.get(1L) != null ? rawCache.get(1L).get() : "null(已被删)")
				+ " — 裸删: evict 在 commit 前就执行了!\n");

		// ═════════════════════════════════════════════════════════════
		// 实验 2: TransactionAwareCacheDecorator — evict 延迟到 afterCommit
		// ═════════════════════════════════════════════════════════════
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 2: TransactionAwareCacheDecorator — evict 延迟到 afterCommit");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  断点: TransactionAwareCacheDecorator#evict L117");
		System.out.println("  观察: isSynchronizationActive()=true → registerSynchronization\n");

		resetProductCache(rawCache);
		productService.insertProduct(2L, "MacBook", 14999);
		rawCache.put(2L, 14999);
		System.out.println("  [初始] DB price=14999, cache=" + rawCache.get(2L).get());

		productService.updatePriceWithTxAwareCache(txAwareCache, 2L, 11999);
		System.out.println("  [事务后] DB price=" + productService.getDbPrice(2L)
				+ ", cache=" + (rawCache.get(2L) != null ? rawCache.get(2L).get() : "null(afterCommit 已删)")
				+ " — 延迟删除: commit 后缓存才被清!\n");

		// ═════════════════════════════════════════════════════════════
		// 实验 3: TransactionAwareCacheDecorator — 回滚安全
		// ═════════════════════════════════════════════════════════════
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 3: 回滚安全 — afterCommit 不触发, 缓存不被误删");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  断点: AbstractPlatformTransactionManager#processRollback");
		System.out.println("  观察: triggerAfterCompletion(STATUS_ROLLED_BACK), 无 triggerAfterCommit\n");

		resetProductCache(rawCache);
		productService.insertProduct(3L, "AirPods", 1999);
		rawCache.put(3L, 1999);
		System.out.println("  [初始] DB price=1999, cache=" + rawCache.get(3L).get());

		try {
			productService.updatePriceWithTxAwareCacheThenRollback(txAwareCache, 3L, 999);
		}
		catch (RuntimeException e) {
			System.out.println("  [捕获异常] " + e.getMessage());
		}
		System.out.println("  [回滚后] DB price=" + productService.getDbPrice(3L)
				+ ", cache=" + (rawCache.get(3L) != null ? rawCache.get(3L).get() : "null")
				+ " — 回滚安全: DB 没变, 缓存也没被删!\n");

		// ═════════════════════════════════════════════════════════════
		// 实验 4: 手动 TransactionSynchronization#afterCommit
		// ═════════════════════════════════════════════════════════════
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 4: 手动 TransactionSynchronization#afterCommit — 精细控制");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  断点: TransactionSynchronizationUtils#triggerAfterCommit");
		System.out.println("  场景: 提交后不仅删缓存, 还要发 MQ 通知其他节点\n");

		resetProductCache(rawCache);
		productService.insertProduct(4L, "iPad", 5999);
		rawCache.put(4L, 5999);
		System.out.println("  [初始] DB price=5999, cache=" + rawCache.get(4L).get());

		productService.updatePriceWithManualSync(rawCache, 4L, 4999);
		System.out.println("  [事务后] DB price=" + productService.getDbPrice(4L)
				+ ", cache=" + (rawCache.get(4L) != null ? rawCache.get(4L).get() : "null(afterCommit 已删)")
				+ "\n");

		// ═════════════════════════════════════════════════════════════
		// 实验 5: @TransactionalEventListener(AFTER_COMMIT) 事件驱动
		// ═════════════════════════════════════════════════════════════
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 5: @TransactionalEventListener(AFTER_COMMIT) — 事件驱动删缓存");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  断点: TransactionalApplicationListenerMethodAdapter#onApplicationEvent");
		System.out.println("  场景: 业务只 publishEvent, 缓存/MQ/审计 各自监听, 完全解耦\n");

		resetProductCache(rawCache);
		productService.insertProduct(5L, "Watch", 3999);
		rawCache.put(5L, 3999);
		System.out.println("  [初始] DB price=3999, cache=" + rawCache.get(5L).get());

		eventService.updatePriceAndPublishEvent(5L, 2999);
		System.out.println("  [事务后] DB price=" + productService.getDbPrice(5L)
				+ ", cache=" + (rawCache.get(5L) != null ? rawCache.get(5L).get() : "null(监听器已删)")
				+ "\n");

		// 5b: 回滚场景
		System.out.println("  --- 5b: 回滚场景 ---");
		rawCache.put(5L, 2999); // 重新预热
		try {
			eventService.updatePriceThenFail(5L, 1999);
		}
		catch (RuntimeException e) {
			System.out.println("  [捕获异常] " + e.getMessage());
		}
		System.out.println("  [回滚后] cache=" + (rawCache.get(5L) != null ? rawCache.get(5L).get() : "null")
				+ " — AFTER_COMMIT 不触发, AFTER_ROLLBACK 触发了告警\n");

		// ═════════════════════════════════════════════════════════════
		// 实验 6: put 也要延迟 — "更新缓存"策略
		// ═════════════════════════════════════════════════════════════
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 6: put 也要延迟 — '更新缓存'策略 vs '删缓存'策略");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  断点: TransactionAwareCacheDecorator#put L95-107");
		System.out.println("  场景: 改价后直接用新值刷缓存, 省一次 DB 回查\n");

		resetProductCache(rawCache);
		productService.insertProduct(6L, "HomePod", 2299);
		rawCache.put(6L, 2299);
		System.out.println("  [初始] DB price=2299, cache=" + rawCache.get(6L).get());

		productService.updatePriceAndRefreshCache(txAwareCache, 6L, 1999);
		System.out.println("  [事务后] DB price=" + productService.getDbPrice(6L)
				+ ", cache=" + (rawCache.get(6L) != null ? rawCache.get(6L).get() : "null")
				+ " — commit 后缓存已被刷为新值!\n");

		// ═════════════════════════════════════════════════════════════
		// 实验 7: putIfAbsent / evictIfPresent 不可延迟
		// ═════════════════════════════════════════════════════════════
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("实验 7: putIfAbsent / evictIfPresent — 立即操作, 不可延迟");
		System.out.println("═══════════════════════════════════════════════════════════════");
		System.out.println("  源码: TransactionAwareCacheDecorator 对这两个方法直接透传 targetCache");
		System.out.println("  原因: 返回值语义要求立即执行(evictIfPresent→boolean, putIfAbsent→旧值)\n");

		resetProductCache(rawCache);
		rawCache.put(7L, 8888);
		System.out.println("  [初始] cache=" + rawCache.get(7L).get());

		productService.demonstrateImmediateOps(txAwareCache, 7L);
		System.out.println("  [结果] cache=" + (rawCache.get(7L) != null ? rawCache.get(7L).get() : "null")
				+ " — putIfAbsent 写入了 99999\n");

		// ═════════════════════════════════════════════════════════════
		// 总结
		// ═════════════════════════════════════════════════════════════
		System.out.println("╔══════════════════════════════════════════════════════════════════╗");
		System.out.println("║  总结: 三种"提交后删缓存"方案对比                                    ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		System.out.println("║  1. TransactionAwareCacheDecorator — 最轻量, 缓存层透明延迟        ║");
		System.out.println("║     适合: 只需延迟 evict/put/clear, 无额外副作用                    ║");
		System.out.println("║  2. 手动 TransactionSynchronization — 最灵活, 可做任意操作          ║");
		System.out.println("║     适合: 删缓存 + 发 MQ + 通知下游 (一个回调搞定)                  ║");
		System.out.println("║  3. @TransactionalEventListener — 最解耦, 事件驱动                 ║");
		System.out.println("║     适合: 多监听器各司其职 (缓存/MQ/审计), 业务零侵入                 ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		System.out.println("║  工程准则: 永远不要在事务提交前删缓存/更新缓存.                       ║");
		System.out.println("║  所有缓存写操作必须延迟到 afterCommit, 这是缓存一致性的底线.           ║");
		System.out.println("╚══════════════════════════════════════════════════════════════════╝");

		ctx.close();
		System.out.println("\n[完成] W48 缓存与事务一致性 全部实验执行完毕");
	}

	private static void resetProductCache(Cache cache) {
		cache.clear();
		CacheTxConfig.PRODUCT_STORE.clear();
	}
}
*/
