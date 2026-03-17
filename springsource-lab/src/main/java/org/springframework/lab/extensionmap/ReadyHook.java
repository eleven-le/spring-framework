package org.springframework.lab.extensionmap;

import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 实例期尾声 —— SmartInitializingSingleton
 * <p>
 * 时机: 所有非懒加载单例 Bean 创建完毕后, 在 finishBeanFactoryInitialization 末尾触发
 * 业务场景: 缓存预热 / 连接池预建 / 服务注册 / 全局一致性校验
 */
public class ReadyHook implements SmartInitializingSingleton {

	@Override
	public void afterSingletonsInstantiated() {
		TimelineTracker.record("实例",
				"SmartInitializingSingleton.afterSingletonsInstantiated",
				"全部单例就绪 (适合缓存预热/服务注册)");
	}
}
