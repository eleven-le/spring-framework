package org.springframework.lab.threadlocal;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import java.util.Locale;

/**
 * W49 — ThreadLocal 边界与上下文传播总览 练兵场入口
 *
 * <p>7 个实验覆盖 ThreadLocal 的四大边界（TX / Web / Biz / Locale）与传播模型：
 * <ol>
 *   <li>四大 ThreadLocal Holder 的默认状态与边界</li>
 *   <li>跨线程上下文丢失（裸线程池提交后全部断裂）</li>
 *   <li>TaskDecorator 传播可观测上下文（capture → restore → finally clear）</li>
 *   <li>TX 上下文不应传播的证明（连接不可跨线程）</li>
 *   <li>InheritableThreadLocal vs ThreadLocal（new Thread vs 线程池复用）</li>
 *   <li>AFTER_COMMIT + 异步传播（事务提交后安全发出站消息）</li>
 *   <li>ThreadLocal 泄漏演示（线程池复用时前一任务的上下文污染下一任务）</li>
 * </ol>
 */
public class ThreadLocalBoundaryMain {

	public static void main(String[] args) throws Exception {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(ThreadLocalBoundaryConfig.class);

		ThreadPoolTaskExecutor rawExecutor = ctx.getBean("rawExecutor", ThreadPoolTaskExecutor.class);
		ThreadPoolTaskExecutor decoratedExecutor = ctx.getBean("decoratedExecutor", ThreadPoolTaskExecutor.class);
		PlatformTransactionManager tm = ctx.getBean(PlatformTransactionManager.class);
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
		TransactionTemplate txTemplate = new TransactionTemplate(tm);

		// ═══════════════════════════════════════════════════════════════
		// 实验 1: 四大 ThreadLocal Holder 的默认状态与边界
		// ═══════════════════════════════════════════════════════════════
		System.out.println("═══ 实验1: 四大 ThreadLocal Holder 的默认状态 ═══");

		// 设置业务上下文（模拟入站 Filter）
		BizContextHolder.set(new BizContextHolder.BizContext(
				"trace-001", "tenant-A", "ORD-2024-001", "api-gateway"));
		// 设置 Locale 上下文（模拟 DispatcherServlet）
		LocaleContextHolder.setLocaleContext(new SimpleLocaleContext(Locale.CHINA));
		// 设置 Web 请求上下文（模拟 RequestContextFilter — 这里用 null 模拟，只看结构）
		// 注：非 Web 环境无法构造真实 ServletRequestAttributes，仅用自定义 RequestAttributes 演示
		RequestContextHolder.setRequestAttributes(new SimpleRequestAttributes("REQ-001"));

		System.out.println("── 主线程（模拟请求线程）──");
		printAllContexts("main");

		// 进入事务看 TX 上下文
		txTemplate.executeWithoutResult(status -> {
			System.out.println("── 主线程（事务内）──");
			printAllContexts("main-tx");
			System.out.println("  TX resources 数量: " +
					TransactionSynchronizationManager.getResourceMap().size());
		});

		System.out.println("── 主线程（事务外）──");
		System.out.println("  TX isSyncActive: " +
				TransactionSynchronizationManager.isSynchronizationActive());

		// ═══════════════════════════════════════════════════════════════
		// 实验 2: 跨线程上下文丢失（裸线程池）
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验2: 裸线程池 — 上下文全部丢失 ═══");
		CountDownLatch latch2 = new CountDownLatch(1);
		rawExecutor.execute(() -> {
			System.out.println("── raw-pool 工作线程 ──");
			printAllContexts("raw-pool");
			// 关键观察：BizContext=null, Locale=默认, Request=null, TX=false
			latch2.countDown();
		});
		latch2.await(5, TimeUnit.SECONDS);

		// ═══════════════════════════════════════════════════════════════
		// 实验 3: TaskDecorator 传播可观测上下文
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验3: TaskDecorator — 可观测上下文传播 ═══");
		CountDownLatch latch3 = new CountDownLatch(1);
		decoratedExecutor.execute(() -> {
			System.out.println("── ctx-pool 工作线程（TaskDecorator 生效）──");
			printAllContexts("ctx-pool");
			// 关键观察：BizContext 已恢复, Request 已恢复, TX 仍为 false（正确！不传播事务）
			latch3.countDown();
		});
		latch3.await(5, TimeUnit.SECONDS);

		// ═══════════════════════════════════════════════════════════════
		// 实验 4: TX 上下文不应跨线程传播（证明）
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验4: TX 上下文不应传播（线程安全证明）═══");
		txTemplate.executeWithoutResult(status -> {
			// 事务内提交异步任务
			Map<Object, Object> txResources = TransactionSynchronizationManager.getResourceMap();
			System.out.println("  主线程 TX resources: " + txResources.size()
					+ " (DataSource→ConnectionHolder)");
			boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
			System.out.println("  主线程 syncActive: " + syncActive);

			CountDownLatch innerLatch = new CountDownLatch(1);
			rawExecutor.execute(() -> {
				System.out.println("  异步线程 TX resources: " +
						TransactionSynchronizationManager.getResourceMap().size() + " (应为 0)");
				System.out.println("  异步线程 syncActive: " +
						TransactionSynchronizationManager.isSynchronizationActive() + " (应为 false)");
				System.out.println("  → 正确！连接/事务不可跨线程共享，否则并发写同一 Connection");
				innerLatch.countDown();
			});
			try { innerLatch.await(5, TimeUnit.SECONDS); }
			catch (InterruptedException ignored) {}
		});

		// ═══════════════════════════════════════════════════════════════
		// 实验 5: InheritableThreadLocal vs ThreadLocal
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验5: InheritableThreadLocal（new Thread 可继承，池复用不可）═══");
		// RequestContextHolder 支持 inheritable 模式
		RequestContextHolder.setRequestAttributes(
				new SimpleRequestAttributes("REQ-INHERIT"), true);
		LocaleContextHolder.setLocaleContext(
				new SimpleLocaleContext(Locale.JAPAN), true);

		// 5a: new Thread — InheritableThreadLocal 生效
		Thread childThread = new Thread(() -> {
			System.out.println("  [new Thread] Request: " +
					RequestContextHolder.getRequestAttributes());
			System.out.println("  [new Thread] Locale: " +
					LocaleContextHolder.getLocale());
			System.out.println("  → InheritableThreadLocal 对 new Thread 子线程有效");
		});
		childThread.start();
		childThread.join();

		// 5b: 线程池 — 线程已创建，不会重新继承
		CountDownLatch latch5 = new CountDownLatch(1);
		rawExecutor.execute(() -> {
			RequestAttributes reqAttr = RequestContextHolder.getRequestAttributes();
			System.out.println("  [线程池] Request: " + reqAttr);
			System.out.println("  → 线程池的线程在池初始化时已创建，不再走 InheritableThreadLocal");
			System.out.println("  → 这就是为什么线程池必须用 TaskDecorator，不能靠 Inheritable");
			latch5.countDown();
		});
		latch5.await(5, TimeUnit.SECONDS);

		// 恢复为非 inheritable 模式
		RequestContextHolder.setRequestAttributes(new SimpleRequestAttributes("REQ-001"));
		LocaleContextHolder.setLocaleContext(new SimpleLocaleContext(Locale.CHINA));

		// ═══════════════════════════════════════════════════════════════
		// 实验 6: AFTER_COMMIT + 异步传播完整链路
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验6: AFTER_COMMIT + 异步传播（事务安全出站）═══");
		CountDownLatch latch6 = new CountDownLatch(1);

		// 模拟入站上下文
		BizContextHolder.set(new BizContextHolder.BizContext(
				"trace-002", "tenant-B", "PAY-2024-002", "checkout-svc"));

		txTemplate.executeWithoutResult(status -> {
			// 1. 事务内写库
			jdbc.update("INSERT INTO orders(order_id, amount) VALUES(?, ?)", "PAY-2024-002", 199);
			System.out.println("  [TX内] 订单已写入 (PAY-2024-002)");

			// 2. 注册 afterCommit 回调 — 事务真提交后才触发异步
			TransactionSynchronization afterCommitSync = new TransactionSynchronization() {
				// 在注册时（提交线程）捕获上下文快照
				final BizContextHolder.BizContext snapshot = BizContextHolder.capture();

				@Override
				public void afterCommit() {
					System.out.println("  [afterCommit] 事务已提交，开始发送异步通知");
					// 用带 TaskDecorator 的线程池提交
					decoratedExecutor.execute(() -> {
						System.out.println("  [async] BizContext: " + BizContextHolder.get());
						System.out.println("  [async] → traceId 贯穿到异步出站，但连接/事务早已释放");
						latch6.countDown();
					});
				}
			};
			TransactionSynchronizationManager.registerSynchronization(afterCommitSync);
		});
		latch6.await(5, TimeUnit.SECONDS);

		// 验证
		Integer orderCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM orders WHERE order_id = ?", Integer.class, "PAY-2024-002");
		System.out.println("  验证: PAY-2024-002 行数 = " + orderCount);

		// ═══════════════════════════════════════════════════════════════
		// 实验 7: ThreadLocal 泄漏演示（线程池复用污染）
		// ═══════════════════════════════════════════════════════════════
		System.out.println("\n═══ 实验7: ThreadLocal 泄漏（线程池复用污染）═══");

		// 先清理主线程上下文
		BizContextHolder.clear();
		RequestContextHolder.resetRequestAttributes();

		// 7a: 故意不清理的任务（模拟开发者忘记 finally clear）
		CountDownLatch latch7a = new CountDownLatch(1);
		rawExecutor.execute(() -> {
			BizContextHolder.set(new BizContextHolder.BizContext(
					"trace-LEAKED", "tenant-LEAKED", "LEAKED-KEY", "leaked-caller"));
			System.out.println("  [task-A] 设置了上下文但没有 clear（模拟泄漏）");
			System.out.println("  [task-A] 线程: " + Thread.currentThread().getName());
			latch7a.countDown();
		});
		latch7a.await(5, TimeUnit.SECONDS);

		// 7b: 下一个任务复用同一线程，读到了上一任务的脏数据
		// 提交多个任务让其中一个复用到泄漏线程
		CountDownLatch latch7b = new CountDownLatch(2);
		for (int i = 0; i < 2; i++) {
			final int idx = i;
			rawExecutor.execute(() -> {
				BizContextHolder.BizContext leaked = BizContextHolder.get();
				if (leaked != null) {
					System.out.println("  [task-B" + idx + "] ⚠ 读到了泄漏的上下文: "
							+ leaked.getTraceId() + " / " + leaked.getTenant());
					System.out.println("  [task-B" + idx + "] 线程: "
							+ Thread.currentThread().getName());
					System.out.println("  → 这就是串号/串租户的根因！");
				}
				else {
					System.out.println("  [task-B" + idx + "] 上下文为 null（干净线程）");
				}
				latch7b.countDown();
			});
		}
		latch7b.await(5, TimeUnit.SECONDS);

		System.out.println("\n  结论: TaskDecorator 的 finally clear 就是为了防止实验7的泄漏");
		System.out.println("  规则: 凡是线程池里用 ThreadLocal，必须 try-finally-clear");

		// ═══════════════════════════════════════════════════════════════
		// 清理
		// ═══════════════════════════════════════════════════════════════
		BizContextHolder.clear();
		RequestContextHolder.resetRequestAttributes();
		LocaleContextHolder.resetLocaleContext();

		rawExecutor.shutdown();
		decoratedExecutor.shutdown();
		ctx.close();

		System.out.println("\n═══ 全部实验完成 ═══");
	}

