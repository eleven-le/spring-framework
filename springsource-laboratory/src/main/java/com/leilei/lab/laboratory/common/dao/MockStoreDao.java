package com.leilei.lab.laboratory.common.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.leilei.lab.laboratory.common.domain.Store;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.mock.MockSupport;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：门店 DAO mock（按 ID / 城市查询）
 * 🔗 业务场景：门店维度价格匹配、城市级灰度发布实验
 */
public class MockStoreDao {

    private static final String RESOURCE = "mysql:store";

    private final MockDataSet dataSet;
    private final MockProfile profile;

    public MockStoreDao(MockDataSet dataSet) {
        this(dataSet, MockProfile.mysql());
    }

    public MockStoreDao(MockDataSet dataSet, MockProfile profile) {
        this.dataSet = dataSet;
        this.profile = profile;
    }

    public Optional<Store> findById(long storeId) {
        MockSupport.simulate(profile, RESOURCE);
        return Optional.ofNullable(dataSet.getStores().get(storeId));
    }

    public List<Store> listByCity(String city) {
        MockSupport.simulate(profile, RESOURCE);
        List<Store> result = new ArrayList<>();
        for (Store store : dataSet.getStores().values()) {
            if (store.getCity().equals(city)) {
                result.add(store);
            }
        }
        return result;
    }
}
