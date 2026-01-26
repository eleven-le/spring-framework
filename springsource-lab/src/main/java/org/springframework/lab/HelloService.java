package org.springframework.lab;

import org.springframework.stereotype.Component;

/**
 * 示例服务类
 */
@Component
public class HelloService {

	public String sayHello(String name) {
		return "Hello, " + name + "!";
	}
}
