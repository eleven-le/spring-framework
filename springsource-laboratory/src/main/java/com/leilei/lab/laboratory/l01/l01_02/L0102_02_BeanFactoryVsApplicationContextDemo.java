package com.leilei.lab.laboratory.l01.l01_02;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;

/**
 * 📖 知识点：[[L01-02-容器心智模型与核心抽象#2. 🏭 生产怎么用对]]（BeanFactory vs ApplicationContext）
 * 🎯 作用：把同一个价格预热 BeanDefinition 分别丢进裸 {@link DefaultListableBeanFactory} 与
 *         {@link GenericApplicationContext}，用「单例是否已实例化 / Aware 是否回调 / BPP 是否自动生效」
 *         三个可观测点，量化两者的心智差异——这正是「为什么生产永远用 ApplicationContext」的实证。
 * 🔗 业务场景：古茗产品中心启动期价格缓存预热。用错容器（或误以为 BeanFactory 能自动预热），
 *         首屏请求就会撞冷缓存回源，大促瞬时高并发下演变成缓存击穿。
 */
public final class L0102_02_BeanFactoryVsApplicationContextDemo {

	private static final String WARMUP_BEAN = "priceCacheWarmupBean";

	private L0102_02_BeanFactoryVsApplicationContextDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 裸 BeanFactory（DefaultListableBeanFactory）====================");
		runBareBeanFactory();
		System.out.println();
		System.out.println("==================== ApplicationContext（GenericApplicationContext）================");
		runApplicationContext();
	}

	/**
	 * 裸 BeanFactory：纯粹的 IoC 引擎。不预实例化单例、不自动织入 BPP、不兜 ApplicationContextAware。
	 */
	private static void runBareBeanFactory() {
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		AtomicInteger bppHits = new AtomicInteger();
		// BPP 必须手动注册，否则它对工厂里的 Bean 视而不见——容易被误以为「BPP 没生效是 bug」。
		factory.addBeanPostProcessor(new CountingBeanPostProcessor(bppHits));
		factory.registerBeanDefinition(WARMUP_BEAN, warmupBeanDefinition());

		// 关键差异 1：注册完 BeanDefinition 后，单例并未被创建——containsSingleton 为 false（懒加载）。
		System.out.println("注册后是否已实例化(containsSingleton) = " + factory.containsSingleton(WARMUP_BEAN)
				+ "  ← 裸工厂不做 preInstantiateSingletons，预热被推迟");

		// 直到第一次 getBean 才触发实例化 + 初始化 + 预热（首个请求才暖缓存 = 击穿风险窗口）。
		L0102_01_PriceCacheWarmupBean bean = factory.getBean(WARMUP_BEAN, L0102_01_PriceCacheWarmupBean.class);
		System.out.println("getBean 后是否已预热(warmedUp) = " + bean.isWarmedUp()
				+ "  规则条数=" + bean.getWarmedRuleCount() + "  预热耗时=" + bean.getWarmupCostMillis() + "ms");

		// 关键差异 2：BeanNameAware 由初始化流程内建回调（工厂也会），ApplicationContextAware 没人兜。
		System.out.println("BeanNameAware 注入 beanName = " + bean.getInjectedBeanName()
				+ "  | ApplicationContextAware 是否注入 = " + bean.isApplicationContextInjected()
				+ "  ← 裸工厂无 ApplicationContextAwareProcessor，Context 注不进来");
		System.out.println("CountingBeanPostProcessor 命中次数 = " + bppHits.get() + "（需手动 addBeanPostProcessor 才有）");
	}

	/**
	 * ApplicationContext：BeanFactory 的超集。refresh() 一把梭——自动织入 BPP、预实例化单例、兜齐 Aware、开放事件/国际化/Environment。
	 */
	private static void runApplicationContext() {
		GenericApplicationContext context = new GenericApplicationContext();
		AtomicInteger bppHits = new AtomicInteger();
		// BPP 以「普通 Bean」身份注册即可——refresh() 的 registerBeanPostProcessors 会自动把它挑出来织入。
		context.registerBean("countingBpp", CountingBeanPostProcessor.class, () -> new CountingBeanPostProcessor(bppHits));
		context.registerBeanDefinition(WARMUP_BEAN, warmupBeanDefinition());

		// refresh() 内部 finishBeanFactoryInitialization -> preInstantiateSingletons：非 lazy 单例此刻全部创建+初始化。
		context.refresh();

		// 关键差异 1：refresh 完成时单例已就位，预热在「放流量之前」跑完——containsSingleton 为 true（饿汉）。
		System.out.println("refresh 后是否已实例化(containsSingleton) = " + context.getBeanFactory().containsSingleton(WARMUP_BEAN)
				+ "  ← refresh 期 preInstantiateSingletons 已饿汉式预热");

		L0102_01_PriceCacheWarmupBean bean = context.getBean(WARMUP_BEAN, L0102_01_PriceCacheWarmupBean.class);
		System.out.println("getBean 取到的已是预热好的单例(warmedUp) = " + bean.isWarmedUp()
				+ "  规则条数=" + bean.getWarmedRuleCount() + "  预热耗时=" + bean.getWarmupCostMillis() + "ms");

		// 关键差异 2：BeanNameAware + ApplicationContextAware 全部兜齐（后者靠 refresh 自动织入的内建 BPP）。
		System.out.println("BeanNameAware 注入 beanName = " + bean.getInjectedBeanName()
				+ "  | ApplicationContextAware 是否注入 = " + bean.isApplicationContextInjected()
				+ "  ← Context 自动注入，可拿到容器引用");
		System.out.println("CountingBeanPostProcessor 命中次数 = " + bppHits.get() + "（refresh 自动织入，无需手动添加）");
		context.close();
	}

	/** 同一份 BeanDefinition 喂给两种容器，差异只来自「容器」本身而非「定义」。 */
	private static BeanDefinition warmupBeanDefinition() {
		return BeanDefinitionBuilder.genericBeanDefinition(L0102_01_PriceCacheWarmupBean.class)
				.setScope(BeanDefinition.SCOPE_SINGLETON)
				.setLazyInit(false)
				.getBeanDefinition();
	}

	/** 教学用 BPP：统计有多少个 Bean 流经初始化前置回调，用来对照「BPP 是否被容器织入」。 */
	static final class CountingBeanPostProcessor implements BeanPostProcessor {

		private final AtomicInteger hits;

		CountingBeanPostProcessor(AtomicInteger hits) {
			this.hits = hits;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			this.hits.incrementAndGet();
			return bean;
		}

	}

}
