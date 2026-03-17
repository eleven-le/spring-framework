package org.springframework.lab.transaction;

/**
 * 账户服务接口 — 让 Spring 默认走 JDK 动态代理
 */
public interface AccountService {

	void transfer(String from, String to, int amount);

	int balance(String name);
}
