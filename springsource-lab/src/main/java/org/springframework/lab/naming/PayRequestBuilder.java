package org.springframework.lab.naming;

/**
 * 【Builder 后缀】构建器：流式链式构建复杂对象。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>BeanDefinitionBuilder — 流式构建 BeanDefinition（属性/构造器参数/作用域等）</li>
 *   <li>UriComponentsBuilder — 流式构建 URI（scheme/host/path/queryParam）</li>
 *   <li>MockMvcRequestBuilders — 流式构建测试请求（get/post/header/content）</li>
 *   <li>ResponseEntity.BodyBuilder — 流式构建 HTTP 响应（status/header/body）</li>
 * </ul>
 *
 * <p>命名规则：XxxBuilder = "我用链式调用一步步构建 Xxx，最后 build() 出成品"
 *
 * <p>设计意图：
 * <ul>
 *   <li>参数多时避免构造器爆炸（构造器参数 4+ 个就该用 Builder）</li>
 *   <li>比 setter 更安全：build() 前做校验，build() 后对象不可变</li>
 *   <li>Spring 风格：静态工厂入口 create()/of() + 链式 set + 终结 build()</li>
 * </ul>
 */
public class PayRequestBuilder {

	private String orderId;
	private long amountInCents;
	private String channelCode;
	private String traceId;
	private String riskTag;

	/** 私有构造器，强制用静态工厂入口（对照 BeanDefinitionBuilder.genericBeanDefinition()） */
	private PayRequestBuilder() {
	}

	/** 静态工厂入口（对照 UriComponentsBuilder.fromPath()） */
	public static PayRequestBuilder create() {
		return new PayRequestBuilder();
	}

	public PayRequestBuilder orderId(String orderId) {
		this.orderId = orderId;
		return this;
	}

	public PayRequestBuilder amount(long amountInCents) {
		this.amountInCents = amountInCents;
		return this;
	}

	public PayRequestBuilder channel(String channelCode) {
		this.channelCode = channelCode;
		return this;
	}

	public PayRequestBuilder traceId(String traceId) {
		this.traceId = traceId;
		return this;
	}

	public PayRequestBuilder riskTag(String riskTag) {
		this.riskTag = riskTag;
		return this;
	}

	/** 终结方法：校验 + 构建不可变对象 */
	public PayRequest build() {
		if (orderId == null) {
			throw new IllegalStateException("orderId 必填");
		}
		if (amountInCents <= 0) {
			throw new IllegalStateException("amount 必须 > 0");
		}
		if (channelCode == null) {
			throw new IllegalStateException("channelCode 必填");
		}

		PayRequest req = new PayRequest(orderId, amountInCents, channelCode);
		if (traceId != null) {
			req.setTraceId(traceId);
		}
		if (riskTag != null) {
			req.setRiskTag(riskTag);
		}
		return req;
	}
}
