package org.springframework.lab.classmigration.pay.resolver;

import org.springframework.lab.classmigration.pay.PayChannel;
import org.springframework.lab.classmigration.pay.PayException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道路由器 —— 策略模式的调度入口
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 HandlerMapping —— 根据请求特征路由到对应 Handler</li>
 *   <li>对标 BeanFactoryUtils.beansOfType() —— 从容器收集所有同类型 Bean</li>
 *   <li>对标 TransactionInterceptor 内部的 determineTransactionManager()
 *       —— 根据 @Transactional(value="xxx") 路由到对应 TM</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>构造器注入 List&lt;PayChannel&gt; —— Spring 自动收集所有 PayChannel 实现</li>
 *   <li>内部转 Map 加速查找 —— 对标 DLBF 的 beanDefinitionMap</li>
 *   <li>新增渠道只需加一个 Bean，Resolver 零修改 —— 开闭原则</li>
 * </ul>
 */
public class PayChannelResolver {

	private final Map<String, PayChannel> channelMap = new ConcurrentHashMap<>();

	/**
	 * 构造器注入所有 PayChannel —— Spring 会自动收集容器中所有 PayChannel 类型的 Bean
	 * 对标：AutowiredAnnotationBeanPostProcessor 处理 List<T> 注入
	 */
	public PayChannelResolver(List<PayChannel> channels) {
		for (PayChannel channel : channels) {
			channelMap.put(channel.getChannelCode(), channel);
			System.out.println("    [Resolver] 注册渠道: " + channel.getChannelCode()
					+ " → " + channel.getClass().getSimpleName());
		}
	}

	/**
	 * 根据渠道编码路由 —— 对标 determineTransactionManager(qualifier)
	 */
	public PayChannel resolve(String channelCode) {
		PayChannel channel = channelMap.get(channelCode);
		if (channel == null) {
			throw new PayException(channelCode, "CHANNEL_NOT_FOUND",
					"不支持的支付渠道: " + channelCode + ", 已注册: " + channelMap.keySet(), null);
		}
		return channel;
	}

	public Map<String, PayChannel> getAllChannels() {
		return channelMap;
	}
}
