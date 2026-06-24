/**
 * 📖 对应章节：[[L01-01-模块地图与生态定位]]
 * 🎯 本包主题：L01-01 模块地图与生态定位——把「一次商品查询穿过的 Spring 层」做成活的模块地图，
 *    并以启动期依赖一致性体检守住「模块版本统一」这条 5.x→6.x 演进与生产选型的底线。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l01.l01_01.L0101_01_ModuleMapProbe}
 *       —— 模块地图探针：热路径每层核心抽象 → 归属模块 → 实现版本。</li>
 *   <li>{@link com.leilei.lab.laboratory.l01.l01_01.L0101_02_StartupDependencyConsistencyGuard}
 *       —— 启动期依赖体检：发现 Spring 模块版本漂移即否决（防 NoSuchMethodError）。</li>
 *   <li>{@link com.leilei.lab.laboratory.l01.l01_01.L0101_03_ProductQueryStackDemo}
 *       —— 可运行 demo：商品查询自检串联 SpringVersion / 模块地图 / 依赖体检 / 探针压测。</li>
 * </ul>
 *
 * 复用 {@code com.leilei.lab.laboratory.common} 的领域模型 / Mock DAO / 压测器；🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l01.l01_01;
