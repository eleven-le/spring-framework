package org.springframework.lab.naming;

/**
 * 【Configurer 后缀】配置者：回调式配置接口，用户实现此接口来定制框架行为。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>WebMvcConfigurer — 配置拦截器/格式化器/视图解析器/CORS</li>
 *   <li>AsyncConfigurer — 配置异步执行器和异常处理器</li>
 *   <li>SchedulingConfigurer — 配置定时任务线程池和触发器</li>
 *   <li>CachingConfigurer — 配置 CacheManager 和 KeyGenerator</li>
 *   <li>WebFluxConfigurer — 配置 WebFlux 组件</li>
 * </ul>
 *
 * <p>命名规则：XxxConfigurer = "我是配置回调接口，框架启动时调我来收集配置"
 *
 * <p>关键区分：
 * <ul>
 *   <li>vs Configurable：Configurable 是"我<b>可以被</b>配置"（暴露setter），Configurer 是"我来配置<b>别人</b>"（回调）</li>
 *   <li>vs Initializer：Initializer 执行初始化<b>动作</b>（命令式），Configurer 收集配置<b>参数</b>（声明式）</li>
 *   <li>vs Customizer：Customizer 定制<b>单个对象</b>（如 WebServerFactoryCustomizer），Configurer 配置<b>整个子系统</b></li>
 * </ul>
 *
 * <p>设计意图：Java 8 接口 default 方法让 Configurer 全部方法可选覆盖，
 * 用户只关心要改的配置项，其他保持默认——比 XML/Properties 更类型安全。
 */
public interface PayChannelConfigurer {

	/** 配置支付渠道注册表 —— 对照 WebMvcConfigurer#addInterceptors */
	default void configureChannels(PayChannelResolver resolver) {
	}

	/** 配置全局超时（毫秒）—— 对照 AsyncConfigurer#getAsyncExecutor */
	default long getTimeoutMs() {
		return 30000;
	}

	/** 是否开启风控 —— 对照 WebMvcConfigurer 中各种 enable 配置 */
	default boolean enableRiskControl() {
		return true;
	}
}
