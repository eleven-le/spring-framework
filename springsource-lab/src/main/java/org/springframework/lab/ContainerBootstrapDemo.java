package org.springframework.lab;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Spring 容器启动示例 - 第一个练兵场示例
 *
 * <p>演示如何使用 AnnotationConfigApplicationContext 启动 Spring 容器
 */
public class ContainerBootstrapDemo {

	public static void main(String[] args) {
		// 1. 创建并启动 Spring 容器
		System.out.println("=== 启动 Spring 容器 ===");
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LabConfig.class);

		// 2. 从容器中获取 Bean
		System.out.println("=== 获取 Bean ===");
		HelloService helloService = context.getBean(HelloService.class);

		// 3. 使用 Bean
		String result = helloService.sayHello("Spring");
		System.out.println(result);

		// 4. 查看容器中注册的 Bean
		System.out.println("=== 容器中注册的 Bean ===");
		String[] beanNames = context.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			System.out.println(" - " + beanName);
		}

		// 5. 关闭容器
		System.out.println("=== 关闭 Spring 容器 ===");
		context.close();
	}
}
