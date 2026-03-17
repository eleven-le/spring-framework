package org.springframework.lab.dynamicregistry;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 场景3: ImportSelector 条件选择 — 根据环境属性动态决定导入哪些中间件.
 *
 * ImportSelector 的核心机制:
 *   1. 在 processImports 中被识别 (candidate.isAssignable(ImportSelector.class))
 *   2. 立即调用 selectImports() 获取类名数组
 *   3. 对返回的类名递归调用 processImports (它们可能又是 Selector/Registrar/@Configuration)
 *
 * 与 DeferredImportSelector 的关键差异:
 *   - ImportSelector: 立即执行, 返回值参与当轮 parse
 *   - DeferredImportSelector: 暂存, 所有普通配置类 parse 完毕后才执行
 *
 * 支持 Aware: EnvironmentAware, BeanFactoryAware, BeanClassLoaderAware, ResourceLoaderAware
 *            (由 ParserStrategyUtils#instantiateClass 注入)
 *
 * 源码路径:
 *   ConfigurationClassParser#processImports:570-586
 *     → ImportSelector (non-deferred) 分支
 *     → selector.selectImports(currentSourceClass.getMetadata())
 *     → processImports(递归)
 */
public class MiddlewareSelector implements ImportSelector, EnvironmentAware {

	private Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		System.out.println("[TIMING] MiddlewareSelector.selectImports() — IMMEDIATE, during parse");

		// 真实场景: 根据 environment.getProperty("middleware.mode") 决定
		// 这里简化为同时返回两个, 模拟"全量中间件"模式
		return new String[]{
				RateLimiter.class.getName(),
				CircuitBreaker.class.getName()
		};
	}
}
