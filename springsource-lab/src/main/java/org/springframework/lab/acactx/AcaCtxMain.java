package org.springframework.lab.acactx;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.lab.acactx.scanpkg.CouponService;

/**
 * W82: AnnotationConfigApplicationContext 启动全链路
 *      register → scan → refresh 串联视角
 *
 * <h2>一句话抽象</h2>
 * AnnotationConfigApplicationContext 是注解驱动容器的"总装车间"——
 * 构造阶段用 Reader(左手) 和 Scanner(右手) 把用户意图转为 BeanDefinition 蓝图,
 * refresh() 阶段把蓝图批量实例化为可用对象;
 * 核心矛盾是: 构造阶段只做"登记"不做"创建", 真正的 Bean 爆炸发生在 refresh 第 5 步和第 11 步。
 *
 * <h2>实验列表</h2>
 * <ol>
 *   <li>Scene 1: 一参构造 — register + refresh 一步到位</li>
 *   <li>Scene 2: 手动三步走 — new() → register() → refresh() 分离控制</li>
 *   <li>Scene 3: scan 模式 — new() → scan() → refresh() 包扫描路径</li>
 *   <li>Scene 4: register + scan 混合 — 两条注册路同时生效</li>
 *   <li>Scene 5: 基础设施审计 — 构造器阶段就已注册的 6 大内部处理器</li>
 *   <li>Scene 6: 双 Scanner 辨析 — 构造器 Scanner vs @ComponentScan Scanner</li>
 *   <li>Scene 7: 先 register 后补 scan —— 验证 refresh 前注册顺序无关性</li>
 *   <li>Scene 8: @Conditional 门控 — register 阶段的条件过滤</li>
 * </ol>
 *
 * <h2>核心调用链 (12 步)</h2>
 * <pre>
 *  ① AnnotationConfigApplicationContext#&lt;init&gt;()
 *     → super() → GenericApplicationContext → new DefaultListableBeanFactory()
 *  ② new AnnotatedBeanDefinitionReader(this)
 *     → AnnotationConfigUtils.registerAnnotationConfigProcessors()
 *     → 注册 6 大基础设施 BD (CCPP/AABPP/CABPP/ELMP/DELF/PABPP)
 *  ③ new ClassPathBeanDefinitionScanner(this)
 *     → 注册默认 include filter (@Component 家族)
 *  ④ register(componentClasses)
 *     → AnnotatedBeanDefinitionReader#doRegisterBean()
 *     → 条件评估 → 作用域解析 → 名称生成 → 通用注解处理 → 注册到 beanDefinitionMap
 *  ⑤ scan(basePackages)
 *     → ClassPathBeanDefinitionScanner#doScan()
 *     → 资源扫描 → 类型过滤 → 候选确认 → 批量注册
 *  ⑥ refresh() → prepareRefresh()
 *     → 设置启动时间、active 标志、校验必须属性
 *  ⑦ obtainFreshBeanFactory()
 *     → GenericApplicationContext#refreshBeanFactory() CAS 防重入
 *  ⑧ prepareBeanFactory()
 *     → 配 SpEL、Aware处理器、可解析依赖、环境单例
 *  ⑨ invokeBeanFactoryPostProcessors() ← BeanDefinition 大爆炸!
 *     → ConfigurationClassPostProcessor 解析 @Configuration/@ComponentScan/@Import/@Bean
 *  ⑩ registerBeanPostProcessors()
 *     → 实例化 AABPP/CABPP 等, 注册到 BPP 链
 *  ⑪ finishBeanFactoryInitialization() ← Bean 实例大爆炸!
 *     → DefaultListableBeanFactory#preInstantiateSingletons()
 *     → 遍历所有非懒加载单例 → getBean → createBean → 注入 → 初始化
 *  ⑫ finishRefresh()
 *     → 发布 ContextRefreshedEvent, 容器就绪
 * </pre>
 *
 * <h2>断点抓手</h2>
 * <ul>
 *   <li>AnnotationConfigApplicationContext 构造器 — 观察 reader/scanner 初始化</li>
 *   <li>AnnotationConfigUtils#registerAnnotationConfigProcessors — 6 大内部处理器注册</li>
 *   <li>AnnotatedBeanDefinitionReader#doRegisterBean — register() 逐字段组装 BD</li>
 *   <li>ClassPathBeanDefinitionScanner#doScan — scan() 资源扫描 + 候选筛选</li>
 *   <li>AbstractApplicationContext#refresh — 12 步生命周期总入口</li>
 *   <li>PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors — Step5 BD 爆炸</li>
 *   <li>DefaultListableBeanFactory#preInstantiateSingletons — Step11 实例爆炸</li>
 * </ul>
 */
