package org.springframework.lab.advisorchain;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.DefaultAdvisorChainFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.adapter.DefaultAdvisorAdapterRegistry;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ===================================================================
 *  W16 · Advisor/Pointcut/Interceptor：拦截链组合模型
 * ===================================================================
 *
 * 一句话抽象：
 *   把"声明/匹配"与"执行/调度"拆开 ——
 *   装配期 Advisor 经 Pointcut 过滤 + AdapterRegistry 适配，生成 MethodInterceptor 链；
 *   运行期 ReflectiveMethodInvocation#proceed() 只做洋葱调度，不关心链怎么来的。
 *
 * 本 Demo 共 6 个实验：
 *   实验1: 四种 Advice 类型 → 统一适配成 MethodInterceptor
 *   实验2: Pointcut 精确匹配 → 不同方法走不同拦截链
 *   实验3: 拦截器排序 → @Order/Ordered 决定语义先后
 *   实验4: 手动组装链 → DefaultAdvisorChainFactory + DefaultAdvisorAdapterRegistry
 *   实验5: 短路拦截器 → 拦截器不调 proceed() 直接返回
 *   实验6: 动态 MethodMatcher → isRuntime() = true, 按参数决定是否拦截
 *
 * 核心调用链（10 步）：
 *   1. JdkDynamicAopProxy#invoke          : 代理入口，拦截方法调用
 *   2. AdvisedSupport#getInterceptors...  : 按 method 取链（含缓存）
 *   3. DefaultAdvisorChainFactory#get...  : 遍历 Advisor 列表
 *   4. PointcutAdvisor#getPointcut        : 拿到 Pointcut
 *   5. ClassFilter#matches                : 类级别过滤
 *   6. MethodMatcher#matches(method,class): 静态方法匹配
 *   7. AdvisorAdapterRegistry#getInterceptors : Advice → MethodInterceptor
 *   8. new ReflectiveMethodInvocation     : 封装 chain + target + method
 *   9. proceed()                          : 洋葱调度，currentIndex++
 *  10. invokeJoinpoint()                  : 链末尾，反射调目标方法
 *
 * 断点抓手（5 个）：
 *   ① DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice  → 看链怎么组装
 *   ② DefaultAdvisorAdapterRegistry#getInterceptors                          → 看 Advice 如何变成 Interceptor
 *   ③ ReflectiveMethodInvocation#proceed                                     → 看洋葱调度
 *   ④ MethodBeforeAdviceInterceptor#invoke                                   → 看 before 适配后的执行
 *   ⑤ AdvisedSupport#getInterceptorsAndDynamicInterceptionAdvice             → 看缓存命中
 */
public class AdvisorChainMain {

