package org.springframework.lab.advisororder;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * ===================================================================
 *  W23 · 拦截链排序语义：@Order / PriorityOrdered / Advisor 排序
 *        （ProxyFactory 手动实验 —— 无 Spring 容器）
 * ===================================================================
 *
 * 一句话抽象：
 *   拦截链的执行顺序不是"实现细节"，它就是"语义本身" ——
 *   幂等在事务外 = 幂等，幂等在事务内 = 不幂等；
 *   排序由 AnnotationAwareOrderComparator 三级决议：
 *   PriorityOrdered 永远压 Ordered → order 值越小优先级越高 → 未声明兜底 LOWEST_PRECEDENCE。
 *
 * 本 Demo 共 5 个实验：
 *   实验1: PriorityOrdered vs Ordered → PriorityOrdered 无视 order 值永远排前
 *   实验2: order 值排序 → 值越小越先执行 before，越后执行 after（洋葱模型）
 *   实验3: 洋葱模型可视化 → before 正序 / after 逆序
 *   实验4: 未声明 order → 兜底 LOWEST_PRECEDENCE，排在最内层
 *   实验5: 语义灾变 → 幂等 order > 事务 order 时，幂等跑到事务内部
 *
 * 核心调用链（10 步）：
 *   1. AbstractAdvisorAutoProxyCreator#findEligibleAdvisors   : 收集候选 Advisor
 *   2. AbstractAdvisorAutoProxyCreator#sortAdvisors            : 排序入口
 *   3. AnnotationAwareOrderComparator#compare                  : 三级比较
 *   4. OrderComparator#doCompare                               : PriorityOrdered 优先判定
 *   5. OrderComparator#getOrder                                : 取 order 值（兜底 LOWEST_PRECEDENCE）
 *   6. AnnotationAwareOrderComparator#findOrder                : @Order 注解读取
 *   7. AspectJPrecedenceComparator#compare                     : 同 Aspect 内 advice 声明顺序
 *   8. AspectJPrecedenceComparator#comparePrecedenceWithinAspect : before 先声明先执行/after 先声明后执行
 *   9. DefaultAdvisorChainFactory#getInterceptorsAndDynamic... : 按排序后的 Advisor 顺序生成链
 *  10. ReflectiveMethodInvocation#proceed                      : 洋葱调度执行
 *
 * 断点抓手（5 个）：
 *   ① AbstractAdvisorAutoProxyCreator#sortAdvisors              → 看排序前后对比
 *   ② OrderComparator#doCompare                                 → 看 PriorityOrdered 判定分支
 *   ③ AnnotationAwareOrderComparator#findOrder                  → 看 @Order 注解读取
 *   ④ AspectJPrecedenceComparator#comparePrecedenceWithinAspect → 看同 Aspect 内排序
 *   ⑤ ReflectiveMethodInvocation#proceed                       → 看运行时洋葱执行顺序
 */
public class AdvisorOrderMain {

