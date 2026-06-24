/**
 * 📖 对应章节：[[L01-02-容器心智模型与核心抽象]]
 * 🎯 本包主题：L01-02 容器心智模型与核心抽象——用价格预热与多渠道价格策略两条 C 端线索，
 *    把「BeanFactory vs ApplicationContext」与「BeanDefinition 一生全景」两个心智模型坐实成可运行实验。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l01.l01_02.L0102_01_PriceCacheWarmupBean}
 *       —— 价格规则缓存预热 Bean（InitializingBean + 两个 Aware），作为容器对照实验的探针。</li>
 *   <li>{@link com.leilei.lab.laboratory.l01.l01_02.L0102_02_BeanFactoryVsApplicationContextDemo}
 *       —— 同一 BeanDefinition 喂裸 BeanFactory 与 ApplicationContext，量化预热时机 / Aware / BPP 三差异。</li>
 *   <li>{@link com.leilei.lab.laboratory.l01.l01_02.L0102_03_BeanDefinitionLifecycleProbe}
 *       —— 父模板 + 多渠道子定义动态注册：定义 → 注册 → 合并 → 实例化，BeanDefinition 一生全景。</li>
 * </ul>
 *
 * 复用 {@code com.leilei.lab.laboratory.common} 的领域模型 / Mock DAO；🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l01.l01_02;
