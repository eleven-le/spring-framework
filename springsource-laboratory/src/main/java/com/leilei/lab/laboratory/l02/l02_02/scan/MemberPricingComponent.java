package com.leilei.lab.laboratory.l02.l02_02.scan;

import java.math.BigDecimal;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（@ComponentScan 命中目标）
 * 🎯 作用：会员定价组件，用 {@link CDomainService} 标记 → 被默认过滤器扫入容器。
 * 🔗 业务场景：会员价整单 95 折。
 */
@CDomainService
public class MemberPricingComponent implements PricingComponent {

	@Override
	public BigDecimal discount(BigDecimal base) {
		return base.multiply(BigDecimal.valueOf(0.05));
	}

	@Override
	public String label() {
		return "会员折扣";
	}
}