	public static void main(String[] args) {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println(" W23 · 拦截链排序语义 Demo（ProxyFactory 手动实验）");
		System.out.println("═══════════════════════════════════════════════════════════\n");

		exp1_priorityOrderedVsOrdered();
		exp2_orderValueSorting();
		exp3_onionModelVisualization();
		exp4_noOrderFallback();
		exp5_semanticDisaster();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验1: PriorityOrdered vs Ordered
	// 要点: PriorityOrdered 是标记接口，无视 order 值永远排在 Ordered 前面
	//       即使 PriorityOrdered 的 order=100，Ordered 的 order=1，
	//       PriorityOrdered 仍然先执行
	// 源码: OrderComparator#doCompare:
	//       boolean p1 = (o1 instanceof PriorityOrdered);
	//       boolean p2 = (o2 instanceof PriorityOrdered);
	//       if (p1 && !p2) return -1;  ← 直接返回，不比 order 值
	// ─────────────────────────────────────────────────────────────
	static void exp1_priorityOrderedVsOrdered() {
		System.out.println("【实验1】PriorityOrdered vs Ordered → PriorityOrdered 永远压 Ordered");
		System.out.println("─────────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new OrderServiceImpl());
		pf.addInterface(OrderService.class);

		// Advisor-A: 实现 Ordered，order=1（值很小，本应"高优先"）
		pf.addAdvisor(createOrderedAdvisor("普通Ordered", 1));

		// Advisor-B: 实现 PriorityOrdered，order=100（值很大，但不影响）
		pf.addAdvisor(new PriorityOrderedAdvisor("PriorityOrdered", 100));

		OrderService proxy = (OrderService) pf.getProxy();
		System.out.println("\n  >> placeOrder (PriorityOrdered 先执行，即使 order=100 > 1):");
		proxy.placeOrder("U001", 100);

		// 用 AnnotationAwareOrderComparator 排序验证
		System.out.println("\n  >> AnnotationAwareOrderComparator 排序验证:");
		List<Advisor> advisors = new ArrayList<>();
		advisors.add(createOrderedAdvisor("Ordered", 1));
		advisors.add(new PriorityOrderedAdvisor("PriorityOrdered", 100));
		advisors.add(createOrderedAdvisor("Ordered", 0));
		advisors.add(new PriorityOrderedAdvisor("PriorityOrdered", 50));

		AnnotationAwareOrderComparator.sort(advisors);
		for (int i = 0; i < advisors.size(); i++) {
			Advisor a = advisors.get(i);
			String type = (a instanceof PriorityOrdered) ? "PriorityOrdered" : "Ordered      ";
			System.out.println("    [" + i + "] " + type + " order=" + ((Ordered) a).getOrder());
		}
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验2: order 值排序 → 值越小优先级越高（先执行 before）
	// 要点: order 值含义 = 洋葱层级，值越小 = 越靠外层
	//       外层先执行 before，后执行 after
	//       业务含义: 1=幂等 → 2=风控 → 3=事务 → target
	// ─────────────────────────────────────────────────────────────
	static void exp2_orderValueSorting() {
		System.out.println("【实验2】order 值排序 → 值越小越先执行 before");
		System.out.println("─────────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new OrderServiceImpl());
		pf.addInterface(OrderService.class);

		// 故意乱序添加，验证排序效果
		pf.addAdvisor(createOrderedAdvisor("事务", 3));
		pf.addAdvisor(createOrderedAdvisor("幂等", 1));
		pf.addAdvisor(createOrderedAdvisor("风控", 2));

		OrderService proxy = (OrderService) pf.getProxy();
		System.out.println("\n  >> 无论添加顺序如何，执行顺序始终由 order 值决定:");
		System.out.println("  >> 期望: 幂等(1)→风控(2)→事务(3)→target→事务(3)→风控(2)→幂等(1)");
		proxy.placeOrder("U001", 200);
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验3: 洋葱模型可视化 → 观察完整的嵌套执行
	// 要点: 三层洋葱 order=10/20/30
	//       进入方向: 10→20→30→target
	//       离开方向: 30→20→10
	//       用缩进清晰展示嵌套层级
	// ─────────────────────────────────────────────────────────────
	static void exp3_onionModelVisualization() {
		System.out.println("【实验3】洋葱模型可视化 → before 正序 / after 逆序");
		System.out.println("─────────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new OrderServiceImpl());
		pf.addInterface(OrderService.class);

		String[] layers = {"外层-审计(10)", "中层-风控(20)", "内层-事务(30)"};
		int[] orders = {10, 20, 30};
		for (int i = 0; i < layers.length; i++) {
			final String name = layers[i];
			final int depth = i + 1;
			DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
				StringBuilder indent = new StringBuilder("    ");
				for (int d = 1; d < depth; d++) {
					indent.append("│ ");
				}
				System.out.println(indent + "┌─ " + name + " BEFORE ─────");
				try {
					Object result = invocation.proceed();
					System.out.println(indent + "└─ " + name + " AFTER (正常) ─");
					return result;
				} catch (Exception e) {
					System.out.println(indent + "└─ " + name + " AFTER (异常: " + e.getMessage() + ") ─");
					throw e;
				}
			});
			advisor.setOrder(orders[i]);
			pf.addAdvisor(advisor);
		}

		OrderService proxy = (OrderService) pf.getProxy();
		System.out.println("\n  >> 正常调用 — 观察洋葱嵌套:");
		proxy.placeOrder("U001", 500);
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验4: 未声明 order → 兜底 LOWEST_PRECEDENCE
	// 要点: 不实现 Ordered 接口、不加 @Order → 默认 Integer.MAX_VALUE
	//       多个未声明 order 的 Advisor 之间顺序不确定 → 危险！
	// 源码: OrderComparator#getOrder → findOrder 返回 null
	//       → return Ordered.LOWEST_PRECEDENCE (Integer.MAX_VALUE)
	// ─────────────────────────────────────────────────────────────
	static void exp4_noOrderFallback() {
		System.out.println("【实验4】未声明 order → 兜底 LOWEST_PRECEDENCE，位于最内层");
		System.out.println("─────────────────────────────────────────────────────────");

		ProxyFactory pf = new ProxyFactory();
		pf.setTarget(new OrderServiceImpl());
		pf.addInterface(OrderService.class);

		// 有 order 的 Advisor
		pf.addAdvisor(createOrderedAdvisor("有序-风控", 5));

		// 无 order 的 Advisor（DefaultPointcutAdvisor 默认 order=LOWEST_PRECEDENCE）
		// 注意: DefaultPointcutAdvisor 实现了 Ordered，默认 order=Integer.MAX_VALUE
		DefaultPointcutAdvisor noOrder1 = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
			System.out.println("    [无序-审计] 我没有设置 order → 兜底 LOWEST_PRECEDENCE=" + Ordered.LOWEST_PRECEDENCE);
			return invocation.proceed();
		});
		// 不调 setOrder → 默认 LOWEST_PRECEDENCE
		pf.addAdvisor(noOrder1);

		DefaultPointcutAdvisor noOrder2 = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
			System.out.println("    [无序-监控] 我也没设置 order → 和审计同 order 值，谁先不保证");
			return invocation.proceed();
		});
		pf.addAdvisor(noOrder2);

		OrderService proxy = (OrderService) pf.getProxy();
		System.out.println("\n  >> 有序的先执行，无序的排在最内层:");
		proxy.placeOrder("U001", 100);

		System.out.println("\n  >> 验证 order 值:");
		for (Advisor a : pf.getAdvisors()) {
			int order = (a instanceof Ordered) ? ((Ordered) a).getOrder() : Ordered.LOWEST_PRECEDENCE;
			System.out.println("    " + a.getClass().getSimpleName()
					+ " → order=" + order
					+ (order == Ordered.LOWEST_PRECEDENCE ? " (LOWEST_PRECEDENCE=Integer.MAX_VALUE)" : ""));
		}
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验5: 语义灾变 → 幂等 order > 事务 order
	// 要点: 幂等(order=1) < 事务(order=5) → 幂等在事务外 → 正确
	//       幂等(order=10) > 事务(order=5) → 幂等在事务内 → 灾难
	//       因为: 事务回滚会一起回滚幂等标记 → 请求可以重复执行
	// 业务映射: C 端治理链 order 常量规范:
	//   幂等=100 < 限流=200 < 风控=300 < 事务=LOWEST_PRECEDENCE
	// ─────────────────────────────────────────────────────────────
	static void exp5_semanticDisaster() {
		System.out.println("【实验5】语义灾变 → 幂等在事务内 vs 事务外");
		System.out.println("─────────────────────────────────────────────────────────");

		// ── 场景A：正确顺序 → 幂等(1) < 事务(5) ──
		System.out.println("\n  场景A: 正确顺序 → 幂等(order=1) 在事务(order=5) 外");
		System.out.println("  ──────────────────────────────────────────────");
		{
			ProxyFactory pf = new ProxyFactory();
			pf.setTarget(new OrderServiceImpl());
			pf.addInterface(OrderService.class);
			pf.addAdvisor(createSemanticAdvisor("幂等", 1, true));
			pf.addAdvisor(createSemanticAdvisor("事务", 5, false));
			OrderService proxy = (OrderService) pf.getProxy();
			proxy.placeOrder("U001", 100);
			System.out.println("  结论: 幂等在事务外 → 事务回滚不影响幂等标记 ✓");
		}

		// ── 场景B：错误顺序 → 幂等(10) > 事务(5) ──
		System.out.println("\n  场景B: 错误顺序 → 幂等(order=10) 在事务(order=5) 内");
		System.out.println("  ──────────────────────────────────────────────");
		{
			ProxyFactory pf = new ProxyFactory();
			pf.setTarget(new OrderServiceImpl());
			pf.addInterface(OrderService.class);
			pf.addAdvisor(createSemanticAdvisor("幂等", 10, true));
			pf.addAdvisor(createSemanticAdvisor("事务", 5, false));
			OrderService proxy = (OrderService) pf.getProxy();
			proxy.placeOrder("U001", 100);
			System.out.println("  结论: 幂等在事务内 → 事务回滚连同幂等标记一起回滚 → 可重复执行 ✗");
		}
		System.out.println();
	}

	// ─── 工具方法 ───────────────────────────────────────────────

	/** 创建实现 Ordered 的 Advisor（DefaultPointcutAdvisor 自带 Ordered） */
	private static DefaultPointcutAdvisor createOrderedAdvisor(String name, int order) {
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
			System.out.println("    [" + name + "-BEFORE] order=" + order);
			try {
				Object result = invocation.proceed();
				System.out.println("    [" + name + "-AFTER]  order=" + order);
				return result;
			} catch (Exception e) {
				System.out.println("    [" + name + "-ERROR]  order=" + order + " → " + e.getMessage());
				throw e;
			}
		});
		advisor.setOrder(order);
		return advisor;
	}

