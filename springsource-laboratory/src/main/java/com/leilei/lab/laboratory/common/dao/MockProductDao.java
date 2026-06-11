package com.leilei.lab.laboratory.common.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.leilei.lab.laboratory.common.domain.Product;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;
import com.leilei.lab.laboratory.common.mock.MockSupport;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：商品 DAO mock（查详情 / 货架列表 / 上下架），每次访问注入 MySQL 级延迟
 * 🔗 业务场景：商品上下架实验、详情页缓存（@Cacheable 回源即本类）
 */
public class MockProductDao {

    private static final String RESOURCE = "mysql:product";

    private final MockDataSet dataSet;
    private final MockProfile profile;

    public MockProductDao(MockDataSet dataSet) {
        this(dataSet, MockProfile.mysql());
    }

    public MockProductDao(MockDataSet dataSet, MockProfile profile) {
        this.dataSet = dataSet;
        this.profile = profile;
    }

    public Optional<Product> findById(long productId) {
        MockSupport.simulate(profile, RESOURCE);
        return Optional.ofNullable(dataSet.getProducts().get(productId));
    }

    /** 货架查询：指定品类下所有在售商品 */
    public List<Product> listOnShelfByCategory(String category) {
        MockSupport.simulate(profile, RESOURCE);
        List<Product> result = new ArrayList<>();
        for (Product product : dataSet.getProducts().values()) {
            if (product.getStatus() == Product.Status.ON_SHELF && product.getCategory().equals(category)) {
                result.add(product);
            }
        }
        return result;
    }

    /** 上下架：返回 false 表示商品不存在 */
    public boolean updateStatus(long productId, Product.Status target) {
        MockSupport.simulate(profile, RESOURCE);
        Product product = dataSet.getProducts().get(productId);
        if (product == null) {
            return false;
        }
        product.changeStatus(target);
        return true;
    }
}
