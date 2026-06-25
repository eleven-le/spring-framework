/**
 * 📖 对应章节：[[L03-02-Aspect切面工程化]]
 * 🎯 本包主题：L03-02 Aspect 切面工程化——切点表达式 designators 与匹配性能、五种通知与执行顺序（5.2.7 变化）、
 *    多切面 @Order 叠加、引介增强 @DeclareParents；Advisor/Advice/Pointcut 模型主讲在 L11-03 / L12-04，本章引用。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l03.l03_02.L0302_01_PointcutDesignatorMatchingDemo}
 *      —— 9 种切点指示符的匹配与成本：within/execution/@annotation（静态可缓存）vs args（动态逐调用，isRuntime=true）。
 *    - {@link com.leilei.lab.laboratory.l03.l03_02.L0302_02_FiveAdviceOrderDemo}
 *      —— 同一切面五种通知的真实执行顺序，坐实 5.2.7 起 @After 永在 @AfterReturning/@AfterThrowing 之后（finally 语义）。
 *    - {@link com.leilei.lab.laboratory.l03.l03_02.L0302_03_MultiAspectOrderDemo}
 *      —— 限流/幂等/审计三切面叠加，@Order 越小越靠外（进先出后），洋葱模型可视化。
 *    - {@link com.leilei.lab.laboratory.l03.l03_02.L0302_04_DeclareParentsIntroductionDemo}
 *      —— @DeclareParents 引介增强：不改业务代码给一批对象混入新接口/新状态（类型级增强）。
 */
package com.leilei.lab.laboratory.l03.l03_02;
