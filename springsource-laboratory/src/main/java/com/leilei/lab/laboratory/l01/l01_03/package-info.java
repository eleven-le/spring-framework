/**
 * 📖 对应章节：[[L01-03-实验室搭建与源码调试姿势]]
 * 🎯 本包主题：L01-03 实验室搭建与源码调试姿势——把「spring-test 实验容器 / @SpringJUnitConfig / MockMvc 工具化」
 *    坐实成本实验室从 L02 起的标准实验姿势：以「断言驱动的真实 Spring 容器」取代 main 方法肉眼看输出。
 *
 * <p>⚠️ 本章示例类位于 <b>测试源集</b>（{@code src/test/java}）——@SpringJUnitConfig / MockMvc 是 spring-test 的能力，
 *    天然属于 test scope；这也是全实验室测试源集的开篇。核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@code L0103_01_PriceMatchContainerTest}
 *       —— @SpringJUnitConfig 拉起「价格多维匹配 + Mock DAO」实验容器，断言驱动；定义后续复用的公共夹具
 *       {@code PriceLabConfig} / {@code PriceMatchService}。</li>
 *   <li>{@code L0103_02_LabContainerToolkitTest}
 *       —— 把容器沉淀成可继承基座 {@code AbstractPriceLabSupport}，实测 ContextCache 复用，并用 ConcurrentBench
 *       对容器里的服务跑高并发报价压测。</li>
 *   <li>{@code L0103_03_PriceQueryMockMvcTest}
 *       —— MockMvcBuilders.standaloneSetup 轻量探测 C 端报价 Controller 的请求映射 / 参数绑定 / 响应体契约。</li>
 * </ul>
 *
 * 复用 {@code com.leilei.lab.laboratory.common} 的领域模型 / Mock DAO / ConcurrentBench；🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l01.l01_03;
