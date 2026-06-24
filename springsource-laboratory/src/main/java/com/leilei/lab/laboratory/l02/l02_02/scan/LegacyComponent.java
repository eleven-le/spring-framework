package com.leilei.lab.laboratory.l02.l02_02.scan;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * 📖 知识点：[[L02-02-Configuration与Bean注册全姿势#2. 🏭 生产怎么用对]]（excludeFilters 排除目标）
 * 🎯 作用：标记「待下线的旧定价组件」——同样元注解 {@link Component} 所以默认会被扫到，
 *         正因如此才需要 {@code @ComponentScan(excludeFilters=...)} 把它显式排除。
 * 🔗 业务场景：旧的固定立减逻辑要灰度下线，代码还没删，但绝不能再被装配进容器误用。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface LegacyComponent {
}
