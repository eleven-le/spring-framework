package org.springframework.lab.scanner;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Arrays;
import java.util.Set;

/**
 * W34 扫描注册：Scanner/Reader/Registry 练兵场
 *
 * <h2>核心矛盾</h2>
 * <p>
 * Scanner/Reader/Registry 三角分工解决的核心矛盾是：
 * <b>"如何把散落在 classpath 各处的类，高效地、可过滤地、可扩展地转换为 BeanDefinition 图纸，
 * 同时严格保持 只注册定义/不实例化 的阶段纪律？"</b>
 * </p>
 *
 * <h2>Demo 场景清单</h2>
 * <pre>
 * Demo1: Reader vs Scanner —— 两条注册路径的本质差异
 * Demo2: 自定义 TypeFilter —— 按自定义注解/接口扫描
 * Demo3: 自定义 BeanNameGenerator —— 控制 bean 命名策略
 * Demo4: 手动操作 Registry —— 编程式注册/查询/删除
 * Demo5: 过滤器链 include/exclude 组合 —— 精确控制扫描范围
 * Demo6: @Conditional 在注册期的拦截 —— 条件化注册
 * </pre>
 *
 * <h2>断点抓手</h2>
 * <ol>
 *   <li>ClassPathScanningCandidateComponentProvider#scanCandidateComponents:432 —— ASM 过滤入口</li>
 *   <li>ClassPathBeanDefinitionScanner#doScan:276 —— findCandidateComponents 返回后的注册循环</li>
 *   <li>AnnotatedBeanDefinitionReader#doRegisterBean:297 —— Reader 路径的图纸创建起点</li>
 *   <li>DefaultListableBeanFactory#registerBeanDefinition —— 最终入库到 ConcurrentHashMap</li>
 *   <li>AnnotationConfigUtils#registerAnnotationConfigProcessors —— 6 大内置处理器注册</li>
 * </ol>
 */
public class ScannerRegistryLab {

	public static void main(String[] args) {
		System.out.println("===== W34 Scanner/Reader/Registry 练兵场 =====\n");

		demo1_ReaderVsScanner();
		demo2_CustomTypeFilter();
		demo3_CustomBeanNameGenerator();
		demo4_RegistryManipulation();
		demo5_IncludeExcludeFilters();
		demo6_ConditionalRegistration();
	}

	// ======================== Demo 1 ========================
	/**
	 * 两条注册路径对比：
	 * - Reader 路径：显式注册，创建 AnnotatedGenericBeanDefinition（反射加载类）
	 * - Scanner 路径：包扫描发现，创建 ScannedGenericBeanDefinition（ASM 读元数据，不加载类）
	 *
	 * 断点：AnnotatedBeanDefinitionReader#doRegisterBean:297 vs
	 *       ClassPathScanningCandidateComponentProvider#scanCandidateComponents:433
	 */
	static void demo1_ReaderVsScanner() {
		System.out.println("--- Demo1: Reader vs Scanner 两条路径 ---");

		// ======== 路径 A：Reader 显式注册 ========
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(factory);

		// 打断点到 doRegisterBean:297 —— 观察创建 AnnotatedGenericBeanDefinition
		reader.register(OrderService.class);

		BeanDefinition readerBd = factory.getBeanDefinition("orderService");
		System.out.println("[Reader路径] BeanDefinition类型: " + readerBd.getClass().getSimpleName());
		// → AnnotatedGenericBeanDefinition（反射驱动，类已加载）
		System.out.println("[Reader路径] beanClassName: " + readerBd.getBeanClassName());

		// ======== 路径 B：Scanner 包扫描 ========
		DefaultListableBeanFactory factory2 = new DefaultListableBeanFactory();
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(factory2);
		// 清除默认 @Component 过滤器，只加我们需要的
		scanner.resetFilters(false);
		scanner.addIncludeFilter(new AssignableTypeFilter(OrderService.class));

		// 打断点到 scanCandidateComponents:433 —— 观察创建 ScannedGenericBeanDefinition
		int count = scanner.scan("org.springframework.lab.scanner");

		BeanDefinition scannerBd = factory2.getBeanDefinition("orderService");
		System.out.println("[Scanner路径] BeanDefinition类型: " + scannerBd.getClass().getSimpleName());
		// → ScannedGenericBeanDefinition（ASM 驱动，类未加载）
		System.out.println("[Scanner路径] 扫描注册了 " + count + " 个 bean");

		System.out.println();
	}

	// ======================== Demo 2 ========================
	/**
	 * 自定义 TypeFilter：按自定义注解扫描
	 * 业务场景：扫描所有标注 @RpcClient 的接口，批量注册为 FactoryBean
	 *
	 * 断点：ClassPathScanningCandidateComponentProvider#isCandidateComponent(MetadataReader):488
	 */
	static void demo2_CustomTypeFilter() {
		System.out.println("--- Demo2: 自定义 TypeFilter 按注解/接口扫描 ---");

		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(factory, false);

		// 场景 A：按自定义注解扫描（如 @RpcClient）
		scanner.addIncludeFilter(new AnnotationTypeFilter(RpcClient.class));

		// 场景 B：按接口类型扫描（如扫描所有 Strategy 实现类）
		// scanner.addIncludeFilter(new AssignableTypeFilter(PayStrategy.class));

		int count = scanner.scan("org.springframework.lab.scanner");
		System.out.println("按 @RpcClient 注解扫描到 " + count + " 个候选");

		for (String name : factory.getBeanDefinitionNames()) {
			BeanDefinition bd = factory.getBeanDefinition(name);
			// 过滤掉 Spring 内部处理器
			if (bd.getBeanClassName() != null && bd.getBeanClassName().contains("lab.scanner")) {
				System.out.println("  → " + name + " : " + bd.getBeanClassName());
			}
		}
		System.out.println();
	}

