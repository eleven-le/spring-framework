/**
 * 📖 对应章节：[[L02-02-Configuration与Bean注册全姿势]]（@ComponentScan / 扫描过滤器实验目标包）
 * 🎯 本包主题：C 端定价组件的可扫描目标集合，供 {@code L0202_02_BeanRegistrationStylesDemo} 演示
 *    「默认过滤器命中 @Component 元注解」与「excludeFilters 显式排除待下线组件」。
 *
 * <p>成员：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_02.scan.CDomainService} —— 自定义 stereotype（元注解 @Component）</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_02.scan.LegacyComponent} —— 待下线标记（同样 @Component，演示被排除）</li>
 *   <li>{@code FullReductionPricingComponent} / {@code MemberPricingComponent} —— @CDomainService，会被扫入</li>
 *   <li>{@code LegacyFixedPricingComponent} —— @LegacyComponent，被 excludeFilters 排除</li>
 * </ul>
 */
package com.leilei.lab.laboratory.l02.l02_02.scan;
