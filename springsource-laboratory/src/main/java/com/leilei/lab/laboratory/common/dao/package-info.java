/**
 * 📖 对应文档：[[01-军火库规范]]
 * 🎯 本包定位：模拟 DAO（内存实现）。统一约定：
 * 每个方法先走 {@code MockSupport.simulate(profile, resource)} 注入延迟与故障；
 * 库存扣减为无锁 CAS 自旋（{@code MockInventoryDao}），是秒杀实验的并发底座；
 * 数据策略（价格裁决、状态机校验等）一律留给章节代码，DAO 只提供数据能力。
 */
package com.leilei.lab.laboratory.common.dao;
