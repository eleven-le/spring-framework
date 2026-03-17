package org.springframework.lab.hierarchy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * W06 - 父容器配置
 *
 * <p>注册父容器独有的 Bean：DataSource（模拟基础设施层）
 */
@Configuration
public class ParentConfig {

	@Bean
	public DataSourceBean dataSource() {
		return new DataSourceBean("jdbc:mysql://parent-ds");
	}

	@Bean
	public SharedService sharedService() {
		return new SharedService("from-PARENT");
	}
}
