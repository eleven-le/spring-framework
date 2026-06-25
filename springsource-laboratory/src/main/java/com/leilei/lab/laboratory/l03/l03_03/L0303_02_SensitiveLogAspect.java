package com.leilei.lab.laboratory.l03.l03_03;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.StringJoiner;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;
import org.springframework.util.ReflectionUtils;

/**
 * 📖 知识点：[[L03-03-切面实战-鉴权日志限流幂等#2. 🏭 生产怎么用对]]（§2.2 日志脱敏切面：PII 脱敏 ⭐）
 * 🎯 作用：用方法级 {@code @AccessLog} 标注「这个方法要打访问日志」，字段级 {@code @Sensitive} 标注「这个字段是 PII」，
 *         由一个 {@code @Around} 切面在进出方法时打印入参——但对 PII 字段按策略脱敏（手机号 138****8888、
 *         姓名 张*、地址保留前 6 位）。脱敏靠 {@link ReflectionUtils} 反射读字段值，业务对象本身不变，
 *         脱敏只发生在「日志渲染」这一刻，杜绝把明文 PII 写进日志文件 / ELK。
 * 🔗 业务场景：C 端「下单 / 改收货地址」接口的访问日志——手机号、收货人、详细地址是强 PII，
 *         明文落日志会触碰《个人信息保护法》合规红线；脱敏切面是产品中心日志的统一基线。
 */
public final class L0303_02_SensitiveLogAspect {

	private L0303_02_SensitiveLogAspect() {
	}

	// ==================== 脱敏注解与策略 ====================

	/** 方法级：标注需要打访问日志的接口。 */
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface AccessLog {
	}

	// 注意：annotation 属性返回的枚举/类型必须 public，否则注解的 JDK 动态代理在跨包读取时会抛 IllegalAccessError
	public enum MaskType { PHONE, NAME, ADDRESS }

	/** 字段级：标注 PII 字段及脱敏策略。 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Sensitive {
		MaskType value();
	}

	/** 脱敏算法集合：每种 PII 一种打码规则（生产里通常做成可配置 SPI）。 */
	static final class Masker {
		static String mask(MaskType type, String raw) {
			if (raw == null || raw.isEmpty()) {
				return raw;
			}
			switch (type) {
				case PHONE:
					return raw.length() == 11 ? raw.substring(0, 3) + "****" + raw.substring(7) : "****";
				case NAME:
					return raw.charAt(0) + "*";
				case ADDRESS:
					return raw.length() <= 6 ? "******" : raw.substring(0, 6) + "***";
				default:
					return "***";
			}
		}
	}

	// ==================== 业务入参对象（含 PII 字段）====================

	static class CreateOrderCmd {
		final long skuId;
		final int qty;
		@Sensitive(MaskType.NAME)
		final String receiver;
		@Sensitive(MaskType.PHONE)
		final String phone;
		@Sensitive(MaskType.ADDRESS)
		final String address;

		CreateOrderCmd(long skuId, int qty, String receiver, String phone, String address) {
			this.skuId = skuId;
			this.qty = qty;
			this.receiver = receiver;
			this.phone = phone;
			this.address = address;
		}

		/** 注意：toString 是「业务原文」，含明文 PII——绝不能直接进日志（切面负责脱敏渲染）。 */
		@Override
		public String toString() {
			return "CreateOrderCmd{skuId=" + skuId + ", qty=" + qty + ", receiver='" + receiver
					+ "', phone='" + phone + "', address='" + address + "'}";
		}
	}

	// ==================== 业务服务 ====================

	static class OrderService {
		@AccessLog
		String createOrder(CreateOrderCmd cmd) {
			System.out.println("        [目标] 真正下单：sku=" + cmd.skuId + ", qty=" + cmd.qty);
			return "GM-ORDER-" + cmd.skuId;
		}
	}

	// ==================== 脱敏日志切面 ====================

	@Aspect
	@Order(50)   // 介于鉴权(0)与限流之间：日志要记录「确认要执行」的请求，但需在业务方法外侧
	static class AccessLogAspect {

		@Around("@annotation(com.leilei.lab.laboratory.l03.l03_03.L0303_02_SensitiveLogAspect.AccessLog)")
		public Object log(ProceedingJoinPoint pjp) throws Throwable {
			String method = ((MethodSignature) pjp.getSignature()).getName();
			System.out.println("  → [访问日志] " + method + " 入参=" + renderMasked(pjp.getArgs()));
			long begin = System.nanoTime();
			try {
				Object ret = pjp.proceed();
				System.out.println("  ← [访问日志] " + method + " 成功，耗时=" + costMs(begin) + "ms，返回=" + ret);
				return ret;
			}
			catch (Throwable ex) {
				System.out.println("  ✗ [访问日志] " + method + " 异常，耗时=" + costMs(begin) + "ms，error=" + ex.getMessage());
				throw ex;
			}
		}

		/** 把每个入参渲染成「脱敏后」字符串：对带 @Sensitive 的字段按策略打码，其余字段原样。 */
		private String renderMasked(Object[] args) {
			StringJoiner joiner = new StringJoiner(", ", "[", "]");
			for (Object arg : args) {
				joiner.add(arg == null ? "null" : maskObject(arg));
			}
			return joiner.toString();
		}

		private String maskObject(Object arg) {
			Class<?> type = arg.getClass();
			// 简单类型/无 @Sensitive 字段的对象，直接 toString（这里只处理领域命令对象的字段级脱敏）
			if (type.getName().startsWith("java.")) {
				return String.valueOf(arg);
			}
			StringJoiner fields = new StringJoiner(", ", type.getSimpleName() + "{", "}");
			for (Field field : type.getDeclaredFields()) {
				ReflectionUtils.makeAccessible(field);
				Object value = ReflectionUtils.getField(field, arg);
				Sensitive sensitive = field.getAnnotation(Sensitive.class);
				if (sensitive != null && value instanceof String) {
					fields.add(field.getName() + "=" + Masker.mask(sensitive.value(), (String) value));
				}
				else {
					fields.add(field.getName() + "=" + value);
				}
			}
			return fields.toString();
		}

		private static long costMs(long beginNanos) {
			return (System.nanoTime() - beginNanos) / 1_000_000;
		}
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class AopConfig {
		@Bean
		OrderService orderService() {
			return new OrderService();
		}

		@Bean
		AccessLogAspect accessLogAspect() {
			return new AccessLogAspect();
		}
	}

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
			OrderService service = ctx.getBean(OrderService.class);

			CreateOrderCmd cmd = new CreateOrderCmd(100001L, 2, "张三丰", "13812348888", "浙江省杭州市余杭区文一西路969号");
			System.out.println("==================== 业务原文（明文 PII，禁止进日志）====================");
			System.out.println("  " + cmd);

			System.out.println("\n==================== 切面脱敏后写入的访问日志 ====================");
			String orderId = service.createOrder(cmd);
			System.out.println("\n  下单结果 = " + orderId);
			System.out.println("\n结论：脱敏只发生在日志渲染瞬间，业务对象明文不动；手机号/姓名/地址按字段注解策略打码，"
					+ "PII 不落盘，合规与可观测两不误。");
		}
	}
}
