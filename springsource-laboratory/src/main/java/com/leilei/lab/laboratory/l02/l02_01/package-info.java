/**
 * 📖 对应章节：[[L02-01-注入方式选型-构造器Setter字段]]
 * 🎯 本包主题：L02-01 注入方式选型——用「价格试算 + 折扣策略」两条 C 端线索，
 *    把「构造器/Setter/字段三种注入的可测性选型」与「@Autowired vs @Resource vs @Inject 语义差异」坐实成可运行实验。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_01.L0201_01_PriceQuoteService}
 *       —— 构造器注入的生产正确姿势（final 依赖 / 单构造器隐式装配 / fail-fast），也是可测性实验的被测目标。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_01.L0201_02_InjectionStyleContrastDemo}
 *       —— 同一依赖三种风格注入，对照「能否脱离容器实例化 / 漏依赖何时暴露 / 依赖能否被偷改」。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_01.L0201_03_AnnotationSemanticsDemo}
 *       —— 多实现折扣策略下，@Autowired(byType+Qualifier/Primary) vs @Resource(byName) vs @Inject(+Named) 的裁决差异。</li>
 * </ul>
 *
 * <p>配套测试（src/test 同包）：
 * {@code L0201_01_ConstructorInjectionTestabilityTest} —— 断言驱动证明构造器注入「一行 new 即可注入 Mock」的可测性。
 *
 * <p>复用 {@code com.leilei.lab.laboratory.common} 的领域模型 / Mock DAO·RPC；🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l02.l02_01;
