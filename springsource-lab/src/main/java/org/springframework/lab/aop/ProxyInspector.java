package org.springframework.lab.aop;

import java.util.Arrays;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

/**
 * 代理内省工具 — 打印代理类型、目标类、Advisor 链
 *
 * <p>核心工具方法:
 * <ul>
 *   <li>AopUtils.isAopProxy() — 判断是否为 AOP 代理</li>
 *   <li>AopUtils.isJdkDynamicProxy() — JDK 动态代理</li>
 *   <li>AopUtils.isCglibProxy() — CGLIB 子类代理</li>
 *   <li>Advised — 每个 AOP 代理都实现此接口, 可获取 Advisor 数组</li>
 * </ul>
 */
public final class ProxyInspector {

	private ProxyInspector() {}

	public static void inspect(String label, Object bean) {
		System.out.println("\n  ── " + label + " 代理内省 ──");
		System.out.println("  Bean 类型: " + bean.getClass().getName());
		System.out.println("  是 AOP 代理: " + AopUtils.isAopProxy(bean));
		System.out.println("  JDK 动态代理: " + AopUtils.isJdkDynamicProxy(bean));
		System.out.println("  CGLIB 代理: " + AopUtils.isCglibProxy(bean));

		if (bean instanceof Advised) {
			Advised advised = (Advised) bean;
			System.out.println("  目标类: " + advised.getTargetClass());
			System.out.println("  proxyTargetClass: " + advised.isProxyTargetClass());
			System.out.println("  接口: " + Arrays.toString(advised.getProxiedInterfaces()));
			System.out.println("  Advisor 链 (" + advised.getAdvisors().length + " 个):");
			for (int i = 0; i < advised.getAdvisors().length; i++) {
				System.out.println("    [" + i + "] " + advised.getAdvisors()[i]);
			}
		}
		System.out.println();
	}
}
