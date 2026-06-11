package com.leilei.lab.laboratory.common.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.leilei.lab.laboratory.common.domain.Inventory;
import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.domain.Product;
import com.leilei.lab.laboratory.common.domain.Sku;
import com.leilei.lab.laboratory.common.domain.Store;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：一套实验数据的容器，由 {@link MockDataFactory} 单线程填充，之后供各 Mock DAO 共享读取
 * 🔗 业务场景：商品 / SKU / 价格规则 / 门店 / 库存种子的统一来源；inventorySeeds 仅为初始值，
 *             运行期并发库存状态由 MockInventoryDao 内部的 CAS 结构持有
 */
public class MockDataSet {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final Map<Long, Sku> skus = new ConcurrentHashMap<>();
    private final Map<Long, List<PriceRule>> priceRulesBySku = new ConcurrentHashMap<>();
    private final Map<Long, Store> stores = new ConcurrentHashMap<>();
    /** 仅在 seed 阶段单线程写入，seed 完成后只读 */
    private final List<Inventory> inventorySeeds = new ArrayList<>();

    public Map<Long, Product> getProducts() {
        return products;
    }

    public Map<Long, Sku> getSkus() {
        return skus;
    }

    public Map<Long, List<PriceRule>> getPriceRulesBySku() {
        return priceRulesBySku;
    }

    public Map<Long, Store> getStores() {
        return stores;
    }

    public List<Inventory> getInventorySeeds() {
        return inventorySeeds;
    }

    @Override
    public String toString() {
        return "MockDataSet{products=" + products.size() + ", skus=" + skus.size()
                + ", stores=" + stores.size() + ", inventorySeeds=" + inventorySeeds.size() + "}";
    }
}
