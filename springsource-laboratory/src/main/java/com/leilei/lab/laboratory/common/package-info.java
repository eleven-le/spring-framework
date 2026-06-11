/**
 * 📖 对应文档：[[01-军火库规范]]（common 基建无独立章节，供全库章节复用）
 * 🎯 本包定位：茶饮 C 端领域底座（5 万+ 门店量级的产品中心 mock）。
 * <p>子包速览：
 * <ul>
 *   <li>{@code domain} —— Product / Sku / PriceRule / Inventory / Order / Store 领域模型</li>
 *   <li>{@code mock}   —— 延迟与故障注入内核（MockProfile / MockSupport）+ 确定性种子数据</li>
 *   <li>{@code dao}    —— 模拟 DAO（内存实现，走延迟 / 故障注入，库存为无锁 CAS）</li>
 *   <li>{@code rpc}    —— 模拟远程服务（营销 RPC）</li>
 *   <li>{@code bench}  —— 简易并发压测工具（barrier 齐射 + P95/P99 延迟统计）</li>
 * </ul>
 */
package com.leilei.lab.laboratory.common;
