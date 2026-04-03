package org.springframework.lab.naming;

import java.util.ArrayList;
import java.util.List;

/**
 * 【Composite 后缀】组合器：将多个同类对象组合成一个，对外表现为单个对象。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>HandlerMethodArgumentResolverComposite — 组合多个参数解析器，遍历找第一个能解析的</li>
 *   <li>HandlerMethodReturnValueHandlerComposite — 组合多个返回值处理器</li>
 *   <li>WebMvcConfigurerComposite — 组合多个 WebMvcConfigurer，逐一回调收集配置</li>
 *   <li>CompositeComponentDefinition — 聚合多个组件定义</li>
 *   <li>CompositeCacheManager — 组合多个 CacheManager</li>
 * </ul>
 *
 * <p>命名规则：XxxComposite 或 CompositeXxx = "我把多个同类对象组合成一个，调用方无感"
 *
 * <p>Spring 两种命名风格：
 * <ul>
 *   <li>后缀式：HandlerMethodArgumentResolver<b>Composite</b>（强调"这是一个组合"）</li>
 *   <li>前缀式：<b>Composite</b>CacheManager（强调"被组合的是 CacheManager"）</li>
 * </ul>
 *
 * <p>设计意图：调用方不关心内部有几个实现，统一调用一个 Composite 即可。
 * 新增校验规则只需 addValidator，不改调用方代码（开闭原则）。
 */
public class CompositePayValidator implements PayValidator {

	private final List<PayValidator> validators = new ArrayList<>();

	public void addValidator(PayValidator validator) {
		this.validators.add(validator);
	}

	/**
	 * 遍历所有校验器，全部通过才算通过。
	 * 对照 HandlerMethodArgumentResolverComposite#resolveArgument — 遍历找匹配的
	 */
	@Override
	public boolean validate(PayRequest request) {
		for (PayValidator validator : validators) {
			if (!validator.validate(request)) {
				System.out.println("    [Composite] 校验失败 @ " + validator.name());
				return false;
			}
			System.out.println("    [Composite] 校验通过 @ " + validator.name());
		}
		return true;
	}

	@Override
	public String name() {
		return "CompositePayValidator(" + validators.size() + " validators)";
	}
}
