package com.leilei.lab.laboratory.l02.l02_07;

import java.util.concurrent.atomic.AtomicReference;

import com.leilei.lab.laboratory.common.domain.Channel;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.SimpleThreadScope;

/**
 * 📖 知识点：[[L02-07-作用域-scope代理与按需注入#2.1 四种作用域语义]]（singleton / prototype / request / session 作用域语义）
 * 🎯 作用：把「同一个 Bean 在四种作用域下，getBean 拿到的是不是同一实例」坐实成可运行实验。
 *         singleton——容器内全局唯一（默认）；prototype——每次 getBean 都新建；
 *         request / session——绑定到一次请求 / 一个会话，本类用 {@link SimpleThreadScope}（线程绑定）
 *         在无 Web 容器的 main 里等价模拟「一次请求 = 一个线程」：同线程多次取同一实例、跨线程取不同实例。
 *         判定依据是 {@code AbstractBeanFactory#doGetBean} 里 singleton / prototype / 自定义 Scope 三条分支。
 * 🔗 业务场景：产品中心一次下单链路里——价格目录服务（无状态，singleton 复用）、下单草稿（每次新建，prototype）、
 *         下单请求上下文（绑定当前请求线程，request）、用户会话（绑定登录会话，session）。
 *         作用域选错会让「请求级状态」被跨请求共享（见 [[L02-07-作用域-scope代理与按需注入#事故一]]）。
 */
public final class L0207_01_BeanScopeSemanticsDemo {

	private L0207_01_BeanScopeSemanticsDemo() {
	}

	public static void main(String[] args) throws InterruptedException {
		// 无 Web 容器的 main 里手动注册 request/session 作用域：用 SimpleThreadScope（线程绑定）等价模拟
		// 「一次请求 = 一个线程」。真实 Web 环境由 spring-web 在 refresh 时注册 RequestScope/SessionScope。
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.getBeanFactory().registerScope("request", new SimpleThreadScope());
		ctx.getBeanFactory().registerScope("session", new SimpleThreadScope());
		ctx.register(ScopeConfig.class);
		ctx.refresh();

		System.out.println("==================== 四种作用域语义（同一类型两次取 Bean，是否同一实例）====================");

		// singleton：容器内全局唯一
		PriceCatalogService s1 = ctx.getBean(PriceCatalogService.class);
		PriceCatalogService s2 = ctx.getBean(PriceCatalogService.class);
		System.out.println("  singleton  价格目录服务   ：两次同一实例? " + (s1 == s2) + "（无状态服务，全局复用一份）");

		// prototype：每次 getBean 都新建
		OrderDraft d1 = ctx.getBean(OrderDraft.class);
		OrderDraft d2 = ctx.getBean(OrderDraft.class);
		System.out.println("  prototype  下单草稿       ：两次同一实例? " + (d1 == d2) + "（每次取都是全新草稿，容器创建后撒手不管）");

		// request：同线程同实例
		OrderRequestContext r1 = ctx.getBean(OrderRequestContext.class);
		OrderRequestContext r2 = ctx.getBean(OrderRequestContext.class);
		System.out.println("  request    下单请求上下文 ：同线程两次同一实例? " + (r1 == r2) + "（同一请求线程内复用一份上下文）");

		// request：跨线程不同实例（另起一个线程 = 另一次请求）
		final AtomicReference<OrderRequestContext> fromOtherThread = new AtomicReference<>();
		Thread anotherRequest = new Thread(() -> fromOtherThread.set(ctx.getBean(OrderRequestContext.class)), "request-2");
		anotherRequest.start();
		anotherRequest.join();
		System.out.println("  request    下单请求上下文 ：跨线程同一实例? " + (r1 == fromOtherThread.get()) + "（另一请求线程拿到独立上下文）");

		// session：同线程同实例（与 request 同为 SimpleThreadScope，差异在真实 Web 下的生命周期长短）
		UserSession u1 = ctx.getBean(UserSession.class);
		UserSession u2 = ctx.getBean(UserSession.class);
		System.out.println("  session    用户会话       ：同线程两次同一实例? " + (u1 == u2) + "（会话级状态，比 request 活得更久）");

		System.out.println();
		System.out.println("[小结] 作用域决定「实例的存活边界」：singleton=容器、prototype=每次、request=一次请求、session=一个会话");

		ctx.close();
	}

	@Configuration
	static class ScopeConfig {

		/** 无状态价格目录服务：默认 singleton，全局复用一份最省内存。 */
		@Bean
		PriceCatalogService priceCatalogService() {
			return new PriceCatalogService();
		}

		/** 下单草稿：每次下单都是独立的一份，prototype 最贴切。 */
		@Bean
		@Scope("prototype")
		OrderDraft orderDraft() {
			return new OrderDraft();
		}

		/** 下单请求上下文：绑定当前请求（线程），request 作用域。 */
		@Bean
		@Scope("request")
		OrderRequestContext orderRequestContext() {
			return new OrderRequestContext();
		}

		/** 用户会话：绑定登录会话，session 作用域，比 request 活得更久。 */
		@Bean
		@Scope("session")
		UserSession userSession() {
			return new UserSession();
		}
	}

	/** 无状态：只读价格目录，singleton 复用。 */
	static class PriceCatalogService {
	}

	/** 一次下单的草稿明细，prototype。 */
	static class OrderDraft {
	}

	/** 一次请求的上下文（门店 / 渠道 / 用户分层），request。 */
	static class OrderRequestContext {

		long storeId;
		Channel channel;
	}

	/** 一个登录会话的用户状态，session。 */
	static class UserSession {

		long userId;
	}
}
