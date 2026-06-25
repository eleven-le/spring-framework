/**
 * 📖 对应章节：[[L04-04-多数据源与分布式事务边界]]
 * 🎯 本包主题：L04-04 多数据源与分布式事务边界——用 {@code AbstractRoutingDataSource} 做动态多数据源（分库/读写分离），
 *    讲清「多数据源下 @Transactional 路由失效」的两类时序坑，并界定「分布式事务边界」：单机本地事务管不了跨库，
 *    用本地消息表（outbox）把跨库一致性降级为最终一致。实验全部用多个物理 HSQLDB + 故障注入自证。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l04.l04_04.L0404_01_DynamicRoutingDataSourceDemo}
 *      —— ⭐ AbstractRoutingDataSource：实现 determineCurrentLookupKey()，同一 JdbcTemplate 按区域 key 动态路由到不同物理库。
 *    - {@link com.leilei.lab.laboratory.l04.l04_04.L0404_02_RoutingFailsInsideTxDemo}
 *      —— 路由失效一：连接在 doBegin 借出即绑定到当前库，事务内再切 key 完全无效，两笔都落在开始时那个库。
 *    - {@link com.leilei.lab.laboratory.l04.l04_04.L0404_03_ReadOnlyRoutingPitfallDemo}
 *      —— 路由失效二（时序坑）：readOnly 标记在 doBegin 之后才设，靠 @Transactional(readOnly=true) 路由从库会落到主库。
 *    - {@link com.leilei.lab.laboratory.l04.l04_04.L0404_04_OutboxEventualConsistencyDemo}
 *      —— 分布式事务边界：单机事务跨不了两库，本地消息表 + afterCommit 中继 + 重试达成最终一致（不丢不超发）。
 */
package com.leilei.lab.laboratory.l04.l04_04;
