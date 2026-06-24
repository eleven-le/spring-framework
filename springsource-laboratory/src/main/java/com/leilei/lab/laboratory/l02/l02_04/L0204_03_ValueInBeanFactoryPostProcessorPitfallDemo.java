package com.leilei.lab.laboratory.l02.l02_04;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * 📖 知识点：[[L02-04-Environment与属性绑定#3. 🧨 事故与避坑]]（@Value 占位符解析时机与失效坑 ⭐）
 * 🎯 作用：复现「@Value 在 {@link BeanFactoryPostProcessor} 里静默失效」这个最隐蔽的坑——
 *         BFPP Bean 在 {@code AbstractApplicationContext#invokeBeanFactoryPostProcessors}（refresh 第 5 步）就被实例化，
 *         **早于 {@code registerBeanPostProcessors}（第 6 步）注册 {@code AutowiredAnnotationBeanPostProcessor}**，
 *         所以处理 {@code @Value}/{@code @Autowired} 的那个 BPP 此刻根本不在场 → BFPP 上的 {@code @Value} 字段
 *         **完全没被注入**，停在 Java 默认值（int=0、引用=null），且**不报错、不打 ERROR**。
 *         对照：{@link EnvironmentAware} 由 {@code ApplicationContextAwareProcessor} 处理，而它在
 *         {@code prepareBeanFactory}（refresh 第 3 步）就注册了，所以 BFPP 里改用 {@code EnvironmentAware}
 *         直接读 {@code Environment} 是正确解。
 * 🔗 业务场景：很多人写「启动期动态注册 Bean / 改 BeanDefinition」的 BFPP 时，想用 {@code @Value} 读个限流阈值、
 *         分片数、灰度比例，结果拿到 0/null，逻辑全跑偏——大促前改了配置中心阈值「没生效」，根因正是这个时序坑。
 */
public final class L0204_03_ValueInBeanFactoryPostProcessorPitfallDemo {

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			Map<String, Object> nacos = new LinkedHashMap<>();
			nacos.put("ratelimit.seckill.qps", 8000);
			ctx.getEnvironment().getPropertySources().addLast(new MapPropertySource("nacosConfig", nacos));

			// 必须把 BFPP 作为「容器管理的 Bean」注册（而非 ctx.addBeanFactoryPostProcessor(new ...) 手工塞实例）：
			// 只有容器创建的 Bean 才会走 ApplicationContextAwareProcessor，EnvironmentAware 回调才会触发。
			ctx.register(GuardConfig.class, EarlyThresholdBFPP.class);
			System.out.println("==================== refresh：观察 BFPP 阶段 @Value vs EnvironmentAware 谁拿到了值 ====================");
			ctx.refresh();

			System.out.println();
			System.out.println("==================== 对照：同样的 @Value 在普通 Bean 上正常解析 ====================");
			LateSeckillGuard late = ctx.getBean(LateSeckillGuard.class);
			System.out.println("[普通 Bean @Value] qps = " + late.getQps() + "  ← 注入期 AutowiredAnnotationBPP 已就位，占位符正常解析");
		}
	}

	/**
	 * 反面教材：BFPP 里用 @Value（失效）+ EnvironmentAware（正确）两路同时读同一个阈值，对照打印。
	 */
	static class EarlyThresholdBFPP implements BeanFactoryPostProcessor, EnvironmentAware {

		/** ❌ 坑：BFPP 实例化时 AutowiredAnnotationBeanPostProcessor 尚未注册，此字段永远停在 0。 */
		@Value("${ratelimit.seckill.qps:2000}")
		private int qpsFromValue;

		/** ✅ 正确：EnvironmentAware 的处理器在 prepareBeanFactory 就注册，setEnvironment 会被回调。 */
		private Environment environment;

		@Override
		public void setEnvironment(Environment environment) {
			this.environment = environment;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			int qpsFromEnv = this.environment.getProperty("ratelimit.seckill.qps", Integer.class, 2000);
			System.out.println("[BFPP @Value]        qpsFromValue = " + this.qpsFromValue
					+ "  ← @Value 静默失效（连默认值 2000 都没有，BPP 未就位 → 字段从未被注入）");
			System.out.println("[BFPP EnvironmentAware] qpsFromEnv = " + qpsFromEnv
					+ "  ← 改读 Environment 即正确拿到配置中心的 8000");
		}
	}

	@Configuration
	static class GuardConfig {

		@Bean
		LateSeckillGuard lateSeckillGuard() {
			return new LateSeckillGuard();
		}
	}

	/** 普通业务 Bean：同样的 @Value 写法，在常规注入期能正常解析。 */
	static class LateSeckillGuard {

		@Value("${ratelimit.seckill.qps:2000}")
		private int qps;

		int getQps() {
			return this.qps;
		}
	}

}
