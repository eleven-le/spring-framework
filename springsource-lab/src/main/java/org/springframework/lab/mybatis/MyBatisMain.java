package org.springframework.lab.mybatis;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import org.apache.ibatis.binding.MapperProxy;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W92 MyBatis-Spring 集成原理:
 *   MapperScannerConfigurer / MapperFactoryBean / SqlSessionTemplate
 *
 * 核心抽象:
 *   "接口即 DAO" — 开发者只写接口 + SQL 注解/XML, 运行时由三巨头协作
 *   自动完成: 扫描注册(MapperScannerConfigurer) → 代理生产(MapperFactoryBean)
 *           → 会话管理(SqlSessionTemplate), 彻底消除手写 SqlSession 的样板代码。
 *
 * 调用链 (12 步):
 *   1.  MapperScannerConfigurer#postProcessBeanDefinitionRegistry : BDRPP 入口, 触发扫描
 *   2.  ClassPathMapperScanner#scan                               : 扫描 basePackage 下所有接口
 *   3.  ClassPathMapperScanner#processBeanDefinitions             : 修改 BD: beanClass→MapperFactoryBean
 *   4.  SqlSessionFactoryBean#afterPropertiesSet                  : InitializingBean 回调, 构建 SqlSessionFactory
 *   5.  SqlSessionFactoryBean#buildSqlSessionFactory              : 解析 Configuration/Environment/MapperRegistry
 *   6.  getBean("orderMapper") → MapperFactoryBean#getObject      : FactoryBean 产出 Mapper 代理
 *   7.  SqlSessionTemplate#getMapper                              : 委托给 MyBatis Configuration.getMapper()
 *   8.  MapperProxyFactory#newInstance                             : JDK Proxy.newProxyInstance(MapperProxy)
 *   9.  业务调用 orderMapper.findById(1)                           : 进入 MapperProxy#invoke
 *  10.  MapperMethod#execute                                      : 路由 SELECT/INSERT/UPDATE/DELETE
 *  11.  SqlSessionTemplate.SqlSessionInterceptor#invoke           : 代理拦截, getSqlSession→执行→closeSqlSession
 *  12.  SqlSessionUtils#getSqlSession                             : 检查 TransactionSynchronizationManager, 决定复用或新建
 *
 * 断点建议 (5 个):
 *   1. MapperScannerConfigurer#postProcessBeanDefinitionRegistry  — 观察 BDRPP 何时触发、扫到几个接口
 *   2. ClassPathMapperScanner#processBeanDefinitions              — 观察 BD 如何从接口改写为 MapperFactoryBean
 *   3. MapperFactoryBean#getObject                                — 观察 FactoryBean 如何产出代理
 *   4. SqlSessionTemplate 内部类 SqlSessionInterceptor#invoke     — 观察每次 SQL 的会话生命周期
 *   5. SqlSessionUtils#getSqlSession                              — 观察事务内复用 vs 非事务新建
 */
public class MyBatisMain {

