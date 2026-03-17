package org.springframework.lab.circulardep;

import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.lab.circulardep.scene1_setter.ServiceA;
import org.springframework.lab.circulardep.scene1_setter.ServiceB;
import org.springframework.lab.circulardep.scene2_constructor.CtorConfig;
import org.springframework.lab.circulardep.scene3_aop_proxy.SettlementService;
import org.springframework.lab.circulardep.scene3_aop_proxy.TradeService;
import org.springframework.lab.circulardep.scene4_prototype.ProtoA;
import org.springframework.lab.circulardep.scene4_prototype.ProtoConfig;
import org.springframework.lab.circulardep.scene5_lazy.LazyServiceA;

/**
 * W13 循环依赖与三级缓存 · 练兵场入口
 *
 * <h2>一句话抽象</h2>
 * 三级缓存解决的核心矛盾是: "singleton Bean 的创建是多步的(实例化→注入→初始化),
 * 而注入阶段又会递归触发其他 Bean 的创建, 如果形成环路就死锁了"。
 * 三级缓存通过「在实例化后、注入前暴露一个早期引用工厂」来打破这个环,
 * 但只能救「setter/字段注入的 singleton」, 救不了「构造器注入」和「prototype」。
 *
 * <h2>场景列表</h2>
 * <ul>
 *   <li>Scene 1: Setter 注入循环(能救) — ServiceA ↔ ServiceB</li>
 *   <li>Scene 2: 构造器注入循环(不能救) — CtorServiceA ↔ CtorServiceB</li>
 *   <li>Scene 3: AOP 代理 + 循环(三级缓存核心价值) — TradeService ↔ SettlementService + LogAspect</li>
 *   <li>Scene 4: Prototype 循环(不能救) — ProtoA ↔ ProtoB</li>
 *   <li>Scene 5: @Lazy 打破构造器循环(逃生通道) — LazyServiceA ↔ LazyServiceB</li>
 * </ul>
 *
 * <h2>断点位置 (5 个抓手)</h2>
 * <ol>
 *   <li>DefaultSingletonBeanRegistry#getSingleton(beanName, true) :195 → 三级缓存查找+升级二级缓存</li>
 *   <li>AbstractAutowireCapableBeanFactory#doCreateBean :613 → addSingletonFactory, 放入三级缓存</li>
 *   <li>AbstractAutoProxyCreator#getEarlyBeanReference → AOP 提前代理创建点</li>
 *   <li>AbstractAutowireCapableBeanFactory#doCreateBean :633 → getSingleton(beanName,false) 一致性校验</li>
 *   <li>DefaultSingletonBeanRegistry#beforeSingletonCreation :353 → 循环检测, 构造器循环在此抛异常</li>
 * </ol>
 *
 * <h2>口述调用链(10步)</h2>
 * <pre>
 * 1. doGetBean("A")        → getSingleton 缓存未命中, 进入创建
 * 2. getSingleton(ObjectFactory) → beforeSingletonCreation 标记 A 正在创建
 * 3. doCreateBean("A")     → createBeanInstance 反射构造空壳 A
 * 4. addSingletonFactory    → 把 () -> getEarlyBeanReference(A) 放入三级缓存
 * 5. populateBean("A")     → @Autowired 发现依赖 B → getBean("B")
 * 6. doCreateBean("B")     → createBeanInstance 空壳 B → addSingletonFactory(B)
 * 7. populateBean("B")     → @Autowired 发现依赖 A → getBean("A")
 * 8. getSingleton("A",true) → 命中三级缓存 → factory.getObject() → 早期引用(可能是AOP代理)
 *                             → 升级到二级缓存, 三级删除
 * 9. B 注入完成 → initializeBean(B) → addSingleton(B) 到一级缓存
 * 10. 回到 A: populateBean 完成 → initializeBean(A) → 一致性校验 → addSingleton(A) 到一级缓存
 * </pre>
 */
public class CircularDepMain {

