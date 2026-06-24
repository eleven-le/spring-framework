/**
 * 📖 对应章节：[[L02-04-Environment与属性绑定]]
 * 🎯 本包主题：L02-04 Environment 与属性绑定——用「限流阈值多来源 / @Value 正常注入 / @Value 在 BFPP 失效」
 *    三条 C 端高危阈值线索，把「Environment / PropertySource 优先级」与「@Value 占位符解析时机与失效坑」坐实成可运行实验。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_04.L0204_01_PropertySourcePriorityDemo}
 *       —— PropertySource 优先级：有序链 + first-wins，启动参数(systemProperties) &gt; Nacos &gt; 本地默认，
 *          addFirst 注入 hotfix 源紧急降级盖过一切。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_04.L0204_02_ValuePlaceholderTimingDemo}
 *       —— @Value 解析时机：占位符在 Bean 创建期注入那刻解析；AnnotationConfigApplicationContext 不配
 *          PropertySourcesPlaceholderConfigurer 也能解析（容器兜底注册默认解析器）；{@code ${k:default}} 缺省语法。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_04.L0204_03_ValueInBeanFactoryPostProcessorPitfallDemo}
 *       —— @Value 失效坑：BFPP 早于 AutowiredAnnotationBeanPostProcessor 实例化，@Value 字段停在默认值 0；
 *          改用 EnvironmentAware 读 Environment 是正确解。</li>
 * </ul>
 *
 * <p>依赖章节 [[L02-02-Configuration与Bean注册全姿势]]（@Configuration/@Bean 注册）。🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l02.l02_04;
