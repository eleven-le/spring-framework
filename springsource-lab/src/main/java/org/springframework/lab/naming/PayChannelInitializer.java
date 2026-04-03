package org.springframework.lab.naming;

/**
 * 【Initializer 后缀】初始化器：在生命周期早期执行一次性设置。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>ApplicationContextInitializer — 在 refresh() 之前初始化上下文（最早介入点）</li>
 *   <li>WebApplicationInitializer — 替代 web.xml 的编程式 Servlet 初始化</li>
 *   <li>ServletContainerInitializer — Servlet 3.0 SPI 初始化入口</li>
 *   <li>ConnectionFactoryInitializer — 用 SQL 脚本初始化数据库</li>
 * </ul>
 *
 * <p>命名规则：XxxInitializer = "我在 Xxx 启动前做一次性初始化，之后不再调用"
 *
 * <p>关键区分：
 * <ul>
 *   <li>vs Processor：Processor 可能<b>多次调用</b>（每个Bean都过一遍），Initializer <b>只调一次</b>（启动时）</li>
 *   <li>vs Configurer：Configurer 收集配置<b>参数</b>（声明式），Initializer 执行初始化<b>动作</b>（命令式）</li>
 *   <li>vs Listener(ContextRefreshed)：Initializer 在 refresh <b>之前</b>，Listener 在 refresh <b>之后</b></li>
 * </ul>
 *
 * <p>设计意图：给框架使用者提供"最早的介入时机"，比如 Spring Boot 的
 * ApplicationContextInitializer 可以在 refresh 前注入 PropertySource、切换 Profile。
 */
public interface PayChannelInitializer {

	/** 在支付系统启动前执行——用于预热/校验/资源分配 */
	void initialize();

	/** 优先级，数字越小越先执行——对照 Ordered 接口 */
	default int getOrder() {
		return 0;
	}
}
