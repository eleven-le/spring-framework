package org.springframework.lab.naming;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * 【Processor 后缀】处理器：拦截→判断→增强/跳过。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>BeanPostProcessor — 每个 Bean 创建后拦截，before/after 两个时机</li>
 *   <li>BeanFactoryPostProcessor — 所有 BD 注册完后拦截，修改 BD 元数据</li>
 *   <li>EventListenerMethodProcessor — 扫描 @EventListener 方法，注册为监听器</li>
 *   <li>ConfigurationClassPostProcessor — 扫描 @Configuration 类，解析 @Bean/@Import</li>
 * </ul>
 *
 * <p>命名规则：Processor 后缀 = "我拦截你的输入，做增强或跳过，输出同类型对象"
 *
 * <p>与 Resolver 的区分（再次强调）：
 * Resolver: String → PayChannel（类型变了）
 * Processor: PayRequest → PayRequest（类型没变，内容增强了）
 *
 * <p>Processor 模式的核心矛盾：
 * 增强能力 vs 性能开销。每加一个 Processor，所有对象都要过一遍。
 * Spring 的解法：PriorityOrdered/Ordered 排序 + 类型检查短路。
 *
 * <pre>
 * 断点：PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors
 *   → 观察 Processor 的排序和调用链
 * </pre>
 */
@Component
public class PayRequestProcessor {

	/**
	 * 处理支付请求：添加追踪ID + 风控检查。
	 * 对照 BeanPostProcessor#postProcessBeforeInitialization：
	 *   输入是 Bean，输出还是 Bean（可能被增强/代理）。
	 */
	public PayRequest process(PayRequest request) {
		// Step 1: 添加追踪ID（对照 ApplicationContextAwareProcessor 注入 Aware）
		request.setTraceId(UUID.randomUUID().toString().substring(0, 8));
		System.out.println("    [Processor] Step1 添加 traceId=" + request.getTraceId());

		// Step 2: 风控检查（对照 AbstractAutoProxyCreator#postProcessAfterInitialization 的条件代理）
		if (request.getAmountInCents() > 50000) {
			request.setRiskTag("HIGH_AMOUNT");
			System.out.println("    [Processor] Step2 风控标记: HIGH_AMOUNT (金额>" + 50000 + "分)");
		}
		else {
			request.setRiskTag("NORMAL");
			System.out.println("    [Processor] Step2 风控标记: NORMAL");
		}

		// 返回同类型对象（增强后的 PayRequest）
		return request;
	}
}
