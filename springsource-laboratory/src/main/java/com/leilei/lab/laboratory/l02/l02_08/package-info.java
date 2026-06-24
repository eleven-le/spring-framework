/**
 * 📖 对应章节：[[L02-08-歧义裁决与容器语义-Primary-Qualifier-DependsOn-Bean覆盖]]
 * 🎯 本包主题：L02-08 歧义裁决与容器语义——当「一个类型多个 Bean」「同名定义撞车」「初始化有隐式先后」时，
 *    容器靠哪些规则裁决与编排。用 C 端高并发线索把六个易踩的容器语义点坐实成可运行实验。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_08.L0208_01_StrategyResolutionDemo}
 *       —— 库存扣减策略族演示歧义裁决优先级链：@Qualifier &gt; @Primary &gt; @Priority &gt; 按名字。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_08.L0208_02_GenericInjectionDemo}
 *       —— 泛型缓存加载器 CacheLoader&lt;Product&gt; / CacheLoader&lt;PriceRule&gt; 靠 ResolvableType 泛型裁决，无需 @Qualifier。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_08.L0208_03_OrderingSemanticsDemo}
 *       —— 下单前置校验链演示 @Order / Ordered / PriorityOrdered 的排序语义，及「@Order 不参与单点歧义裁决」的边界。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_08.L0208_04_DependsOnDemo}
 *       —— 价格缓存预热用 @DependsOn 强编排初始化顺序，并演示环形 depends-on 启动期 fail-fast。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_08.L0208_05_OverrideAndAliasDemo}
 *       —— 支付网关撞名演示 allowBeanDefinitionOverriding 覆盖语义（默认静默覆盖 vs 关闭后 fail-fast）与别名机制。</li>
 * </ul>
 *
 * <p>复用 {@code com.leilei.lab.laboratory.common} 的领域模型（Product / PriceRule）；🚫 禁止 hello-world。
 * 依赖章节：[[L02-01-注入方式选型-构造器Setter字段]]（本章是其「多候选裁决」一节的纵深展开）。
 */
package com.leilei.lab.laboratory.l02.l02_08;
