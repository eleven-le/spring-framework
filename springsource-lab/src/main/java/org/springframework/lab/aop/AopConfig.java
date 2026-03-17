package org.springframework.lab.aop;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AOP 配置类
 *
 * <p>@EnableAspectJAutoProxy 的作用链路:
 * <pre>
 * 1. @Import(AspectJAutoProxyRegistrar.class)
 * 2. AspectJAutoProxyRegistrar#registerBeanDefinitions
 * 3. AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary
 * 4. 注册 AnnotationAwareAspectJAutoProxyCreator (bean名: internalAutoProxyCreator)
 *    → 它是 SmartInstantiationAwareBeanPostProcessor
 *    → 在 postProcessAfterInitialization 阶段 wrapIfNecessary
 * </pre>
 *
 * <p>proxyTargetClass=false (默认): 有接口用 JDK, 无接口用 CGLIB
 * <p>proxyTargetClass=true: 全部用 CGLIB
 * <p>exposeProxy=true: 通过 AopContext.currentProxy() 获取当前代理 (解决自调用失效)
 */
@Configuration
@ComponentScan("org.springframework.lab.aop")
@EnableAspectJAutoProxy   // 默认 proxyTargetClass=false
public class AopConfig {
}
