/**
 * 📖 对应章节：[[L02-02-Configuration与Bean注册全姿势]]
 * 🎯 本包主题：L02-02 Configuration 与 Bean 注册全姿势——用「价格缓存单例 + 定价组件族 + 跨包同名服务」三条 C 端线索，
 *    把「@Configuration full vs lite」「@Bean/@Import/@ComponentScan/扫描过滤器」「BeanNameGenerator 与命名规则」坐实成可运行实验。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_02.L0202_01_FullVsLiteConfigDemo}
 *       —— full vs lite（proxyBeanMethods）：CGLIB 增强拦截 @Bean 直调 vs 每次 new 克隆单例，预热失效事故复现。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_02.L0202_02_BeanRegistrationStylesDemo}
 *       —— Bean 进容器四姿势：@ComponentScan 默认过滤器 / excludeFilters 排除 / @Bean 工厂方法 / @Import 编程式注册。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_02.L0202_03_BeanNameGeneratorDemo}
 *       —— 命名规则：默认短类名命名器跨包撞名抛 ConflictingBeanDefinitionException，全限定名命名器化解。</li>
 * </ul>
 *
 * <p>实验目标子包：{@code l02_02.scan}（@ComponentScan 命中/排除目标）、{@code l02_02.naming}（跨包同名类）。
 * <p>复用 {@code com.leilei.lab.laboratory.common} 的领域模型与种子数据工厂；🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l02.l02_02;
