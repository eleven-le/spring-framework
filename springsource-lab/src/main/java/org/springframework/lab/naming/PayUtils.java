package org.springframework.lab.naming;

/**
 * 【Utils 后缀】工具类：无状态静态方法集合。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>BeanUtils — Bean 实例化、属性拷贝、方法查找</li>
 *   <li>StringUtils — hasText/trimWhitespace/tokenizeToStringArray</li>
 *   <li>ClassUtils — forName/isPresent/getAllInterfaces/getDefaultClassLoader</li>
 *   <li>ReflectionUtils — findMethod/invokeMethod/makeAccessible</li>
 *   <li>CollectionUtils — isEmpty/contains/findValueOfType</li>
 *   <li>AopUtils — isAopProxy/isJdkDynamicProxy/isCglibProxy</li>
 *   <li>TransactionSynchronizationUtils — triggerBeforeCommit/triggerAfterCommit</li>
 *   <li>AnnotationUtils — findAnnotation/getAnnotation/isAnnotationPresent</li>
 * </ul>
 *
 * <p>命名规则：XxxUtils = "我是纯静态工具，不持有状态，不需要实例化"
 *
 * <p>Spring 风格约定：
 * <ul>
 *   <li>类声明为 abstract 并提供 private 构造器，彻底禁止实例化</li>
 *   <li>所有方法都是 static，不依赖实例状态</li>
 *   <li>Utils vs Helper：Spring 主流用 Utils；Helper 偶尔出现但不推荐</li>
 *   <li>Utils 放 util/support 包，不放核心包</li>
 * </ul>
 */
public abstract class PayUtils {

	private PayUtils() {
		// 不允许实例化——对照 Spring 所有 Utils 类的写法
	}

	/** 金额格式化：分 → 元（保留2位小数） */
	public static String formatAmount(long amountInCents) {
		return String.format("%.2f", amountInCents / 100.0);
	}

	/** 订单号脱敏：只展示前4+后4位（对照 Spring 日志中对敏感信息的处理思路） */
	public static String maskOrderId(String orderId) {
		if (orderId == null || orderId.length() <= 8) {
			return orderId;
		}
		return orderId.substring(0, 4) + "****" + orderId.substring(orderId.length() - 4);
	}

	/** 判断是否高额交易（>=500元） */
	public static boolean isHighValue(long amountInCents) {
		return amountInCents >= 50000;
	}

	/** 生成简易 traceId */
	public static String generateTraceId() {
		return "T-" + Long.toHexString(System.nanoTime());
	}
}
