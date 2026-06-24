package com.leilei.lab.laboratory.l02.l02_02.scan;

import java.math.BigDecimal;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（@ComponentScan 扫描目标）
 * 🎯 作用：C 端定价组件抽象——被 {@code @ComponentScan} 扫进容器的业务组件统一契约。
 * 🔗 业务场景：下单算价时按渠道/活动挑选定价组件；本接口让「扫描过滤器选中了谁」可被断言。
 */
public interface PricingComponent {

	/** 在 base 价基础上算出立减金额（演示用，逻辑从简）。 */
	BigDecimal discount(BigDecimal base);

	/** 组件可读名，便于实验输出辨认。 */
	String label();
}
