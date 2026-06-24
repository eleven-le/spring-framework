/**
 * 📖 对应章节：[[L02-09-容器基础设施-Resource-SpEL-MessageSource-父子容器]]
 * 🎯 本包主题：L02-09 容器基础设施四件套——资源寻址（Resource/ResourceLoader）、表达式引擎（SpEL）、
 *    国际化文案（MessageSource）、层级容器（父子 BeanFactory）。用 C 端定价/下单/多语言/多业务线场景把这四个
 *    「冷门但面试爱问」的容器基础设施坐实成可运行实验。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_09.L0209_01_ResourceLoaderDemo}
 *       —— Resource/ResourceLoader 抽象：classpath: 内置兜底价、classpath*: 跨模块聚合区域价、file: 直读热更目录，一套 API 三类来源。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_09.L0209_02_SpELInContainerDemo}
 *       —— SpEL：@Value("#{...}") 的 Bean 属性引用/T(...)静态/三元/Elvis/systemProperties/集合选择，及容器外手撸解析对价格规则做选择 .?[] 与投影 .![]。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_09.L0209_03_MessageSourceI18nDemo}
 *       —— MessageSource：ResourceBundleMessageSource 按 Locale 出中/英文案、{0} 占位填参、defaultMessage 兜底、Bean 必须名为 "messageSource"、MessageSourceAccessor。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_09.L0209_04_ParentChildContextDemo}
 *       —— 父子容器：可见性单向（子见父、父不见子）、containsLocalBean vs containsBean、同名 Bean 子遮蔽父、getParentBeanFactory 层级链。</li>
 * </ul>
 *
 * <p>资源文件：{@code resources/com/leilei/lab/laboratory/l02/l02_09/} 下的价格 properties（baseline + region/*）、
 * {@code resources/i18n/order-messages*.properties}（中/英文案）。
 * 复用 {@code com.leilei.lab.laboratory.common} 的领域模型（Product / PriceRule / Channel）；🚫 禁止 hello-world。
 * 依赖章节：[[L02-02-Configuration与Bean注册全姿势]]；父子容器与 [[L05-01]] MVC 父子容器互链。
 */
package com.leilei.lab.laboratory.l02.l02_09;