	// ======================== Demo 3 ========================
	/**
	 * 自定义 BeanNameGenerator
	 * 业务场景：多模块项目中类名冲突（两个模块都有 UserService），用全限定名命名
	 *
	 * 断点：AnnotationBeanNameGenerator#generateBeanName:80
	 */
	static void demo3_CustomBeanNameGenerator() {
		System.out.println("--- Demo3: 自定义 BeanNameGenerator ---");

		// 策略 1：默认策略（首字母小写）—— 同名冲突会抛异常
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		// 策略 2：全限定名策略（避免跨模块冲突）
		ctx.setBeanNameGenerator(new FullyQualifiedBeanNameGenerator());
		ctx.register(OrderService.class);
		ctx.refresh();

		String[] names = ctx.getBeanDefinitionNames();
		for (String name : names) {
			if (name.contains("lab.scanner")) {
				System.out.println("  全限定名: " + name);
				// → org.springframework.lab.scanner.OrderService
			}
		}
		ctx.close();
		System.out.println();
	}

	// ======================== Demo 4 ========================
	/**
	 * 编程式操作 BeanDefinitionRegistry
	 * 业务场景：插件系统动态注册/替换/移除 BeanDefinition
	 *
	 * 断点：DefaultListableBeanFactory#registerBeanDefinition —— 观察 beanDefinitionMap.put
	 */
	static void demo4_RegistryManipulation() {
		System.out.println("--- Demo4: 编程式操作 Registry ---");

		DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

		// 1. 手动注册
		AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(OrderService.class);
		abd.setScope(BeanDefinition.SCOPE_SINGLETON);
		abd.setLazyInit(true);
		registry.registerBeanDefinition("myOrderService", abd);
		System.out.println("[注册] myOrderService → " + registry.containsBeanDefinition("myOrderService"));

		// 2. 查询
		System.out.println("[查询] BeanDefinition数量: " + registry.getBeanDefinitionCount());
		System.out.println("[查询] 所有名称: " + Arrays.toString(registry.getBeanDefinitionNames()));

		// 3. 替换（同名再次注册 = 覆盖）
		AnnotatedGenericBeanDefinition replacement = new AnnotatedGenericBeanDefinition(VipOrderService.class);
		registry.registerBeanDefinition("myOrderService", replacement);
		BeanDefinition replaced = registry.getBeanDefinition("myOrderService");
		System.out.println("[替换后] beanClassName: " + replaced.getBeanClassName());

		// 4. 删除
		registry.removeBeanDefinition("myOrderService");
		System.out.println("[删除后] 还存在? " + registry.containsBeanDefinition("myOrderService"));

		// 5. 别名
		registry.registerBeanDefinition("orderSvc", abd);
		registry.registerAlias("orderSvc", "orderAlias");
		System.out.println("[别名] isBeanNameInUse('orderAlias'): " + registry.isBeanNameInUse("orderAlias"));

		System.out.println();
	}

	// ======================== Demo 5 ========================
	/**
	 * Include/Exclude 过滤器组合
	 * 业务场景：扫描某包下所有 @Component，但排除测试用的 Mock 类
	 *
	 * 过滤器执行顺序：先 exclude → 再 include → 再 @Conditional
	 * 断点：ClassPathScanningCandidateComponentProvider#isCandidateComponent(MetadataReader):488
	 */
	static void demo5_IncludeExcludeFilters() {
		System.out.println("--- Demo5: Include/Exclude 过滤器组合 ---");

		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(factory, false);

		// Include: 扫描所有 PayStrategy 实现类
		scanner.addIncludeFilter(new AssignableTypeFilter(PayStrategy.class));

		// Exclude: 排除 Mock 实现
		scanner.addExcludeFilter(new AnnotationTypeFilter(MockBean.class));

		int count = scanner.scan("org.springframework.lab.scanner");
		System.out.println("Include PayStrategy 但 Exclude @MockBean: 命中 " + count + " 个");
		for (String name : factory.getBeanDefinitionNames()) {
			BeanDefinition bd = factory.getBeanDefinition(name);
			if (bd.getBeanClassName() != null && bd.getBeanClassName().contains("lab.scanner")) {
				System.out.println("  → " + name);
			}
		}
		System.out.println();
	}

	// ======================== Demo 6 ========================
	/**
	 * @Conditional 在注册期的拦截效果
	 * Reader 路径在 doRegisterBean:298 调用 conditionEvaluator.shouldSkip()
	 *
	 * 断点：ConditionEvaluator#shouldSkip:80
	 */
	static void demo6_ConditionalRegistration() {
		System.out.println("--- Demo6: @Conditional 注册期拦截 ---");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// ConditionalService 上标注了 @Conditional(OnPropertyCondition.class)
		// 当系统属性 feature.enabled=true 时才注册
		System.setProperty("feature.enabled", "false");
		ctx.register(ConditionalService.class);
		ctx.refresh();

		boolean exists = ctx.containsBeanDefinition("conditionalService");
		System.out.println("feature.enabled=false → 注册成功? " + exists);
		// → false，被 ConditionEvaluator 拦截
		ctx.close();

		// 对比：开启条件
		System.setProperty("feature.enabled", "true");
		AnnotationConfigApplicationContext ctx2 = new AnnotationConfigApplicationContext();
		ctx2.register(ConditionalService.class);
		ctx2.refresh();
		System.out.println("feature.enabled=true  → 注册成功? " + ctx2.containsBeanDefinition("conditionalService"));
		// → true
		ctx2.close();

		// 清理
		System.clearProperty("feature.enabled");
		System.out.println();
	}
}
