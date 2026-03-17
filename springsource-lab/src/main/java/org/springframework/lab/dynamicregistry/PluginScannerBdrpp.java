package org.springframework.lab.dynamicregistry;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * 场景1: BDRPP 独立动态注册.
 *
 * 职责: 模拟"插件扫描器", 在定义阶段注册 PluginBean.
 * 链式反应: 同时注册一个 SecondaryBdrpp (它是另一个 BDRPP),
 *          验证 PostProcessorRegistrationDelegate 的 while(reiterate) 循环
 *          能在下一轮扫描中发现并执行它.
 *
 * 源码路径:
 *   PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors
 *     → Phase 3: while (reiterate) { ... }
 *     → 每轮 getBeanNamesForType(BDRPP.class), 发现新注册的 BDRPP 就再跑一轮
 */
public class PluginScannerBdrpp implements BeanDefinitionRegistryPostProcessor {

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("[TIMING] PluginScannerBdrpp.postProcessBeanDefinitionRegistry()");

		// 1. 注册业务 Bean
		RootBeanDefinition pluginBd = new RootBeanDefinition(PluginBean.class);
		pluginBd.getConstructorArgumentValues().addIndexedArgumentValue(0, "risk-engine-plugin");
		registry.registerBeanDefinition("pluginBean", pluginBd);

		// 2. 链式反应: 注册另一个 BDRPP → 触发 while(reiterate) 再跑一轮
		if (!registry.containsBeanDefinition("secondaryBdrpp")) {
			registry.registerBeanDefinition("secondaryBdrpp", new RootBeanDefinition(SecondaryBdrpp.class));
			System.out.println("[TIMING] PluginScannerBdrpp registered SecondaryBdrpp (chain reaction!)");
		}
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// BDRPP 同时也是 BFPP, 这个回调在所有 BDRPP 执行完之后才触发
		System.out.println("[TIMING] PluginScannerBdrpp.postProcessBeanFactory() — runs AFTER all BDRPP done");
	}
}