	// ========================= 辅助方法 =========================

	/**
	 * 打印四大上下文的当前状态
	 */
	private static void printAllContexts(String label) {
		System.out.println("  [" + label + "] thread=" + Thread.currentThread().getName());
		System.out.println("  [" + label + "] BizContext=" + BizContextHolder.get());
		System.out.println("  [" + label + "] Locale=" + LocaleContextHolder.getLocale());
		System.out.println("  [" + label + "] Request=" + RequestContextHolder.getRequestAttributes());
		System.out.println("  [" + label + "] TX syncActive=" +
				TransactionSynchronizationManager.isSynchronizationActive());
	}

	// ========================= 简易 RequestAttributes 实现 =========================

	/**
	 * 简易 RequestAttributes 实现（非 Web 环境替代 ServletRequestAttributes）
	 * 仅用于演示 ThreadLocal 边界，真实项目中由 DispatcherServlet 自动创建
	 */
	static class SimpleRequestAttributes implements RequestAttributes {
		private final String requestId;

		SimpleRequestAttributes(String requestId) {
			this.requestId = requestId;
		}

		@Override public Object getAttribute(String name, int scope) { return null; }
		@Override public void setAttribute(String name, Object value, int scope) {}
		@Override public void removeAttribute(String name, int scope) {}
		@Override public String[] getAttributeNames(int scope) { return new String[0]; }
		@Override public void registerDestructionCallback(String name, Runnable callback, int scope) {}
		@Override public Object resolveReference(String key) { return null; }
		@Override public String getSessionId() { return "session-" + requestId; }
		@Override public Object getSessionMutex() { return this; }

		@Override
		public String toString() {
			return "SimpleRequestAttributes{requestId='" + requestId + "'}";
		}
	}
}
