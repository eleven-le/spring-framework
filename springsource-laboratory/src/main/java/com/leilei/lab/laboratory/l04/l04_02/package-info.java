/**
 * 📖 对应章节：[[L04-02-事务失效八股的事故现场]]
 * 🎯 本包主题：L04-02 事务失效八股的事故现场——@Transactional 在哪些场景下「贴了却不生效」。
 *    两条主线：① rollbackFor 默认只回滚 RuntimeException/Error（受检异常默认提交）；
 *    ② 事务失效八场景（自调用 / 非 public / 吞异常 / 多线程 / final / 未托管 / 引擎不支持 / 传播配错）。
 *    实验用嵌入式 HSQLDB 提供「真实可提交/可回滚」的库，让每一种失效都在真实数据行数上现形。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l04.l04_02.L0402_01_RollbackForCheckedExceptionDemo}
 *      —— rollbackFor 默认边界：受检异常默认提交（事故）vs rollbackFor=Exception.class 回滚 vs 运行时异常基线回滚。
 *    - {@link com.leilei.lab.laboratory.l04.l04_02.L0402_02_SelfInvocationFailureDemo}
 *      —— 自调用失效：this.xxx() 绕过代理事务不生效（active=false）vs 跨 Bean 调用走代理回滚生效。
 *    - {@link com.leilei.lab.laboratory.l04.l04_02.L0402_03_SwallowedAndNonPublicFailureDemo}
 *      —— 吞异常失效（setRollbackOnly 修复）+ 非 public 方法失效（publicMethodsOnly 过滤）。
 *    - {@link com.leilei.lab.laboratory.l04.l04_02.L0402_04_MultiThreadTxFailureDemo}
 *      —— 多线程失效：事务绑线程 ThreadLocal 不跨线程，子线程独立提交、主事务回滚带不走它。
 */
package com.leilei.lab.laboratory.l04.l04_02;
