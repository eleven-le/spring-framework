/**
 * 📖 对应章节：[[L03-03-切面实战-鉴权日志限流幂等]]
 * 🎯 本包主题：L03-03 注解切面落地——把「鉴权 / 日志脱敏 / 限流 / 幂等」四类横切关注点做成
 *    「注解声明意图 + @Around 切面统一拦截」的标准姿势；@Order 决定四道闸的洋葱排布
 *    （鉴权 0 → 日志 50 → 限流 1… 见各类，生产排布原则「越廉价、拒绝率越高的闸越往外」，
 *    多切面叠加规则主讲在 [[L03-02-Aspect切面工程化]]）。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l03.l03_03.L0303_01_AuthCheckAspect}
 *      —— 鉴权：@RequireLogin/@RequireRole + AnnotatedElementUtils 从目标方法读注解，未登录/无权限 fail-fast。
 *    - {@link com.leilei.lab.laboratory.l03.l03_03.L0303_02_SensitiveLogAspect}
 *      —— 日志脱敏：@AccessLog 方法注解 + @Sensitive 字段注解，日志渲染瞬间对 PII 打码，明文不落盘。
 *    - {@link com.leilei.lab.laboratory.l03.l03_03.L0303_03_RateLimitAspect}
 *      —— 限流：@RateLimit + @annotation 绑定注解实例 + 令牌桶，超额 fail-fast；ConcurrentBench 量化效果。
 *    - {@link com.leilei.lab.laboratory.l03.l03_03.L0303_04_IdempotentAspect}
 *      —— 幂等：@Idempotent + putIfAbsent(SETNX) 原子占位，并发同 requestId 只执行一次，杜绝重复扣减。
 */
package com.leilei.lab.laboratory.l03.l03_03;
