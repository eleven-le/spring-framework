package com.leilei.lab.laboratory.l02.l02_02.naming;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（BeanNameGenerator 与命名冲突）
 * 🎯 作用：分渠道报价服务契约——两个不同包下的同名实现类 {@code PriceService}，
 *         用来演示「默认短类名命名规则」如何制造跨包命名冲突，以及全限定名命名器如何化解。
 * 🔗 业务场景：小程序与 App 各有一套 PriceService，模块拆分后落在不同包，类名却撞了。
 */
public interface ChannelPriceService {

	String channel();
}
