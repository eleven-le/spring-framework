package org.springframework.lab.circulardep;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;

/**
 * W13 循环依赖与三级缓存 · 练兵场配置
 *
 * 注意: Scene2(构造器循环) 和 Scene4(prototype循环) 会在启动时抛异常,
 * 所以默认排除这两个包, 由 Main 中单独启动独立容器来验证。
 */
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(
		basePackages = "org.springframework.lab.circulardep",
		excludeFilters = {
				@ComponentScan.Filter(type = FilterType.REGEX,
						pattern = "org\\.springframework\\.lab\\.circulardep\\.scene2_constructor\\..*"),
				@ComponentScan.Filter(type = FilterType.REGEX,
						pattern = "org\\.springframework\\.lab\\.circulardep\\.scene4_prototype\\..*")
		}
)
public class CircularDepConfig {
}
