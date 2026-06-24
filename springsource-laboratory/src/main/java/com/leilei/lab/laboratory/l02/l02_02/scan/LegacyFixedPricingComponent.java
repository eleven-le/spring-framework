package com.leilei.lab.laboratory.l02.l02_02.scan;

import java.math.BigDecimal;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（excludeFilters 排除目标）
 * 🎯 作用：旧的固定立减组件，用 {@link LegacyComponent} 标记 → 默认会被扫到，须被 excludeFilters 排除。
 * 🔗 业务场景：待下线的「无脑减 2 元」逻辑，留代码但禁止再装配。
 */
@LegacyComponent
public class LegacyFixedPricingComponent implements PricingComponent {

	@Override
	public BigDecimal discount(BigDecimal base) {
		return BigDecimal.valueOf(2);
	}

	@Override
	public String label() {
		return "旧固定立减(待下线)";
	}
}
