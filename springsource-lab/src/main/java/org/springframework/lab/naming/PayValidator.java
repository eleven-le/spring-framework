package org.springframework.lab.naming;

/**
 * 支付校验器接口 —— Composite 模式的叶子节点契约。
 */
public interface PayValidator {

	/** 校验支付请求，返回 true 表示通过 */
	boolean validate(PayRequest request);

	/** 校验器名称（用于日志） */
	String name();
}
