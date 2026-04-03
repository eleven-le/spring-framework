package org.springframework.lab.dogetbean;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.lang.Nullable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ===================================================================
 *  W28 · doGetBean：单例缓存 / 依赖解析 / 作用域
 * ===================================================================
 *
 * 一句话抽象：
 *   doGetBean 是 Spring 容器的"总调度台" ——
 *   核心矛盾是：一次 getBean() 调用需要在"缓存命中(O(1)快速返回)"与
 *   "首次创建(依赖解析+循环引用+作用域分支)"之间做出正确路由。
 *   13 步流程覆盖：别名解析 → 三级缓存 → FactoryBean 分流 → 父容器委托
 *   → dependsOn 递归 → singleton/prototype/自定义 scope 三路分支
 *   → 类型转换。每一步都是生产级 Bug 的高发区。
 *
 * 本 Demo 共 7 个实验：
 *   实验1: 单例缓存命中 → 反射窥探三级缓存 L1/L2/L3
 *   实验2: 别名解析 → alias 链 + & 前缀
 *   实验3: dependsOn 依赖解析 → 保证创建顺序 + 循环 dependsOn 检测
 *   实验4: 父容器委托 → 本地无 BD 时委托给 parent
 *   实验5: singleton vs prototype 对比 → 缓存策略差异
 *   实验6: 自定义 Scope → ThreadScope 演示
 *   实验7: FactoryBean 分流 → getBean("x") vs getBean("&x")
 *
 * 核心调用链（13 步）：
 *   1. AbstractBeanFactory#doGetBean                          : 总入口
 *   2. transformedBeanName                                    : 别名解析 + & 前缀剥离
 *   3. DefaultSingletonBeanRegistry#getSingleton(name)        : L1 快速缓存查找
 *   4. getObjectForBeanInstance                               : FactoryBean 分流
 *   5. isPrototypeCurrentlyInCreation                         : prototype 循环引用检测
 *   6. parentBeanFactory.getBean                              : 父容器委托
 *   7. markBeanAsCreated                                      : 元数据缓存标记
 *   8. getMergedLocalBeanDefinition                           : 合并父子 BD
 *   9. dependsOn → registerDependentBean + getBean(dep)       : 依赖递归创建
 *  10. getSingleton(name, ObjectFactory) → createBean         : 单例创建（含三级缓存协议）
 *  11. createBean(prototype)                                  : 原型创建（不缓存）
 *  12. scope.get(name, ObjectFactory)                         : 自定义 scope 委托
 *  13. adaptBeanInstance                                      : requiredType 类型转换
 *
 * 断点抓手（5 个）：
 *   ① AbstractBeanFactory#doGetBean:249                       → 总入口，看 name/args/requiredType
 *   ② DefaultSingletonBeanRegistry#getSingleton:180           → 三级缓存查找路径
 *   ③ AbstractBeanFactory#doGetBean:314-330                   → dependsOn 循环检测 + 递归 getBean
 *   ④ AbstractBeanFactory#doGetBean:333-386                   → singleton/prototype/scope 三路分支
 *   ⑤ AbstractBeanFactory#getObjectForBeanInstance:1860       → FactoryBean 分流决策
 */
public class DoGetBeanMain {

