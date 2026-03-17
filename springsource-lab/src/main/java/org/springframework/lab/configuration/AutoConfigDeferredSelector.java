package org.springframework.lab.configuration;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * DeferredImportSelector: 在 processImports 中被识别后,
 * 不立即执行 selectImports, 而是暂存到 deferredImportSelectorHandler.
 *
 * 所有配置类 parse 完毕后, 在 parser.parse() 方法末尾调用
 * deferredImportSelectorHandler.process() 统一触发.
 *
 * Spring Boot 的 AutoConfigurationImportSelector 就是 DeferredImportSelector,
 * 保证所有用户配置优先解析, 自动配置最后补位.
 *
 * 源码路径:
 *   ConfigurationClassParser#processImports
 *     → selector instanceof DeferredImportSelector
 *     → deferredImportSelectorHandler.handle() (暂存)
 *   ConfigurationClassParser#parse 方法末尾
 *     → deferredImportSelectorHandler.process()
 *     → DeferredImportSelectorGroupingHandler#processGroupImports
 *     → 再回到 processImports 处理返回的类名
 */
public class AutoConfigDeferredSelector implements DeferredImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		System.out.println("  [AutoConfigDeferredSelector] selectImports (DEFERRED, runs LAST!)");
		return new String[]{MonitorReporter.class.getName()};
	}
}
