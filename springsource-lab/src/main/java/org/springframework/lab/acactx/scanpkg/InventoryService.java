package org.springframework.lab.acactx.scanpkg;

import org.springframework.stereotype.Component;

/**
 * scan 包中的另一个组件 — 验证批量扫描
 */
@Component
public class InventoryService {

	public String check() {
		return "InventoryService: 库存充足";
	}
}
