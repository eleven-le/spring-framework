package org.springframework.lab.naming;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 【Resolver 后缀】解析器：输入→输出转换。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>ViewResolver — 视图名称 → View 对象</li>
 *   <li>HandlerExceptionResolver — 异常 → ModelAndView</li>
 *   <li>PropertyResolver — 属性 key → 属性 value</li>
 *   <li>AutowireCandidateResolver — BeanDefinition → 是否是候选者</li>
 *   <li>BeanDefinitionValueResolver — BD 中的值引用 → 实际对象</li>
 * </ul>
 *
 * <p>命名规则：Resolver 后缀 = "给我一个输入，我解析出一个输出"
 *
 * <p>与 Processor 的区分（关键！）：
 * Resolver = 转换（A → B），输入输出类型不同。把"渠道编码"变成"渠道对象"。
 * Processor = 增强（A → A'），输入输出类型相同。把"普通请求"变成"带风控标记的请求"。
 *
 * <p>策略模式的典型载体：多种 Resolver 实现同一个接口，
 * 容器按优先级链式调用，第一个返回非 null 的 Resolver 胜出。
 * 对照 HandlerExceptionResolver 链。
 */
@Component
public class PayChannelResolver {

	private final Map<String, PayChannel> channelMap = new HashMap<>();
	private final PayChannel fallback;

	@Autowired
	public PayChannelResolver(
			@Autowired PayChannel defaultPayChannel,
			@Autowired PayChannel alipayChannel,
			@Autowired PayChannel simplePayChannel) {
		// 注册已知渠道
		channelMap.put("ALIPAY", alipayChannel);
		channelMap.put("DEFAULT", defaultPayChannel);
		// 对照 SimpleAutowireCandidateResolver → QualifierAnnotationAutowireCandidateResolver 的链式降级
		this.fallback = defaultPayChannel;
	}

	/**
	 * 解析渠道编码 → PayChannel 实例。
	 * 对照 ViewResolver#resolveViewName：给定名称，返回对应的 View。
	 *
	 * @param channelCode 渠道编码（ALIPAY / WECHAT / ...）
	 * @return 对应的 PayChannel，未找到则降级到 Default
	 */
	public PayChannel resolve(String channelCode) {
		PayChannel channel = channelMap.get(channelCode);
		if (channel != null) {
			System.out.println("    [Resolver] '" + channelCode + "' → " + channel.getClass().getSimpleName());
			return channel;
		}
		// 降级策略（对照 AutowireCandidateResolver 的降级链：Qualifier → Generic → Simple）
		System.out.println("    [Resolver] '" + channelCode + "' 未匹配, 降级到 " + fallback.getClass().getSimpleName());
		return fallback;
	}
}
