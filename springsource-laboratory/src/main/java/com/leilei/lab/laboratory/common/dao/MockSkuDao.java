package com.leilei.lab.laboratory.common.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.leilei.lab.laboratory.common.domain.Sku;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.mock.MockSupport;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：SKU DAO mock（按 ID / 按商品维度查询）
 * 🔗 业务场景：下单选规格、价格匹配前置查询
 */
public class MockSkuDao {

    private static final String RESOURCE = "mysql:sku";

    private final MockDataSet dataSet;
    private final MockProfile profile;

    public MockSkuDao(MockDataSet dataSet) {
        this(dataSet, MockProfile.mysql());
    }

    public MockSkuDao(MockDataSet dataSet, MockProfile profile) {
        this.dataSet = dataSet;
        this.profile = profile;
    }

    public Optional<Sku> findById(long skuId) {
        MockSupport.simulate(profile, RESOURCE);
        return Optional.ofNullable(dataSet.getSkus().get(skuId));
    }

    public List<Sku> listByProductId(long productId) {
        MockSupport.simulate(profile, RESOURCE);
        List<Sku> result = new ArrayList<>();
        for (Sku sku : dataSet.getSkus().values()) {
            if (sku.getProductId() == productId) {
                result.add(sku);
            }
        }
        return result;
    }
}
