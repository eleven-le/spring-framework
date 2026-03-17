package org.springframework.lab.factorybean;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * getBean 分流演示: 普通 Bean vs FactoryBean (& 前缀)
 *
 * 断点建议:
 *   1. AbstractBeanFactory#getObjectForBeanInstance   (L1860) -- 分流决策点
 *   2. FactoryBeanRegistrySupport#getObjectFromFactoryBean (L96) -- 产物缓存+创建
 *   3. FactoryBeanRegistrySupport#doGetObjectFromFactoryBean (L156) -- 实际调 getObject()
 *   4. DefaultListableBeanFactory#preInstantiateSingletons (L935) -- SmartFactoryBean 提前触发
 */
public class FactoryBeanMain {

	public static void main(String[] args) {
		System.out.println("============ 容器启动 (观察 EagerMetrics 是否在启动期就创建产物) ============");
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(FactoryBeanConfig.class);
		System.out.println("============ 容器启动完毕 ============\n");

		// ========== 场景 1: getBean(name) vs getBean("&" + name) 分流 ==========
		System.out.println("===== 场景1: getBean 分流 (核心) =====");

		// 不带 & → 拿到的是 FactoryBean.getObject() 产物
		Object product = ctx.getBean("payChannel");
		System.out.println("getBean(\"payChannel\")        类型: " + product.getClass().getSimpleName());
		System.out.println("getBean(\"payChannel\")        结果: " + product);

		// 带 & → 拿到的是 FactoryBean 实例本身
		Object factory = ctx.getBean("&payChannel");
		System.out.println("getBean(\"&payChannel\")       类型: " + factory.getClass().getSimpleName());
		System.out.println("getBean(\"&payChannel\")       结果: " + factory);
		System.out.println("是否为 FactoryBean:           " + (factory instanceof FactoryBean));
		System.out.println("channelName:                  " + ((PayChannelFactoryBean) factory).getChannelName());

		// 按类型获取 → 自动解包为产物
		PayChannel byType = ctx.getBean(PayChannel.class);
		System.out.println("getBean(PayChannel.class)    结果: " + byType);
		System.out.println("product == byType ?          " + (product == byType)); // true, 同一缓存

		// 使用产物
		System.out.println("调用 pay():                   " + byType.pay("ORD-20260312-001", 9900));

		// ========== 场景 2: 单例产物缓存验证 ==========
		System.out.println("\n===== 场景2: 单例产物缓存 (factoryBeanObjectCache) =====");
		Object p1 = ctx.getBean("payChannel");
		Object p2 = ctx.getBean("payChannel");
		System.out.println("两次 getBean 是否同一对象:     " + (p1 == p2));
		System.out.println("(getObject() 只调用一次, 第二次直接从 factoryBeanObjectCache 取)");

		// ========== 场景 3: 非单例 FactoryBean -- 每次新建产物 ==========
		System.out.println("\n===== 场景3: isSingleton()=false, 每次产生新对象 =====");
		String token1 = (String) ctx.getBean("tokenGenerator");
		String token2 = (String) ctx.getBean("tokenGenerator");
		String token3 = (String) ctx.getBean("tokenGenerator");
		System.out.println("token1: " + token1);
		System.out.println("token2: " + token2);
		System.out.println("token3: " + token3);
		System.out.println("token1 == token2 ?           " + (token1 == token2)); // false

		// 但 FactoryBean 本身仍是单例
		Object fb1 = ctx.getBean("&tokenGenerator");
		Object fb2 = ctx.getBean("&tokenGenerator");
		System.out.println("&tokenGenerator 同一实例?     " + (fb1 == fb2)); // true

		// ========== 场景 4: SmartFactoryBean#isEagerInit ==========
		System.out.println("\n===== 场景4: SmartFactoryBean (已在启动期提前创建, 上面日志可验证) =====");
		MetricsClient mc = ctx.getBean(MetricsClient.class);
		mc.report("order.pay.latency", 42.5);

		// ========== 场景 5: isFactoryBean 判断 ==========
		System.out.println("\n===== 场景5: BeanFactory#isFactoryBean 判断 =====");
		System.out.println("\"payChannel\" isFactoryBean?  " + ctx.getBeanFactory().isFactoryBean("payChannel"));
		System.out.println("\"&payChannel\" isFactoryBean? " + ctx.getBeanFactory().isFactoryBean("&payChannel"));

		// ========== 场景 6: getBean 对非 FactoryBean 加 & 前缀 → 抛异常 ==========
		System.out.println("\n===== 场景6: 对普通 Bean 加 & 前缀会抛 BeanIsNotAFactoryException =====");
		try {
			ctx.getBean("&factoryBeanConfig"); // factoryBeanConfig 是普通 @Configuration 类
		}
		catch (Exception e) {
			System.out.println("异常类型: " + e.getClass().getSimpleName());
			System.out.println("异常信息: " + e.getMessage());
		}

		ctx.close();
	}
}
