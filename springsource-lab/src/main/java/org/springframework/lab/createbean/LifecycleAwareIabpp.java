package org.springframework.lab.createbean;

import java.beans.PropertyDescriptor;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 场景 3: InstantiationAwareBeanPostProcessor — 观察实例化前后 + 属性注入前的 5 个回调。
 *
 * 回调顺序:
 *   1. postProcessBeforeInstantiation  → resolveBeforeInstantiation (createBean 内, 可短路)
 *   2. postProcessAfterInstantiation   → populateBean 开头 (返回 false 可跳过注入)
 *   3. postProcessProperties           → populateBean 中段 (@Autowired/@Value 在这里介入)
 *
 * 断点:
 *   - AbstractAutowireCapableBeanFactory#resolveBeforeInstantiation:1128
 *   - AbstractAutowireCapableBeanFactory#populateBean:1398
 *
 * C端映射: "动态数据源路由注入" / "灰度属性覆盖" / "敏感字段脱敏拦截"
 */
@Component
public class LifecycleAwareIabpp implements InstantiationAwareBeanPostProcessor {

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		if (isLabBean(beanName)) {
			System.out.println("[IABPP]          ◆ beforeInstantiation: " + beanName
					+ " → 返回 null, 走正常创建流程");
		}
		// 返回 null → 走正常 doCreateBean; 返回非 null → 短路, 跳过 doCreateBean
		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		if (isLabBean(beanName)) {
			System.out.println("[IABPP]          ◆ afterInstantiation:  " + beanName
					+ " → 返回 true, 继续属性注入");
		}
		// 返回 true → 继续 populateBean; 返回 false → 跳过所有属性注入
		return true;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
			throws BeansException {
		if (isLabBean(beanName)) {
			System.out.println("[IABPP]          ◆ postProcessProperties: " + beanName
					+ " → @Autowired/@Value 注入在此介入");
		}
		return pvs;
	}

	private boolean isLabBean(String name) {
		return "orderService".equals(name) || "payService".equals(name);
	}
}
