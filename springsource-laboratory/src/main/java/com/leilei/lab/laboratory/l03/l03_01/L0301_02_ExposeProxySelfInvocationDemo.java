package com.leilei.lab.laboratory.l03.l03_01;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import com.leilei.lab.laboratory.common.dao.MockInventoryDao;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;

import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.ProxyFactory;

/**
 * 📖 知识点：[[L03-01-代理机制与选型-JDK与CGLIB#3. 🧨 事故与避坑]]（exposeProxy / AopContext / 自调用失效）
 * 🎯 作用：复现 AOP 头号事故「目标内部 this 自调用绕过代理 → 切面静默失效」，并给出官方解法：
 *         开 {@code proxyTargetClass}/{@code exposeProxy}（{@link ProxyFactory#setExposeProxy}）后，
 *         在内部用 {@link AopContext#currentProxy()} 拿回代理对象再调，使被调方法重新走代理链。
 *         用一个「计数环绕通知」统计每个方法真正进入代理链的次数：
 *         自调用版 deduct() 计数=0（被绕过），exposeProxy 版 deduct() 计数恢复 = 调用次数。
 * 🔗 业务场景：大促秒杀「下单 seckill() 内部调用扣库存 deduct()」——若 deduct() 上挂了
 *         限流 / 幂等 / 库存校验切面，自调用会让这些防护全部失效，导致超卖、重复扣减；
 *         这是 L03-04「AOP 失效场景全集」的开场事故，本章先把根因（代理对象 ≠ this）讲透。
 */
public final class L0301_02_ExposeProxySelfInvocationDemo {

	private L0301_02_ExposeProxySelfInvocationDemo() {
	}

	/** 模拟挂在 deduct() 上的「库存防护切面」：每进一次代理链计一次数，借此判定切面是否真的生效。 */
	static class GuardCountingAdvice implements MethodInterceptor {
		final AtomicInteger seckillEnters = new AtomicInteger();
		final AtomicInteger deductEnters = new AtomicInteger();

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			String name = invocation.getMethod().getName();
			if ("seckill".equals(name)) {
				seckillEnters.incrementAndGet();
			}
			else if ("deduct".equals(name)) {
				deductEnters.incrementAndGet();
			}
			return invocation.proceed();
		}
	}

	/** 秒杀库存服务：seckill 内部要调 deduct，是「自调用是否走代理」的标准靶子。 */
	static class SeckillStockService {

		private final MockInventoryDao inventoryDao;
		/** 切换内部调用方式：false=this 直调（失效），true=AopContext.currentProxy() 调（生效）。 */
		private final boolean viaProxy;

		SeckillStockService(MockInventoryDao inventoryDao, boolean viaProxy) {
			this.inventoryDao = inventoryDao;
			this.viaProxy = viaProxy;
		}

		boolean seckill(long skuId, long storeId, int qty) {
			if (viaProxy) {
				// 拿回代理对象再调：deduct 重新经过代理链，挂在它上面的切面恢复生效
				return ((SeckillStockService) AopContext.currentProxy()).deduct(skuId, storeId, qty);
			}
			// this.deduct(...)：直接打到目标实例，彻底绕开代理 → deduct 上的切面静默失效
			return deduct(skuId, storeId, qty);
		}

		boolean deduct(long skuId, long storeId, int qty) {
			return inventoryDao.tryDeduct(skuId, storeId, qty);
		}
	}

	private static SeckillStockService buildProxy(MockInventoryDao dao, boolean viaProxy,
			boolean exposeProxy, GuardCountingAdvice advice) {
		ProxyFactory pf = new ProxyFactory(new SeckillStockService(dao, viaProxy));
		pf.setProxyTargetClass(true);       // 无接口的 service，走 CGLIB 子类代理
		pf.setExposeProxy(exposeProxy);     // 仅当需要内部自调用走代理时才需开启
		pf.addAdvice(advice);
		return (SeckillStockService) pf.getProxy();
	}

	public static void main(String[] args) {
		MockDataSet dataSet = MockDataFactory.seed(1, 1);
		long skuId = 1000010L;   // 100001 号商品的中杯
		long storeId = 1001L;
		int rounds = 20;

		// 场景 A：内部 this.deduct() 自调用 —— deduct 上的切面被绕过
		MockInventoryDao daoA = new MockInventoryDao(dataSet, MockProfile.inMemory());
		GuardCountingAdvice adviceA = new GuardCountingAdvice();
		SeckillStockService proxyA = buildProxy(daoA, false, false, adviceA);
		for (int i = 0; i < rounds; i++) {
			proxyA.seckill(skuId, storeId, 1);
		}
		System.out.println("==================== 场景 A：this 自调用（exposeProxy=false）====================");
		System.out.println("seckill 进入切面次数 = " + adviceA.seckillEnters.get() + "（外部调，正常走代理）");
		System.out.println("deduct  进入切面次数 = " + adviceA.deductEnters.get()
				+ "  ❌ 被自调用绕过，挂在 deduct 上的限流/幂等切面全部失效");

		// 场景 B：exposeProxy=true + AopContext.currentProxy() 内部调用 —— 切面恢复
		MockInventoryDao daoB = new MockInventoryDao(dataSet, MockProfile.inMemory());
		GuardCountingAdvice adviceB = new GuardCountingAdvice();
		SeckillStockService proxyB = buildProxy(daoB, true, true, adviceB);
		for (int i = 0; i < rounds; i++) {
			proxyB.seckill(skuId, storeId, 1);
		}
		System.out.println();
		System.out.println("==================== 场景 B：AopContext.currentProxy()（exposeProxy=true）====================");
		System.out.println("seckill 进入切面次数 = " + adviceB.seckillEnters.get());
		System.out.println("deduct  进入切面次数 = " + adviceB.deductEnters.get()
				+ "  ✅ 经代理回调，deduct 上的切面恢复生效");
		System.out.println();
		System.out.println("结论：自调用绕过代理是「代理对象 ≠ 目标 this」的必然结果，"
				+ "exposeProxy 是把代理对象临时塞进 ThreadLocal 的官方逃生门（首选还是拆分 Bean，见 L03-04）。");
	}
}