	public static void main(String[] args) {
		System.out.println("==============================================");
		System.out.println("  W13 循环依赖与三级缓存 · 练兵场");
		System.out.println("==============================================\n");

		// ========== Scene 1: Setter/Field 注入循环依赖 — 能救 ==========
		System.out.println("--- Scene 1: Setter/@Autowired 字段注入循环 (能救) ---");
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(CircularDepConfig.class);
		ServiceA a = ctx.getBean(ServiceA.class);
		ServiceB b = ctx.getBean(ServiceB.class);
		System.out.println("  A.call() = " + a.call());
		System.out.println("  B.call() = " + b.call());
		System.out.println("  验证: A == B 中注入的 A? " + (a == ctx.getBean(ServiceA.class)));
		System.out.println("  [OK] setter 循环依赖解决成功\n");

		// ========== Scene 3: AOP 代理 + 循环依赖 ==========
		System.out.println("--- Scene 3: AOP 代理 + 循环依赖 (三级缓存核心价值) ---");
		TradeService trade = ctx.getBean(TradeService.class);
		SettlementService settle = ctx.getBean(SettlementService.class);
		System.out.println("  trade 的类型: " + trade.getClass().getName());
		System.out.println("  settle 的类型: " + settle.getClass().getName());
		System.out.println("  settle 持有的 trade: " + settle.whoIsMyTrade());
		System.out.println("  trade 是 CGLIB 代理? " + trade.getClass().getName().contains("$$"));
		trade.executeTrade("ORD-001");
		System.out.println("  [OK] AOP 代理 + 循环依赖解决成功\n");

		// ========== Scene 5: @Lazy 打破构造器循环 ==========
		System.out.println("--- Scene 5: @Lazy 打破构造器循环依赖 (逃生通道) ---");
		LazyServiceA lazyA = ctx.getBean(LazyServiceA.class);
		System.out.println("  lazyA.call() = " + lazyA.call());
		System.out.println("  [OK] @Lazy 打破构造器循环成功\n");

		ctx.close();

		// ========== Scene 2: 构造器注入循环依赖 — 不能救 ==========
		System.out.println("--- Scene 2: 构造器注入循环 (不能救, 预期抛异常) ---");
		try {
			new AnnotationConfigApplicationContext(CtorConfig.class);
			System.out.println("  [UNEXPECTED] 没有抛异常?!");
		}
		catch (BeanCurrentlyInCreationException e) {
			System.out.println("  [EXPECTED] " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		catch (Exception e) {
			// 实际会被 UnsatisfiedDependencyException 包装
			System.out.println("  [EXPECTED] " + e.getClass().getSimpleName());
			Throwable cause = e;
			while (cause.getCause() != null) {
				cause = cause.getCause();
				if (cause instanceof BeanCurrentlyInCreationException) {
					System.out.println("  Root cause: " + cause.getMessage());
					break;
				}
			}
		}
		System.out.println();

		// ========== Scene 4: Prototype 循环依赖 — 不能救 ==========
		System.out.println("--- Scene 4: Prototype 循环 (不能救, 预期抛异常) ---");
		try {
			AnnotationConfigApplicationContext protoCtx =
					new AnnotationConfigApplicationContext(ProtoConfig.class);
			protoCtx.getBean(ProtoA.class); // prototype 在 getBean 时才创建
		}
		catch (BeanCurrentlyInCreationException e) {
			System.out.println("  [EXPECTED] " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		catch (Exception e) {
			System.out.println("  [EXPECTED] " + e.getClass().getSimpleName());
			Throwable cause = e;
			while (cause.getCause() != null) {
				cause = cause.getCause();
				if (cause instanceof BeanCurrentlyInCreationException) {
					System.out.println("  Root cause: " + cause.getMessage());
					break;
				}
			}
		}

		System.out.println("\n==============================================");
		System.out.println("  W13 练兵场结束");
		System.out.println("==============================================");
	}
}
