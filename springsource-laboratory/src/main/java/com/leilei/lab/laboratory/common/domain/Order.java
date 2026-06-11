package com.leilei.lab.laboratory.common.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：C 端订单领域模型，金额由明细聚合，状态机字段可流转
 * 🔗 业务场景：下单扣库存、事务传播实验、订单状态机事件实验的实体底座
 */
public class Order {

    public enum Status { CREATED, PAID, MAKING, READY, COMPLETED, CANCELLED }

    private static final AtomicLong SEQ = new AtomicLong();
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String id;
    private final long userId;
    private final long storeId;
    private final Channel channel;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private volatile Status status;
    private final LocalDateTime createdAt;

    public Order(String id, long userId, long storeId, Channel channel, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
        this.id = id;
        this.userId = userId;
        this.storeId = storeId;
        this.channel = channel;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.subtotal());
        }
        this.totalAmount = total;
        this.status = Status.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    /** 订单号：GM + 时间戳 + 门店号 + 4 位序列，非全局唯一算法，仅供实验 */
    public static String generateId(long storeId) {
        return "GM" + ID_TIME.format(LocalDateTime.now()) + storeId
                + String.format("%04d", SEQ.incrementAndGet() % 10000);
    }

    /** 状态流转（合法迁移校验留给章节代码演示状态机时实现） */
    public void changeStatus(Status target) {
        this.status = target;
    }

    public String getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getStoreId() {
        return storeId;
    }

    public Channel getChannel() {
        return channel;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Order{id='" + id + "', userId=" + userId + ", storeId=" + storeId
                + ", channel=" + channel + ", totalAmount=" + totalAmount + ", status=" + status + "}";
    }
}
