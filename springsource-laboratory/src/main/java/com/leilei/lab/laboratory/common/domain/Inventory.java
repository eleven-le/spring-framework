package com.leilei.lab.laboratory.common.domain;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：门店级 SKU 库存的不可变快照（version 用于乐观锁语义演示）
 * 🔗 业务场景：库存扣减 / 大促秒杀实验的读视图；并发扣减语义收敛在 MockInventoryDao
 */
public class Inventory {

    private final long skuId;
    private final long storeId;
    private final int availableStock;
    private final long version;

    public Inventory(long skuId, long storeId, int availableStock, long version) {
        this.skuId = skuId;
        this.storeId = storeId;
        this.availableStock = availableStock;
        this.version = version;
    }

    public long getSkuId() {
        return skuId;
    }

    public long getStoreId() {
        return storeId;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Inventory{skuId=" + skuId + ", storeId=" + storeId
                + ", availableStock=" + availableStock + ", version=" + version + "}";
    }
}
