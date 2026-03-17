package org.springframework.lab.configuration;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * ImportBeanDefinitionRegistrar: 在 processImports 中被识别后暂存到 ConfigurationClass,
 * 等 Reader 阶段在 loadBeanDefinitionsFromRegistrars 中回调 registerBeanDefinitions.
 *
 * 业务场景: 扫描 @RpcClient 接口, 为每个接口注册 FactoryBean 类型的 BD.
 * 这里简化为手动注册一个 RpcProxy BD.
 *
 * 源码路径:
 *   ConfigurationClassParser#processImports → candidate.isAssignable(Registrar.class)
 *   → configClass.addImportBeanDefinitionRegistrar(registrar, metadata)
 *   → [Reader 阶段] loadBeanDefinitionsFromRegistrars → registrar.registerBeanDefinitions()
 */
public class RpcClientRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
			BeanDefinitionRegistry registry) {

		System.out.println("  [RpcClientRegistrar] registerBeanDefinitions invoked!");

		RootBeanDefinition bd = new RootBeanDefinition();
		bd.setBeanClass(RpcProxy.class);
		bd.setRole(BeanDefinition.ROLE_APPLICATION);
		bd.getConstructorArgumentValues().addIndexedArgumentValue(0, "riskControlService");

		registry.registerBeanDefinition("riskControlRpcProxy", bd);
	}
}
