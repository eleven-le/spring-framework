package org.springframework.lab.hierarchy;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W06 - 父子容器与边界：ApplicationContext Hierarchy
 *
 * <p>三大核心维度验证：
 * <ol>
 *   <li>查找可见性：子 -> 父可见，父 -> 子不可见</li>
 *   <li>事件传播：子容器 publishEvent 会冒泡到父容器</li>
 *   <li>基础设施隔离：父容器的 BPP 不会影响子容器的 Bean 创建</li>
 * </ol>
 *
 * <p>断点位置：
 * <ul>
 *   <li>AbstractBeanFactory#doGetBean : 278 行 —— parentBeanFactory 委托分支</li>
 *   <li>AbstractApplicationContext#publishEvent : 434 行 —— 事件向父传播</li>
 *   <li>GenericApplicationContext#setParent : 159 行 —— 建立父子关系</li>
 * </ul>
 */
public class HierarchyMain {

	// ====================== 自定义事件 ======================
	static class OrderCreatedEvent extends ApplicationEvent {
		private final String orderId;

		OrderCreatedEvent(Object source, String orderId) {
			super(source);
			this.orderId = orderId;
		}

		@Override
		public String toString() {
			return "OrderCreatedEvent{orderId='" + orderId + "'}";
		}
	}

	public static void main(String[] args) {
		System.out.println("========== Scene 1: Bean 查找可见性 ==========");
		testBeanVisibility();

		System.out.println("\n========== Scene 2: 事件传播机制 ==========");
		testEventPropagation();

		System.out.println("\n========== Scene 3: 基础设施(BPP)隔离 ==========");
		testBppIsolation();
	}

	/**
	 * 场景 1：Bean 查找可见性
	 *
	 * <p>核心链路：AbstractBeanFactory#doGetBean
	 *   -> if (parentBeanFactory != null && !containsBeanDefinition(beanName))
	 *   -> parentBeanFactory.getBean(...)
	 *
	 * <p>验证点：
	 *   - 子容器 getBean("dataSource") 能拿到父容器的 Bean（向上委托）
	 *   - 父容器 getBean("orderController") 抛 NoSuchBeanDefinitionException（父不认子）
	 *   - 子容器同名 Bean 优先于父容器（containsBeanDefinition 为 true 时不委托）
	 */
	static void testBeanVisibility() {
		// 1. 创建并刷新父容器
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(ParentConfig.class);
		parent.setDisplayName("PARENT-CTX");

		// 2. 创建子容器，设置父容器后再刷新
		// 断点：GenericApplicationContext#setParent -> beanFactory.setParentBeanFactory(...)
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.setDisplayName("CHILD-CTX");
		child.setParent(parent);
		child.register(ChildConfig.class);
		child.refresh();

		// 验证 1：子容器可以拿到父容器的 dataSource
		// 断点：AbstractBeanFactory#doGetBean 278 行，观察 parentBeanFactory 不为 null
		DataSourceBean ds = child.getBean(DataSourceBean.class);
		System.out.println("[子 -> 父] child.getBean(DataSourceBean) = " + ds);

		// 验证 2：父容器拿不到子容器的 orderController
		try {
			parent.getBean(OrderController.class);
			System.out.println("[父 -> 子] ERROR: 不应该走到这里！");
		}
		catch (NoSuchBeanDefinitionException e) {
			System.out.println("[父 -> 子] parent.getBean(OrderController) -> " + e.getClass().getSimpleName());
		}

		// 验证 3：子容器同名 Bean 覆盖父容器
		// 断点：AbstractBeanFactory#doGetBean 280 行，containsBeanDefinition 返回 true，不走父容器
		SharedService fromChild = child.getBean(SharedService.class);
		SharedService fromParent = parent.getBean(SharedService.class);
		System.out.println("[同名覆盖] child 拿到: " + fromChild + " | parent 拿到: " + fromParent);

		// 验证 4：containsLocalBean vs containsBean
		System.out.println("[containsLocalBean] child.containsLocalBean('dataSource') = "
				+ child.containsLocalBean("dataSource"));
		System.out.println("[containsBean]      child.containsBean('dataSource')      = "
				+ child.containsBean("dataSource"));

		child.close();
		parent.close();
	}

	/**
	 * 场景 2：事件传播机制
	 *
	 * <p>核心链路：AbstractApplicationContext#publishEvent
	 *   -> getApplicationEventMulticaster().multicastEvent(...)  // 先在本容器广播
	 *   -> if (this.parent != null) parent.publishEvent(event)   // 再冒泡到父
	 *
	 * <p>验证点：
	 *   - 子容器发布事件，父容器监听器也能收到（单向冒泡）
	 *   - 父容器发布事件，子容器监听器收不到（父不通知子）
	 */
	static void testEventPropagation() {
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
		parent.setDisplayName("PARENT-CTX");
		parent.register(ParentConfig.class);
		// 父容器注册监听器
		parent.addApplicationListener((ApplicationListener<OrderCreatedEvent>) event ->
				System.out.println("  [PARENT-Listener] 收到事件: " + event));
		parent.refresh();

		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.setDisplayName("CHILD-CTX");
		child.setParent(parent);
		child.register(ChildConfig.class);
		// 子容器注册监听器
		child.addApplicationListener((ApplicationListener<OrderCreatedEvent>) event ->
				System.out.println("  [CHILD-Listener]  收到事件: " + event));
		child.refresh();

		// 子容器发布事件 -> 子容器监听器和父容器监听器都会收到
		// 断点：AbstractApplicationContext#publishEvent 434 行 (if this.parent != null)
		System.out.println("--- 子容器发布 OrderCreatedEvent ---");
		child.publishEvent(new OrderCreatedEvent(child, "ORD-2024-001"));

		// 父容器发布事件 -> 只有父容器监听器收到
		System.out.println("--- 父容器发布 OrderCreatedEvent ---");
		parent.publishEvent(new OrderCreatedEvent(parent, "ORD-2024-002"));

		child.close();
		parent.close();
	}

	/**
	 * 场景 3：基础设施（BPP）隔离
	 *
	 * <p>核心原理：每个容器独立执行 refresh() -> registerBeanPostProcessors()
	 *   BPP 是从当前 BeanFactory 的 BeanDefinition 中查找并注册的，
	 *   不会向上查找父容器的 BPP。
	 *
	 * <p>验证点：
	 *   - 父容器的 TimingBpp 只拦截父容器的 Bean 创建
	 *   - 子容器创建 Bean 时不会触发父容器的 TimingBpp
	 */
	static void testBppIsolation() {
		System.out.println("--- 父容器 refresh（TimingBpp 只拦截父容器 Bean）---");
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(ParentWithBppConfig.class);
		parent.setDisplayName("PARENT-CTX");

		System.out.println("--- 子容器 refresh（TimingBpp 不会出现）---");
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.setDisplayName("CHILD-CTX");
		child.setParent(parent);
		child.register(ChildConfig.class);
		child.refresh();

		System.out.println("[结论] 子容器的 orderController 创建过程中没有 TimingBpp 的输出 -> BPP 不继承");

		child.close();
		parent.close();
	}
}
