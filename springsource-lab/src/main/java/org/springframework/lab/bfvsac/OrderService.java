package org.springframework.lab.bfvsac;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * 订单服务 —— 同时实现 BeanFactory 级 Aware 和 ApplicationContext 级 Aware
 *
 * <p>关键观察点：
 * <ul>
 *   <li>BeanFactory 级 Aware（BeanNameAware / BeanFactoryAware）：
 *       由 AbstractAutowireCapableBeanFactory#invokeAwareMethods 直接硬编码调用，
 *       <b>裸 BeanFactory 也能触发</b></li>
 *   <li>ApplicationContext 级 Aware（EnvironmentAware / ResourceLoaderAware /
 *       ApplicationEventPublisherAware / ApplicationContextAware）：
 *       由 ApplicationContextAwareProcessor（BPP）在 postProcessBeforeInitialization 中注入，
 *       <b>只有 ApplicationContext 才会注册这个 BPP → 裸 BeanFactory 不触发</b></li>
 * </ul>
 *
 * <p>断点抓手：
 * <ul>
 *   <li>AbstractAutowireCapableBeanFactory#invokeAwareMethods → 观察 BeanFactory 级回调</li>
 *   <li>ApplicationContextAwareProcessor#invokeAwareInterfaces → 观察 Context 级回调</li>
 * </ul>
 */
@Service
public class OrderService implements
		BeanNameAware,          // BeanFactory 级
		BeanFactoryAware,       // BeanFactory 级
		EnvironmentAware,       // Context 级
		ResourceLoaderAware,    // Context 级
		ApplicationEventPublisherAware, // Context 级
		ApplicationContextAware {       // Context 级

	// ---- BeanFactory 级 Aware 注入的字段 ----
	private String beanName;
	private BeanFactory beanFactory;

	// ---- ApplicationContext 级 Aware 注入的字段 ----
	private Environment environment;
	private ResourceLoader resourceLoader;
	private ApplicationEventPublisher eventPublisher;
	private ApplicationContext applicationContext;

	// ========== BeanFactory 级 Aware ==========

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
		System.out.println("  [BeanFactory级] BeanNameAware#setBeanName → " + name);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		System.out.println("  [BeanFactory级] BeanFactoryAware#setBeanFactory → " + beanFactory.getClass().getSimpleName());
	}

	// ========== ApplicationContext 级 Aware ==========

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
		System.out.println("  [Context级] EnvironmentAware#setEnvironment → " + environment.getClass().getSimpleName());
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		System.out.println("  [Context级] ResourceLoaderAware#setResourceLoader → " + resourceLoader.getClass().getSimpleName());
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.eventPublisher = publisher;
		System.out.println("  [Context级] ApplicationEventPublisherAware#setApplicationEventPublisher → " + publisher.getClass().getSimpleName());
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		this.applicationContext = ctx;
		System.out.println("  [Context级] ApplicationContextAware#setApplicationContext → " + ctx.getClass().getSimpleName());
	}

	// ========== 业务方法 ==========

	/** 模拟下单 —— 下单成功后发布事件 */
	public void placeOrder(String orderId) {
		System.out.println("\n  → OrderService#placeOrder: 订单 " + orderId + " 创建成功");
		if (eventPublisher != null) {
			eventPublisher.publishEvent(new PaymentSuccessEvent(this, orderId));
		} else {
			System.out.println("  ⚠ eventPublisher 为 null（裸 BeanFactory 没有注入事件发布器）");
		}
	}

	/** 模拟资源加载 —— 只有 ApplicationContext 才有 ResourceLoader */
	public void loadResource(String location) {
		if (resourceLoader != null) {
			Resource resource = resourceLoader.getResource(location);
			System.out.println("  → 资源加载成功: " + resource.getClass().getSimpleName() + " → " + resource.getDescription());
		} else {
			System.out.println("  ⚠ resourceLoader 为 null（裸 BeanFactory 不具备资源加载能力）");
		}
	}

	/** 打印所有 Aware 字段状态，用于对比 */
	public void printAwareStatus() {
		System.out.println("  ┌───────────────────────────────────────────────┐");
		System.out.println("  │          Aware 回调注入状态一览表              │");
		System.out.println("  ├────────────────────────┬────────────────────────┤");
		System.out.printf("  │ %-22s │ %-22s │%n", "BeanFactory级", beanName != null && beanFactory != null ? "全部注入" : "未注入");
		System.out.printf("  │   beanName             │ %-22s │%n", beanName != null ? beanName : "null");
		System.out.printf("  │   beanFactory           │ %-22s │%n", beanFactory != null ? beanFactory.getClass().getSimpleName() : "null");
		System.out.println("  ├────────────────────────┼────────────────────────┤");
		System.out.printf("  │ %-22s │ %-22s │%n", "Context级", applicationContext != null ? "全部注入" : "未注入");
		System.out.printf("  │   environment           │ %-22s │%n", environment != null ? environment.getClass().getSimpleName() : "null");
		System.out.printf("  │   resourceLoader        │ %-22s │%n", resourceLoader != null ? resourceLoader.getClass().getSimpleName() : "null");
		System.out.printf("  │   eventPublisher        │ %-22s │%n", eventPublisher != null ? eventPublisher.getClass().getSimpleName() : "null");
		System.out.printf("  │   applicationContext    │ %-22s │%n", applicationContext != null ? applicationContext.getClass().getSimpleName() : "null");
		System.out.println("  └────────────────────────┴────────────────────────┘");
	}
}
