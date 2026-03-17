package org.springframework.lab.hierarchy;

/**
 * 模拟数据源 —— 只在父容器注册
 */
public class DataSourceBean {

	private final String url;

	public DataSourceBean(String url) {
		this.url = url;
	}

	@Override
	public String toString() {
		return "DataSourceBean{url='" + url + "'}";
	}
}
