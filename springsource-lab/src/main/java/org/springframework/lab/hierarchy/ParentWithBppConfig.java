package org.springframework.lab.hierarchy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 带 BPP 的父容器配置 —— 用于演示基础设施隔离
 */
@Configuration
public class ParentWithBppConfig {

	@Bean
	public DataSourceBean dataSource() {
		return new DataSourceBean("jdbc:mysql://parent-ds");
	}

	@Bean
	public static TimingBpp timingBpp() {
		return new TimingBpp();
	}
}
