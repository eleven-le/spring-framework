package org.springframework.lab.advisorchain;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

import java.lang.reflect.Method;

/**
 * 动态 Pointcut 示例 —— 仅当 placeOrder 的 amount > 5000 时匹配
 *
 * 核心知识点：
 *  - 静态匹配 (matches(Method, Class)): 代理创建时执行一次，过滤掉明显不匹配的方法
 *  - isRuntime() = true: 告诉框架需要动态匹配
 *  - 动态匹配 (matches(Method, Class, Object...)): 每次调用都执行，可以看到实参
 *
 * 框架处理逻辑（DefaultAdvisorChainFactory）：
 *  - 静态匹配通过 + isRuntime() = true → 包装成 InterceptorAndDynamicMethodMatcher
 *  - 运行时 ReflectiveMethodInvocation#proceed() 遇到该包装类时，
 *    先调 matcher.matches(method, class, args)，匹配才执行 interceptor
 *
 * 断点位置：
 *  - DefaultAdvisorChainFactory:84 → 看 isRuntime 分支
 *  - ReflectiveMethodInvocation:163 → 看 InterceptorAndDynamicMethodMatcher 判定
 */
public class LargeAmountPointcut implements Pointcut {

	@Override
	public ClassFilter getClassFilter() {
		return ClassFilter.TRUE;  // 所有类都匹配
	}

	@Override
	public MethodMatcher getMethodMatcher() {
		return new MethodMatcher() {

			/** 静态匹配：只对 placeOrder 方法感兴趣 */
			@Override
			public boolean matches(Method method, Class<?> targetClass) {
				return "placeOrder".equals(method.getName());
			}

			/** 标记为运行时匹配 */
			@Override
			public boolean isRuntime() {
				return true;
			}

			/** 动态匹配：金额 > 5000 才真正拦截 */
			@Override
			public boolean matches(Method method, Class<?> targetClass, Object... args) {
				if (args.length >= 2 && args[1] instanceof Integer) {
					int amount = (Integer) args[1];
					boolean matched = amount > 5000;
					System.out.println("  [DynamicMatcher] amount=" + amount
							+ " → " + (matched ? "匹配" : "跳过"));
					return matched;
				}
				return false;
			}
		};
	}
}
