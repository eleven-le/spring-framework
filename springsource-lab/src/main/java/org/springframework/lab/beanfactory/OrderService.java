package org.springframework.lab.beanfactory;

import org.springframework.stereotype.Service;

/**
 * 业务 Service — 用于实验按名称查找、别名查找、类型查找。
 */
@Service("orderService")
public class OrderService {

	public String placeOrder(String item) {
		return "下单成功: " + item;
	}
}
