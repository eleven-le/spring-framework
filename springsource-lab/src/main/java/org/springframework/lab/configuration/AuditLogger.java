package org.springframework.lab.configuration;

/**
 * @Import(AuditLogger.class) 导入的普通类.
 *
 * processImports 发现它既不是 ImportSelector 也不是 Registrar,
 * 就当作一个 @Configuration 类递归解析, 并标记为 imported.
 * 在 Reader 阶段由 registerBeanDefinitionForImportedConfigurationClass 注册 BD.
 */
public class AuditLogger {
	@Override
	public String toString() {
		return "AuditLogger@" + Integer.toHexString(hashCode());
	}
}
