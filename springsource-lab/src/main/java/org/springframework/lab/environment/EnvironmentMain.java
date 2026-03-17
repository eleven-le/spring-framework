package org.springframework.lab.environment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * W07 - Environment 与 PropertySource 配置抽象体系 调试入口
 *
 * <h2>一句话抽象</h2>
 * Environment 解决"多来源配置统一读取"问题：N 个 PropertySource 按优先级排成链表，
 * 通过 PropertySourcesPropertyResolver 遍历拿"首个命中"，
 * 核心矛盾是"来源越多、越灵活 vs 优先级越难控制、越容易被覆盖"。
 *
 * <h2>实验列表</h2>
 * <ul>
 *   <li>实验 1: PropertySource 优先级 — 观察 addFirst 覆盖 properties 文件值</li>
 *   <li>实验 2: Profile 激活 — 观察 prod profile 激活后 db.host 值变化</li>
 *   <li>实验 3: 占位符解析 — 观察 ${...} 递归解析机制</li>
 *   <li>实验 4: 必需属性校验 — 观察 validateRequiredProperties 抛异常</li>
 *   <li>实验 5: 遍历所有 PropertySource — 观察 MutablePropertySources 链表顺序</li>
 * </ul>
 *
 * <h2>断点位置（3 个抓手）</h2>
 * <ol>
 *   <li>AbstractEnvironment:137 — 构造器，观察 customizePropertySources 模板方法填充默认源</li>
 *   <li>PropertySourcesPropertyResolver:80 — for 循环遍历 PropertySource 链表，观察首个命中</li>
 *   <li>ConfigurationClassParser:279 — processPropertySource 处理 @PropertySource 注解</li>
 * </ol>
 *
 * <h2>口述调用链（8 步）</h2>
 * <pre>
 * 1. AnnotationConfigApplicationContext 构造时，getEnvironment() 懒创建 StandardEnvironment
 * 2.   → AbstractEnvironment 构造器调模板方法 customizePropertySources()
 * 3.     → StandardEnvironment 往 MutablePropertySources 里 addLast systemProperties 和 systemEnvironment
 * 4. refresh() → prepareRefresh() → initPropertySources() + validateRequiredProperties()
 * 5. refresh() → invokeBeanFactoryPostProcessors → ConfigurationClassParser 扫 @PropertySource
 * 6.   → processPropertySource() 把 properties 文件包装成 ResourcePropertySource 加到链表
 * 7. 运行时 getProperty("db.host") → PropertySourcesPropertyResolver 按链表顺序遍历
 * 8.   → 首个命中的 PropertySource 返回值，支持 ${...} 递归解析 + ConversionService 类型转换
 * </pre>
 */
public class EnvironmentMain {

	public static void main(String[] args) {
		System.out.println("========================================");
		System.out.println("  W07 Environment & PropertySource 练兵场");
		System.out.println("========================================\n");

		// --- 实验 1: PropertySource 优先级 ---
		scene1_priorityOverride();

		// --- 实验 2: Profile 激活 ---
		scene2_profileActivation();

		// --- 实验 3: 占位符解析 ---
		scene3_placeholderResolution();

		// --- 实验 4: 必需属性校验 ---
		scene4_requiredProperties();

		// --- 实验 5: 遍历所有 PropertySource ---
		scene5_listAllSources();

		// --- 实验 6: 自定义 PropertySource（模拟远程配置中心） ---
		scene6_customPropertySource();

		// --- 实验 7: @Value 注入链路追踪 ---
		scene7_valueInjectionChain();

		// --- 实验 8: 父子容器 Environment merge ---
		scene8_parentChildMerge();
	}

