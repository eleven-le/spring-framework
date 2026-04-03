package org.springframework.lab.factorybeandeep;

/**
 * 模拟连接对象 -- FactoryBean 产物
 * 真实场景: 数据库连接、RPC 长连接、MQ 连接等需要工厂管理的重量级资源
 */
public class Connection {

	private final String url;
	private final long createdAt;

	public Connection(String url) {
		this.url = url;
		this.createdAt = System.nanoTime();
	}

	public String getUrl() {
		return url;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	@Override
	public String toString() {
		return "Connection(" + url + "@" + (createdAt % 100000) + ")";
	}
}
