package org.springframework.lab.autowire;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 手动构造 DependencyDescriptor, 观察其内部结构和解析过程
 *
 * 断点建议: DefaultListableBeanFactory#doResolveDependency 第1354行
 *   → 观察 findAutowireCandidates 返回的 matchingBeans
 *   → 观察 determineAutowireCandidate 的 @Primary/@Priority/字段名降级
 */
public class DependencyDescriptorInspector {

	/** 模拟一个无 @Qualifier 的字段注入点 */
	private PayChannel someChannel;

	public static void inspect(AnnotationConfigApplicationContext ctx) {
		DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();

		try {
			Field field = DependencyDescriptorInspector.class.getDeclaredField("someChannel");
			DependencyDescriptor dd = new DependencyDescriptor(field, true);

			System.out.println("--- DependencyDescriptor 内部结构 ---");
			System.out.println("  dependencyType  = " + dd.getDependencyType().getSimpleName());
			System.out.println("  dependencyName  = " + dd.getDependencyName());
			System.out.println("  required        = " + dd.isRequired());
			System.out.println("  eager           = " + dd.isEager());
			System.out.println("  resolvableType  = " + dd.getResolvableType());
			System.out.println("  annotations     = " + Arrays.toString(dd.getAnnotations()));

			// 手动触发解析, 观察解析结果
			Set<String> autowiredBeanNames = new LinkedHashSet<>();
			Object resolved = bf.resolveDependency(dd, null, autowiredBeanNames, null);
			System.out.println("  resolved bean   = " + resolved);
			System.out.println("  resolved names  = " + autowiredBeanNames);
			System.out.println("  → 无@Qualifier时, @Primary(WechatChannel) 胜出");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
