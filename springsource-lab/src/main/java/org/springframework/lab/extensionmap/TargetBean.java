package org.springframework.lab.extensionmap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 全生命周期目标 Bean —— 实现全部 Aware + 初始化三连 + 销毁三连
 * 用于观察实例期各扩展点的精确调用顺序
 */
public class TargetBean implements BeanNameAware, BeanFactoryAware,
		ApplicationContextAware, InitializingBean, DisposableBean {

	private String config = "default";

	public TargetBean() {
		TimelineTracker.record("实例", "TargetBean.<init> 构造器", "new 出裸对象, 尚未注入属性");
	}

	public void setConfig(String config) { this.config = config; }
	public String getConfig() { return config; }

	// ============ Aware 回调 (initializeBean -> invokeAwareMethods) ============

	@Override
	public void setBeanName(String name) {
		TimelineTracker.record("实例", "BeanNameAware.setBeanName", "beanName=" + name);
	}

	@Override
	public void setBeanFactory(BeanFactory bf) throws BeansException {
		TimelineTracker.record("实例", "BeanFactoryAware.setBeanFactory", "BeanFactory 级 Aware (invokeAwareMethods)");
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		TimelineTracker.record("实例", "ApplicationContextAware.setApplicationContext",
				"Context 级 Aware (由 ApplicationContextAwareProcessor BPP 触发)");
	}

	// ============ 初始化三连 ============

	@PostConstruct
	public void postConstruct() {
		TimelineTracker.record("实例", "@PostConstruct",
				"注解驱动初始化 (由 CommonAnnotationBPP.before 触发)");
	}

	@Override
	public void afterPropertiesSet() {
		TimelineTracker.record("实例", "InitializingBean.afterPropertiesSet", "接口驱动初始化");
	}

	public void customInit() {
		TimelineTracker.record("实例", "init-method (customInit)", "@Bean(initMethod) 指定的初始化");
	}

	// ============ 销毁三连 ============

	@PreDestroy
	public void preDestroy() {
		TimelineTracker.record("销毁", "@PreDestroy", "注解驱动销毁");
	}

	@Override
	public void destroy() {
		TimelineTracker.record("销毁", "DisposableBean.destroy", "接口驱动销毁");
	}

	public void customDestroy() {
		TimelineTracker.record("销毁", "destroy-method (customDestroy)", "@Bean(destroyMethod) 指定的销毁");
	}

	// ============ 业务方法 ============

	public String process(String input) {
		return "processed: " + input + " [config=" + config + "]";
	}
}
