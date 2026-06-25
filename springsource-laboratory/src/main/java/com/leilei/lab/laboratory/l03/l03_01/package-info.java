/**
 * 📖 对应章节：[[L03-01-代理机制与选型-JDK与CGLIB]]
 * 🎯 本包主题：L03-01 代理机制与选型——JDK 动态代理 vs CGLIB 取舍、proxyTargetClass / exposeProxy / AopContext、
 *    以及 CGLIB 子类代理的 final/private 硬约束。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l03.l03_01.L0301_01_JdkVsCglibProxySelectionDemo}
 *      —— DefaultAopProxyFactory 三条选型规则：有接口走 JDK / 无接口走 CGLIB / proxyTargetClass 强制 CGLIB。
 *    - {@link com.leilei.lab.laboratory.l03.l03_01.L0301_02_ExposeProxySelfInvocationDemo}
 *      —— 自调用绕过代理导致切面失效，exposeProxy + AopContext.currentProxy() 逃生门（秒杀扣库存）。
 *    - {@link com.leilei.lab.laboratory.l03.l03_01.L0301_03_CglibFinalMethodConstraintDemo}
 *      —— CGLIB 子类代理无法覆写 final/private 方法，挂其上的切面静默失效（上下架操作日志）。
 */
package com.leilei.lab.laboratory.l03.l03_01;
