package com.leilei.lab.laboratory.common.mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.leilei.lab.laboratory.common.domain.Channel;
import com.leilei.lab.laboratory.common.domain.Inventory;
import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.domain.Product;
import com.leilei.lab.laboratory.common.domain.Sku;
import com.leilei.lab.laboratory.common.domain.Store;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：确定性种子数据工厂（固定 seed=42，同参数两次生成结果一致，压测断言可复现）
 * 🔗 业务场景：一行代码生成「N 个商品 × 3 杯型 SKU × M 家门店」的产品中心微缩沙盘；
 *             每 3 个商品挂一条小程序渠道促销价（演示价格多维匹配的优先级裁决）
 */
public final class MockDataFactory {

    private static final String[] PRODUCT_NAMES = {
            "珍珠奶茶", "杨枝甘露", "芝士葡萄", "椰椰芒芒", "四季春",
            "金桔柠檬", "烤奶", "抹茶拿铁", "满杯红柚", "多肉青提"
    };
    private static final String[] CATEGORIES = {"奶茶", "果茶", "咖啡"};
    private static final String[] CITIES = {"杭州", "上海", "苏州", "南京", "宁波"};
    private static final String[] CUPS = {"中杯", "大杯", "超大杯"};

    private MockDataFactory() {
    }

    /**
     * 生成实验沙盘。
     *
     * @param productCount 商品数（SKU 数 = 商品数 × 3 杯型）
     * @param storeCount   门店数（门店号从 1001 起）
     */
    public static MockDataSet seed(int productCount, int storeCount) {
        if (productCount <= 0 || storeCount <= 0) {
            throw new IllegalArgumentException("productCount/storeCount must be positive");
        }
        Random random = new Random(42);
        LocalDateTime now = LocalDateTime.now();
        MockDataSet dataSet = new MockDataSet();

        for (int s = 0; s < storeCount; s++) {
            long storeId = 1001 + s;
            String city = CITIES[s % CITIES.length];
            dataSet.getStores().put(storeId, new Store(storeId, city + "·" + (s + 1) + "号店", city, Store.Status.OPEN));
        }

        for (int p = 0; p < productCount; p++) {
            long productId = 100001 + p;
            int generation = p / PRODUCT_NAMES.length;
            String name = PRODUCT_NAMES[p % PRODUCT_NAMES.length] + (generation == 0 ? "" : "·" + (generation + 1) + "代");
            Product product = new Product(productId, name, CATEGORIES[p % CATEGORIES.length], Product.Status.ON_SHELF);
            dataSet.getProducts().put(productId, product);

            for (int c = 0; c < CUPS.length; c++) {
                long skuId = productId * 10 + c;
                Map<String, String> specs = new LinkedHashMap<>();
                specs.put("杯型", CUPS[c]);
                specs.put("糖度", "五分糖");
                specs.put("温度", "少冰");
                // 单价 10~23 元，杯型每档 +2 元
                BigDecimal basePrice = BigDecimal.valueOf((10 + p % 12 + c * 2) * 100L, 2);
                dataSet.getSkus().put(skuId, new Sku(skuId, productId, specs, basePrice));
                product.getSkuIds().add(skuId);

                List<PriceRule> rules = new ArrayList<>();
                // 兜底规则：全国全渠道基础价
                rules.add(new PriceRule(skuId * 100, skuId, null, null, basePrice, 0, null, null));
                if (p % 3 == 0) {
                    // 促销规则：随机门店 + 小程序渠道立减 2 元，优先级 10，有效期 [昨天, +30 天]
                    long promoStoreId = 1001 + random.nextInt(storeCount);
                    BigDecimal promoPrice = basePrice.subtract(BigDecimal.valueOf(200L, 2)).max(BigDecimal.valueOf(100L, 2));
                    rules.add(new PriceRule(skuId * 100 + 1, skuId, promoStoreId, Channel.MINI_PROGRAM,
                            promoPrice, 10, now.minusDays(1), now.plusDays(30)));
                }
                dataSet.getPriceRulesBySku().put(skuId, rules);

                for (int s = 0; s < storeCount; s++) {
                    long storeId = 1001 + s;
                    dataSet.getInventorySeeds().add(new Inventory(skuId, storeId, 100 + random.nextInt(400), 0L));
                }
            }
        }
        return dataSet;
    }
}
