package org.springframework.lab.aop;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 强制 CGLIB 代理配置 — 对比实验用
 *
 * <p>proxyTargetClass=true 时:
 * DefaultAopProxyFactory 无条件走 ObjenesisCglibAopProxy,
 * 即使 OrderServiceImpl 实现了 OrderService 接口也生成子类代理。
 *
 * <p>好处: 可以注入具体类型 (OrderServiceImpl) 而非必须注入接口
 * <p>代价: final 方法/类不能被代理, 内存多一份子类
 */
@Configuration
@ComponentScan("org.springframework.lab.aop")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopCglibConfig {
}
