package org.springframework.lab.lifecycle;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

/**
 * 实验 1: 实现所有生命周期接口的 Bean — 验证回调的精确执行顺序。
 *
 * 实现接口清单:
 *   BeanNameAware / BeanClassLoaderAware / BeanFactoryAware        → invokeAwareMethods (BeanFactory 级)
 *   EnvironmentAware / ResourceLoaderAware / ApplicationEventPublisherAware
 *     / MessageSourceAware / ApplicationContextAware                → ApplicationContextAwareProcessor (Context 级)
 *   InitializingBean / DisposableBean                               → initializeBean / destroy
 *
 * 回调方式三重对照:
 *   @PostConstruct / afterPropertiesSet / customInit
 *   @PreDestroy    / DisposableBean.destroy / customDestroy
 */
public class FullLifecycleBean implements
		// --- BeanFactory 级 Aware (invokeAwareMethods 内直接调用) ---
		BeanNameAware, BeanClassLoaderAware, BeanFactoryAware,
		// --- ApplicationContext 级 Aware (ApplicationContextAwareProcessor#postProcessBeforeInitialization) ---
		EnvironmentAware, ResourceLoaderAware, ApplicationEventPublisherAware,
		MessageSourceAware, ApplicationContextAware,
		// --- 初始化 & 销毁 ---
		InitializingBean, DisposableBean {

	private static int step = 0;

	private static String s() {
		return String.format("[%02d]", ++step);
	}

	// ============================== 构造 ==============================

	public FullLifecycleBean() {
		System.out.println(s() + " FullLifecycleBean 构造器 (createBeanInstance)");
	}

	// ==================== BeanFactory 级 Aware ====================
	// 触发点: AbstractAutowireCapableBeanFactory#invokeAwareMethods

	@Override
	public void setBeanName(String name) {
		System.out.println(s() + " BeanNameAware.setBeanName = " + name);
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		System.out.println(s() + " BeanClassLoaderAware.setBeanClassLoader");
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		System.out.println(s() + " BeanFactoryAware.setBeanFactory");
	}

	// ==================== ApplicationContext 级 Aware ====================
	// 触发点: ApplicationContextAwareProcessor#postProcessBeforeInitialization
	// 注意: 在 BPP.before 阶段执行, 顺序由 ApplicationContextAwareProcessor 内部固定

	@Override
	public void setEnvironment(Environment environment) {
		System.out.println(s() + " EnvironmentAware.setEnvironment");
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		System.out.println(s() + " ResourceLoaderAware.setResourceLoader");
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		System.out.println(s() + " ApplicationEventPublisherAware.setApplicationEventPublisher");
	}

	@Override
	public void setMessageSource(MessageSource messageSource) {
		System.out.println(s() + " MessageSourceAware.setMessageSource");
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		System.out.println(s() + " ApplicationContextAware.setApplicationContext");
	}

	// ==================== 初始化三连 ====================

	@PostConstruct
	public void postConstruct() {
		System.out.println(s() + " @PostConstruct (CommonAnnotationBPP → BPP.before 阶段)");
	}

	@Override
	public void afterPropertiesSet() {
		System.out.println(s() + " InitializingBean.afterPropertiesSet");
	}

	public void customInit() {
		System.out.println(s() + " @Bean(initMethod=\"customInit\")");
	}

	// ==================== 销毁三连 ====================

	@PreDestroy
	public void preDestroy() {
		System.out.println(s() + " @PreDestroy (CommonAnnotationBPP → DestructionAwareBPP)");
	}

	@Override
	public void destroy() {
		System.out.println(s() + " DisposableBean.destroy");
	}

	public void customDestroy() {
		System.out.println(s() + " @Bean(destroyMethod=\"customDestroy\")");
	}

	// ==================== 业务方法 ====================

	public String sayHello() {
		return "FullLifecycleBean is alive!";
	}

	public static void resetStep() {
		step = 0;
	}
}
