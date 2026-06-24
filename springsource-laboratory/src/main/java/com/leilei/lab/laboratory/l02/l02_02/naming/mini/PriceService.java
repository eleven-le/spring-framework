package com.leilei.lab.laboratory.l02.l02_02.naming.mini;

import com.leilei.lab.laboratory.l02.l02_02.naming.ChannelPriceService;

import org.springframework.stereotype.Service;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（默认命名规则 = 短类名首字母小写）
 * 🎯 作用：小程序渠道报价服务。短类名 {@code PriceService} → 默认命名为 {@code priceService}，
 *         与 {@code naming.app.PriceService} 撞名，是命名冲突实验的一方。
 * 🔗 业务场景：小程序下单链路的渠道定价。
 */
@Service
public class PriceService implements ChannelPriceService {

	@Override
	public String channel() {
		return "小程序";
	}
}
