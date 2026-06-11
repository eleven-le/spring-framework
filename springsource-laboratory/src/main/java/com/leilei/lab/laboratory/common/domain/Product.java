package com.leilei.lab.laboratory.common.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：商品（SPU）领域模型，承载上下架状态流转
 * 🔗 业务场景：商品上下架、货架查询、详情页缓存击穿实验的实体底座
 */
public class Product {

    public enum Status { DRAFT, ON_SHELF, OFF_SHELF }

    private final long id;
    private final String name;
    private final String category;
    private final List<Long> skuIds = new ArrayList<>();
    private volatile Status status;
    private volatile LocalDateTime updatedAt;

    public Product(long id, String name, String category, Status status) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    /** 上下架状态流转（合法性校验留给章节代码演示，基建不做策略） */
    public void changeStatus(Status target) {
        this.status = target;
        this.updatedAt = LocalDateTime.now();
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public List<Long> getSkuIds() {
        return skuIds;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', category='" + category + "', status=" + status + "}";
    }
}
