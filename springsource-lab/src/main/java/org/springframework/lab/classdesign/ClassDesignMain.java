package org.springframework.lab.classdesign;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;

/**
 * W99 - Spring 经典类图三层设计：接口 → 抽象骨架 → 默认实现
 *
 * <p>三大体系拆解：
 * <ol>
 *   <li>BeanFactory 体系：BeanFactory → AbstractBeanFactory → DefaultListableBeanFactory</li>
 *   <li>Context 体系：ApplicationContext → AbstractApplicationContext → GenericApplicationContext</li>
 *   <li>TxManager 体系：PlatformTransactionManager → AbstractPlatformTransactionManager → DataSourceTransactionManager</li>
 * </ol>
 *
 * <p>核心设计哲学：接口定 "能做什么"，抽象骨架定 "怎么做的流程"，默认实现定 "用什么做"。
 *
 * <p>断点建议：
 * <ol>
 *   <li>AbstractBeanFactory#doGetBean() 第 254 行 —— 观察 getBean 骨架如何调用 createBean 钩子</li>
 *   <li>AbstractApplicationContext#refresh() 第 554 行 —— 观察模板方法如何编排 12 步</li>
 *   <li>AbstractPlatformTransactionManager#getTransaction() 第 341 行 —— 观察 doGetTransaction/doBegin 如何被调度</li>
 * </ol>
 */
public class ClassDesignMain {

	public static void main(String[] args) {

		System.out.println("========== W99 三层设计全景演示 ==========\n");

		// ------------------------------------------------------------------
		// 一、BeanFactory 体系：接口菱形 → 二级抽象 → 终极实现
		// ------------------------------------------------------------------
		System.out.println("【1】BeanFactory 体系 —— 类层次拆解");
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		printHierarchy(bf, "DefaultListableBeanFactory");
		// 验证接口分层：BeanFactory(只读) → Hierarchical(父子) → Listable(批量) → Configurable(写入)
		System.out.println("  是 BeanFactory?               " + (bf instanceof BeanFactory));
		System.out.println("  是 HierarchicalBeanFactory?    " + (bf instanceof HierarchicalBeanFactory));
		System.out.println("  是 ListableBeanFactory?        " + (bf instanceof ListableBeanFactory));
		System.out.println("  是 ConfigurableBeanFactory?    " + (bf instanceof ConfigurableBeanFactory));
		System.out.println("  是 AutowireCapableBeanFactory? " + (bf instanceof AutowireCapableBeanFactory));
		System.out.println("  是 ConfigurableListableBeanFactory? " + (bf instanceof ConfigurableListableBeanFactory));
		System.out.println("  是 AbstractBeanFactory?        " + (bf instanceof AbstractBeanFactory));
		System.out.println("  是 AbstractAutowireCapableBeanFactory? " + (bf instanceof AbstractAutowireCapableBeanFactory));
		System.out.println();

		// ------------------------------------------------------------------
		// 二、ApplicationContext 体系：Context 接口 → 抽象模板 → 两条实现路线
		// ------------------------------------------------------------------
		System.out.println("【2】ApplicationContext 体系 —— 类层次拆解");
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(ClassDesignConfig.class);
		printHierarchy(ctx, "AnnotationConfigApplicationContext");
		System.out.println("  是 ApplicationContext?             " + (ctx instanceof ApplicationContext));
		System.out.println("  是 ConfigurableApplicationContext? " + (ctx instanceof ConfigurableApplicationContext));
		System.out.println("  是 AbstractApplicationContext?     " + (ctx instanceof AbstractApplicationContext));
		System.out.println("  是 GenericApplicationContext?      " + (ctx instanceof GenericApplicationContext));

		// 关键洞察：Context 内部持有 BeanFactory —— "组合优于继承" 的典范
		ConfigurableListableBeanFactory internalBf = ctx.getBeanFactory();
		System.out.println("  内部 BeanFactory 类型: " + internalBf.getClass().getSimpleName());
		System.out.println("  同一个 DefaultListableBeanFactory? " + (internalBf instanceof DefaultListableBeanFactory));
		System.out.println();

		// ------------------------------------------------------------------
		// 三、TransactionManager 体系：接口 → 抽象骨架 → JDBC 实现
		// ------------------------------------------------------------------
		System.out.println("【3】TransactionManager 体系 —— 类层次拆解");
		DataSourceTransactionManager txMgr = ctx.getBean(DataSourceTransactionManager.class);
		printHierarchy(txMgr, "DataSourceTransactionManager");
		System.out.println("  是 PlatformTransactionManager?         " + (txMgr instanceof PlatformTransactionManager));
		System.out.println("  是 AbstractPlatformTransactionManager? " + (txMgr instanceof AbstractPlatformTransactionManager));
		System.out.println();

		// ------------------------------------------------------------------
		// 四、运行时演示：模板方法调度
		// ------------------------------------------------------------------
		System.out.println("【4】运行时模板方法演示 —— TxManager getTransaction → doGetTransaction → doBegin");
		System.out.println("  → 断点打在 AbstractPlatformTransactionManager#getTransaction() 第 341 行");
		System.out.println("  → F7 进入 doGetTransaction()，会跳转到 DataSourceTransactionManager#doGetTransaction()");
		System.out.println("  → F7 进入 doBegin()，会跳转到 DataSourceTransactionManager#doBegin()");

		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		// ★ 断点位置: AbstractPlatformTransactionManager#getTransaction() 第 341 行
		TransactionStatus status = txMgr.getTransaction(def);
		System.out.println("  事务已开启: isNewTransaction=" + status.isNewTransaction());
		txMgr.commit(status);
		System.out.println("  事务已提交");
		System.out.println();

		// ------------------------------------------------------------------
		// 五、BeanFactory 三个抽象方法的分工验证
		// ------------------------------------------------------------------
		System.out.println("【5】AbstractBeanFactory 三个抽象方法 → 由 DefaultListableBeanFactory / AACBF 实现");
		System.out.println("  containsBeanDefinition(name) → DLBF 查 beanDefinitionMap");
		System.out.println("  getBeanDefinition(name)      → DLBF 查 beanDefinitionMap");
		System.out.println("  createBean(name, mbd, args)  → AACBF 编排实例化+注入+初始化");
		System.out.println("  → 断点打在 AbstractBeanFactory#doGetBean() 第 254 行");
		System.out.println("  → F7 进入 createBean() 会跳到 AbstractAutowireCapableBeanFactory");
		String dummy = ctx.getBean("dummyService", String.class);
		System.out.println("  getBean(\"dummyService\") = " + dummy);
		System.out.println();

		// ------------------------------------------------------------------
		// 六、AbstractApplicationContext 三个抽象方法分工验证
		// ------------------------------------------------------------------
		System.out.println("【6】AbstractApplicationContext 三个抽象方法 → 由 GenericApplicationContext 实现");
		System.out.println("  refreshBeanFactory() → GAC: CAS 保证只 refresh 一次，不新建 BF");
		System.out.println("  closeBeanFactory()   → GAC: 清理序列化 ID");
		System.out.println("  getBeanFactory()     → GAC: 直接返回构造器注入的 DefaultListableBeanFactory");
		System.out.println("  → 对比 AbstractRefreshableApplicationContext: 每次 refresh 销毁旧 BF，新建新 BF");
		System.out.println();

		System.out.println("========== 三层设计 核心洞察 ==========");
		System.out.println("1. 接口层 = 契约(能力宣告)，面向调用者；不同子接口 = 不同视角的能力切面");
		System.out.println("2. 抽象层 = 骨架(模板方法)，面向框架自身；final 方法锁流程，abstract/protected 开扩展点");
		System.out.println("3. 实现层 = 落地(数据结构+资源绑定)，面向技术选型；DLBF=ConcurrentHashMap, DSTM=JDBC Connection");
		System.out.println("4. Context 对 BeanFactory 用组合而非继承 → 门面模式，对外统一入口，对内委托");
		System.out.println("5. 三层分离 = 职责正交：改流程不改存储，改存储不改契约");

		ctx.close();
	}

