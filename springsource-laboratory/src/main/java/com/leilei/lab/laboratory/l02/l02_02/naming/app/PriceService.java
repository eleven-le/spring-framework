package com.leilei.lab.laboratory.l02.l02_02.naming.app;

import com.leilei.lab.laboratory.l02.l02_02.naming.ChannelPriceService;

import org.springframework.stereotype.Service;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（默认命名规则 = 短类名首字母小写）
 * 🎯 作用：App 渠道报价服务。短类名同为 {@code PriceService} → 默认命名同为 {@code priceService}，
 *         与 {@code naming.mini.PriceService} 撞名，是命名冲突实验的另一方。
 * 🔗 业务场景：App 下单链路的渠道定价。
 */
@Service
public class PriceService implements ChannelPriceService {

	@Override
	public String channel() {
		return "App";
	}
}
