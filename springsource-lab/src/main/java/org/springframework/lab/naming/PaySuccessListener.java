package org.springframework.lab.naming;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 【Listener 后缀】监听器：事件驱动的观察者，被动响应事件。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>ApplicationListener — 监听 ApplicationEvent（泛型指定关心哪种事件）</li>
 *   <li>ContextRefreshedEvent 监听 — 容器启动完成后做初始化</li>
 *   <li>TransactionalApplicationListener — 事务感知监听（AFTER_COMMIT 等阶段）</li>
 *   <li>RequestContextListener — Servlet 请求生命周期监听</li>
 *   <li>SessionDestroyedEvent 监听 — 会话销毁时清理资源</li>
 * </ul>
 *
 * <p>命名规则：XxxListener = "我监听 Xxx 事件，事件来了我就干活"
 *
 * <p>关键区分：
 * <ul>
 *   <li>vs Processor：Processor 主动拦截<b>每个对象</b>，Listener 被动等<b>事件推送</b></li>
 *   <li>vs Interceptor：Interceptor 能<b>修改结果/短路</b>，Listener 只能<b>旁路通知</b>不能改结果</li>
 *   <li>vs Handler：Handler 处理<b>请求</b>（一对一分发），Listener 订阅<b>事件</b>（一对多广播）</li>
 * </ul>
 */
@Component
public class PaySuccessListener implements ApplicationListener<PaySuccessEvent> {

	@Override
	public void onApplicationEvent(PaySuccessEvent event) {
		System.out.println("    [Listener] 收到支付成功事件: orderId=" + event.getOrderId()
				+ ", amount=" + PayUtils.formatAmount(event.getAmountInCents()) + "元");
		System.out.println("    [Listener] → 触发后续: 发短信通知 / 更新积分 / 推送MQ");
	}
}
