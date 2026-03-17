package org.springframework.lab.processor;

/**
 * 审计日志服务 -- 不带 @Component，由 BDRPP 动态注册
 * 模拟 "引入 jar 自动生效" 场景
 */
public class AuditLogger {

	public void log(String action) {
		System.out.println("[AUDIT] " + action);
	}
}