	public static void main(String[] args) throws Exception {
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println(" W16 · Advisor/Pointcut/Interceptor 拦截链组合模型 Demo");
		System.out.println("═══════════════════════════════════════════════════════\n");

		exp1_fourAdviceTypes();
		exp2_pointcutFiltering();
		exp3_interceptorOrdering();
		exp4_manualChainAssembly();
		exp5_shortCircuit();
		exp6_dynamicMethodMatcher();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验1: 四种 Advice 类型 → 统一适配成 MethodInterceptor
	// 要点: Spring 对 MethodBeforeAdvice / AfterReturningAdvice / ThrowsAdvice
	//       都通过 AdvisorAdapter 适配成 MethodInterceptor，
	//       运行时只有一种执行模型: interceptor.invoke(invocation)
	// ─────────────────────────────────────────────────────────────
	static void exp1_fourAdviceTypes() {
		System.out.println("【实验1】四种 Advice 类型 → 统一适配成 MethodInterceptor");
		System.out.println("─────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new TradeServiceImpl());
		pf.addInterface(TradeService.class);

		// (a) MethodBeforeAdvice —— 前置通知
		pf.addAdvice(new MethodBeforeAdvice() {
			@Override
			public void before(Method method, Object[] args, Object target) {
				System.out.println("  [BEFORE] 幂等校验 → method=" + method.getName());
			}
		});

		// (b) AfterReturningAdvice —— 返回后通知
		pf.addAdvice(new AfterReturningAdvice() {
			@Override
			public void afterReturning(Object returnValue, Method method, Object[] args, Object target) {
				System.out.println("  [AFTER_RETURNING] 审计日志 → result=" + returnValue);
			}
		});

		// (c) ThrowsAdvice —— 异常通知（tag 接口，靠方法签名反射匹配）
		pf.addAdvice(new ThrowsAdvice() {
			public void afterThrowing(Method method, Object[] args, Object target,
									  IllegalArgumentException ex) {
				System.out.println("  [THROWS] 异常告警 → " + ex.getMessage());
			}
		});

		// (d) MethodInterceptor —— 环绕通知（AOP Alliance 原生，无需适配）
		pf.addAdvice((MethodInterceptor) invocation -> {
			long start = System.nanoTime();
			try {
				Object result = invocation.proceed();
				return result;
			} finally {
				long cost = (System.nanoTime() - start) / 1_000_000;
				System.out.println("  [AROUND] 耗时统计 → " + invocation.getMethod().getName()
						+ " cost=" + cost + "ms");
			}
		});

		TradeService proxy = (TradeService) pf.getProxy();

		// 正常调用 → before + around + afterReturning
		System.out.println("\n  >> placeOrder(正常):");
		String orderId = proxy.placeOrder("U001", 100);
		System.out.println("  >> 结果: " + orderId);

		// 异常调用 → before + around + throws
		System.out.println("\n  >> placeOrder(异常):");
		try {
			proxy.placeOrder("U002", -1);
		}
		catch (Exception e) {
			System.out.println("  >> 捕获异常: " + e.getMessage());
		}

		// 打印 Advisor 链
		System.out.println("\n  >> Advisor 链:");
		for (Advisor advisor : pf.getAdvisors()) {
			System.out.println("     " + advisor.getClass().getSimpleName()
					+ " → advice=" + advisor.getAdvice().getClass().getSimpleName());
		}
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验2: Pointcut 精确匹配 → 不同方法走不同拦截链
	// 要点: Pointcut = ClassFilter + MethodMatcher
	//       NameMatchMethodPointcut 只匹配指定方法名
	//       不同方法的 interceptor chain 长度不同（验证缓存分离）
	// ─────────────────────────────────────────────────────────────
	static void exp2_pointcutFiltering() {
		System.out.println("【实验2】Pointcut 精确匹配 → 不同方法走不同拦截链");
		System.out.println("─────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new TradeServiceImpl());
		pf.addInterface(TradeService.class);

		// Advisor-1: 只拦截 placeOrder
		NameMatchMethodPointcut orderPointcut = new NameMatchMethodPointcut();
		orderPointcut.setMappedName("placeOrder");
		pf.addAdvisor(new DefaultPointcutAdvisor(orderPointcut, new MethodBeforeAdvice() {
			@Override
			public void before(Method method, Object[] args, Object target) {
				System.out.println("  [风控] 仅 placeOrder 触发 → userId=" + args[0]);
			}
		}));

		// Advisor-2: 只拦截 refund
		NameMatchMethodPointcut refundPointcut = new NameMatchMethodPointcut();
		refundPointcut.setMappedName("refund");
		pf.addAdvisor(new DefaultPointcutAdvisor(refundPointcut, (MethodInterceptor) invocation -> {
			System.out.println("  [退款审批] 仅 refund 触发 → orderId=" + invocation.getArguments()[0]);
			return invocation.proceed();
		}));

		// Advisor-3: 通配 —— 所有方法都拦截
		pf.addAdvice((MethodInterceptor) invocation -> {
			System.out.println("  [全局日志] 所有方法 → " + invocation.getMethod().getName());
			return invocation.proceed();
		});

		TradeService proxy = (TradeService) pf.getProxy();

		System.out.println("\n  >> placeOrder (应触发: 风控 + 全局日志):");
		proxy.placeOrder("U001", 50);

		System.out.println("\n  >> refund (应触发: 退款审批 + 全局日志):");
		proxy.refund("ORD-123");
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验3: 拦截器排序 → 顺序就是语义
	// 要点: Advisor 实现 Ordered 接口，order 值越小越靠外（先执行 before，后执行 after）
	//       场景：幂等(order=1) → 风控(order=2) → 事务(order=3)
	//       顺序反了语义就变了（比如幂等在事务内 = 不幂等）
	// ─────────────────────────────────────────────────────────────
	static void exp3_interceptorOrdering() {
		System.out.println("【实验3】拦截器排序 → 顺序就是语义");
		System.out.println("─────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new TradeServiceImpl());
		pf.addInterface(TradeService.class);

		// 故意乱序添加
		pf.addAdvisor(createOrderedAdvisor("事务", 3));
		pf.addAdvisor(createOrderedAdvisor("幂等", 1));
		pf.addAdvisor(createOrderedAdvisor("风控", 2));

		TradeService proxy = (TradeService) pf.getProxy();

		System.out.println("\n  >> placeOrder (期望顺序: 幂等→风控→事务→target→事务→风控→幂等):");
		proxy.placeOrder("U001", 200);

		System.out.println("\n  >> Advisor 顺序:");
		Advisor[] advisors = pf.getAdvisors();
		for (int i = 0; i < advisors.length; i++) {
			System.out.println("     [" + i + "] " + advisors[i]);
		}
		System.out.println();
	}

	private static DefaultPointcutAdvisor createOrderedAdvisor(String name, int order) {
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
			System.out.println("  [" + name + "-BEFORE] order=" + order);
			try {
				Object result = invocation.proceed();
				System.out.println("  [" + name + "-AFTER]  order=" + order);
				return result;
			}
			catch (Exception e) {
				System.out.println("  [" + name + "-ERROR]  order=" + order + " → " + e.getMessage());
				throw e;
			}
		});
		advisor.setOrder(order);
		return advisor;
	}

	// ─────────────────────────────────────────────────────────────
	// 实验4: 手动组装链 → 深入 DefaultAdvisorChainFactory
	// 要点: 不通过 ProxyFactory，直接操作 AdvisedSupport + ChainFactory
	//       验证：同一 Advisor 列表，不同 method 得到不同长度的 chain
	// ─────────────────────────────────────────────────────────────
	static void exp4_manualChainAssembly() throws Exception {
		System.out.println("【实验4】手动组装链 → DefaultAdvisorChainFactory + AdapterRegistry");
		System.out.println("─────────────────────────────────────────────────────");

		// 构造 AdvisedSupport（ProxyFactory 的父类）
		AdvisedSupport config = new AdvisedSupport();
		config.setTarget(new TradeServiceImpl());
		config.addInterface(TradeService.class);

		// 添加一个仅匹配 placeOrder 的 Advisor
		NameMatchMethodPointcut pc = new NameMatchMethodPointcut();
		pc.setMappedName("placeOrder");
		config.addAdvisor(new DefaultPointcutAdvisor(pc, (MethodInterceptor) invocation -> {
			System.out.println("    [仅placeOrder] 拦截");
			return invocation.proceed();
		}));

		// 添加一个匹配所有方法的 Advisor
		config.addAdvice((MethodInterceptor) invocation -> {
			System.out.println("    [全局] 拦截");
			return invocation.proceed();
		});

		// 手动用 ChainFactory 组装
		DefaultAdvisorChainFactory chainFactory = new DefaultAdvisorChainFactory();

		Method placeOrderMethod = TradeService.class.getMethod("placeOrder", String.class, int.class);
		Method refundMethod = TradeService.class.getMethod("refund", String.class);

		List<Object> chainForPlaceOrder = chainFactory.getInterceptorsAndDynamicInterceptionAdvice(
				config, placeOrderMethod, TradeServiceImpl.class);
		List<Object> chainForRefund = chainFactory.getInterceptorsAndDynamicInterceptionAdvice(
				config, refundMethod, TradeServiceImpl.class);

		System.out.println("\n  placeOrder 的 chain 长度: " + chainForPlaceOrder.size());
		for (Object obj : chainForPlaceOrder) {
			System.out.println("    → " + obj.getClass().getSimpleName());
		}

		System.out.println("  refund 的 chain 长度:     " + chainForRefund.size());
		for (Object obj : chainForRefund) {
			System.out.println("    → " + obj.getClass().getSimpleName());
		}

		// 手动查看 AdapterRegistry 适配能力
		System.out.println("\n  验证 AdapterRegistry 适配:");
		DefaultAdvisorAdapterRegistry registry = new DefaultAdvisorAdapterRegistry();
		MethodBeforeAdvice beforeAdvice = (method, a, target) -> {};
		Advisor wrapped = registry.wrap(beforeAdvice);
		System.out.println("    wrap(MethodBeforeAdvice) → " + wrapped.getClass().getSimpleName());
		org.aopalliance.intercept.MethodInterceptor[] interceptors = registry.getInterceptors(wrapped);
		for (org.aopalliance.intercept.MethodInterceptor mi : interceptors) {
			System.out.println("    getInterceptors → " + mi.getClass().getSimpleName());
		}
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验5: 短路拦截器 → 不调 proceed() 直接返回
	// 要点: 责任链允许任意拦截器"短路" —— 不调 proceed() 就不往下走
	//       场景：风控拦截 → 直接拒绝，后续事务/业务都不执行
	// ─────────────────────────────────────────────────────────────
	static void exp5_shortCircuit() {
		System.out.println("【实验5】短路拦截器 → 风控拦截直接拒绝");
		System.out.println("─────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new TradeServiceImpl());
		pf.addInterface(TradeService.class);

		// 拦截器1: 审计（最外层）
		pf.addAdvisor(createOrderedAdvisor("审计", 1));

		// 拦截器2: 风控短路 —— 金额 > 10000 直接拒绝
		DefaultPointcutAdvisor riskAdvisor = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
			if ("placeOrder".equals(invocation.getMethod().getName())) {
				int amount = (int) invocation.getArguments()[1];
				if (amount > 10000) {
					System.out.println("  [风控-短路] 金额=" + amount + " > 10000, 直接拒绝！不调 proceed()");
					return "RISK-BLOCKED";  // 不调 proceed()，链断了
				}
			}
			return invocation.proceed();
		});
		riskAdvisor.setOrder(2);
		pf.addAdvisor(riskAdvisor);

		// 拦截器3: 事务（最内层，被短路后不会执行）
		pf.addAdvisor(createOrderedAdvisor("事务", 3));

		TradeService proxy = (TradeService) pf.getProxy();

		System.out.println("\n  >> placeOrder(amount=100) 正常链路:");
		System.out.println("  >> 结果: " + proxy.placeOrder("U001", 100));

		System.out.println("\n  >> placeOrder(amount=50000) 被风控短路:");
		System.out.println("  >> 结果: " + proxy.placeOrder("U001", 50000));
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验6: 动态 MethodMatcher → isRuntime()=true, 按参数匹配
	// 要点: 静态匹配在代理创建时做一次，动态匹配每次调用都做
	//       InterceptorAndDynamicMethodMatcher 包装了 interceptor + matcher
	//       场景：仅当 amount > 5000 时触发大额审批拦截
	// ─────────────────────────────────────────────────────────────
	static void exp6_dynamicMethodMatcher() {
		System.out.println("【实验6】动态 MethodMatcher → 按参数决定是否拦截");
		System.out.println("─────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new TradeServiceImpl());
		pf.addInterface(TradeService.class);

		// 使用自定义动态 Pointcut
		pf.addAdvisor(new DefaultPointcutAdvisor(
				new LargeAmountPointcut(),
				(MethodInterceptor) invocation -> {
					System.out.println("  [大额审批] 触发！amount=" + invocation.getArguments()[1]);
					return invocation.proceed();
				}
		));

		TradeService proxy = (TradeService) pf.getProxy();

		System.out.println("\n  >> placeOrder(amount=100) → 不触发大额审批:");
		proxy.placeOrder("U001", 100);

		System.out.println("\n  >> placeOrder(amount=8000) → 触发大额审批:");
		proxy.placeOrder("U001", 8000);

		System.out.println("\n  >> refund → 不触发（静态匹配就排除了）:");
		proxy.refund("ORD-001");
		System.out.println();
	}
}
