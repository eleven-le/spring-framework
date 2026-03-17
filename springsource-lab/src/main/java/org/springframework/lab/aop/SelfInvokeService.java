package org.springframework.lab.aop;

import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;

/**
 * 自调用场景 — 演示 AOP 代理失效与 exposeProxy 解法
 *
 * <p>问题: this.internal() 走的是 target 的直接调用, 不经过代理, AOP 失效
 * <p>原因: JDK/CGLIB 代理的 invoke/intercept 只拦截「通过代理对象的外部调用」
 *
 * <p>解法:
 * <ol>
 *   <li>AopContext.currentProxy() — 需要 @EnableAspectJAutoProxy(exposeProxy=true)</li>
 *   <li>注入自身 — 依赖 Spring 循环依赖支持</li>
 *   <li>重构 — 拆到不同 Bean</li>
 * </ol>
 */
@Service
public class SelfInvokeService {

	public String outer() {
		System.out.println("  [SelfInvokeService] outer 被调用");
		// 直接 this 调用 — AOP 不会拦截 internal()
		return "outer → " + internal();
	}

	public String outerViaProxy() {
		System.out.println("  [SelfInvokeService] outerViaProxy 被调用");
		// 通过 AopContext 获取代理 — AOP 可以拦截 internal()
		SelfInvokeService proxy = (SelfInvokeService) AopContext.currentProxy();
		return "outerViaProxy → " + proxy.internal();
	}

	public String internal() {
		System.out.println("  [SelfInvokeService] internal 被调用");
		return "internal-result";
	}
}
