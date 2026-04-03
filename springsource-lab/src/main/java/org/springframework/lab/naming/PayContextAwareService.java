package org.springframework.lab.naming;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 同时实现 Spring 内置 Aware + 自定义 Aware 的 Bean。
 * 展示两种 Aware 的注入时机差异：
 *
 * <pre>
 * 时序：
 *   1. BeanNameAware#setBeanName         → invokeAwareMethods 阶段（硬编码在 AbstractAutowireCapableBeanFactory）
 *   2. BeanFactoryAware#setBeanFactory   → invokeAwareMethods 阶段
 *   3. PayContextAware#setPayContext      → BPP.before 阶段（自定义 BPP 驱动）
 *   4. ApplicationContextAware#setAppCtx → BPP.before 阶段（ApplicationContextAwareProcessor 驱动）
 * </pre>
 */
@Component
public class PayContextAwareService implements BeanNameAware, ApplicationContextAware, PayContextAware {

	private String beanName;
	private ApplicationContext applicationContext;
	private PayContext payContext;

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
		System.out.println("    [Aware] ① BeanNameAware#setBeanName = " + name);
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx) {
		this.applicationContext = ctx;
		System.out.println("    [Aware] ② ApplicationContextAware#setApplicationContext");
	}

	@Override
	public void setPayContext(PayContext payContext) {
		this.payContext = payContext;
		System.out.println("    [Aware] ③ PayContextAware#setPayContext = " + payContext);
	}

	public void showInjectedCapabilities() {
		System.out.println("  === Aware 注入结果 ===");
		System.out.println("  beanName:    " + beanName);
		System.out.println("  appContext:  " + (applicationContext != null ? "已注入" : "未注入"));
		System.out.println("  payContext:  " + payContext);
		System.out.println("  结论: Aware = 声明式依赖注入的回调契约，\"我需要什么\"容器就给什么");
	}
}
