package org.springframework.lab.beanfactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W27 — DefaultListableBeanFactory：核心数据结构与查找模型
 *
 * <h2>一句话抽象</h2>
 * <b>DefaultListableBeanFactory</b> 解决"如何在单个容器内高效管理数千个 Bean 的定义、实例、类型索引和依赖关系"
 * ——核心矛盾是 <i>按名称查找 O(1) 很简单 vs 按类型查找需要全量遍历 BeanDefinition 很昂贵</i>，
 * 解法是 <b>分层数据结构</b>：底层 DefaultSingletonBeanRegistry 管实例（三级缓存），
 * 中层 AbstractBeanFactory 管合并后 BeanDefinition（mergedBeanDefinitions），
 * 顶层 DefaultListableBeanFactory 管原始注册（beanDefinitionMap）+ 类型索引缓存（allBeanNamesByType）。
 *
 * <h2>实验列表</h2>
 * <ol>
 *   <li>实验 1: 核心数据结构全景 — 通过反射窥探 beanDefinitionMap / singletonObjects / allBeanNamesByType 的实际内容</li>
 *   <li>实验 2: 按名称查找 vs 按类型查找 — getBean(name) O(1) vs getBeanNamesForType O(n) + 缓存</li>
 *   <li>实验 3: 注册与覆盖 — registerBeanDefinition 新增/覆盖流程 + hasBeanCreationStarted 分支</li>
 *   <li>实验 4: 按类型查找多实现 — getBeanNamesForType 返回多个 + @Primary/@Qualifier 消歧</li>
 *   <li>实验 5: 冻结配置 — freezeConfiguration 后 allBeanNamesByType 缓存生效</li>
 *   <li>实验 6: 手动注册 Singleton — registerSingleton 绕过 BeanDefinition，进入 manualSingletonNames</li>
 *   <li>实验 7: 别名机制 — registerAlias + transformedBeanName 解析</li>
 *   <li>实验 8: preInstantiateSingletons — 遍历 beanDefinitionNames 触发全量单例初始化</li>
 * </ol>
 *
 * <h2>核心调用链 (12 步)</h2>
 * <pre>
 *  ① registerBeanDefinition : 校验 → beanDefinitionMap.put + beanDefinitionNames.add (hasBeanCreationStarted 分支: synchronized 复制 List)
 *  ② preInstantiateSingletons : 遍历 beanDefinitionNames → getMergedLocalBeanDefinition → 非抽象+单例+非懒加载 → getBean
 *  ③ getBean(name) → doGetBean : transformedBeanName 解析别名 → getSingleton 查三级缓存
 *  ④ getSingleton(beanName) : singletonObjects → earlySingletonObjects → singletonFactories 三级缓存逐级查
 *  ⑤ doGetBean 缓存未命中 : 查父工厂 → markBeanAsCreated → getMergedLocalBeanDefinition → depends-on 递归 → createBean
 *  ⑥ getBean(Class) → resolveNamedBean : getBeanNamesForType 按类型查 → 多个候选 → determinePrimaryCandidate → getBean(name)
 *  ⑦ getBeanNamesForType : frozen? → 查缓存 allBeanNamesByType/singletonBeanNamesByType; 否则 doGetBeanNamesForType 全量遍历
 *  ⑧ doGetBeanNamesForType : 遍历 beanDefinitionNames → getMergedLocalBeanDefinition → isTypeMatch; 再遍历 manualSingletonNames
 *  ⑨ resolveDependency : 分流 Optional/ObjectProvider/JSR-330 Provider/@Lazy → doResolveDependency
 *  ⑩ doResolveDependency : shortcut → @Value → 多元素(List/Map/Array) → findAutowireCandidates → determineAutowireCandidate 消歧
 *  ⑪ freezeConfiguration : configurationFrozen=true + frozenBeanDefinitionNames 快照 → 后续 getBeanNamesForType 走缓存
 *  ⑫ addSingleton : singletonObjects.put + singletonFactories.remove + earlySingletonObjects.remove + registeredSingletons.add — 三级缓存晋升
 * </pre>
 *
 * <h2>断点位置（5 个抓手）</h2>
 * <ol>
 *   <li>DefaultListableBeanFactory#registerBeanDefinition L1045 — 观察 beanDefinitionMap + beanDefinitionNames 的写入时机与覆盖策略</li>
 *   <li>AbstractBeanFactory#doGetBean L249 — 按名称查找总入口，观察 transformedBeanName + getSingleton + 父工厂委托</li>
 *   <li>DefaultListableBeanFactory#getBeanNamesForType(Class,boolean,boolean) L598 — 类型查找入口，观察 frozen 缓存命中 vs 全量遍历</li>
 *   <li>DefaultListableBeanFactory#doGetBeanNamesForType L615 — 全量遍历核心循环，观察 isTypeMatch 逐个匹配</li>
 *   <li>DefaultSingletonBeanRegistry#getSingleton(String,boolean) L180 — 三级缓存查找顺序: singletonObjects→earlySingletonObjects→singletonFactories</li>
 * </ol>
 */
