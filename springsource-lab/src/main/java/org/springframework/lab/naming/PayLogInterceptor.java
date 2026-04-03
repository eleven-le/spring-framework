package org.springframework.lab.naming;

/**
 * 【Interceptor 后缀】拦截器：在目标调用前后插入横切逻辑，可以修改结果甚至短路。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>HandlerInterceptor — MVC 请求拦截（preHandle/postHandle/afterCompletion 三段式）</li>
 *   <li>MethodInterceptor (AOP Alliance) — AOP 方法拦截（invoke 环绕通知）</li>
 *   <li>TransactionInterceptor — @Transactional 的真正执行者（开启事务→调用→提交/回滚）</li>
 *   <li>ClientHttpRequestInterceptor — RestTemplate 出站请求拦截（加 Header/日志/限流）</li>
 *   <li>AsyncHandlerInterceptor — 异步请求拦截（afterConcurrentHandlingStarted）</li>
 * </ul>
 *
 * <p>命名规则：XxxInterceptor = "我在 Xxx 执行前后拦截，可以修改入参/出参/短路拒绝"
 *
 * <p>关键区分：
 * <ul>
 *   <li>vs Processor：Processor 对<b>对象</b>做增强（A→A'），Interceptor 对<b>调用</b>做拦截（before/after）</li>
 *   <li>vs Decorator：Decorator 包装对象<b>长期持有</b>，Interceptor 拦截调用<b>每次触发</b></li>
 *   <li>vs Listener：Listener 事后通知<b>不能改结果</b>，Interceptor <b>能改结果甚至短路</b></li>
 *   <li>vs Filter：Filter 是 Servlet 规范（web层），Interceptor 是 Spring MVC 层（可以拿到 Handler 信息）</li>
 * </ul>
 *
 * <p>三段式设计（对照 HandlerInterceptor）：
 * <ul>
 *   <li>preHandle — 前置拦截，返回 false 可短路</li>
 *   <li>postHandle — 后置拦截，可修改结果</li>
 *   <li>afterCompletion — 完成后清理，无论成功失败都执行</li>
 * </ul>
 */
public class PayLogInterceptor {

	/** 前置拦截（返回 false 可短路）—— 对照 HandlerInterceptor#preHandle */
	public boolean preHandle(PayRequest request) {
		System.out.println("    [Interceptor] preHandle: orderId=" + request.getOrderId()
				+ ", channel=" + request.getChannelCode()
				+ ", amount=" + PayUtils.formatAmount(request.getAmountInCents()) + "元");
		return true; // 返回 false 则短路，不执行后续
	}

	/** 后置拦截 —— 对照 HandlerInterceptor#postHandle */
	public void postHandle(PayRequest request, PayResult result) {
		System.out.println("    [Interceptor] postHandle: "
				+ (result.isSuccess() ? "成功" : "失败") + " → " + result.getMessage());
	}

	/** 完成后回调（无论成功失败）—— 对照 HandlerInterceptor#afterCompletion */
	public void afterCompletion(PayRequest request, PayResult result, Exception ex) {
		System.out.println("    [Interceptor] afterCompletion: 记录审计日志"
				+ (ex != null ? ", 异常=" + ex.getMessage() : ""));
	}
}
