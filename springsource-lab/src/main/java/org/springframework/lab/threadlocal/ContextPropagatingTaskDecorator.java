package org.springframework.lab.threadlocal;

import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * W49 — 上下文传播 TaskDecorator（生产级写法）
 *
 * <p>核心三板斧：
 * <ol>
 *   <li><b>capture</b>（提交线程）：快照当前线程的业务上下文 + Web 请求上下文</li>
 *   <li><b>restore</b>（执行线程 run 开头）：将快照恢复到执行线程</li>
 *   <li><b>clear</b>（finally）：无论成败必须清理，防止线程池复用导致泄漏</li>
 * </ol>
 *
 * <p>关键设计决策：
 * - 传播 BizContext（traceId/tenant/bizKey/caller）✓
 * - 传播 RequestAttributes（Controller 层需要异步读 header/session）✓
 * - <b>不传播</b> TransactionSynchronizationManager（事务/连接不可跨线程）✗
 * - <b>不传播</b> AopContext.currentProxy()（AOP 语义不跨线程）✗
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		// ──── 1. capture：在提交线程执行（此时 ThreadLocal 有值）────
		final BizContextHolder.BizContext bizSnapshot = BizContextHolder.capture();
		final RequestAttributes reqSnapshot = RequestContextHolder.getRequestAttributes();

		return () -> {
			// ──── 2. restore：在执行线程（线程池工作线程）开头 ────
			BizContextHolder.restore(bizSnapshot);
			if (reqSnapshot != null) {
				RequestContextHolder.setRequestAttributes(reqSnapshot);
			}
			try {
				// ──── 执行真正的业务逻辑 ────
				runnable.run();
			}
			finally {
				// ──── 3. clear：无论成败，执行线程 finally 清理 ────
				BizContextHolder.clear();
				RequestContextHolder.resetRequestAttributes();
			}
		};
	}
}