	public static void main(String[] args) throws Exception {
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println(" W28 · doGetBean：单例缓存 / 依赖解析 / 作用域 Demo");
		System.out.println("═══════════════════════════════════════════════════════════\n");

		exp1_singletonCacheInspection();
		exp2_aliasResolution();
		exp3_dependsOnProcessing();
		exp4_parentBeanFactoryDelegation();
		exp5_singletonVsPrototype();
		exp6_customScope();
		exp7_factoryBeanRouting();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验1: 单例缓存命中 — 反射窥探三级缓存
	// 要点: L1=singletonObjects(完全初始化), L2=earlySingletonObjects(早期引用),
	//       L3=singletonFactories(工厂)
	//       第一次 getBean → createBean + addSingleton(L1), 清理 L2/L3
	//       第二次 getBean → L1 直接命中, O(1) 返回
	// 源码: DefaultSingletonBeanRegistry#getSingleton(String, boolean)
	// ─────────────────────────────────────────────────────────────
	static void exp1_singletonCacheInspection() throws Exception {
		System.out.println("【实验1】单例缓存命中 → 反射窥探三级缓存");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("orderService", OrderService.class);
		ctx.refresh();

		DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();

		// 反射读取三级缓存
		Map<?, ?> l1 = getField(bf, "singletonObjects");
		Map<?, ?> l2 = getField(bf, "earlySingletonObjects");
		Map<?, ?> l3 = getField(bf, "singletonFactories");

		System.out.println("  >> L1 (singletonObjects) 包含 orderService: " + l1.containsKey("orderService"));
		System.out.println("  >> L2 (earlySingletonObjects) 包含 orderService: " + l2.containsKey("orderService"));
		System.out.println("  >> L3 (singletonFactories) 包含 orderService: " + l3.containsKey("orderService"));

		// 验证同一性
		Object first = ctx.getBean("orderService");
		Object second = ctx.getBean("orderService");
		System.out.println("  >> 两次 getBean 同一实例: " + (first == second) + " (L1 缓存命中)");

		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验2: 别名解析 → alias 链 + & 前缀
	// 要点: transformedBeanName 做两件事:
	//       1. BeanFactoryUtils.transformedBeanName → 剥离 & 前缀
	//       2. canonicalName → 沿 aliasMap 链解析到最终名
	// 源码: SimpleAliasRegistry#canonicalName
	// ─────────────────────────────────────────────────────────────
	static void exp2_aliasResolution() {
		System.out.println("【实验2】别名解析 → alias 链 + & 前缀");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("orderService", OrderService.class);
		ctx.refresh();

		// 注册别名链: svc → orderService, order → svc (传递)
		ctx.getBeanFactory().registerAlias("orderService", "svc");
		ctx.getBeanFactory().registerAlias("svc", "order");

		Object byName = ctx.getBean("orderService");
		Object byAlias1 = ctx.getBean("svc");
		Object byAlias2 = ctx.getBean("order");

		System.out.println("  >> getBean(\"orderService\") == getBean(\"svc\"): " + (byName == byAlias1));
		System.out.println("  >> getBean(\"svc\") == getBean(\"order\"): " + (byAlias1 == byAlias2));
		System.out.println("  >> 结论: 别名链最终解析到同一个 canonical name");

		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验3: dependsOn 依赖解析 → 保证创建顺序
	// 要点: @DependsOn 在 doGetBean 中通过 getBean(dep) 递归创建
	//       registerDependentBean 记录双向依赖关系
	//       isDependent 检测循环 dependsOn → 抛 BeanCreationException
	// 源码: doGetBean:314-330
	// ─────────────────────────────────────────────────────────────
	static void exp3_dependsOnProcessing() {
		System.out.println("【实验3】dependsOn 依赖解析 → 保证创建顺序");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(DependsOnConfig.class);

		System.out.println("\n  >> 验证: DataSource 先于 OrderService 创建");
		System.out.println("  >> (观察上面的 [构造] 打印顺序)");

		// 验证销毁顺序: 依赖方先销毁
		System.out.println("\n  >> close() — 销毁顺序反转:");
		ctx.close();
		System.out.println();
	}

	@Configuration
	static class DependsOnConfig {
		@Bean
		public DataSourceBean dataSource() {
			return new DataSourceBean();
		}

		@Bean
		@DependsOn("dataSource")
		public OrderService orderService() {
			return new OrderService();
		}
	}

	// ─────────────────────────────────────────────────────────────
	// 实验4: 父容器委托
	// 要点: doGetBean 中如果本地无 BeanDefinition 且有 parentBeanFactory
	//       → 委托给 parent.getBean()
	//       子容器可以看到父容器的 Bean，反之不行
	// 源码: doGetBean:279-298
	// ─────────────────────────────────────────────────────────────
	static void exp4_parentBeanFactoryDelegation() {
		System.out.println("【实验4】父容器委托 → 本地无 BD 时委托 parent");
		System.out.println("─────────────────────────────────────────────────────────");

		// 父容器
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
		parent.registerBean("sharedService", SharedService.class);
		parent.refresh();

		// 子容器
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.setParent(parent);
		child.registerBean("orderService", OrderService.class);
		child.refresh();

		// 子容器能拿到父容器的 Bean
		SharedService fromChild = child.getBean(SharedService.class);
		System.out.println("  >> 子容器 getBean(SharedService): " + fromChild + " (来自父容器)");

		// 父容器拿不到子容器的 Bean
		try {
			parent.getBean(OrderService.class);
			System.out.println("  >> 父容器 getBean(OrderService): 不应该到这里");
		} catch (Exception e) {
			System.out.println("  >> 父容器 getBean(OrderService): " + e.getClass().getSimpleName() + " (看不到子容器)");
		}

		child.close();
		parent.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验5: singleton vs prototype 对比
	// 要点: singleton → getSingleton(name, ObjectFactory) → L1 缓存 → 同一实例
	//       prototype → beforePrototypeCreation + createBean → 不缓存 → 每次新实例
	//       prototype 循环引用 → ThreadLocal 检测 → BeanCurrentlyInCreationException
	// 源码: doGetBean:333-359 (singleton/prototype 两个分支)
	// ─────────────────────────────────────────────────────────────
	static void exp5_singletonVsPrototype() {
		System.out.println("【实验5】singleton vs prototype → 缓存策略差异");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// singleton
		ctx.registerBean("singletonSvc", OrderService.class);

		// prototype
		RootBeanDefinition prototypeDef = new RootBeanDefinition(OrderService.class);
		prototypeDef.setScope("prototype");
		ctx.getDefaultListableBeanFactory().registerBeanDefinition("prototypeSvc", prototypeDef);

		ctx.refresh();

		// singleton: 每次同一实例
		Object s1 = ctx.getBean("singletonSvc");
		Object s2 = ctx.getBean("singletonSvc");
		System.out.println("  >> singleton: s1==s2 = " + (s1 == s2) + " (L1 缓存命中)");

		// prototype: 每次新实例
		Object p1 = ctx.getBean("prototypeSvc");
		Object p2 = ctx.getBean("prototypeSvc");
		System.out.println("  >> prototype: p1==p2 = " + (p1 == p2) + " (每次 createBean)");
		System.out.println("  >> prototype 实例数: p1=" + System.identityHashCode(p1)
				+ ", p2=" + System.identityHashCode(p2));

		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验6: 自定义 Scope — SimpleThreadScope 演示
	// 要点: doGetBean 的第三分支: scope.get(name, ObjectFactory)
	//       Scope 接口的 get 方法决定缓存策略
	//       SimpleThreadScope 用 ThreadLocal<Map> 存储 → 同线程同实例，不同线程不同实例
	// 源码: doGetBean:362-386 (custom scope branch)
	// ─────────────────────────────────────────────────────────────
	static void exp6_customScope() throws Exception {
		System.out.println("【实验6】自定义 Scope → ThreadScope 演示");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		// 注册自定义 Scope
		ctx.getBeanFactory().registerScope("thread", new SimpleThreadScope());

		// 注册 thread scope 的 Bean
		RootBeanDefinition threadDef = new RootBeanDefinition(OrderService.class);
		threadDef.setScope("thread");
		ctx.getDefaultListableBeanFactory().registerBeanDefinition("threadSvc", threadDef);

		ctx.refresh();

		// 主线程: 同一实例
		Object t1 = ctx.getBean("threadSvc");
		Object t2 = ctx.getBean("threadSvc");
		System.out.println("  >> 主线程: t1==t2 = " + (t1 == t2) + " (同线程同实例)");

		// 新线程: 不同实例
		final Object[] otherThreadBean = new Object[1];
		Thread other = new Thread(() -> {
			otherThreadBean[0] = ctx.getBean("threadSvc");
		});
		other.start();
		other.join();
		System.out.println("  >> 跨线程: t1==otherThread = " + (t1 == otherThreadBean[0]) + " (不同线程不同实例)");

		ctx.close();
		System.out.println();
	}

	// ─────────────────────────────────────────────────────────────
	// 实验7: FactoryBean 分流 → getBean("x") vs getBean("&x")
	// 要点: doGetBean 调用 getObjectForBeanInstance 做 FactoryBean 分流
	//       getBean("myFactory") → 调用 factory.getObject() 返回产品
	//       getBean("&myFactory") → 返回 FactoryBean 本身
	// 源码: getObjectForBeanInstance:1860-1901
	// ─────────────────────────────────────────────────────────────
	static void exp7_factoryBeanRouting() {
		System.out.println("【实验7】FactoryBean 分流 → getBean(\"x\") vs getBean(\"&x\")");
		System.out.println("─────────────────────────────────────────────────────────");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean("rpcProxy", RpcProxyFactoryBean.class);
		ctx.refresh();

		// 不带 & → 返回 FactoryBean.getObject() 的产品
		Object product = ctx.getBean("rpcProxy");
		System.out.println("  >> getBean(\"rpcProxy\"): " + product.getClass().getSimpleName()
				+ " → " + product + " (FactoryBean 的产品)");

		// 带 & → 返回 FactoryBean 本身
		Object factory = ctx.getBean("&rpcProxy");
		System.out.println("  >> getBean(\"&rpcProxy\"): " + factory.getClass().getSimpleName()
				+ " (FactoryBean 本身)");

		System.out.println("  >> product instanceof OrderService: " + (product instanceof OrderService));
		System.out.println("  >> factory instanceof FactoryBean: " + (factory instanceof FactoryBean));

		ctx.close();
		System.out.println();
	}

	// ═══════════════════════════════════════════════════════════
	//  内部类
	// ═══════════════════════════════════════════════════════════

	/** 简单订单服务 */
	static class OrderService {
		public OrderService() {
			System.out.println("    [构造] OrderService created");
		}
	}

	/** 数据源 Bean（用于 dependsOn 演示） */
	static class DataSourceBean {
		public DataSourceBean() {
			System.out.println("    [构造] DataSourceBean created (应先于 OrderService)");
		}

		@javax.annotation.PreDestroy
		public void close() {
			System.out.println("    [销毁] DataSourceBean closed (应后于 OrderService)");
		}
	}

	/** 共享服务（用于父容器演示） */
	static class SharedService {
		@Override
		public String toString() {
			return "SharedService@" + Integer.toHexString(hashCode());
		}
	}

	/** 模拟 RPC 代理 FactoryBean */
	static class RpcProxyFactoryBean implements FactoryBean<OrderService> {
		@Override
		public OrderService getObject() {
			System.out.println("    [FactoryBean] getObject() → 创建 RPC 代理");
			return new OrderService();
		}

		@Override
		public Class<?> getObjectType() {
			return OrderService.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}
	}

	/** 简单的 ThreadLocal Scope 实现 */
	static class SimpleThreadScope implements Scope {
		private final ThreadLocal<Map<String, Object>> threadScope =
				ThreadLocal.withInitial(HashMap::new);

		@Override
		public Object get(String name, ObjectFactory<?> objectFactory) {
			Map<String, Object> scope = this.threadScope.get();
			Object obj = scope.get(name);
			if (obj == null) {
				obj = objectFactory.getObject();
				scope.put(name, obj);
			}
			return obj;
		}

		@Override
		@Nullable
		public Object remove(String name) {
			return this.threadScope.get().remove(name);
		}

		@Override
		public void registerDestructionCallback(String name, Runnable callback) {
			// ThreadScope 不支持销毁回调
		}

		@Override
		@Nullable
		public Object resolveContextualObject(String key) {
			return null;
		}

		@Override
		public String getConversationId() {
			return Thread.currentThread().getName();
		}
	}

	// ─── 反射工具 ───
	@SuppressWarnings("unchecked")
	private static <T> T getField(Object target, String fieldName) throws Exception {
		Class<?> clazz = target.getClass();
		while (clazz != null) {
			try {
				Field field = clazz.getDeclaredField(fieldName);
				field.setAccessible(true);
				return (T) field.get(target);
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		throw new NoSuchFieldException(fieldName);
	}
}
