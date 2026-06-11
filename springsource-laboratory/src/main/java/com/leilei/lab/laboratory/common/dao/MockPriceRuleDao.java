package com.leilei.lab.laboratory.common.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.mock.MockSupport;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：价格规则 DAO mock，只负责取回 SKU 的全部候选规则
 * 🔗 业务场景：价格多维匹配实验——「门店 / 渠道 / 时段 + 优先级」的裁决策略由章节代码实现，
 *             便于演示策略模式与 ConversionService / SpEL 等不同实现姿势
 */
public class MockPriceRuleDao {

    private static final String RESOURCE = "mysql:price_rule";

    private final MockDataSet dataSet;
    private final MockProfile profile;

    public MockPriceRuleDao(MockDataSet dataSet) {
        this(dataSet, MockProfile.mysql());
    }

    public MockPriceRuleDao(MockDataSet dataSet, MockProfile profile) {
        this.dataSet = dataSet;
        this.profile = profile;
    }

    /** 返回该 SKU 的全部候选规则（防御性拷贝，调用方可自由排序过滤） */
    public List<PriceRule> listBySkuId(long skuId) {
        MockSupport.simulate(profile, RESOURCE);
        List<PriceRule> rules = dataSet.getPriceRulesBySku().get(skuId);
        return rules == null ? Collections.emptyList() : new ArrayList<>(rules);
    }
}
