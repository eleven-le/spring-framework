package org.springframework.lab.naming;

/**
 * 【Holder 后缀】持有者：封装 ThreadLocal 线程绑定上下文。
 *
 * <p>Spring 中 Holder 有两种经典用法：
 * <ul>
 *   <li><b>ThreadLocal 持有者</b>（本类演示）：线程安全地保管上下文，用完必须清理</li>
 *   <li><b>数据打包持有者</b>：把对象 + 元数据打包传递（如 BeanDefinitionHolder = BD + name + aliases）</li>
 * </ul>
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>RequestContextHolder — 持有当前线程的 HttpServletRequest/RequestAttributes</li>
 *   <li>LocaleContextHolder — 持有当前线程的 Locale/TimeZone</li>
 *   <li>TransactionSynchronizationManager — 持有事务资源 Map + 同步回调列表</li>
 *   <li>SecurityContextHolder (Spring Security) — 持有当前线程的 Authentication</li>
 *   <li>BeanDefinitionHolder — 打包 BeanDefinition + beanName + aliases 传递</li>
 * </ul>
 *
 * <p>命名规则：XxxHolder = "我帮你在当前线程里保管 Xxx，用完记得 clear"
 *
 * <p>关键设计取舍：
 * <ul>
 *   <li>方便性 vs 安全性：ThreadLocal 让任意层级代码拿到上下文，但忘记 clear 会内存泄漏</li>
 *   <li>显式传参 vs 隐式传播：Holder 避免了参数层层透传，但增加了隐式依赖（调试更难）</li>
 *   <li>Spring 的做法：入口设置 + Filter/Interceptor 清理，形成 try/finally 范式</li>
 * </ul>
 *
 * <pre>
 * 断点抓手：RequestContextHolder#setRequestAttributes()
 *   → 观察 DispatcherServlet 在 FrameworkServlet#processRequest 中设置，finally 中清理
 * </pre>
 */
public class PayContextHolder {

	private static final ThreadLocal<PayContext> holder = new ThreadLocal<>();

	/** 绑定上下文到当前线程（对照 RequestContextHolder#setRequestAttributes） */
	public static void set(PayContext context) {
		if (context != null) {
			holder.set(context);
		}
		else {
			holder.remove();
		}
	}

	/**
	 * 获取当前线程的上下文（必须存在，否则抛异常）。
	 * 对照 RequestContextHolder#currentRequestAttributes() — 不存在时抛 IllegalStateException
	 */
	public static PayContext require() {
		PayContext ctx = holder.get();
		if (ctx == null) {
			throw new IllegalStateException(
					"PayContext 未绑定到当前线程，请在入口处调用 PayContextHolder.set()");
		}
		return ctx;
	}

	/**
	 * 获取当前线程的上下文（可能为 null）。
	 * 对照 RequestContextHolder#getRequestAttributes() — 允许返回 null
	 */
	public static PayContext get() {
		return holder.get();
	}

	/** 清理当前线程的上下文（必须在 finally 中调用，防止内存泄漏） */
	public static void clear() {
		holder.remove();
	}
}
