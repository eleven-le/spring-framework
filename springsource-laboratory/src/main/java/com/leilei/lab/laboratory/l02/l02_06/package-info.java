/**
 * 📖 对应章节：[[L02-06-Bean生命周期回调全图]]
 * 🎯 本包主题：L02-06 Bean 生命周期回调全图——用「SKU 价格缓存预热 / 库存快照 / 门店路由」三个 C 端 Bean，
 *    把 init/destroy 三种写法的固定执行顺序、@PostConstruct/@PreDestroy 的 BPP 原理、Aware 两组回调的时机差，
 *    坐实成可运行实验。编排者是 AbstractAutowireCapableBeanFactory#initializeBean（init）与 DisposableBeanAdapter#destroy（destroy）。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_06.L0206_01_BeanLifecycleCallbackOrderDemo}
 *       —— 一个价格缓存预热 Bean 同时挂三种 init + 三种 destroy 写法：构造器 → @PostConstruct → afterPropertiesSet
 *          → 自定义 initMethod；销毁逆向 @PreDestroy → destroy() → 自定义 destroyMethod。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_06.L0206_02_PostConstructPrincipleDemo}
 *       —— @PostConstruct/@PreDestroy 原理：CommonAnnotationBeanPostProcessor（InitDestroyAnnotationBPP 子类）反射扫描
 *          LifecycleMetadata 后回调；注解类型可插拔；BPP 不在场则静默失效（裸 BeanFactory / JDK 9+ 移除 javax.annotation）。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_06.L0206_03_AwareCallbackTimingDemo}
 *       —— Aware 回调时机表：BeanName/BeanClassLoader/BeanFactoryAware 由 invokeAwareMethods 硬编码直调（最早），
 *          Environment/ApplicationContextAware 由 ApplicationContextAwareProcessor(BPP) 在 before-init 阶段回调；
 *          与 [[L09-04-Aware全家与注入时机]] 互链，本章侧重时机。</li>
 * </ul>
 *
 * <p>依赖章节 [[L02-02-Configuration与Bean注册全姿势]]（@Bean 的 initMethod/destroyMethod、容器自动注册 CommonAnnotationBPP 的前提）。🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l02.l02_06;
