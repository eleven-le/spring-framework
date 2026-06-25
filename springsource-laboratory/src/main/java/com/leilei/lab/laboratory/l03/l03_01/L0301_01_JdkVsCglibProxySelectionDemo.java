package com.leilei.lab.laboratory.l03.l03_01;

import java.math.BigDecimal;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import com.leilei.lab.laboratory.common.domain.Channel;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

/**
 * 📖 知识点：[[L03-01-代理机制与选型-JDK与CGLIB#2. 🏭 生产怎么用对]]（JDK 动态代理 vs CGLIB 取舍 / proxyTargetClass）
 * 🎯 作用：把 {@code DefaultAopProxyFactory#createAopProxy} 的三条选型规则坐实成可运行实验——
 *         ① 目标有用户接口 → 默认走 {@code JdkDynamicAopProxy}（生成实现接口的 $ProxyN）；
 *         ② 目标无接口 → 只能走 {@code ObjenesisCglibAopProxy}（生成目标的子类）；
 *         ③ {@code proxyTargetClass=true} → 即使有接口也强制 CGLIB 子类代理。
 *         用 {@link AopUtils#isJdkDynamicProxy} / {@link AopUtils#isCglibProxy} 验明正身，
 *         并印证「JDK 代理只能按接口类型接收，强转目标实现类会 ClassCastException」这一最常见踩坑。
 * 🔗 业务场景：产品中心「价格查询服务」——面向接口编程时 Spring 默认给 JDK 代理；
 *         一旦某处用实现类类型注入（如 @Autowired PriceQueryServiceImpl），启动即 BeanNotOfRequiredTypeException，
 *         这正是 Spring Boot 2.x 把 proxyTargetClass 默认改 true 的根因。
 */
public final class L0301_01_JdkVsCglibProxySelectionDemo {

	private L0301_01_JdkVsCglibProxySelectionDemo() {
	}

	/** C 端价格查询门面：对外只暴露接口，是「面向接口编程 → JDK 代理」的典型形态。 */
	interface PriceQueryService {
		BigDecimal quote(long skuId, Channel channel);
	}

	/** 价格查询实现：内部走多维规则匹配（此处简化），真实链路见 L02-05 / L07。 */
	static class PriceQueryServiceImpl implements PriceQueryService {
		@Override
		public BigDecimal quote(long skuId, Channel channel) {
			// 小程序渠道立减 2 元，演示用
			BigDecimal base = BigDecimal.valueOf(1500L, 2);
			return channel == Channel.MINI_PROGRAM ? base.subtract(BigDecimal.valueOf(200L, 2)) : base;
		}
	}

	/** 无接口的纯实现：营销活动计算器，故意不抽接口 → 只能被 CGLIB 子类代理。 */
	static class PromotionCalculator {
		BigDecimal discount(BigDecimal price) {
			return price.multiply(BigDecimal.valueOf(0.9));
		}
	}

	/** 一条「什么都不做、只放行」的环绕通知，仅用于触发代理生成（真实切面见 L03-03）。 */
	static class PassThroughAdvice implements MethodInterceptor {
		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			return invocation.proceed();
		}
	}

	public static void main(String[] args) {
		// ① 有接口 + 默认配置 → JDK 动态代理
		ProxyFactory jdkPf = new ProxyFactory(new PriceQueryServiceImpl());
		jdkPf.addAdvice(new PassThroughAdvice());
		Object jdkProxy = jdkPf.getProxy();
		System.out.println("==================== ① 有接口 + 默认 → JDK 动态代理 ====================");
		System.out.println("isJdkDynamicProxy = " + AopUtils.isJdkDynamicProxy(jdkProxy));
		System.out.println("代理类名          = " + jdkProxy.getClass().getName() + "（com.sun.proxy.$ProxyN）");
		System.out.println("能按接口接收      = " + (jdkProxy instanceof PriceQueryService));
		PriceQueryService svc = (PriceQueryService) jdkProxy;
		System.out.println("小程序报价        = " + svc.quote(10000100L, Channel.MINI_PROGRAM) + " 元");
		try {
			// JDK 代理不是 PriceQueryServiceImpl 的子类，强转实现类必炸（最常见的注入踩坑现场）
			PriceQueryServiceImpl impl = (PriceQueryServiceImpl) jdkProxy;
			System.out.println("不应到达：" + impl);
		}
		catch (ClassCastException ex) {
			System.out.println("强转实现类 → ClassCastException（JDK 代理只认接口类型）✅ 预期内");
		}

		// ② 无接口 → 只能 CGLIB 子类代理
		ProxyFactory cglibPf = new ProxyFactory(new PromotionCalculator());
		cglibPf.addAdvice(new PassThroughAdvice());
		Object cglibProxy = cglibPf.getProxy();
		System.out.println();
		System.out.println("==================== ② 无接口 → CGLIB 子类代理 ====================");
		System.out.println("isCglibProxy = " + AopUtils.isCglibProxy(cglibProxy));
		System.out.println("代理类名     = " + cglibProxy.getClass().getName() + "（含 $$EnhancerBySpringCGLIB$$）");
		System.out.println("是目标的子类 = " + (cglibProxy instanceof PromotionCalculator));

		// ③ 有接口但 proxyTargetClass=true → 强制 CGLIB
		ProxyFactory forcedPf = new ProxyFactory(new PriceQueryServiceImpl());
		forcedPf.addAdvice(new PassThroughAdvice());
		forcedPf.setProxyTargetClass(true);
		Object forcedProxy = forcedPf.getProxy();
		System.out.println();
		System.out.println("==================== ③ 有接口 + proxyTargetClass=true → 强制 CGLIB ====================");
		System.out.println("isCglibProxy        = " + AopUtils.isCglibProxy(forcedProxy));
		System.out.println("既是接口也是实现子类 = " + ((forcedProxy instanceof PriceQueryService)
				&& (forcedProxy instanceof PriceQueryServiceImpl)) + "（CGLIB 子类天然 instanceof 实现类，注入实现类不再报错）");
	}
}
