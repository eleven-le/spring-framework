package org.springframework.lab.aabpp;

import java.lang.reflect.Field;
import java.util.Collection;

import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 元数据审查器 — 反射查看 AABPP 的 injectionMetadataCache, 直观展示注入落点
 *
 * 这个类帮你理解 buildAutowiringMetadata 到底收集了哪些 InjectedElement
 */
public class MetadataInspector {

	@SuppressWarnings("unchecked")
	public static void inspect(AnnotationConfigApplicationContext ctx) {
		System.out.println("====== AABPP injectionMetadataCache 内容审查 ======\n");
		try {
			AutowiredAnnotationBeanPostProcessor aabpp =
					ctx.getBean(AutowiredAnnotationBeanPostProcessor.class);

			// 反射访问 private 缓存
			Field cacheField = AutowiredAnnotationBeanPostProcessor.class
					.getDeclaredField("injectionMetadataCache");
			cacheField.setAccessible(true);

			java.util.Map<String, InjectionMetadata> cache =
					(java.util.Map<String, InjectionMetadata>) cacheField.get(aabpp);

			for (java.util.Map.Entry<String, InjectionMetadata> entry : cache.entrySet()) {
				String beanName = entry.getKey();
				InjectionMetadata metadata = entry.getValue();

				// 反射获取 injectedElements
				Field elementsField = InjectionMetadata.class.getDeclaredField("injectedElements");
				elementsField.setAccessible(true);
				Collection<?> elements = (Collection<?>) elementsField.get(metadata);

				if (elements.isEmpty()) continue;

				System.out.println("Bean: " + beanName + " → " + elements.size() + " 个注入点:");
				for (Object element : elements) {
					// InjectedElement.member 字段
					Field memberField = findMemberField(element.getClass());
					if (memberField != null) {
						memberField.setAccessible(true);
						java.lang.reflect.Member member =
								(java.lang.reflect.Member) memberField.get(element);
						String type = element.getClass().getSimpleName();
						System.out.println("  [" + type + "] " + member);
					}
				}
				System.out.println();
			}
		}
		catch (Exception e) {
			System.out.println("反射审查失败: " + e.getMessage());
		}
	}

	private static Field findMemberField(Class<?> clazz) {
		while (clazz != null) {
			try {
				return clazz.getDeclaredField("member");
			}
			catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}
}
