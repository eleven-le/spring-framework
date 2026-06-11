package com.leilei.lab.laboratory.common.rpc;

import java.math.BigDecimal;

import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.mock.MockSupport;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：营销服务 RPC mock，查询用户在某 SKU 上的最优立减金额（确定性伪随机，便于断言）
 * 🔗 业务场景：下单试算链路的远程依赖——事务内远程调用 / 超时降级 / 异步编排实验的靶子
 */
public class MockMarketingRpc {

    private static final String RESOURCE = "rpc:marketing";

    private final MockProfile profile;

    public MockMarketingRpc() {
        this(MockProfile.rpc());
    }

    public MockMarketingRpc(MockProfile profile) {
        this.profile = profile;
    }

    /** 无可用优惠返回 0.00；(userId + skuId) % 5 == 0 的组合返回立减 2 元，结果可复现 */
    public BigDecimal queryBestDiscount(long userId, long skuId) {
        MockSupport.simulate(profile, RESOURCE);
        return (userId + skuId) % 5 == 0 ? BigDecimal.valueOf(200L, 2) : BigDecimal.valueOf(0L, 2);
    }
}
