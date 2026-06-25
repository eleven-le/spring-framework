package com.leilei.lab.laboratory.l03.l03_03;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

/**
 * 📖 知识点：[[L03-03-切面实战-鉴权日志限流幂等#2. 🏭 生产怎么用对]]（§2.1 鉴权切面：注解驱动鉴权 ⭐）
 * 🎯 作用：用 {@code @RequireLogin} / {@code @RequireRole} 两个方法级注解 + 一个 {@code @Around} 切面，
 *         把「登录态校验 + 角色鉴权」从每个业务方法里抽出来，统一收口到一个切面。
 *         关键技术点：通过 Spring 的 {@link AnnotatedElementUtils#findMergedAnnotation} 从
 *         <b>目标方法</b>（而非接口/代理方法）上读注解——这是注解驱动切面读取元注解的标准姿势，
 *         能正确处理组合注解 / 派生属性，比 {@code method.getAnnotation(...)} 更稳。
 * 🔗 业务场景：C 端「我的优惠券领取」「下单」「修改收货地址」等接口——未登录直接拒、角色不符直接拒，
 *         鉴权逻辑零散落在 controller/service 是技术债，注解 + 切面是产品中心的统一拦截姿势。
 */
public final class L0303_01_AuthCheckAspect {

	private L0303_01_AuthCheckAspect() {
	}

	// ==================== 鉴权注解 ====================

	/** 标注「必须登录」的方法。 */
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface RequireLogin {
	}

	/** 标注「必须具备某角色」的方法（隐含必须登录）。 */
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface RequireRole {
		String value();
	}

	// ==================== 登录上下文（线程绑定，模拟网关透传的用户态）====================

	/** 模拟 C 端用户上下文：登录态与角色由网关解析 token 后塞进 ThreadLocal，业务线程内可取。 */
	static final class UserContext {
		private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

		private final long userId;
		private final Set<String> roles;

		private UserContext(long userId, Set<String> roles) {
			this.userId = userId;
			this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
		}

		static void login(long userId, String... roles) {
			HOLDER.set(new UserContext(userId, new HashSet<>(Arrays.asList(roles))));
		}

		static void logout() {
			HOLDER.remove();
		}

		static UserContext current() {
			return HOLDER.get();
		}
	}

	/** 鉴权未通过的统一异常（生产里对应 401/403，全局异常处理器翻译成标准响应）。 */
	static final class AuthException extends RuntimeException {
		AuthException(String message) {
			super(message);
		}
	}

	// ==================== 业务服务：注解标注鉴权要求 ====================

	static class CouponService {
		@RequireLogin
		String claim(long couponId) {
			UserContext ctx = UserContext.current();
			System.out.println("        [目标] 用户 " + ctx.userId + " 领取优惠券 " + couponId);
			return "COUPON-" + couponId + "@" + ctx.userId;
		}

		@RequireRole("STORE_MANAGER")
		String forceOffShelf(long skuId) {
			System.out.println("        [目标] 门店店长强制下架 sku " + skuId);
			return "OFF_SHELF:" + skuId;
		}
	}

	// ==================== 鉴权切面 ====================

	@Aspect
	@Order(0)   // 鉴权放最外层：未登录/无权限的流量在最外圈就被拒，里层一切开销都省掉
	static class AuthAspect {

		/** 切「带 @RequireLogin 或 @RequireRole 的方法」。注解读取交给 around 内部用 AnnotatedElementUtils 做。 */
		@Around("@annotation(com.leilei.lab.laboratory.l03.l03_03.L0303_01_AuthCheckAspect.RequireLogin) "
				+ "|| @annotation(com.leilei.lab.laboratory.l03.l03_03.L0303_01_AuthCheckAspect.RequireRole)")
		public Object check(ProceedingJoinPoint pjp) throws Throwable {
			Method target = ((MethodSignature) pjp.getSignature()).getMethod();
			UserContext ctx = UserContext.current();

			// 1) 登录态校验：@RequireLogin 或 @RequireRole 都隐含「必须登录」
			if (ctx == null) {
				System.out.println("  ✗ [鉴权] " + target.getName() + " 未登录，拒绝（401）");
				throw new AuthException("未登录");
			}

			// 2) 角色鉴权：用 findMergedAnnotation 从目标方法读 @RequireRole（支持组合注解/派生属性）
			RequireRole requireRole = AnnotatedElementUtils.findMergedAnnotation(target, RequireRole.class);
			if (requireRole != null && !ctx.roles.contains(requireRole.value())) {
				System.out.println("  ✗ [鉴权] " + target.getName() + " 需要角色 [" + requireRole.value()
						+ "]，当前用户角色 " + ctx.roles + "，拒绝（403）");
				throw new AuthException("无权限：需要 " + requireRole.value());
			}

			System.out.println("  ✓ [鉴权] " + target.getName() + " 通过（user=" + ctx.userId + ", roles=" + ctx.roles + "）");
			return pjp.proceed();
		}
	}

	@Configuration
	@EnableAspectJAutoProxy   // CouponService 无接口 → 自动回落 CGLIB
	static class AopConfig {
		@Bean
		CouponService couponService() {
			return new CouponService();
		}

		@Bean
		AuthAspect authAspect() {
			return new AuthAspect();
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			CouponService service = ctx.getBean(CouponService.class);

			System.out.println("==================== 场景一：未登录领券 → 401 拒绝 ====================");
			UserContext.logout();
			tryCall(() -> service.claim(8001L));

			System.out.println("\n==================== 场景二：已登录普通用户领券 → 放行 ====================");
			UserContext.login(660088L, "C_USER");
			tryCall(() -> service.claim(8001L));

			System.out.println("\n==================== 场景三：普通用户强制下架 → 403（缺 STORE_MANAGER）====================");
			tryCall(() -> service.forceOffShelf(10000100L));

			System.out.println("\n==================== 场景四：店长账号强制下架 → 放行 ====================");
			UserContext.login(990001L, "C_USER", "STORE_MANAGER");
			tryCall(() -> service.forceOffShelf(10000100L));

			UserContext.logout();
			System.out.println("\n结论：鉴权 = 注解声明意图 + 切面统一拦截；@Order(0) 让它在所有切面最外层，"
					+ "未授权流量付出最小代价即被拒。");
		}
	}

	private static void tryCall(Runnable call) {
		try {
			call.run();
		}
		catch (AuthException ex) {
			System.out.println("  → 调用方收到鉴权异常：" + ex.getMessage());
		}
	}
}
