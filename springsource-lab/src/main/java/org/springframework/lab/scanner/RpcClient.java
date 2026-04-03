package org.springframework.lab.scanner;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解：标记 RPC 客户端接口
 * 业务场景：扫描所有 @RpcClient 接口，为其生成 FactoryBean 代理
 *
 * 类似 Dubbo 的 @DubboReference / Feign 的 @FeignClient 的底层原理
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcClient {
	String value() default "";
}
