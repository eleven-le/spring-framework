package org.springframework.lab.acactx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 通过 @ComponentScan 被扫描注册的业务 Bean
 * 演示: @ComponentScan 在 refresh 第 5 步由 CCPP 内部新建 Scanner 执行,
 * 而非构造器阶段的 this.scanner
 */
@Service
public class UserService {

	@Autowired
	private OrderRepository orderRepository;

	public String whoAmI() {
		return "UserService{orderRepo=" + orderRepository + "}";
	}
}
