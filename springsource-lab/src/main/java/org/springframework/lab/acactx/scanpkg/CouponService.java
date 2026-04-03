package org.springframework.lab.acactx.scanpkg;

import org.springframework.stereotype.Service;

/**
 * 独立包中的 @Service — 专门用于 ctx.scan() 路径的演示
 *
 * <p>此类不在 AppConfig 的 @ComponentScan 范围内,
 * 只有通过 ctx.scan("org.springframework.lab.acactx.scanpkg") 才能被发现。
 * 用于区分"构造器 Scanner 扫到的 Bean"与"@ComponentScan Scanner 扫到的 Bean"。
 */
@Service
public class CouponService {

	public String issue() {
		return "CouponService: 发放优惠券成功";
	}
}
