package org.springframework.lab.factorybean;

/**
 * 模拟监控 SDK 客户端 -- EagerMetricsFactoryBean 的产物
 */
public class MetricsClient {

	public MetricsClient() {
		System.out.println("[MetricsClient] 实例化完成, 连接已建立");
	}

	public void report(String metric, double value) {
		System.out.println("[MetricsClient] 上报: " + metric + "=" + value);
	}
}
