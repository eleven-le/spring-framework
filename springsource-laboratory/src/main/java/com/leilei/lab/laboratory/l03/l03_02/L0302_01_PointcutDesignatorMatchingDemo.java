package com.leilei.lab.laboratory.l03.l03_02;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import com.leilei.lab.laboratory.common.domain.Channel;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;

/**
 * 📖 知识点：[[L03-02-Aspect切面工程化#2. 🏭 生产怎么用对]]（切点表达式 designators 与匹配性能）
 * 🎯 作用：把 9 种切点指示符（designator）坐实成可运行实验——用 {@link AspectJExpressionPointcut}
 *         直接对方法做 {@link AspectJExpressionPointcut#matches(Method, Class)} 匹配，并打印
 *         {@link AspectJExpressionPointcut#isRuntime()}：
 *         ① {@code within} / {@code execution} 是纯「静态匹配」——只看类 / 方法签名，isRuntime=false，
 *            匹配结果可被框架缓存，每个 (method,class) 只算一次；
 *         ② {@code args(..)} / {@code this(..)} / {@code target(..)} / 甚至 {@code @annotation(..)} 会被 AspectJ
 *            标成 isRuntime=true（保守地保留「运行期再判一次」的可能），进了热点链路就是逐调用的潜在开销
 *            （注意：{@code @annotation} 即便用类型字面量，AspectJ 仍报 isRuntime=true——实测见 main）。
 *         结论：切点优先用 {@code within}（先做 ClassFilter 把整类挡在外面，最便宜），
 *         慎用 {@code args} 这类运行期指示符做粗粒度匹配。
 * 🔗 业务场景：产品中心价格查询 / 商品上下架是 C 端最高频调用，切点写得越「动态」，
 *         AOP 在每次报价 / 每次下单上多付的匹配成本越高——切点表达式就是切面的「索引设计」。
 */
public final class L0302_01_PointcutDesignatorMatchingDemo {

	private L0302_01_PointcutDesignatorMatchingDemo() {
	}

	/** 价格查询门面，面向接口编程；quote 是 C 端最高频方法。 */
	interface PriceQueryService {
		BigDecimal quote(long skuId, Channel channel);
	}

	/** 标注「需埋点监控」的方法注解，给 {@code @annotation} 指示符当靶子。 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@interface Monitored {
	}

	/** 价格查询实现，落在 ...l03_02 包下，给 {@code within} 指示符当靶子。 */
	static class PriceQueryServiceImpl implements PriceQueryService {
		@Override
		@Monitored
		public BigDecimal quote(long skuId, Channel channel) {
			BigDecimal base = BigDecimal.valueOf(1500L, 2);
			return channel == Channel.MINI_PROGRAM ? base.subtract(BigDecimal.valueOf(200L, 2)) : base;
		}

		/** 非 quote 方法，用来证明 execution 的方法名筛选确实把它挡在外面。 */
		public void onShelf(long skuId) {
		}
	}

	/** 用一个切点表达式去匹配一组方法，打印命中情况与 isRuntime（是否逐调用再判）。 */
	private static void probe(String designatorDesc, String expression, Method... methods) {
		AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
		pc.setExpression(expression);
		System.out.println("---- " + designatorDesc + " ----");
		System.out.println("表达式      : " + expression);
		// isRuntime=true 表示该切点含 args/this/target 等运行期绑定，需逐次调用再匹配（成本最高）
		System.out.println("isRuntime   : " + pc.isRuntime() + (pc.isRuntime() ? "（动态：每次调用都再判一次）" : "（静态：(method,class) 只判一次、可缓存）"));
		for (Method m : methods) {
			// ClassFilter 先粗筛类，再 MethodMatcher 细筛方法——within 这类指示符在第一步就能挡掉整类，最省
			boolean classPass = pc.getClassFilter().matches(m.getDeclaringClass());
			boolean methodPass = pc.matches(m, m.getDeclaringClass());
			System.out.println("  " + m.getDeclaringClass().getSimpleName() + "#" + m.getName()
					+ " → 类筛=" + classPass + ", 方法命中=" + methodPass);
		}
		System.out.println();
	}

	public static void main(String[] args) throws NoSuchMethodException {
		Method quote = PriceQueryServiceImpl.class.getMethod("quote", long.class, Channel.class);
		Method onShelf = PriceQueryServiceImpl.class.getMethod("onShelf", long.class);

		System.out.println("==================== 切点指示符（designator）与匹配成本 ====================\n");

		// ① execution：最常用，按「方法签名」精确匹配（返回值 + 类 + 方法名 + 参数），静态
		probe("execution —— 按方法签名（最常用，静态）",
				"execution(* com.leilei.lab.laboratory.l03.l03_02..*Service*.quote(..))", quote, onShelf);

		// ② within：按「类型/包」匹配，只走 ClassFilter，是最便宜的粗筛指示符，静态
		probe("within —— 按类型/包（最便宜的粗筛，静态）",
				"within(com.leilei.lab.laboratory.l03.l03_02..*)", quote, onShelf);

		// ③ @annotation：按「方法上的注解类型」匹配，注解驱动切面（鉴权/限流/幂等）的标配；
		//    注意 AspectJ 即便对类型字面量也保守地标 isRuntime=true（静态预筛仍准确，但保留运行期复判）
		probe("@annotation —— 按方法注解类型（注解驱动切面标配；AspectJ 标 isRuntime=true）",
				"@annotation(com.leilei.lab.laboratory.l03.l03_02.L0302_01_PointcutDesignatorMatchingDemo.Monitored)",
				quote, onShelf);

		// ④ args：按「运行期实参类型」匹配 → isRuntime=true，每次调用都再判，热点链路慎用
		probe("args —— 按运行期实参类型（动态！逐调用再判）",
				"args(long, com.leilei.lab.laboratory.common.domain.Channel)", quote, onShelf);

		System.out.println("结论：切点是切面的「索引设计」——优先 within/execution 这类静态指示符"
				+ "（结果可缓存、ClassFilter 先挡整类），\n"
				+ "      args/this/target 绑定参数会让 isRuntime=true、退化为逐调用匹配；"
				+ "C 端高频方法上的切点务必往「静态、窄」写。");
	}
}
