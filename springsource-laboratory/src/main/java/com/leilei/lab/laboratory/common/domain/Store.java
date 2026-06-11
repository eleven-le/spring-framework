package com.leilei.lab.laboratory.common.domain;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：门店领域模型（5 万+ 门店量级的最小字段集）
 * 🔗 业务场景：门店维度价格匹配、门店库存、城市级灰度实验
 */
public class Store {

    public enum Status { OPEN, SUSPENDED, CLOSED }

    private final long id;
    private final String name;
    private final String city;
    private volatile Status status;

    public Store(long id, String name, String city, Status status) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public Status getStatus() {
        return status;
    }

    public void changeStatus(Status target) {
        this.status = target;
    }

    @Override
    public String toString() {
        return "Store{id=" + id + ", name='" + name + "', city='" + city + "', status=" + status + "}";
    }
}