	public static void main(String[] args) throws Exception {
		System.out.println("╔══════════════════════════════════════════════════════════════╗");
		System.out.println("║  W92 MyBatis-Spring 集成原理 — 三巨头协作全景               ║");
		System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(MyBatisConfig.class);

		OrderMapper orderMapper = ctx.getBean(OrderMapper.class);
		OrderService orderService = ctx.getBean(OrderService.class);

		// ===== 场景 1: 验证 Mapper 代理本质 =====
		System.out.println("===== 场景1: Mapper 代理本质 — MapperProxy (JDK 动态代理) =====");
		System.out.println("orderMapper 类型: " + orderMapper.getClass().getName());
		System.out.println("是否 JDK 代理:    " + Proxy.isProxyClass(orderMapper.getClass()));
		if (Proxy.isProxyClass(orderMapper.getClass())) {
			Object handler = Proxy.getInvocationHandler(orderMapper);
			System.out.println("InvocationHandler: " + handler.getClass().getName());
			// MapperProxy 内部持有 SqlSession → 实际是 SqlSessionTemplate
			if (handler instanceof MapperProxy) {
				Field sqlSessionField = MapperProxy.class.getDeclaredField("sqlSession");
				sqlSessionField.setAccessible(true);
				Object sqlSession = sqlSessionField.get(handler);
				System.out.println("MapperProxy 持有的 SqlSession: " + sqlSession.getClass().getName());
				System.out.println("是否 SqlSessionTemplate:       " + (sqlSession instanceof SqlSessionTemplate));
			}
		}

		// ===== 场景 2: BeanDefinition 层面验证 MapperScannerConfigurer 的工作成果 =====
		System.out.println("\n===== 场景2: BeanDefinition 层面 — MapperFactoryBean 注册验证 =====");
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ctx.getBeanFactory();
		BeanDefinition mapperBD = beanFactory.getBeanDefinition("orderMapper");
		System.out.println("orderMapper BD.beanClassName: " + mapperBD.getBeanClassName());
		System.out.println("是否 MapperFactoryBean:       " +
				MapperFactoryBean.class.getName().equals(mapperBD.getBeanClassName()));
		System.out.println("构造参数 (接口类型):           " +
				mapperBD.getConstructorArgumentValues().getGenericArgumentValues());

		// 用 & 前缀获取 FactoryBean 本身
		Object rawFactory = ctx.getBean("&orderMapper");
		System.out.println("getBean(\"&orderMapper\") 类型: " + rawFactory.getClass().getName());

		// ===== 场景 3: 基本 CRUD — 无事务模式 =====
		System.out.println("\n===== 场景3: 基本查询 — 无事务 (每次调用独立 SqlSession) =====");
		Order order1 = orderMapper.findById(1L);
		System.out.println("findById(1): " + order1);
		List<Order> userOrders = orderMapper.findByUserId(1001L);
		System.out.println("findByUserId(1001) 数量: " + userOrders.size());
		userOrders.forEach(o -> System.out.println("  → " + o));

		// 无事务下两次查询返回不同对象 (不同 SqlSession, 无一级缓存)
		Order a = orderMapper.findById(1L);
		Order b = orderMapper.findById(1L);
		System.out.println("无事务 — 两次查询同一对象? " + (a == b) + " (预期 false, 不同 SqlSession)");

		// ===== 场景 4: 事务内查询 — SqlSession 复用 + 一级缓存 =====
		System.out.println("\n===== 场景4: 事务内查询 — SqlSession 复用, 一级缓存生效 =====");
		Order txOrder = orderService.queryOrderInTx(1L);
		System.out.println("事务内查询结果: " + txOrder);
		System.out.println("(上方日志 first == second 应为 true — 一级缓存命中)");

		// ===== 场景 5: 事务写操作 — 同一 SqlSession 保证原子性 =====
		System.out.println("\n===== 场景5: 事务写操作 — insert + update 在同一事务 =====");
		Order newOrder = orderService.placeOrder("ORD-2026-NEW", 1003L, 25600L);
		System.out.println("下单结果: " + newOrder);
		System.out.println("状态验证: " + newOrder.getStatus() + " (预期 PAID)");

		// ===== 场景 6: SqlSessionTemplate 线程安全本质 =====
		System.out.println("\n===== 场景6: SqlSessionTemplate 内部代理结构 =====");
		SqlSessionTemplate template = ctx.getBean(SqlSessionTemplate.class);
		System.out.println("SqlSessionTemplate 类型:     " + template.getClass().getName());
		// 反射查看内部 sqlSessionProxy
		Field proxyField = SqlSessionTemplate.class.getDeclaredField("sqlSessionProxy");
		proxyField.setAccessible(true);
		Object sqlSessionProxy = proxyField.get(template);
		System.out.println("内部 sqlSessionProxy 类型:   " + sqlSessionProxy.getClass().getName());
		System.out.println("是否 JDK 代理:               " + Proxy.isProxyClass(sqlSessionProxy.getClass()));
		if (Proxy.isProxyClass(sqlSessionProxy.getClass())) {
			System.out.println("InvocationHandler:           " +
					Proxy.getInvocationHandler(sqlSessionProxy).getClass().getName());
			System.out.println("(这就是 SqlSessionInterceptor — 每次调用都走它, 决定 session 复用/新建)");
		}

		// ===== 场景 7: 全量数据验证 =====
		System.out.println("\n===== 场景7: 全量数据验证 =====");
		List<Order> all1001 = orderService.listByUser(1001L);
		System.out.println("用户 1001 订单数: " + all1001.size());
		all1001.forEach(o -> System.out.println("  → " + o));

		System.out.println("\n============ 容器关闭 ============");
		ctx.close();
	}
}
