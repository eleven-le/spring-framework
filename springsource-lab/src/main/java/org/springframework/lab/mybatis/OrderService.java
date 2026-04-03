package org.springframework.lab.mybatis;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单服务 — 展示 Mapper 在事务环境下的使用。
 *
 * 关注点:
 *   1. OrderMapper 注入的实际上是 MapperProxy (JDK 动态代理)
 *   2. 在 @Transactional 方法中, 同一事务内多次调用 Mapper 会复用同一个 SqlSession
 *      (由 SqlSessionTemplate → SqlSessionUtils → TransactionSynchronizationManager 保证)
 *   3. 没有事务时, 每次 Mapper 调用都会打开-提交-关闭一个独立 SqlSession
 */
@Service
public class OrderService {

	private final OrderMapper orderMapper;

	public OrderService(OrderMapper orderMapper) {
		this.orderMapper = orderMapper;
	}

	/** 无事务: 每次 mapper 调用 = 独立 SqlSession */
	public Order queryOrder(Long id) {
		return orderMapper.findById(id);
	}

	/** 有事务: 同一 SqlSession 复用, 一级缓存生效 */
	@Transactional
	public Order queryOrderInTx(Long id) {
		Order first = orderMapper.findById(id);
		// 第二次查询 — 在同一事务内命中一级缓存, 返回同一对象引用
		Order second = orderMapper.findById(id);
		System.out.println("  一级缓存验证: first == second ? " + (first == second));
		return first;
	}

	/** 下单 + 扣款(模拟): 两条 SQL 在同一事务 */
	@Transactional
	public Order placeOrder(String orderNo, Long userId, Long amount) {
		Order order = new Order(orderNo, userId, amount);
		orderMapper.insert(order);
		System.out.println("  插入订单: " + order);
		orderMapper.updateStatus(order.getId(), "PAID");
		System.out.println("  更新状态 → PAID");
		return orderMapper.findById(order.getId());
	}

	/** 查用户所有订单 */
	public List<Order> listByUser(Long userId) {
		return orderMapper.findByUserId(userId);
	}
}
