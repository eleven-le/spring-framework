package org.springframework.lab.factorybeandeep;

import java.util.Map;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W31: FactoryBean 深挖 — 产物缓存 / 类型预测 / SmartFactoryBean
 *
 * 本 Demo 覆盖三个核心主题:
 *   1. 产物缓存: factoryBeanObjectCache 的 synchronized + 双检锁 + 循环引用容忍
 *   2. 类型预测: getTypeForFactoryBean 降级链 (属性→泛型→工厂方法→快捷实例→兜底)
 *   3. SmartFactoryBean: isPrototype()/isEagerInit() 的三态语义与启动期行为
 *
 * 核心断点:
 *   1. FactoryBeanRegistrySupport#getObjectFromFactoryBean:97  → 产物缓存入口
 *   2. AbstractAutowireCapableBeanFactory#getTypeForFactoryBean:848 → 类型预测降级链
 *   3. DefaultListableBeanFactory#preInstantiateSingletons:993  → SmartFactoryBean 判断
 *   4. AbstractAutowireCapableBeanFactory#getSingletonFactoryBeanForTypeCheck:1005 → 快捷实例
 *   5. AbstractAutowireCapableBeanFactory#postProcessObjectFromFactoryBean:1946 → BPP作用于产物
 */
public class FactoryBeanDeepMain {

	public static void main(String[] args) {
		System.out.println("╔══════════════════════════════════════════════════════════════╗");
		System.out.println("║  W31: FactoryBean 深挖 — 产物缓存 / 类型预测 / SmartFactoryBean ║");
		System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

		System.out.println("====== 容器启动 (观察 EagerRegistry 是否启动期就创建产物) ======");
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(FactoryBeanDeepConfig.class);
		System.out.println("====== 容器启动完毕 ======\n");

		// ═══════════════════════════════════════════════════
		// 场景 1: 产物缓存 — factoryBeanObjectCache
		// ═══════════════════════════════════════════════════
		System.out.println("═══ 场景1: 产物缓存 — 单例 FactoryBean 产物只创建一次 ═══");

		Connection c1 = (Connection) ctx.getBean("cacheConn");
		System.out.println("  第1次 getBean: " + c1);

		Connection c2 = (Connection) ctx.getBean("cacheConn");
		System.out.println("  第2次 getBean: " + c2);

		System.out.println("  c1 == c2 (同一对象)?  " + (c1 == c2));  // true

		CacheDemoFactoryBean cacheFB = (CacheDemoFactoryBean) ctx.getBean("&cacheConn");
		System.out.println("  getObject() 调用次数: " + cacheFB.getCallCount()); // 1
		System.out.println("  → 结论: singleton 产物被缓存到 factoryBeanObjectCache, getObject() 只调 1 次\n");

		// ═══════════════════════════════════════════════════
		// 场景 2: 类型预测 — getBean(Class) 如何找到 FactoryBean 产物
		// ═══════════════════════════════════════════════════
		System.out.println("═══ 场景2: 类型预测 — @Autowired / getBean(Class) 的匹配机制 ═══");

		// getBean(Connection.class) 能找到 CacheDemoFactoryBean 和 TypePredictionFactoryBean 的产物
		Map<String, Connection> conns = ctx.getBeansOfType(Connection.class);
		System.out.println("  getBeansOfType(Connection.class): " + conns.keySet());
		System.out.println("  → Spring 通过 getObjectType()/泛型推导 预测产物类型, 不需要提前 getObject()\n");

		// NullTypeFactoryBean 返回 null → 类型不可预测
		System.out.println("  nullTypeBean 的产物类型预测:");
		DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();
		Class<?> nullType = bf.getType("nullTypeBean");
		System.out.println("  getType(\"nullTypeBean\"): " + nullType);
		System.out.println("  → getObjectType()=null 的 FactoryBean 在 autowire 时被跳过\n");

		// ═══════════════════════════════════════════════════
		// 场景 3: SmartFactoryBean 三态语义
		// ═══════════════════════════════════════════════════
		System.out.println("═══ 场景3: SmartFactoryBean 三态语义 ═══");

		// 3a: isSingleton=false + isPrototype=false → 作用域对象
		System.out.println("  [3a] ScopedSession: isSingleton=false, isPrototype=false");
		ScopedSessionFactoryBean scopedFB = (ScopedSessionFactoryBean) ctx.getBean("&scopedSession");
		System.out.println("  isSingleton: " + scopedFB.isSingleton());
		System.out.println("  isPrototype: " + scopedFB.isPrototype());

		String s1 = (String) ctx.getBean("scopedSession");
		String s2 = (String) ctx.getBean("scopedSession");
		System.out.println("  s1: " + s1);
		System.out.println("  s2: " + s2);
		System.out.println("  s1 == s2 ?  " + (s1 == s2));  // false, 每次都调 getObject
		System.out.println("  → 不是单例(不缓存), 也不是原型(不保证独立), 是作用域语义\n");

		// 3b: isEagerInit=true → 启动期就触发 getObject
		System.out.println("  [3b] EagerRegistry: isEagerInit=true → 上面启动日志中已执行 getObject()");
		String endpoint = (String) ctx.getBean("eagerRegistry");
		System.out.println("  eagerRegistry 产物: " + endpoint + "\n");

		// ═══════════════════════════════════════════════════
		// 场景 4: BPP 对产物的后处理
		// ═══════════════════════════════════════════════════
		System.out.println("═══ 场景4: BPP 对 FactoryBean 产物的后处理 ═══");
		System.out.println("  (上面日志中 [BPP] 行已展示 postProcessAfterInitialization 对 Connection 产物生效)");
		System.out.println("  路径: getObjectFromFactoryBean → postProcessObjectFromFactoryBean");
		System.out.println("       → applyBeanPostProcessorsAfterInitialization(产物, beanName)\n");

		// ═══════════════════════════════════════════════════
		// 场景 5: isFactoryBean 与 & 前缀总结
		// ═══════════════════════════════════════════════════
		System.out.println("═══ 场景5: FactoryBean 身份判断 ═══");
		System.out.println("  isFactoryBean(\"cacheConn\"):  " + bf.isFactoryBean("cacheConn"));
		System.out.println("  getType(\"cacheConn\"):        " + bf.getType("cacheConn"));       // Connection
		System.out.println("  getType(\"&cacheConn\"):       " + bf.getType("&cacheConn"));      // CacheDemoFactoryBean
		System.out.println("  → getType 无 & 返回产物类型, 有 & 返回工厂类型");

		System.out.println("\n╔══════════════════════════════════════════════════════╗");
		System.out.println("║  核心结论                                            ║");
		System.out.println("║  1. 产物缓存: singleton 产物存 factoryBeanObjectCache ║");
		System.out.println("║  2. 类型预测: 5级降级, getObjectType()=null会断链    ║");
		System.out.println("║  3. SmartFB: 三态(单例/原型/作用域)+启动期触发       ║");
		System.out.println("╚══════════════════════════════════════════════════════╝");

		ctx.close();
	}
}
