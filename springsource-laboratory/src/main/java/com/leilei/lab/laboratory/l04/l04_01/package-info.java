/**
 * 📖 对应章节：[[L04-01-声明式事务与七种传播行为]]
 * 🎯 本包主题：L04-01 声明式事务与七种传播行为——7 种传播行为的生产语义与用例矩阵、
 *    isolation / readOnly / timeout 三属性的落地、以及 🧊 NESTED 与 SAVEPOINT 的局部回滚。
 *    实验用嵌入式 HSQLDB 提供「真实可提交/可回滚」的库，让传播与回滚语义在真实数据上自证。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l04.l04_01.L0401_01_PropagationMatrixDemo}
 *      —— 7 种传播行为 ×「无外层/有外层」真值矩阵，logging 版 DataSourceTransactionManager 暴露 begin/suspend/resume。
 *    - {@link com.leilei.lab.laboratory.l04.l04_01.L0401_02_RequiresNewAuditDemo}
 *      —— REQUIRES_NEW 独立提交：主单回滚但操作流水存活（对照 REQUIRED 一起回滚）。
 *    - {@link com.leilei.lab.laboratory.l04.l04_01.L0401_03_NestedSavepointBatchOnShelfDemo}
 *      —— NESTED savepoint 局部回滚：批量上架坏项跳过、好项保留（对照 REQUIRED 一损俱损 UnexpectedRollbackException）。
 *    - {@link com.leilei.lab.laboratory.l04.l04_01.L0401_04_IsolationReadOnlyTimeoutDemo}
 *      —— isolation 改物理连接隔离级 / readOnly 写入同步管理器 / timeout 触发 TransactionTimedOutException。
 */
package com.leilei.lab.laboratory.l04.l04_01;