public class AcaCtxMain {

	public static void main(String[] args) {
		System.out.println("===== W82: AnnotationConfigApplicationContext 启动全链路 =====\n");

		scene1_oneArgConstructor();
		scene2_manualThreeStep();
		scene3_scanMode();
		scene4_registerPlusScan();
		scene5_infrastructureAudit();
		scene6_dualScannerDiff();
		scene7_registerOrderIrrelevant();
		scene8_conditionalGate();

		System.out.println("\n===== All Scenes Done =====");
	}

	/**
	 * Scene 1: 一参构造器 — 最常见的用法
	 * new ACAC(AppConfig.class) 内部等价于: this() → register() → refresh()
	 */
	private static void scene1_oneArgConstructor() {
		System.out.println("--- Scene 1: 一参构造器 (register + refresh 一步到位) ---");
		// 断点: AnnotationConfigApplicationContext(Class<?>... componentClasses)
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AppConfig.class);

		UserService userService = ctx.getBean(UserService.class);
		System.out.println("UserService: " + userService.whoAmI());
		System.out.println("BD count after refresh: " + ctx.getBeanDefinitionCount());
		ctx.close();
		System.out.println();
	}

	/**
	 * Scene 2: 手动三步走 — 最能看清 register 与 refresh 的分界
	 * new ACAC() 只建骨架(reader + scanner + 6 大处理器 BD)
	 * register() 只登记用户 BD
	 * refresh() 才真正创建 Bean
	 */
	private static void scene2_manualThreeStep() {
		System.out.println("--- Scene 2: 手动三步走 (new → register → refresh) ---");

		// Step A: 构造 — 此时只有 6 个基础设施 BD
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		System.out.println("[after new()]    BD count = " + ctx.getBeanDefinitionCount());

		// Step B: register — 用户配置类变为 BD, 但 @ComponentScan 还没生效
		ctx.register(AppConfig.class);
		System.out.println("[after register] BD count = " + ctx.getBeanDefinitionCount());

		// Step C: refresh — @ComponentScan 生效, @Bean 生效, 所有单例实例化
		ctx.refresh();
		System.out.println("[after refresh]  BD count = " + ctx.getBeanDefinitionCount());

		UserService us = ctx.getBean(UserService.class);
		System.out.println("UserService: " + us.whoAmI());
		ctx.close();
		System.out.println();
	}

	/**
	 * Scene 3: scan 模式 — 不传 Config 类, 直接扫包
	 * 等价于 XML 的 &lt;context:component-scan base-package="..."/&gt;
	 */
	private static void scene3_scanMode() {
		System.out.println("--- Scene 3: scan 模式 (new → scan → refresh) ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		System.out.println("[after new()]  BD count = " + ctx.getBeanDefinitionCount());

		// scan() 内部走 ClassPathBeanDefinitionScanner#doScan
		// 会扫到 @Component/@Service/@Repository/@Controller
		ctx.scan("org.springframework.lab.acactx.scanpkg");
		System.out.println("[after scan()] BD count = " + ctx.getBeanDefinitionCount());

		ctx.refresh();
		System.out.println("[after refresh] BD count = " + ctx.getBeanDefinitionCount());

		CouponService couponService = ctx.getBean(CouponService.class);
		System.out.println("CouponService from scan: " + couponService.issue());
		ctx.close();
		System.out.println();
	}

	/**
	 * Scene 4: register + scan 混合模式
	 * 两条路注册的 BD 在 refresh 后全部可用
	 */
	private static void scene4_registerPlusScan() {
		System.out.println("--- Scene 4: register + scan 混合 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(AppConfig.class);
		ctx.scan("org.springframework.lab.acactx.scanpkg");
		ctx.refresh();

		// AppConfig @ComponentScan 扫到的 UserService
		UserService us = ctx.getBean(UserService.class);
		// scan("scanpkg") 扫到的 CouponService
		CouponService cs = ctx.getBean(CouponService.class);
		System.out.println("UserService (from register path): " + us.whoAmI());
		System.out.println("CouponService (from scan path):   " + cs.issue());
		ctx.close();
		System.out.println();
	}

	/**
	 * Scene 5: 基础设施审计 — 构造器阶段已注册的 6 大内部处理器
	 * AnnotationConfigUtils.registerAnnotationConfigProcessors() 在
	 * new AnnotatedBeanDefinitionReader(this) 时就被调用
	 */
	private static void scene5_infrastructureAudit() {
		System.out.println("--- Scene 5: 构造器阶段的 6 大基础设施 BD 审计 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		DefaultListableBeanFactory bf = ctx.getDefaultListableBeanFactory();

		String[] names = ctx.getBeanDefinitionNames();
		System.out.println("构造器阶段 BD 数量: " + names.length);
		for (String name : names) {
			BeanDefinition bd = bf.getBeanDefinition(name);
			System.out.printf("  %-55s role=%s  class=%s%n",
					name,
					bd.getRole() == BeanDefinition.ROLE_INFRASTRUCTURE ? "INFRA" : "APP",
					bd.getBeanClassName());
		}
		// 不 refresh, 直接关闭 — 演示"只建骨架不开工"
		System.out.println();
	}

	/**
	 * Scene 6: 双 Scanner 辨析
	 * - 构造器创建的 scanner (this.scanner) 供外部 ctx.scan() 调用
	 * - @ComponentScan 由 ConfigurationClassPostProcessor 内部创建新的 Scanner 来执行
	 * 它们是两个不同的 Scanner 实例!
	 */
	private static void scene6_dualScannerDiff() {
		System.out.println("--- Scene 6: 双 Scanner 辨析 ---");
		System.out.println("构造器 Scanner: 由 new ClassPathBeanDefinitionScanner(this) 创建");
		System.out.println("  → 仅在调用 ctx.scan(\"pkg\") 时使用");
		System.out.println("  → 可通过 ctx.addIncludeFilter()/addExcludeFilter() 定制");
		System.out.println();
		System.out.println("@ComponentScan Scanner: 由 ComponentScanAnnotationParser 内部新建");
		System.out.println("  → 在 refresh() 第 5 步 ConfigurationClassPostProcessor 中触发");
		System.out.println("  → 使用 @ComponentScan 注解属性配置 (basePackages/excludeFilters 等)");
		System.out.println("  → 与构造器 Scanner 完全独立, 互不影响");
		System.out.println();

		// 验证: 即使不调用 ctx.scan(), @ComponentScan 仍然生效
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AppConfig.class);
		System.out.println("未调用 ctx.scan(), 但 @ComponentScan 照样扫到 UserService: "
				+ ctx.containsBean("userService"));
		ctx.close();
		System.out.println();
	}

	/**
	 * Scene 7: 先 register 后补 scan — 注册顺序不影响最终结果
	 * 只要在 refresh() 之前完成所有注册, 顺序无关
	 */
	private static void scene7_registerOrderIrrelevant() {
		System.out.println("--- Scene 7: 注册顺序无关性 ---");

		// 顺序 A: scan → register
		AnnotationConfigApplicationContext ctxA = new AnnotationConfigApplicationContext();
		ctxA.scan("org.springframework.lab.acactx.scanpkg");
		ctxA.register(ManualBean.class);
		ctxA.refresh();

		// 顺序 B: register → scan
		AnnotationConfigApplicationContext ctxB = new AnnotationConfigApplicationContext();
		ctxB.register(ManualBean.class);
		ctxB.scan("org.springframework.lab.acactx.scanpkg");
		ctxB.refresh();

		System.out.println("顺序A (scan→register) BD count: " + ctxA.getBeanDefinitionCount());
		System.out.println("顺序B (register→scan) BD count: " + ctxB.getBeanDefinitionCount());
		System.out.println("两种顺序 BD 数量一致: "
				+ (ctxA.getBeanDefinitionCount() == ctxB.getBeanDefinitionCount()));

		ctxA.close();
		ctxB.close();
		System.out.println();
	}

	/**
	 * Scene 8: @Conditional 门控 — register 阶段的条件过滤
	 * doRegisterBean 中 conditionEvaluator.shouldSkip() 会在注册时就生效
	 */
	private static void scene8_conditionalGate() {
		System.out.println("--- Scene 8: @Conditional 门控 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 设置环境变量, 让 @ConditionalOnProperty 的条件不满足
		ctx.getEnvironment().getSystemProperties().put("feature.beta", "false");
		ctx.register(AppConfig.class);
		ctx.register(BetaFeatureConfig.class);
		ctx.refresh();

		System.out.println("feature.beta=false → BetaService 存在? "
				+ ctx.containsBean("betaService"));

		ctx.close();

		// 再次, 设置条件满足
		AnnotationConfigApplicationContext ctx2 = new AnnotationConfigApplicationContext();
		ctx2.getEnvironment().getSystemProperties().put("feature.beta", "true");
		ctx2.register(AppConfig.class);
		ctx2.register(BetaFeatureConfig.class);
		ctx2.refresh();

		System.out.println("feature.beta=true  → BetaService 存在? "
				+ ctx2.containsBean("betaService"));

		ctx2.close();
		System.out.println();
	}
}
