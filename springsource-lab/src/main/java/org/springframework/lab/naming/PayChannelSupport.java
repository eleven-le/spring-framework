package org.springframework.lab.naming;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 【Support 后缀】可复用工具基类，不是独立组件。
 *
 * <p>对照 Spring：
 * <ul>
 *   <li>ApplicationObjectSupport — 提供 getApplicationContext()/getMessageSourceAccessor() 等工具方法</li>
 *   <li>TransactionAspectSupport — 提供 createTransactionIfNecessary()/commitTransactionAfterReturning() 等事务工具</li>
 *   <li>WebContentGenerator — 提供 checkRequest()/prepareResponse() 等 HTTP 工具</li>
 * </ul>
 *
 * <p>命名规则：Support 后缀 = "我不是一个独立的组件，我是被继承的工具箱"
 *
 * <p>与 Abstract 的区分：
 * Abstract = 定义流程骨架（模板方法），关注"做事的顺序"。
 * Support = 提供工具方法（被继承复用），关注"需要什么工具"。
 * 一个类可以同时是 Abstract + Support：AbstractXxxSupport（先复用工具，再定义骨架）。
 */
public abstract class PayChannelSupport implements ApplicationContextAware, BeanNameAware {

	private ApplicationContext applicationContext;
	private String beanName;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	// ─── 工具方法（子类复用）─────────────────────────────────────────────────

	/** 获取 ApplicationContext（对照 ApplicationObjectSupport#getApplicationContext） */
	protected ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	/** 获取 Bean 名称 */
	protected String getBeanName() {
		return this.beanName;
	}

	/** 统一日志格式（子类复用） */
	protected void logPayment(String action, String orderId, long amount) {
		System.out.println("    [Support] [" + getBeanName() + "] " + action
				+ " orderId=" + orderId + " amount=" + amount + "分");
	}

	/** 简易重试工具（子类复用） */
	protected <T> T retryOnFailure(int maxRetries, java.util.function.Supplier<T> action) {
		Exception lastEx = null;
		for (int i = 1; i <= maxRetries; i++) {
			try {
				return action.get();
			}
			catch (Exception e) {
				lastEx = e;
				System.out.println("    [Support] 重试 " + i + "/" + maxRetries + ": " + e.getMessage());
			}
		}
		throw new RuntimeException("重试 " + maxRetries + " 次后仍失败", lastEx);
	}

	/** 从容器获取配置值（子类复用） */
	protected String getConfigValue(String key, String defaultValue) {
		String value = applicationContext.getEnvironment().getProperty(key);
		return value != null ? value : defaultValue;
	}
}
