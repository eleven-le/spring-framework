package com.leilei.lab.laboratory.common.domain;

import java.math.BigDecimal;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：订单明细行（SKU + 数量 + 成交单价）
 * 🔗 业务场景：下单金额计算、扣库存明细
 */
public class OrderItem {

    private final long skuId;
    private final String skuName;
    private final int quantity;
    private final BigDecimal unitPrice;

    public OrderItem(long skuId, String skuName, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        this.skuId = skuId;
        this.skuName = skuName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public long getSkuId() {
        return skuId;
    }

    public String getSkuName() {
        return skuName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    @Override
    public String toString() {
        return "OrderItem{skuId=" + skuId + ", skuName='" + skuName + "', quantity=" + quantity
                + ", unitPrice=" + unitPrice + "}";
    }
}
