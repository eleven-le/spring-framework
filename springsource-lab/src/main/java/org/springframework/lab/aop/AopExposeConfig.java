package org.springframework.lab.aop;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * exposeProxy=true 配置 — 允许 AopContext.currentProxy()
 *
 * <p>源码路径: JdkDynamicAopProxy#invoke / CglibAopProxy.DynamicAdvisedInterceptor#intercept
 * → if (this.advised.exposeProxy) AopContext.setCurrentProxy(proxy)
 */
@Configuration
@ComponentScan("org.springframework.lab.aop")
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class AopExposeConfig {
}
