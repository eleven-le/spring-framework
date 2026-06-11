package com.leilei.lab.laboratory.common.domain;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：C 端下单 / 定价渠道枚举
 * 🔗 业务场景：渠道维度的价格匹配（小程序促销价 vs 外卖加价）、渠道库存隔离
 */
public enum Channel {

    MINI_PROGRAM("小程序"),
    APP("App"),
    TAKEOUT_MEITUAN("美团外卖"),
    TAKEOUT_ELEME("饿了么"),
    POS("门店收银");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
