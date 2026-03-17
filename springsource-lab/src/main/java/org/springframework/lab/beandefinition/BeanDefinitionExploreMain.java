package org.springframework.lab.beandefinition;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W04 - BeanDefinition 体系与合并策略 调试入口
 *

 学习建议

 1. 先运行 BeanDefinitionExploreMain 的实验 1，观察 @Component 注册的 BD 类型全部是 ScannedGenericBeanDefinition
 2. 在 getMergedBeanDefinition:1386 打断点，运行实验 2 观察 parent-child 合并过程
 3. 重点关注 overrideFrom:298 的覆盖规则——特别是 propertyValues 是追加而非替换
 4. 合上笔记口述 7 句话

 * <p>断点位置：
 * <ul>
 *   <li>AbstractBeanFactory#getMergedLocalBeanDefinition (第 1353 行) — 合并入口
 *   <li>AbstractBeanFactory#getMergedBeanDefinition(name, bd, containingBd) (第 1386 行) — 合并核心
 *   <li>AbstractBeanDefinition#overrideFrom (第 298 行) — 子覆盖父的具体逻辑
 * </ul>
 * <p>实验目的：观察 GenericBeanDefinition 如何在 getMergedBeanDefinition 中变成 RootBeanDefinition
 */
public class BeanDefinitionExploreMain {

	public static void main(String[] args) {
		// === 实验 1：注解驱动 — 观察 @Component 注册的 BD 类型 ===
		System.out.println("=== 实验 1: 注解驱动 BeanDefinition ===");
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(BdLabConfig.class);

		// 打印每个 BD 的实际类型
		for (String name : ctx.getBeanDefinitionNames()) {
			BeanDefinition bd = ctx.getBeanDefinition(name);
			System.out.printf("  %-40s → %s%n", name, bd.getClass().getSimpleName());
		}
		ctx.close();

		// === 实验 2：手动注册 parent-child 合并 ===
		System.out.println("\n=== 实验 2: Parent-Child 合并 ===");
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

		// 注册 parent（抽象定义，不会实例化）
		RootBeanDefinition parentDef = new RootBeanDefinition();
		parentDef.setBeanClassName("org.springframework.lab.beandefinition.OrderService");
		parentDef.setScope(BeanDefinition.SCOPE_SINGLETON);
		parentDef.setAbstract(true);
		parentDef.setLazyInit(true);
		factory.registerBeanDefinition("parentOrder", parentDef);

		// 注册 child（继承 parent，覆盖 scope）
		GenericBeanDefinition childDef = new GenericBeanDefinition();
		childDef.setParentName("parentOrder");
		// 不设 beanClassName → 从 parent 继承
		childDef.setScope(BeanDefinition.SCOPE_PROTOTYPE);  // 覆盖 parent 的 singleton
		childDef.setAbstract(false);
		factory.registerBeanDefinition("childOrder", childDef);

		// 触发合并 — 在此打断点观察 getMergedBeanDefinition 的合并过程
		BeanDefinition merged = factory.getMergedBeanDefinition("childOrder");
		System.out.printf("  merged class  : %s%n", merged.getClass().getSimpleName());
		System.out.printf("  beanClassName : %s%n", merged.getBeanClassName());
		System.out.printf("  scope         : %s%n", merged.getScope());
		System.out.printf("  isAbstract    : %s%n", merged.isAbstract());
		System.out.printf("  lazyInit      : %s%n", merged.isLazyInit());
	}
}

