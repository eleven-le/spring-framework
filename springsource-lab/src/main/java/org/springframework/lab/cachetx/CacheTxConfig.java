package org.springframework.lab.cachetx;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Arrays;

/**
 * W48 -- 缓存与事务一致性 练兵场配置
 *
 * <p>提供两套 CacheManager:
 * <ul>
 *   <li><b>rawCacheManager</b>: 原始 SimpleCacheManager, put/evict 立即生效</li>
 *   <li><b>txAwareCacheManager</b>: TransactionAwareCacheManagerProxy 包装, put/evict 延迟到 afterCommit</li>
 * </ul>
 *
 * <p>关键: TransactionAwareCacheDecorator 的 put/evict/clear 三个方法
 * 在检测到 isSynchronizationActive() == true 时,
 * 注册一个匿名 TransactionSynchronization, 把真正操作推迟到 afterCommit.
 *
 * <p>这样做的核心收益:
 * 事务回滚时, afterCommit 不会被调用, 缓存不会被错误删除/更新;
 * 事务提交后, 才把缓存删除/更新, 保证数据库与缓存的因果一致性.
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.springframework.lab.cachetx")
public class CacheTxConfig {

	/**
	 * 暴露底层 ConcurrentMap 供实验直接观测缓存内容
	 */
	public static final ConcurrentMap<Object, Object> PRODUCT_STORE = new ConcurrentHashMap<>();
	public static final ConcurrentMap<Object, Object> STOCK_STORE = new ConcurrentHashMap<>();

	// ─── DataSource / TX ───

	@Bean
	public DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.addScript("classpath:lab-cachetx-schema.sql")
				.build();
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource ds) {
		return new JdbcTemplate(ds);
	}

	// ─── 原始 CacheManager (put/evict 立即执行) ───

	@Bean
	public CacheManager rawCacheManager() {
		SimpleCacheManager mgr = new SimpleCacheManager();
		mgr.setCaches(Arrays.asList(
				new ConcurrentMapCache("product", PRODUCT_STORE, false),
				new ConcurrentMapCache("stock", STOCK_STORE, false)
		));
		mgr.afterPropertiesSet(); // 手动初始化
		return mgr;
	}

	// ─── 事务感知 CacheManager (put/evict 延迟到 afterCommit) ───

	@Bean
	public CacheManager txAwareCacheManager(CacheManager rawCacheManager) {
		return new TransactionAwareCacheManagerProxy(rawCacheManager);
	}

	/**
	 * 直接暴露一个被 TransactionAwareCacheDecorator 包装的 Cache 实例,
	 * 方便实验中手动操作.
	 */
	@Bean
	public Cache txAwareProductCache(CacheManager rawCacheManager) {
		Cache raw = rawCacheManager.getCache("product");
		return new TransactionAwareCacheDecorator(raw);
	}
}
