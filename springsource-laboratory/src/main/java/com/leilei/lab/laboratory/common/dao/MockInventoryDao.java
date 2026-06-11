package com.leilei.lab.laboratory.common.dao;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.leilei.lab.laboratory.common.domain.Inventory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.mock.MockSupport;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：库存 DAO mock —— 无锁 CAS 扣减（不超卖）+ 回补 + 快照读，version 模拟乐观锁
 * 🔗 业务场景：大促秒杀库存扣减、事务回滚回补、ConcurrentBench 压测的标准靶子
 */
public class MockInventoryDao {

    private static final String RESOURCE = "mysql:inventory";

    private static final class StockEntry {
        final AtomicInteger stock;
        final AtomicLong version = new AtomicLong();

        StockEntry(int initial) {
            this.stock = new AtomicInteger(initial);
        }
    }

    private final ConcurrentHashMap<String, StockEntry> stocks = new ConcurrentHashMap<>();
    private final MockProfile profile;

    public MockInventoryDao(MockDataSet dataSet) {
        this(dataSet, MockProfile.mysql());
    }

    public MockInventoryDao(MockDataSet dataSet, MockProfile profile) {
        this.profile = profile;
        for (Inventory seed : dataSet.getInventorySeeds()) {
            stocks.put(key(seed.getSkuId(), seed.getStoreId()), new StockEntry(seed.getAvailableStock()));
        }
    }

    private static String key(long skuId, long storeId) {
        return skuId + "@" + storeId;
    }

    /** 库存快照（读到的是某一瞬间的一致值，不保证读后不变） */
    public Optional<Inventory> findOne(long skuId, long storeId) {
        MockSupport.simulate(profile, RESOURCE);
        StockEntry entry = stocks.get(key(skuId, storeId));
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new Inventory(skuId, storeId, entry.stock.get(), entry.version.get()));
    }

    /** CAS 自旋扣减：库存不足返回 false，并发安全、不超卖 */
    public boolean tryDeduct(long skuId, long storeId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        MockSupport.simulate(profile, RESOURCE);
        StockEntry entry = stocks.get(key(skuId, storeId));
        if (entry == null) {
            return false;
        }
        while (true) {
            int current = entry.stock.get();
            if (current < quantity) {
                return false;
            }
            if (entry.stock.compareAndSet(current, current - quantity)) {
                entry.version.incrementAndGet();
                return true;
            }
        }
    }

    /** 回补库存（下单失败补偿 / 取消单），与扣减同样递增 version */
    public void restore(long skuId, long storeId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        MockSupport.simulate(profile, RESOURCE);
        StockEntry entry = stocks.get(key(skuId, storeId));
        if (entry != null) {
            entry.stock.addAndGet(quantity);
            entry.version.incrementAndGet();
        }
    }

    /** 全网可售库存合计（大促容量评估视角） */
    public int totalAvailable(long skuId) {
        MockSupport.simulate(profile, RESOURCE);
        String prefix = skuId + "@";
        int total = 0;
        for (Map.Entry<String, StockEntry> entry : stocks.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                total += entry.getValue().stock.get();
            }
        }
        return total;
    }
}
