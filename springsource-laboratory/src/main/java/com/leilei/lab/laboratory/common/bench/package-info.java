/**
 * 📖 对应文档：[[01-军火库规范]]
 * 🎯 本包定位：简易并发压测工具 —— barrier 齐射（所有线程就绪后同时起跑）、
 * 成功 / 失败计数、吞吐与 avg / P50 / P95 / P99 / max 延迟统计。
 * 用法：{@code ConcurrentBench.run("秒杀扣库存", 50, 200, () -> dao.tryDeduct(...))}。
 */
package com.leilei.lab.laboratory.common.bench;
