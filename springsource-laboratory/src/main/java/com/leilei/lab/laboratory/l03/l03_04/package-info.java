/**
 * 📖 对应章节：[[L03-04-AOP失效场景全集与排查]]
 * 🎯 本包主题：L03-04 AOP 失效场景全集与排查决策树——把「切面为什么没生效」拆成四类可复现的失效，
 *    每类都用「计数切面 / 离线 matches」量化「切面是否真的进了代理链」，对应排查决策树的一条分支：
 *    自调用 → 方法不可代理 → 切点不匹配 → 容器外/跨线程。根因总纲：Spring AOP 是<b>代理织入</b>，
 *    只有「容器托管 + 能被代理 + 切点匹配 + 经代理对象调用」四个条件同时满足，切面才生效，缺一即静默失效。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l03.l03_04.L0304_01_SelfInvocationFailureDemo}
 *      —— 失效之首：同类 this 自调用绕过代理；三条修复（AopContext.currentProxy / 拆 Bean）量化对比。
 *    - {@link com.leilei.lab.laboratory.l03.l03_04.L0304_02_NonProxyableMethodFailureDemo}
 *      —— 方法不可代理：final/static/private 无法被 CGLIB override，CglibAopProxy 仅日志警告、静默失效。
 *    - {@link com.leilei.lab.laboratory.l03.l03_04.L0304_03_PointcutMismatchFailureDemo}
 *      —— 切点写错：用 AspectJExpressionPointcut#matches 离线体检表达式，五类高频写错一眼定位。
 *    - {@link com.leilei.lab.laboratory.l03.l03_04.L0304_04_UnmanagedAndAsyncFailureDemo}
 *      —— 容器边界：自己 new 的裸对象无代理；exposeProxy 代理上下文是 ThreadLocal，不跨线程必失效。
 */
package com.leilei.lab.laboratory.l03.l03_04;