public class BeanFactoryMain {

	public static void main(String[] args) throws Exception {
		System.out.println("╔════════════════════════════════════════════════════════════════╗");
		System.out.println("║  W27 — DefaultListableBeanFactory: 核心数据结构与查找模型       ║");
		System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BeanFactoryConfig.class);
		DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();

		// ========== 实验 1: 核心数据结构全景 ==========
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("实验 1: 核心数据结构全景 — 窥探内部 Map 结构");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: DefaultListableBeanFactory 字段定义 L163-186\n");

		// beanDefinitionMap
		@SuppressWarnings("unchecked")
		Map<String, BeanDefinition> bdMap = getField(bf, DefaultListableBeanFactory.class, "beanDefinitionMap");
		System.out.println("  beanDefinitionMap (ConcurrentHashMap<String, BeanDefinition>):");
		System.out.println("    size=" + bdMap.size() + ", 部分 keys=" + sampleKeys(bdMap, 8));

		// beanDefinitionNames
		@SuppressWarnings("unchecked")
		List<String> bdNames = getField(bf, DefaultListableBeanFactory.class, "beanDefinitionNames");
		System.out.println("  beanDefinitionNames (ArrayList<String>, 保序!):");
		System.out.println("    size=" + bdNames.size() + ", 前8=" + bdNames.subList(0, Math.min(8, bdNames.size())));

		// singletonObjects (三级缓存 L1)
		@SuppressWarnings("unchecked")
		Map<String, Object> singletons = getField(bf, DefaultSingletonBeanRegistry.class, "singletonObjects");
		System.out.println("  singletonObjects (ConcurrentHashMap<String, Object>) — 三级缓存 L1:");
		System.out.println("    size=" + singletons.size() + ", 部分 keys=" + sampleKeys(singletons, 8));

		// allBeanNamesByType 缓存
		@SuppressWarnings("unchecked")
		Map<Class<?>, String[]> typeCache = getField(bf, DefaultListableBeanFactory.class, "allBeanNamesByType");
		System.out.println("  allBeanNamesByType (ConcurrentHashMap<Class, String[]>) — 类型索引缓存:");
		System.out.println("    size=" + typeCache.size() + " (frozen后才填充)");

		// manualSingletonNames
		@SuppressWarnings("unchecked")
		Set<String> manualNames = getField(bf, DefaultListableBeanFactory.class, "manualSingletonNames");
		System.out.println("  manualSingletonNames (LinkedHashSet<String>) — 手动注册的单例:");
		System.out.println("    " + manualNames);

		// ========== 实验 2: 按名称查找 vs 按类型查找 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 2: 按名称查找 O(1) vs 按类型查找 O(n)");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: AbstractBeanFactory#doGetBean L249 — 按名称查找");
		System.out.println("  断点: DefaultListableBeanFactory#doGetBeanNamesForType L615 — 按类型全量遍历\n");

		// 按名称: ConcurrentHashMap.get → O(1)
		long t1 = System.nanoTime();
		Object orderSvc = bf.getBean("orderService");
		long nameTime = System.nanoTime() - t1;
		System.out.println("  getBean(\"orderService\") = " + orderSvc.getClass().getSimpleName()
				+ " (" + nameTime / 1000 + " μs)");

		// 按类型: getBeanNamesForType → 遍历 beanDefinitionNames
		long t2 = System.nanoTime();
		String[] names = bf.getBeanNamesForType(OrderService.class);
		long typeTime = System.nanoTime() - t2;
		System.out.println("  getBeanNamesForType(OrderService) = " + Arrays.toString(names)
				+ " (" + typeTime / 1000 + " μs)");

		// ========== 实验 3: 注册与覆盖 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 3: registerBeanDefinition — 注册/覆盖流程");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: DefaultListableBeanFactory#registerBeanDefinition L1045\n");

		int sizeBefore = bdMap.size();
		RootBeanDefinition newBd = new RootBeanDefinition(PayChannel.class);
		newBd.getConstructorArgumentValues().addGenericArgumentValue("apple");
		newBd.getConstructorArgumentValues().addGenericArgumentValue("Apple Pay");
		bf.registerBeanDefinition("applepay", newBd);
		System.out.println("  注册新 BD 'applepay': bdMap " + sizeBefore + " → " + bdMap.size());
		System.out.println("  beanDefinitionNames 末尾: " + bdNames.get(bdNames.size() - 1));

		// 覆盖已有 BD
		RootBeanDefinition overrideBd = new RootBeanDefinition(PayChannel.class);
		overrideBd.getConstructorArgumentValues().addGenericArgumentValue("alipay-v2");
		overrideBd.getConstructorArgumentValues().addGenericArgumentValue("支付宝V2");
		bf.registerBeanDefinition("alipay", overrideBd);
		System.out.println("  覆盖 BD 'alipay': allowBeanDefinitionOverriding="
				+ bf.isAllowBeanDefinitionOverriding());

