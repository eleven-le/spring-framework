package org.springframework.lab.factorybean;

import java.lang.reflect.Proxy;

import org.springframework.beans.factory.FactoryBean;

/**
 * 场景 1: 基础 FactoryBean -- 动态代理生成 PayChannel 实现
 *
 * 模拟 RPC/SDK 场景: 业务方只定义接口, FactoryBean 负责生产代理对象。
 * 容器管理的是 FactoryBean 本身, 调用方通过 getBean("payChannel") 拿到的是代理产物。
 *
 * 核心分流点验证:
 *   getBean("payChannel")   → 代理产物 (Proxy)
 *   getBean("&payChannel")  → PayChannelFactoryBean 实例本身
 */
public class PayChannelFactoryBean implements FactoryBean<PayChannel> {

	private final String channelName;

	public PayChannelFactoryBean(String channelName) {
		this.channelName = channelName;
		System.out.println("[PayChannelFactoryBean] 构造: channelName=" + channelName);
	}

	@Override
	public PayChannel getObject() throws Exception {
		System.out.println("[PayChannelFactoryBean] getObject() 被调用, 创建代理: " + channelName);
		return (PayChannel) Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class<?>[]{PayChannel.class},
				(proxy, method, args) -> {
					if ("pay".equals(method.getName())) {
						return "[" + channelName + "] 支付成功: orderId=" + args[0] + ", amount=" + args[1] + "分";
					}
					if ("channel".equals(method.getName())) {
						return channelName;
					}
					if ("toString".equals(method.getName())) {
						return "PayChannel$Proxy(" + channelName + ")";
					}
					return null;
				}
		);
	}

	@Override
	public Class<?> getObjectType() {
		return PayChannel.class;
	}

	@Override
	public boolean isSingleton() {
		return true; // 产物也是单例, 会被缓存到 factoryBeanObjectCache
	}

	public String getChannelName() {
		return channelName;
	}
}
