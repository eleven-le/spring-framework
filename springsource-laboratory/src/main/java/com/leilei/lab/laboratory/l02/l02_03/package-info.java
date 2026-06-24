/**
 * 📖 对应章节：[[L02-03-条件装配-Conditional与Profile]]
 * 🎯 本包主题：L02-03 条件装配——用「秒杀开关 / 缓存兜底相位 / 营销网关多环境」三条 C 端线索，
 *    把「@Conditional 派生体系与自定义 Condition」「ConfigurationPhase 相位」「@Profile 多环境装配」坐实成可运行实验。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_03.L0203_01_CustomConditionalDemo}
 *       —— 自定义 @Conditional 派生注解（@ConditionalOnFlag/@ConditionalOnGrayRelease），按 Environment 开关装配秒杀组件，并演示多条件 AND。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_03.L0203_02_ConditionPhaseDemo}
 *       —— ConfigurationPhase 相位事故：普通 Condition 在 PARSE_CONFIGURATION 误判「缺主缓存」误装兜底；ConfigurationCondition 指定 REGISTER_BEAN 修复。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_03.L0203_03_ProfileMultiEnvDemo}
 *       —— @Profile 多环境：简单名 / 取反 / 表达式 AND / default 兜底四形态，营销网关随 profile 一次切死。</li>
 * </ul>
 *
 * <p>复用 {@code com.leilei.lab.laboratory.common} 的 MockMarketingRpc / MockProfile；🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l02.l02_03;