	/** 创建带语义标注的 Advisor（用于实验5） */
	private static DefaultPointcutAdvisor createSemanticAdvisor(String name, int order, boolean isIdempotent) {
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
			if (isIdempotent) {
				System.out.println("    ┌ [" + name + "] 检查幂等键... (order=" + order + ")");
			} else {
				System.out.println("    ┌ [" + name + "] 开启事务... (order=" + order + ")");
			}
			try {
				Object result = invocation.proceed();
				if (isIdempotent) {
					System.out.println("    └ [" + name + "] 标记幂等键已消费 (order=" + order + ")");
				} else {
					System.out.println("    └ [" + name + "] 提交事务 (order=" + order + ")");
				}
				return result;
			} catch (Exception e) {
				if (!isIdempotent) {
					System.out.println("    └ [" + name + "] 回滚事务 (order=" + order + ")");
				}
				throw e;
			}
		});
		advisor.setOrder(order);
		return advisor;
	}
}

/**
 * 实现 PriorityOrdered 的自定义 Advisor
 *
 * 关键点: DefaultPointcutAdvisor 只实现 Ordered 接口
 * 要让 OrderComparator#doCompare 走 PriorityOrdered 分支，
 * 必须自定义子类同时实现 PriorityOrdered 标记接口。
 *
 * 源码: OrderComparator#doCompare 第一步:
 *   boolean p1 = (o1 instanceof PriorityOrdered);
 *   if (p1 && !p2) return -1;  ← PriorityOrdered 直接赢
 */
class PriorityOrderedAdvisor extends DefaultPointcutAdvisor implements PriorityOrdered {

	private final String name;

	public PriorityOrderedAdvisor(String name, int order) {
		super((MethodInterceptor) invocation -> {
			System.out.println("    [" + name + "-BEFORE] order=" + order + " (PriorityOrdered)");
			try {
				Object result = invocation.proceed();
				System.out.println("    [" + name + "-AFTER]  order=" + order + " (PriorityOrdered)");
				return result;
			} catch (Exception e) {
				System.out.println("    [" + name + "-ERROR]  order=" + order + " → " + e.getMessage());
				throw e;
			}
		});
		this.name = name;
		setOrder(order);
	}
}