	/**
	 * 实验 1: 用 addFirst 注入一个高优先级 MapPropertySource，覆盖 properties 文件的值
	 */
	private static void scene1_priorityOverride() {
		System.out.println("--- 实验 1: PropertySource 优先级覆盖 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(EnvConfig.class);

		// 在 refresh 之前，手动往最高优先级插入一个运行时配置源
		ConfigurableEnvironment env = ctx.getEnvironment();
		env.getPropertySources().addFirst(
				new MapPropertySource("runtimeOverride",
						Map.of("db.host", "override-host", "db.port", "9999")));

		ctx.refresh();

		// 预期: db.host=override-host (被最高优先级覆盖)
		// 预期: app.name=JarvisApp   (未覆盖, 从 lab-common.properties 读取)
		System.out.println("db.host  = " + env.getProperty("db.host"));
		System.out.println("db.port  = " + env.getProperty("db.port"));
		System.out.println("app.name = " + env.getProperty("app.name"));
		System.out.println();
		ctx.close();
	}

	/**
	 * 实验 2: 激活 prod profile, 观察 @Profile 条件 Bean 和 @PropertySource 加载
	 */
	private static void scene2_profileActivation() {
		System.out.println("--- 实验 2: Profile 激活 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(EnvConfig.class);
		// 激活 prod profile
		ctx.getEnvironment().setActiveProfiles("prod");
		ctx.refresh();

		ConfigurableEnvironment env = ctx.getEnvironment();
		// 预期: db.host=prod-db.jarvis.com (来自 lab-prod.properties)
		System.out.println("db.host     = " + env.getProperty("db.host"));
		System.out.println("app.timeout = " + env.getProperty("app.timeout"));
		System.out.println("active profiles = " + String.join(",", env.getActiveProfiles()));

		// 验证 @Profile("prod") 的 Bean 是否注册
		String[] names = ctx.getBeanNamesForType(NotificationService.class);
		System.out.println("NotificationService beans = " + String.join(", ", names));
		System.out.println();
		ctx.close();
	}

	/**
	 * 实验 3: ${...} 占位符递归解析
	 */
	private static void scene3_placeholderResolution() {
		System.out.println("--- 实验 3: 占位符解析 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(EnvConfig.class);

		ConfigurableEnvironment env = ctx.getEnvironment();
		// 手动加一个含嵌套占位符的 PropertySource
		env.getPropertySources().addFirst(
				new MapPropertySource("placeholderDemo", Map.of(
						"jdbc.url", "jdbc:mysql://${db.host}:${db.port}/mydb"
				)));
		ctx.refresh();

		// 预期: resolvePlaceholders 会递归展开 ${db.host} 和 ${db.port}
		String resolved = env.resolvePlaceholders("${jdbc.url}");
		System.out.println("resolved jdbc.url = " + resolved);

		// 带默认值的占位符
		String withDefault = env.resolvePlaceholders("${missing.key:fallback-value}");
		System.out.println("missing key with default = " + withDefault);
		System.out.println();
		ctx.close();
	}

	/**
	 * 实验 4: 必需属性校验 — 模拟 prepareRefresh 中的 validateRequiredProperties
	 */
	private static void scene4_requiredProperties() {
		System.out.println("--- 实验 4: 必需属性校验 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(EnvConfig.class);

		// 标记一个不存在的属性为必需
		ctx.getEnvironment().setRequiredProperties("MUST_EXIST_ENV_VAR");

		try {
			ctx.refresh(); // 会在 prepareRefresh → validateRequiredProperties 时抛异常
		}
		catch (Exception e) {
			System.out.println("预期异常: " + e.getMessage());
		}
		System.out.println();
	}

	/**
	 * 实验 5: 遍历 MutablePropertySources 链表，观察所有 PropertySource 及其顺序
	 */
	private static void scene5_listAllSources() {
		System.out.println("--- 实验 5: 遍历所有 PropertySource (优先级从高到低) ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(EnvConfig.class);
		ctx.getEnvironment().setActiveProfiles("prod");
		ctx.refresh();

		MutablePropertySources sources = ctx.getEnvironment().getPropertySources();
		int index = 0;
		for (PropertySource<?> ps : sources) {
			System.out.printf("  [%d] %s (%s)%n", index++, ps.getName(), ps.getClass().getSimpleName());
		}
		System.out.println();
		ctx.close();
	}

	/**
	 * 实验 6: 自定义 PropertySource —— 模拟远程配置中心（Apollo/Nacos）
	 * <p>
	 * 业务落地: 把 ConcurrentHashMap 换成你们配置中心 SDK 的 getConfig()
	 * 断点: PropertySourcesPropertyResolver:80 观察自定义源参与优先级遍历
	 */
	private static void scene6_customPropertySource() {
		System.out.println("--- 实验 6: 自定义 PropertySource（模拟远程配置中心）---");

		// 模拟远程配置中心的数据存储
		ConcurrentHashMap<String, Object> remoteStore = new ConcurrentHashMap<>();
		remoteStore.put("feature.gray.ratio", "0.3");
		remoteStore.put("db.host", "remote-db.jarvis.com");

		// 自定义可枚举的 PropertySource
		EnumerablePropertySource<ConcurrentHashMap<String, Object>> remoteSource =
				new EnumerablePropertySource<ConcurrentHashMap<String, Object>>("remoteConfigCenter", remoteStore) {
					@Override
					public Object getProperty(String name) {
						return this.source.get(name);
					}
					@Override
					public String[] getPropertyNames() {
						return this.source.keySet().toArray(new String[0]);
					}
				};

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(EnvConfig.class);

		// addFirst → 最高优先级，覆盖 lab-common.properties 的 db.host
		ctx.getEnvironment().getPropertySources().addFirst(remoteSource);
		ctx.refresh();

		System.out.println("db.host (远程覆盖) = " + ctx.getEnvironment().getProperty("db.host"));
		System.out.println("feature.gray.ratio = " + ctx.getEnvironment().getProperty("feature.gray.ratio"));
		System.out.println("app.name (未覆盖)  = " + ctx.getEnvironment().getProperty("app.name"));

		// 模拟远程配置热更新（只改 store，Environment 下次 getProperty 自动读到新值）
		remoteStore.put("feature.gray.ratio", "0.8");
		System.out.println("热更新后 gray.ratio = " + ctx.getEnvironment().getProperty("feature.gray.ratio"));
		System.out.println();
		ctx.close();
	}

	/**
	 * 实验 7: @Value 注入链路追踪
	 * <p>
	 * 链路: @Value → AutowiredAnnotationBPP → BeanFactory.resolveEmbeddedValue
	 *       → StringValueResolver(env.resolvePlaceholders) → PropertySourcesPropertyResolver
	 * 断点: AbstractBeanFactory#resolveEmbeddedValue, AbstractEnvironment#resolvePlaceholders
	 */
	private static void scene7_valueInjectionChain() {
		System.out.println("--- 实验 7: @Value 注入链路追踪 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(EnvConfig.class, ValueInjectionConfig.class);
		ctx.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("bizOverride", Map.of("biz.discount", "0.75")));
		ctx.refresh();

		PricingService pricing = ctx.getBean(PricingService.class);
		System.out.println("discount  = " + pricing.discount + " (来自 bizOverride MapPropertySource)");
		System.out.println("appName   = " + pricing.appName + " (来自 lab-common.properties)");
		System.out.println("fallback  = " + pricing.missing + " (来自 @Value 默认值)");
		System.out.println();
		ctx.close();
	}

	@Configuration
	static class ValueInjectionConfig {
		@Bean
		PricingService pricingService() {
			return new PricingService();
		}
	}

	static class PricingService {
		@Value("${biz.discount:1.0}")
		double discount;

		@Value("${app.name:unknown}")
		String appName;

		@Value("${not.exist.key:default-fallback}")
		String missing;
	}

	/**
	 * 实验 8: 父子容器 Environment merge
	 * <p>
	 * 关键: AbstractApplicationContext#setParent → child.getEnvironment().merge(parent.getEnvironment())
	 * 父容器的 PropertySource 被 addLast 到子容器末尾 → 子容器同名 key 优先级更高
	 * 断点: AbstractEnvironment#merge:515
	 */
	private static void scene8_parentChildMerge() {
		System.out.println("--- 实验 8: 父子容器 Environment merge ---");

		// 父容器
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
		parent.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("parentSource", Map.of(
						"shared.key", "from-parent",
						"parent.only", "parent-value")));
		parent.refresh();

		// 子容器
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.setParent(parent); // 触发 environment.merge(parent.environment)
		child.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("childSource", Map.of(
						"shared.key", "from-child",
						"child.only", "child-value")));
		child.register(EnvConfig.class);
		child.refresh();

		ConfigurableEnvironment childEnv = child.getEnvironment();
		System.out.println("shared.key  = " + childEnv.getProperty("shared.key") + " (子覆盖父)");
		System.out.println("parent.only = " + childEnv.getProperty("parent.only") + " (继承父)");
		System.out.println("child.only  = " + childEnv.getProperty("child.only") + " (子独有)");

		// 打印 merge 后 PropertySource 链表顺序
		System.out.println("merge 后链表顺序:");
		int i = 0;
		for (PropertySource<?> ps : childEnv.getPropertySources()) {
			System.out.printf("  [%d] %s%n", i++, ps.getName());
		}
		System.out.println();

		child.close();
		parent.close();
	}
}
