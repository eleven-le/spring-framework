package org.springframework.lab.createbean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 全生命周期 Bean — 演示 createBean 主链中每一个回调的执行顺序。
 *
 * 预期打印顺序:
 * 1. 构造器
 * 2. BeanNameAware.setBeanName
 * 3. BeanFactoryAware.setBeanFactory
 * 4. @PostConstruct (BPP.before 阶段, CommonAnnotationBPP)
 * 5. InitializingBean.afterPropertiesSet
 * 6. @Bean(initMethod="customInit")
 * 7. BPP.postProcessAfterInitialization
 * ---
 * 8. @PreDestroy
 * 9. DisposableBean.destroy
 * 10. @Bean(destroyMethod="customDestroy")
 *
 * 注意: OrderService 不加 @Component，用 @Bean(initMethod/destroyMethod) 注册，
 * 这样可以同时演示三种初始化 + 三种销毁回调。
 */
public class OrderService implements BeanNameAware, BeanFactoryAware,
		InitializingBean, DisposableBean {

	@Autowired
	private PayService payService;

	public OrderService() {
		System.out.println("[OrderService]   ① 构造器执行 (createBeanInstance)");
	}

	// ---- Aware 回调 (initializeBean → invokeAwareMethods) ----

	@Override
	public void setBeanName(String name) {
		System.out.println("[OrderService]   ② BeanNameAware.setBeanName = " + name);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		System.out.println("[OrderService]   ③ BeanFactoryAware.setBeanFactory");
	}

	// ---- init 回调 (initializeBean → invokeInitMethods) ----

	@PostConstruct
	public void postConstruct() {
		System.out.println("[OrderService]   ④ @PostConstruct (BPP.before 阶段)");
	}

	@Override
	public void afterPropertiesSet() {
		System.out.println("[OrderService]   ⑤ InitializingBean.afterPropertiesSet");
	}

	public void customInit() {
		System.out.println("[OrderService]   ⑥ @Bean(initMethod=\"customInit\")");
	}

	// ---- destroy 回调 ----

	@PreDestroy
	public void preDestroy() {
		System.out.println("[OrderService]   ⑧ @PreDestroy");
	}

	@Override
	public void destroy() {
		System.out.println("[OrderService]   ⑨ DisposableBean.destroy");
	}

	public void customDestroy() {
		System.out.println("[OrderService]   ⑩ @Bean(destroyMethod=\"customDestroy\")");
	}

	// ---- 业务方法 ----

	public String placeOrder(String item) {
		return "ORDER-OK: " + item + " | " + payService.pay(item, 100);
	}
}