	/** 打印对象的类继承链 */
	private static void printHierarchy(Object obj, String label) {
		System.out.println("  " + label + " 类继承链:");
		Class<?> clz = obj.getClass();
		StringBuilder indent = new StringBuilder("    ");
		while (clz != null) {
			System.out.println(indent + "↑ " + clz.getSimpleName());

			// 打印该层直接实现的接口
			Class<?>[] interfaces = clz.getInterfaces();
			if (interfaces.length > 0) {
				StringBuilder ifNames = new StringBuilder();
				for (Class<?> iface : interfaces) {
					if (ifNames.length() > 0) ifNames.append(", ");
					ifNames.append(iface.getSimpleName());
				}
				System.out.println(indent + "  implements: " + ifNames);
			}

			clz = clz.getSuperclass();
			indent.append("  ");
		}
	}

	@Configuration
	static class ClassDesignConfig {

		@Bean
		public DataSource dataSource() {
			DriverManagerDataSource ds = new DriverManagerDataSource();
			ds.setDriverClassName("org.h2.Driver");
			ds.setUrl("jdbc:h2:mem:classdesign;DB_CLOSE_DELAY=-1");
			ds.setUsername("sa");
			ds.setPassword("");
			return ds;
		}

		@Bean
		public DataSourceTransactionManager transactionManager(DataSource ds) {
			return new DataSourceTransactionManager(ds);
		}

		@Bean
		public String dummyService() {
			return "I am a simple bean for getBean() demo";
		}
	}
}
