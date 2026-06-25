package com.leilei.lab.laboratory.l03.l03_04;

import java.lang.reflect.Method;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;

/**
 * 📖 知识点：[[L03-04-AOP失效场景全集与排查#2. 🏭 生产怎么用对]]（§2.3 切点表达式写错——离线自检工具 ⭐）
 * 🎯 作用：把「切面没生效」里最难肉眼排查的一类——<b>切点表达式写错、压根没匹配上任何方法</b>——做成可离线复现的单测。
 *         直接 new 一个 {@link AspectJExpressionPointcut}，{@code setExpression} 后对目标 {@link Method} 调
 *         {@link AspectJExpressionPointcut#matches(Method, Class)}，true/false 一眼定位表达式对错，
 *         无需起容器、无需打断点。这就是排查决策树里「确认是否匹配」那一步的官方手术刀
 *         （Spring 内部 {@code @Around} 切点匹配走的就是这个类，见本章源码导读 §5.1）。
 *         覆盖五类高频写错：包名错、方法名拼错、参数签名写死、返回类型写死、修饰符过窄。
 * 🔗 业务场景：商品上下架 {@code changeShelfStatus(skuId, status)} 要挂「操作审计 + 缓存失效」切面，
 *         结果切点把包名抄成了上一章的 l03_03、或把 {@code (..)} 写成 {@code (long)} 漏了 status 参数，
 *         切面静默不匹配——上下架既没审计也没刷缓存，C 端还在卖已下架商品。
 */
public final class L0304_03_PointcutMismatchFailureDemo {

	private L0304_03_PointcutMismatchFailureDemo() {
	}

	/** 上下架服务：被切的目标。真实签名 {@code boolean changeShelfStatus(long, int)}。 */
	static class ShelfService {
		public boolean changeShelfStatus(long skuId, int status) {
			return status == 1;
		}
	}

	/** 用 AspectJExpressionPointcut 离线判定：表达式是否匹配目标方法。 */
	private static void check(String label, String expression, Method target) {
		AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
		pc.setExpression(expression);
		boolean matched = pc.matches(target, target.getDeclaringClass());
		System.out.printf("  %-5s %-12s : %s%n", matched ? "✅匹配" : "❌不匹配", label, expression);
	}

	public static void main(String[] args) throws NoSuchMethodException {
		Method target = ShelfService.class.getMethod("changeShelfStatus", long.class, int.class);
		String base = "com.leilei.lab.laboratory.l03.l03_04."
				+ "L0304_03_PointcutMismatchFailureDemo.ShelfService";

		System.out.println("目标方法 = boolean ShelfService.changeShelfStatus(long, int)");
		System.out.println("\n==================== 逐条体检切点表达式 ====================");

		// 0. 正确写法：基准
		check("正确", "execution(* " + base + ".changeShelfStatus(..))", target);

		// 1. 包名写错（抄成了上一章 l03_03）→ 整条切点不匹配，最隐蔽
		check("包名错", "execution(* com.leilei.lab.laboratory.l03.l03_03."
				+ "L0304_03_PointcutMismatchFailureDemo.ShelfService.changeShelfStatus(..))", target);

		// 2. 方法名拼错（漏了 Status）→ 不匹配
		check("方法名错", "execution(* " + base + ".changeShelf(..))", target);

		// 3. 参数签名写死，漏了 status 这个 int 参数 → 不匹配（(..) 才是通配）
		check("参数写死", "execution(* " + base + ".changeShelfStatus(long))", target);

		// 4. 返回类型写死成 String，真实是 boolean → 不匹配（* 才是通配返回类型）
		check("返回写死", "execution(String " + base + ".changeShelfStatus(..))", target);

		// 5. 修饰符过窄：限定 protected，真实是 public → 不匹配
		check("修饰符窄", "execution(protected * " + base + ".changeShelfStatus(..))", target);

		System.out.println("\n结论：切面不生效且自调用/final 都排除后，第一时间用 AspectJExpressionPointcut#matches "
				+ "把切点拎出来离线体检——matches=false 即表达式没匹配上。"
				+ "高频坑：包名复制错、漏 (..)、把返回类型/修饰符写死。");
	}
}
