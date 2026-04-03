package org.springframework.lab.naming;

/**
 * 【Abstract 前缀】模板骨架：定义流程，留 hook 给子类。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>AbstractApplicationContext#refresh() — 12 步骨架，obtainFreshBeanFactory() 留给子类</li>
 *   <li>AbstractBeanFactory#getBean() — doGetBean 骨架，createBean() 留给子类</li>
 *   <li>AbstractPlatformTransactionManager#getTransaction() — 事务模板，doBegin() 留给子类</li>
 * </ul>
 *
 * <p>命名规则：Abstract 前缀 = "我是骨架，不能直接实例化，子类必须实现 doXxx 方法"
 *
 * <p>与 Default 的区分：
 * Abstract 不能直接用（必须继承），Default 拿来就能用。
 * Abstract 定义"怎么做的流程"，Default 定义"做什么的默认值"。
 *
 * <pre>
 * 断点抓手：AbstractApplicationContext#refresh() 第 554 行
 *   → 观察模板方法骨架：哪些步骤是 final 不可变的，哪些是 protected 可覆盖的
 * </pre>
 */
public abstract class AbstractPayChannel implements PayChannel {

	/**
	 * 模板方法骨架（对照 AbstractApplicationContext#refresh）
	 * 流程：validate → prepare → doExecute → onSuccess/onFailure
	 * 子类只需实现 doExecute()，骨架保证流程完整性。
	 */
	@Override
	public final PayResult pay(String orderId, long amountInCents) {
		// Step 1: 参数校验（骨架固定步骤）
		validate(orderId, amountInCents);
		System.out.println("    [Abstract] Step1 validate 通过");

		// Step 2: 准备上下文（可覆盖 hook）
		prepare(orderId);
		System.out.println("    [Abstract] Step2 prepare 完成");

		try {
			// Step 3: 核心执行（子类必须实现）
			PayResult result = doExecute(orderId, amountInCents);
			System.out.println("    [Abstract] Step3 doExecute 完成");

			// Step 4: 成功回调（可覆盖 hook）
			onSuccess(orderId, result);
			return result;
		}
		catch (Exception e) {
			// Step 4: 失败回调（可覆盖 hook）
			onFailure(orderId, e);
			return new PayResult(orderId, false, e.getMessage());
		}
	}

	// ─── 骨架固定步骤（private/final，子类不可更改）─────────────────────────

	private void validate(String orderId, long amountInCents) {
		if (orderId == null || orderId.isEmpty()) {
			throw new IllegalArgumentException("orderId 不能为空");
		}
		if (amountInCents <= 0) {
			throw new IllegalArgumentException("金额必须大于 0");
		}
	}

	// ─── 可覆盖 hook（protected，子类可选覆盖）───────────────────────────────

	/** 准备上下文，子类可覆盖。对照 AbstractApplicationContext#postProcessBeanFactory */
	protected void prepare(String orderId) {
		// 默认空实现，子类可选覆盖
	}

	/** 成功回调 hook */
	protected void onSuccess(String orderId, PayResult result) {
		// 默认空实现
	}

	/** 失败回调 hook */
	protected void onFailure(String orderId, Exception e) {
		System.out.println("    [Abstract] onFailure: " + e.getMessage());
	}

	// ─── 子类必须实现（abstract，子类必须覆盖）──────────────────────────────

	/** 核心支付逻辑，子类必须实现。对照 AbstractBeanFactory#createBean */
	protected abstract PayResult doExecute(String orderId, long amountInCents);
}
