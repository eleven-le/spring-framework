/**
 * 📖 对应文档：[[01-军火库规范]]
 * 🎯 本包定位：C 端产品中心领域模型 mock —— 商品(SPU) / SKU / 价格规则 / 库存 / 订单 / 门店。
 * 约定：金额一律 BigDecimal(scale=2)；可变状态字段用 volatile；并发修改语义收敛在 dao 层。
 */
package com.leilei.lab.laboratory.common.domain;
