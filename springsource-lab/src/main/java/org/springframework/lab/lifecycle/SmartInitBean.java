package org.springframework.lab.lifecycle;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 实验 3: SmartInitializingSingleton — 所有单例初始化完毕后的全局回调。
 *
 * 触发点: DefaultListableBeanFactory#preInstantiateSingletons 第二轮循环 (line 957-971)
 *
 * 关键区别:
 *   afterPropertiesSet / @PostConstruct → 当前 Bean 初始化完就调, 其他 Bean 可能还没创建
 *   afterSingletonsInstantiated         → 所有非懒加载单例都已创建完毕后才调
 *
 * C端场景: 启动后预热缓存 / 校验所有 Handler 是否注册完整 / 建立全局索引
 */
@Component
public class SmartInitBean implements SmartInitializingSingleton {

	@Autowired
	private ApplicationContext ctx;

	@Override
	public void afterSingletonsInstantiated() {
		int count = ctx.getBeanDefinitionCount();
		System.out.println("[SmartInitBean]  ★ afterSingletonsInstantiated → 全部单例就绪, 共 "
				+ count + " 个 BeanDefinition");
		System.out.println("                   场景: 缓存预热 / Handler 完整性校验 / 全局索引构建");
	}
}
