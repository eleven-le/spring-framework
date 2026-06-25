package com.leilei.lab.laboratory.l03.l03_01;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import com.leilei.lab.laboratory.common.domain.Product;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

/**
 * 📖 知识点：[[L03-01-代理机制与选型-JDK与CGLIB#4. 🧠 设计思想]]（CGLIB 子类代理的硬约束）
 * 🎯 作用：坐实 CGLIB「子类继承覆写」机制的两条硬约束——final 方法 / private 方法无法被覆写，
 *         因此挂在它们上的切面静默失效（CGLIB 仅 WARN 一句 "Unable to proxy method ... it is final"，
 *         不报错、不阻断启动）。用计数通知对比：普通方法进切面，final 方法 0 次。
 *         由此推出选型铁律：① 被代理类不能是 final class；② 需被增强的方法不能声明 final/private。
 * 🔗 业务场景：产品中心「上下架服务」误把核心写操作 onShelf() 标了 final（图个「禁止子类乱改」），
 *         结果挂在它上面的操作日志 / 灰度开关 / 库存联动切面全部失效，灰度放量时无日志可查——
 *         典型「代理选型与编码约束不匹配」事故，根因不在切面写错，而在被代理方法的可覆写性。
 */
public final class L0301_03_CglibFinalMethodConstraintDemo {

	private L0301_03_CglibFinalMethodConstraintDemo() {
	}

	/** 统计每个方法是否真正进入代理链：final 方法因无法被 CGLIB 覆写，永远不会被计数。 */
	static class AuditCountingAdvice implements MethodInterceptor {
		final AtomicInteger offShelfEnters = new AtomicInteger();
		final AtomicInteger onShelfFinalEnters = new AtomicInteger();

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			String name = invocation.getMethod().getName();
			if ("offShelf".equals(name)) {
				offShelfEnters.incrementAndGet();
			}
			else if ("onShelf".equals(name)) {
				onShelfFinalEnters.incrementAndGet();
			}
			return invocation.proceed();
		}
	}

	/** 上下架服务：onShelf 误标 final（CGLIB 无法覆写 → 切面失效），offShelf 正常可代理做对照。 */
	static class ShelfService {

		/** ❌ 误标 final：CGLIB 子类无法覆写，挂在它上面的操作日志/灰度切面全部失效。 */
		final void onShelf(Product product) {
			product.changeStatus(Product.Status.ON_SHELF);
		}

		/** ✅ 非 final：可被 CGLIB 子类覆写，切面正常生效。 */
		void offShelf(Product product) {
			product.changeStatus(Product.Status.OFF_SHELF);
		}
	}

	public static void main(String[] args) {
		AuditCountingAdvice advice = new AuditCountingAdvice();
		ProxyFactory pf = new ProxyFactory(new ShelfService());
		pf.setProxyTargetClass(true);   // 无接口 → CGLIB 子类代理
		pf.addAdvice(advice);
		ShelfService proxy = (ShelfService) pf.getProxy();

		System.out.println("代理类型 = " + (AopUtils.isCglibProxy(proxy) ? "CGLIB 子类代理" : "其他")
				+ "（" + proxy.getClass().getSimpleName() + "）");
		System.out.println("（运行时控制台会出现一条 CGLIB WARN：Unable to proxy method [onShelf] ... it is final）");

		Product product = new Product(100001L, "珍珠奶茶", "奶茶", Product.Status.DRAFT);
		int rounds = 10;
		for (int i = 0; i < rounds; i++) {
			proxy.onShelf(product);    // final：直接打到目标，绕过代理
			proxy.offShelf(product);   // 非 final：经代理链
		}

		System.out.println();
		System.out.println("==================== CGLIB final 方法约束验证（各调用 " + rounds + " 次）====================");
		System.out.println("offShelf 进入切面次数 = " + advice.offShelfEnters.get() + "  ✅ 非 final，切面生效");
		System.out.println("onShelf  进入切面次数 = " + advice.onShelfFinalEnters.get()
				+ "  ❌ final 无法被 CGLIB 覆写，操作日志/灰度切面静默失效");
		System.out.println();
		System.out.println("铁律：被代理类不可 final；需增强的方法不可 final/private——"
				+ "这是 CGLIB「子类继承覆写」机制的物理边界，与切面写得对不对无关。");
	}
}
