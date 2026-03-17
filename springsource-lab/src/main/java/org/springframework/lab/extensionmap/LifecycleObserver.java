package org.springframework.lab.extensionmap;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.PriorityOrdered;

/**
 * 实例期全景观察者 —— 同时实现 IABPP + MergedBDPP + BPP(IABPP 继承自 BPP)
 * <p>
 * PriorityOrdered(HIGHEST_PRECEDENCE) 保证在内置 BPP 之前执行,
 * 从而在时间线上清晰展示: Observer.before → 内置Aware/PostConstruct → Observer.after
 * <p>
 * 仅观察 targetBean, 过滤掉 Spring 内部 Bean 的噪音
 */
public class LifecycleObserver implements InstantiationAwareBeanPostProcessor,
		MergedBeanDefinitionPostProcessor, PriorityOrdered {

	private boolean isTarget(String beanName) {
		return "targetBean".equals(beanName);
	}

	// ===== 1. IABPP: 实例化前 (可短路, 返回非 null 则跳过 doCreateBean) =====
	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
		if (isTarget(beanName)) {
			TimelineTracker.record("实例",
					"IABPP.postProcessBeforeInstantiation",
					beanName + " (返回 null=放行, 非 null=短路 createBean)");
		}
		return null;
	}

	// ===== 2. MergedBDPP: 合并 BD 后回调 (构造器之后, populateBean 之前) =====
	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition bd, Class<?> beanType, String beanName) {
		if (isTarget(beanName)) {
			TimelineTracker.record("实例",
					"MergedBDPP.postProcessMergedBeanDefinition",
					beanName + " (缓存 @Autowired/@Value 注入元数据)");
		}
	}

	// ===== 3. IABPP: 实例化后 (返回 false 则跳过属性注入) =====
	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) {
		if (isTarget(beanName)) {
			TimelineTracker.record("实例",
					"IABPP.postProcessAfterInstantiation",
					beanName + " (返回 false=跳过属性注入)");
		}
		return true;
	}

	// ===== 4. IABPP: 属性注入 (@Autowired/@Value 在此由 AutowiredAnnotationBPP 处理) =====
	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		if (isTarget(beanName)) {
			TimelineTracker.record("实例",
					"IABPP.postProcessProperties",
					beanName + " (@Autowired/@Value 注入在此发生)");
		}
		return pvs;
	}

	// ===== 5. BPP: 初始化前 (后续内置 BPP 触发 Aware + @PostConstruct) =====
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (isTarget(beanName)) {
			TimelineTracker.record("实例",
					"BPP.postProcessBeforeInitialization",
					beanName + " (Observer 最先; 后续 BPP 触发 Aware/@PostConstruct)");
		}
		return bean;
	}

	// ===== 6. BPP: 初始化后 (后续 AutoProxyCreator 在此创建 AOP 代理) =====
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (isTarget(beanName)) {
			TimelineTracker.record("实例",
					"BPP.postProcessAfterInitialization",
					beanName + " (Observer 最先; 后续 AutoProxyCreator 创建代理)");
		}
		return bean;
	}

	@Override
	public void resetBeanDefinition(String beanName) {
		// MergedBDPP 接口方法, 合并 BD 失效时回调, 通常不需要处理
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}
}
