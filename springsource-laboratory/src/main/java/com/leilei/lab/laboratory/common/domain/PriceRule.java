package com.leilei.lab.laboratory.common.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：价格规则模型，门店 / 渠道 / 时段三个维度 + 优先级
 * 🔗 业务场景：价格多维匹配（全国基础价 < 渠道促销价 < 门店活动价），priority 大者胜出
 */
public class PriceRule {

    private final long id;
    private final long skuId;
    /** null = 不限门店（全国价） */
    private final Long storeId;
    /** null = 不限渠道 */
    private final Channel channel;
    private final BigDecimal price;
    /** 数值越大优先级越高，最佳规则的裁决策略由章节代码实现 */
    private final int priority;
    /** null = 不限起始时间 */
    private final LocalDateTime effectiveFrom;
    /** null = 不限结束时间 */
    private final LocalDateTime effectiveTo;

    public PriceRule(long id, long skuId, Long storeId, Channel channel, BigDecimal price,
            int priority, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        this.id = id;
        this.skuId = skuId;
        this.storeId = storeId;
        this.channel = channel;
        this.price = price;
        this.priority = priority;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** 该规则在给定门店 / 渠道 / 时刻是否生效 */
    public boolean matches(long storeId, Channel channel, LocalDateTime at) {
        if (this.storeId != null && this.storeId.longValue() != storeId) {
            return false;
        }
        if (this.channel != null && this.channel != channel) {
            return false;
        }
        if (this.effectiveFrom != null && at.isBefore(this.effectiveFrom)) {
            return false;
        }
        if (this.effectiveTo != null && at.isAfter(this.effectiveTo)) {
            return false;
        }
        return true;
    }

    public long getId() {
        return id;
    }

    public long getSkuId() {
        return skuId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public Channel getChannel() {
        return channel;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getPriority() {
        return priority;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    @Override
    public String toString() {
        return "PriceRule{id=" + id + ", skuId=" + skuId + ", storeId=" + storeId
                + ", channel=" + channel + ", price=" + price + ", priority=" + priority + "}";
    }
}
