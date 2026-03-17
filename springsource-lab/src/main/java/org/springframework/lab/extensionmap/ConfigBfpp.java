package org.springframework.lab.extensionmap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 定义期 —— BeanFactoryPostProcessor
 * <p>
 * 业务场景: 配置中心覆盖 / 加密配置解密 / 修改 BD 属性
 * 时机: 在 BDRPP 之后、Bean 实例化之前
 * 限制: 只能修改已有 BD, 不能注册新 BD (没有 Registry 参数)
 */
public class ConfigBfpp implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory bf) throws BeansException {
		int count = bf.getBeanDefinitionCount();
		TimelineTracker.record("定义",
				"BFPP.postProcessBeanFactory",
				"可见 " + count + " 个 BD, 可修改 scope/lazy/属性值, 不可注册新 BD");
	}
}
