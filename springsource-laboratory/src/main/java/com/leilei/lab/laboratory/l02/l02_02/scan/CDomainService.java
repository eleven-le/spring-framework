package com.leilei.lab.laboratory.l02.l02_02.scan;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（自定义 stereotype / 默认扫描过滤器）
 * 🎯 作用：产品中心自定义业务语义注解——元注解 {@link Component}，
 *         因此默认的 {@code AnnotationTypeFilter(Component.class)} 包含过滤器会把它当组件扫到。
 * 🔗 业务场景：团队不想满屏 {@code @Service}，约定用 {@code @CDomainService} 标记「C 端领域服务」，
 *         既能被扫描，又携带统一的命名/分层语义。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface CDomainService {

	/** 透传给 {@code @Component#value}（@AliasFor 桥接），作为显式 Bean 名（留空则走默认命名规则）。 */
	@AliasFor(annotation = Component.class)
	String value() default "";
}
