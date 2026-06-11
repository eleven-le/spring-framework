package com.leilei.lab.laboratory.common.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：SKU 领域模型，规格组合（杯型 / 糖度 / 温度）+ 基础售价
 * 🔗 业务场景：价格多维匹配、库存扣减、下单明细的最小售卖单元
 */
public class Sku {

    private final long id;
    private final long productId;
    /** 规格组合，如 {杯型: 大杯, 糖度: 五分糖, 温度: 少冰} */
    private final Map<String, String> specs;
    private final BigDecimal basePrice;
    private volatile boolean sellable;

    public Sku(long id, long productId, Map<String, String> specs, BigDecimal basePrice) {
        this.id = id;
        this.productId = productId;
        this.specs = Collections.unmodifiableMap(new LinkedHashMap<>(specs));
        this.basePrice = basePrice;
        this.sellable = true;
    }

    public long getId() {
        return id;
    }

    public long getProductId() {
        return productId;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public boolean isSellable() {
        return sellable;
    }

    public void setSellable(boolean sellable) {
        this.sellable = sellable;
    }

    @Override
    public String toString() {
        return "Sku{id=" + id + ", productId=" + productId + ", specs=" + specs + ", basePrice=" + basePrice + "}";
    }
}
