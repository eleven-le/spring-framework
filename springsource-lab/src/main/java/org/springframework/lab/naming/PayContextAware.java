package org.springframework.lab.naming;

import org.springframework.beans.factory.Aware;

/**
 * 【Aware 后缀】回调契约：容器给你注入能力。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>ApplicationContextAware — 注入 ApplicationContext</li>
 *   <li>BeanFactoryAware — 注入 BeanFactory</li>
 *   <li>BeanNameAware — 注入 Bean 名称</li>
 *   <li>EnvironmentAware — 注入 Environment</li>
 * </ul>
 *
 * <p>命名规则：Aware 后缀 = "我需要某个基础设施，请容器在初始化时回调注入"
 *
 * <p>Aware 的本质：控制反转的"通知机制"。
 * 你不用 getBean() 去拿，而是声明"我需要"，容器会在合适的时机主动给你。
 *
 * <p>注入时机（重要）：
 * BeanNameAware/BeanFactoryAware → AbstractAutowireCapableBeanFactory#invokeAwareMethods（属性注入之后）
 * ApplicationContextAware/EnvironmentAware → ApplicationContextAwareProcessor#postProcessBeforeInitialization（BPP 阶段）
 *
 * <p>自定义 Aware 需要自己写一个 BPP 来驱动回调（Spring 不会自动识别你的自定义 Aware）。
 *
 * <pre>
 * 断点：ApplicationContextAwareProcessor#postProcessBeforeInitialization
 *   → 观察 6 种 Aware 的回调注入顺序
 * </pre>
 */
public interface PayContextAware extends Aware {

	/**
	 * 容器（通过自定义 BPP）在 Bean 初始化时调用此方法，注入 PayContext。
	 * 对照 ApplicationContextAware#setApplicationContext。
	 */
	void setPayContext(PayContext payContext);
}