		// ========== 实验 4: 按类型查找多实现 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 4: 按类型查找多实现 — getBeanNamesForType + @Primary 消歧");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: DefaultListableBeanFactory#resolveNamedBean L1284 — 多候选消歧\n");

		String[] payChannels = bf.getBeanNamesForType(PayChannel.class);
		System.out.println("  getBeanNamesForType(PayChannel) = " + Arrays.toString(payChannels));
		System.out.println("  共 " + payChannels.length + " 个候选, @Primary=alipay");

		Map<String, PayChannel> all = bf.getBeansOfType(PayChannel.class);
		System.out.println("  getBeansOfType(PayChannel) = " + all);

		// getBean(Class) 走 @Primary 消歧
		PayChannel primary = bf.getBean(PayChannel.class);
		System.out.println("  getBean(PayChannel.class) → @Primary 命中: " + primary);

		// ========== 实验 5: 冻结配置 — 类型缓存生效 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 5: freezeConfiguration — 类型查找缓存生效");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: DefaultListableBeanFactory#getBeanNamesForType L598 — frozen 缓存分支\n");

		System.out.println("  frozen前 allBeanNamesByType.size=" + typeCache.size());
		bf.freezeConfiguration();
		// 触发一次类型查找来填充缓存
		bf.getBeanNamesForType(PayChannel.class);
		bf.getBeanNamesForType(OrderService.class);
		System.out.println("  frozen后 + 查找后 allBeanNamesByType.size=" + typeCache.size());
		System.out.println("  configurationFrozen=" + bf.isConfigurationFrozen());

		// ========== 实验 6: 手动注册 Singleton ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 6: registerSingleton — 绕过 BeanDefinition 直接注册实例");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: DefaultSingletonBeanRegistry#registerSingleton L118\n");

		PayChannel manual = new PayChannel("paypal", "PayPal");
		bf.registerSingleton("paypal", manual);
		System.out.println("  registerSingleton('paypal') 后:");
		System.out.println("    containsBeanDefinition('paypal')=" + bf.containsBeanDefinition("paypal")
				+ " (无 BD!)");
		System.out.println("    containsBean('paypal')=" + bf.containsBean("paypal") + " (但有实例!)");
		System.out.println("    manualSingletonNames 包含 'paypal': " + manualNames.contains("paypal"));

		// ========== 实验 7: 别名机制 ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 7: 别名机制 — registerAlias + transformedBeanName");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: SimpleAliasRegistry#canonicalName — 递归解析别名链\n");

		bf.registerAlias("orderService", "os");
		bf.registerAlias("os", "svc"); // 别名链: svc → os → orderService
		System.out.println("  注册别名: orderService → os → svc");
		System.out.println("  getBean(\"os\")=" + bf.getBean("os").getClass().getSimpleName());
		System.out.println("  getBean(\"svc\")=" + bf.getBean("svc").getClass().getSimpleName());
		System.out.println("  getAliases(\"orderService\")=" + Arrays.toString(bf.getAliases("orderService")));

		// ========== 实验 8: preInstantiateSingletons ==========
		System.out.println("\n═══════════════════════════════════════════════════════");
		System.out.println("实验 8: preInstantiateSingletons — 全量初始化");
		System.out.println("═══════════════════════════════════════════════════════");
		System.out.println("  断点: DefaultListableBeanFactory#preInstantiateSingletons L980");
		System.out.println("  流程: 遍历 beanDefinitionNames → getMergedLocalBeanDefinition");
		System.out.println("       → !abstract && singleton && !lazyInit → getBean(name)");
		System.out.println("       → 全部完成后再遍历触发 SmartInitializingSingleton\n");
		System.out.println("  (已在 refresh 第 11 步执行完毕)");
		System.out.println("  当前 singletonObjects.size=" + singletons.size());
		System.out.println("  beanDefinitionNames.size=" + bdNames.size());

		ctx.close();
		System.out.println("\n[完成] W27 DefaultListableBeanFactory 全部实验执行完毕");
	}

	@SuppressWarnings("unchecked")
	private static <T> T getField(Object target, Class<?> clazz, String fieldName) throws Exception {
		Field f = clazz.getDeclaredField(fieldName);
		f.setAccessible(true);
		return (T) f.get(target);
	}

	private static String sampleKeys(Map<?, ?> map, int max) {
		StringBuilder sb = new StringBuilder("[");
		int i = 0;
		for (Object key : map.keySet()) {
			if (i >= max) { sb.append(", ..."); break; }
			if (i > 0) sb.append(", ");
			sb.append(key);
			i++;
		}
		sb.append("]");
		return sb.toString();
	}
}
