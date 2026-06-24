package com.leilei.lab.laboratory.l02.l02_06;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 📖 知识点：[[L02-06-Bean生命周期回调全图#2.3 Aware 回调时机表]]（Aware 回调时机；与 [[L09-04-Aware全家与注入时机]] 互链，本章侧重时机）
 * 🎯 作用：把「一个 Bean 实现多个 Aware + 三类 init/destroy 回调时，全链路谁先谁后」打成一张可运行的时机表。
 *         关键结论：{@link BeanNameAware}/{@link BeanClassLoaderAware}/{@link BeanFactoryAware} 这一组由
 *         {@code AbstractAutowireCapableBeanFactory#invokeAwareMethods} 在 {@code initializeBean} 最开头**硬编码直调**，
 *         早于一切 BeanPostProcessor；而 {@link EnvironmentAware}/{@link ApplicationContextAware} 这一组靠
 *         {@code ApplicationContextAwareProcessor}（一个 BPP）在 before-init 阶段回调，晚于前一组、与 {@code @PostConstruct} 同处
 *         before-init 阶段且因注册更早而先于它。完整链路：构造器 → BeanXxxAware → Environment/ApplicationContextAware
 *         → {@code @PostConstruct} → {@code afterPropertiesSet} → 自定义 init → (运行) → {@code @PreDestroy}
 *         → {@code destroy()} → 自定义 destroy。
 * 🔗 业务场景：产品中心「门店路由 Bean」需要 BeanFactory 拿协作 Bean、Environment 读机房/灰度开关、ApplicationContext 发事件——
 *         若误在 setBeanName 阶段就去读 Environment（此刻 EnvironmentAware 尚未回调、字段还是 null），灰度开关恒为默认值，
 *         踩中「Aware 回调有先后」的时机坑。
 */
public final class L0206_03_AwareCallbackTimingDemo {

	private L0206_03_AwareCallbackTimingDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 全链路回调时机（全局自增序号 = 真实执行顺序）====================");
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(RouterConfig.class);
		StoreRouter router = ctx.getBean(StoreRouter.class);

		System.out.println();
		System.out.println("[运行期] router 已就绪，beanName=" + router.beanName + "，灰度机房=" + router.grayIdc);
		System.out.println();

		System.out.println("==================== 关闭容器：销毁回调逆序触发 ====================");
		ctx.close();
	}

	@Configuration
	static class RouterConfig {

		@Bean(initMethod = "warmUp", destroyMethod = "shutdown")
		StoreRouter storeRouter() {
			return new StoreRouter();
		}
	}

	/** 门店路由 Bean：实现两组 Aware + 三类 init/destroy，用全局序号打印全链路时机。 */
	static class StoreRouter implements BeanNameAware, BeanClassLoaderAware, BeanFactoryAware,
			EnvironmentAware, ApplicationContextAware, InitializingBean, DisposableBean {

		private static final AtomicInteger SEQ = new AtomicInteger();

		private String beanName;
		private Environment environment;
		private String grayIdc = "(未读取)";

		StoreRouter() {
			System.out.println(step() + "构造器：实例化完成");
		}

		// —— 第一组 Aware：invokeAwareMethods 硬编码直调，早于一切 BeanPostProcessor ——
		@Override
		public void setBeanName(String name) {
			this.beanName = name;
			System.out.println(step() + "BeanNameAware#setBeanName：拿到 beanName=" + name + "（invokeAwareMethods，最早）");
		}

		@Override
		public void setBeanClassLoader(ClassLoader classLoader) {
			System.out.println(step() + "BeanClassLoaderAware#setBeanClassLoader（invokeAwareMethods）");
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
			System.out.println(step() + "BeanFactoryAware#setBeanFactory：拿到 BeanFactory（invokeAwareMethods，第一组最后）");
		}

		// —— 第二组 Aware：ApplicationContextAwareProcessor(BPP) 在 before-init 阶段回调，晚于第一组 ——
		@Override
		public void setEnvironment(Environment environment) {
			this.environment = environment;
			System.out.println(step() + "EnvironmentAware#setEnvironment：拿到 Environment（ApplicationContextAwareProcessor，before-init BPP）");
		}

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			System.out.println(step() + "ApplicationContextAware#setApplicationContext：拿到容器（同上 BPP，第二组最后）");
		}

		// —— @PostConstruct：CommonAnnotationBPP，同处 before-init 阶段，因注册晚于上面那个 BPP 而在其后 ——
		@PostConstruct
		void postConstruct() {
			// 此刻 Environment 已注入，可安全读取灰度开关（若在 setBeanName 阶段读则为 null，踩时机坑）
			this.grayIdc = (environment != null ? environment.getProperty("store.gray.idc", "hz-gray-01") : "(env 尚未注入)");
			System.out.println(step() + "@PostConstruct：Environment 已就绪，读灰度机房=" + grayIdc + "（before-init 阶段，晚于第二组 Aware）");
		}

		// —— afterPropertiesSet / 自定义 init：invokeInitMethods 阶段，晚于全部 before-init BPP ——
		@Override
		public void afterPropertiesSet() {
			System.out.println(step() + "InitializingBean#afterPropertiesSet（invokeInitMethods，晚于 @PostConstruct）");
		}

		void warmUp() {
			System.out.println(step() + "warmUp()[自定义 initMethod]：预热门店路由表（init 链最后一步）");
		}

		// —— 销毁链：逆序 ——
		@PreDestroy
		void preDestroy() {
			System.out.println(step() + "@PreDestroy：摘流量、停止接收路由请求（销毁链最早）");
		}

		@Override
		public void destroy() {
			System.out.println(step() + "DisposableBean#destroy（晚于 @PreDestroy）");
		}

		void shutdown() {
			System.out.println(step() + "shutdown()[自定义 destroyMethod]：释放路由表与连接（销毁链最后）");
		}

		private static String step() {
			return "  [" + SEQ.incrementAndGet() + "] ";
		}
	}
}
