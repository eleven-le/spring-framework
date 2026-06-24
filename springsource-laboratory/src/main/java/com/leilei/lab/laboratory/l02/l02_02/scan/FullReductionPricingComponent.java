package com.leilei.lab.laboratory.l02.l02_02.scan;

import java.math.BigDecimal;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（@ComponentScan 命中目标）
 * 🎯 作用：满减定价组件，用 {@link CDomainService} 标记 → 被默认过滤器扫入容器。
 * 🔗 业务场景：满 30 减 5 的活动定价。
 */
@CDomainService
public class FullReductionPricingComponent implements PricingComponent {

	@Override
	public BigDecimal discount(BigDecimal base) {
		return base.compareTo(BigDecimal.valueOf(30)) >= 0 ? BigDecimal.valueOf(5) : BigDecimal.ZERO;
	}

	@Override
	public String label() {
		return "满减";
	}
}
