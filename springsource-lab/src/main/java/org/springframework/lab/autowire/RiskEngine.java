package org.springframework.lab.autowire;

/**
 * 风控引擎接口 —— 灰度组件, 可能不存在
 * 演示: ObjectProvider.getIfAvailable() 处理可选依赖
 */
public interface RiskEngine {
	boolean evaluate(String orderId);
}
