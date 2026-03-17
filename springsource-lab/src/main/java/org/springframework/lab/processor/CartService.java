package org.springframework.lab.processor;

import org.springframework.stereotype.Component;

/**
 * 购物车服务 -- 被 BFPP 改 scope、被 BPP 包装的目标 Bean
 */
@Component
public class CartService {

	public String addItem(String item) {
		return "added: " + item + " (instance=" + Integer.toHexString(hashCode()) + ")";
	}
}
