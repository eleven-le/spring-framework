package org.springframework.lab.createbean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 场景 2: 自定义 BPP — 观察 initializeBean 中 before/after 回调的触发时机。
 *
 * 断点: postProcessBeforeInitialization 和 postProcessAfterInitialization
 * 对照源码: AbstractAutowireCapableBeanFactory#initializeBean:1796 / :1808
 *
 * C端映射: 类似 "全链路方法耗时采集 / Trace 注入 / 灰度标记注入"。
 */
@Component
public class LifecycleTrackerBpp implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (isLabBean(beanName)) {
			System.out.println("[TrackerBPP]     >>> beforeInit:  " + beanName
					+ " (" + bean.getClass().getSimpleName() + ")");
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (isLabBean(beanName)) {
			System.out.println("[TrackerBPP]     <<< afterInit:   " + beanName
					+ " (" + bean.getClass().getSimpleName() + ")  ⑦ BPP.after");
		}
		return bean;
	}

	private boolean isLabBean(String name) {
		return "orderService".equals(name) || "payService".equals(name);
	}
}
