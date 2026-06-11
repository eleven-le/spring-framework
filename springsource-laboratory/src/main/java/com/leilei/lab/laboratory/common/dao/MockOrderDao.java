package com.leilei.lab.laboratory.common.dao;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.leilei.lab.laboratory.common.domain.Order;
import com.leilei.lab.laboratory.common.mock.MockAccessException;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.mock.MockSupport;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：订单 DAO mock，save 以订单号唯一约束模拟幂等冲突（重复落单抛异常）
 * 🔗 业务场景：下单链路、事务回滚实验、幂等切面的「数据库唯一索引兜底」一层
 */
public class MockOrderDao {

    private static final String RESOURCE = "mysql:order";

    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();
    private final MockProfile profile;

    public MockOrderDao() {
        this(MockProfile.mysql());
    }

    public MockOrderDao(MockProfile profile) {
        this.profile = profile;
    }

    /** 落单：订单号已存在时模拟唯一索引冲突，抛 {@link MockAccessException} */
    public Order save(Order order) {
        MockSupport.simulate(profile, RESOURCE);
        Order previous = orders.putIfAbsent(order.getId(), order);
        if (previous != null) {
            throw new MockAccessException("订单号唯一索引冲突（幂等兜底触发）: " + order.getId());
        }
        return order;
    }

    public Optional<Order> findById(String orderId) {
        MockSupport.simulate(profile, RESOURCE);
        return Optional.ofNullable(orders.get(orderId));
    }

    /** 状态流转：返回 false 表示订单不存在 */
    public boolean updateStatus(String orderId, Order.Status target) {
        MockSupport.simulate(profile, RESOURCE);
        Order order = orders.get(orderId);
        if (order == null) {
            return false;
        }
        order.changeStatus(target);
        return true;
    }

    public int count() {
        return orders.size();
    }
}
