package org.springframework.lab.configuration;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * ImportSelector: 在 processImports 中被识别为 ImportSelector 子类,
 * 调用 selectImports() 返回类名数组, 再对这些类递归 processImports.
 *
 * 业务场景: 根据环境/配置动态选择导入哪些支付通道实现.
 * 这里简化为直接返回两个实现类.
 *
 * 源码路径:
 *   ConfigurationClassParser#processImports → candidate.isAssignable(ImportSelector.class)
 *   → selector.selectImports() → processImports(递归)
 */
public class PayChannelImportSelector implements ImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		System.out.println("  [PayChannelImportSelector] selectImports invoked!");
		return new String[]{
				AlipayChannel.class.getName(),
				WechatPayChannel.class.getName()
		};
	}
}
