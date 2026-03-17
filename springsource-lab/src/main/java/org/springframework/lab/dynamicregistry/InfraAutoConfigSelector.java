package org.springframework.lab.dynamicregistry;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 场景4: DeferredImportSelector — 自动配置补位语义.
 *
 * 核心设计意图:
 *   所有用户显式配置(@Configuration/@Import)优先解析完毕,
 *   Deferred 最后补位 → 天然支持"用户配置覆盖自动配置".
 *   Spring Boot 的 AutoConfigurationImportSelector 就是这个模式.
 *
 * 执行流程:
 *   1. processImports 识别为 DeferredImportSelector
 *      → deferredImportSelectorHandler.handle() 暂存到 List
 *   2. parser.parse() 方法末尾 (行192)
 *      → deferredImportSelectorHandler.process()
 *      → DeferredImportSelectorGroupingHandler#processGroupImports
 *      → 再回到 processImports 处理返回的类名
 *
 * Group API:
 *   getImportGroup() 可返回自定义 Group 实现, 用于:
 *   - 合并多个 DeferredImportSelector 的结果
 *   - 自定义排序逻辑 (Spring Boot AutoConfigurationGroup 就用了这个)
 *   - 返回 null 表示每个 Selector 独立成组
 */
public class InfraAutoConfigSelector implements DeferredImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		System.out.println("[TIMING] InfraAutoConfigSelector.selectImports() — DEFERRED, runs LAST!");
		return new String[]{HealthEndpoint.class.getName()};
	}

	// 返回 null → 使用 DefaultDeferredImportSelectorGroup, 每个 Selector 独立成组
	// 若返回自定义 Group.class → 多个 DeferredImportSelector 共享一个 Group, 可统一排序
	@Override
	public Class<? extends Group> getImportGroup() {
		return null;
	}
}
