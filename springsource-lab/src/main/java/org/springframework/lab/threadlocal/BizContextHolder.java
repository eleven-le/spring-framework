package org.springframework.lab.threadlocal;

import org.springframework.core.NamedThreadLocal;
import org.springframework.lang.Nullable;

/**
 * W49 — 自定义业务上下文 Holder（对标 MDC / SecurityContextHolder 的 ThreadLocal 持有者模式）
 *
 * <p>生产中 traceId/tenant/bizKey/caller 等字段通常由入站 Filter/Interceptor 设置，
 * 出站 Interceptor 注入到 Header，异步通过 TaskDecorator 传播。
 *
 * <p>设计要点：
 * - 与 RequestContextHolder、SecurityContextHolder 完全一致的 static ThreadLocal 模式
 * - capture() + restore() + clear() 三板斧用于跨线程传播
 * - 不传播事务资源，只传播可观测/业务语义字段
 */
public abstract class BizContextHolder {

	private static final ThreadLocal<BizContext> contextHolder =
			new NamedThreadLocal<>("Business context (traceId/tenant/bizKey/caller)");

	public static void set(BizContext ctx) {
		if (ctx == null) {
			contextHolder.remove();
		}
		else {
			contextHolder.set(ctx);
		}
	}

	@Nullable
	public static BizContext get() {
		return contextHolder.get();
	}

	public static void clear() {
		contextHolder.remove();
	}

	/**
	 * 快照捕获 —— 在提交线程（主线程）调用，得到不可变副本
	 */
	@Nullable
	public static BizContext capture() {
		BizContext current = contextHolder.get();
		return current != null ? current.copy() : null;
	}

	/**
	 * 恢复快照 —— 在执行线程（异步线程）调用
	 */
	public static void restore(@Nullable BizContext snapshot) {
		if (snapshot != null) {
			contextHolder.set(snapshot);
		}
	}

	// ========================= 内部值对象 =========================

	public static class BizContext {
		private final String traceId;
		private final String tenant;
		private final String bizKey;
		private final String caller;

		public BizContext(String traceId, String tenant, String bizKey, String caller) {
			this.traceId = traceId;
			this.tenant = tenant;
			this.bizKey = bizKey;
			this.caller = caller;
		}

		public BizContext copy() {
			return new BizContext(traceId, tenant, bizKey, caller);
		}

		public String getTraceId() { return traceId; }
		public String getTenant() { return tenant; }
		public String getBizKey() { return bizKey; }
		public String getCaller() { return caller; }

		@Override
		public String toString() {
			return "BizContext{traceId='" + traceId + "', tenant='" + tenant
					+ "', bizKey='" + bizKey + "', caller='" + caller + "'}";
		}
	}
}
