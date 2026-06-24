package com.leilei.lab.laboratory.l02.l02_06;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;

/**
 * 📖 知识点：[[L02-06-Bean生命周期回调全图#2.2 @PostConstruct/@PreDestroy 原理]]（CommonAnnotationBeanPostProcessor / LifecycleMetadata）
 * 🎯 作用：拆穿 {@code @PostConstruct}/{@code @PreDestroy} 的「魔法」——它们不是 JVM/容器内建语义，
 *         而是 {@link CommonAnnotationBeanPostProcessor}（一个 {@link InitDestroyAnnotationBeanPostProcessor} 子类）
 *         在 before-init / before-destruction 阶段用反射扫描 {@code LifecycleMetadata} 后回调的结果。
 *         本实验三步坐实：① 手动驱动 BPP 触发回调；② 原理是「注解类型可插拔」（CommonAnnotationBPP 只是预设了
 *         PostConstruct/PreDestroy）；③ 一旦那个 BPP 不在场（裸 BeanFactory / JDK 9+ 移除 javax.annotation 又漏补依赖），
 *         {@code @PostConstruct} 就是块死注解，静默失效。
 * 🔗 业务场景：库存中心「门店库存快照预热 Bean」用 {@code @PostConstruct} 在启动期加载快照——
 *         若该 Bean 被塞进一个没注册 CommonAnnotationBPP 的自定义子容器/裸 BeanFactory，预热静默不执行，
 *         线上表现为「库存永远是 0、下单全部判无货」，且无任何报错，极难排查。
 */
public final class L0206_02_PostConstructPrincipleDemo {

	private L0206_02_PostConstructPrincipleDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 手动驱动 CommonAnnotationBeanPostProcessor：@PostConstruct/@PreDestroy 全靠它 ====================");
		CommonAnnotationBeanPostProcessor bpp = new CommonAnnotationBeanPostProcessor();
		InventorySnapshotBean bean = new InventorySnapshotBean();
		System.out.println("[new 之后] ready=" + bean.ready + "  ← 构造完，@PostConstruct 还没跑");
		bpp.postProcessBeforeInitialization(bean, "inventorySnapshotBean");   // 等价容器 before-init 阶段
		System.out.println("[before-init 之后] ready=" + bean.ready + "  ← @PostConstruct 由 BPP 反射触发");
		bpp.postProcessBeforeDestruction(bean, "inventorySnapshotBean");      // 等价容器销毁阶段
		System.out.println();

		System.out.println("==================== 2. 原理 = 注解类型可插拔的 InitDestroyAnnotationBeanPostProcessor ====================");
		InitDestroyAnnotationBeanPostProcessor custom = new InitDestroyAnnotationBeanPostProcessor();
		custom.setInitAnnotationType(WarmUp.class);       // CommonAnnotationBPP 内部就是这样预设 PostConstruct/PreDestroy
		custom.setDestroyAnnotationType(Evict.class);
		CustomAnnotatedBean cab = new CustomAnnotatedBean();
		custom.postProcessBeforeInitialization(cab, "customAnnotatedBean");
		custom.postProcessBeforeDestruction(cab, "customAnnotatedBean");
		System.out.println("[小结] 同一套机制，换个注解类型就换套回调 ⇒ @PostConstruct 没有任何特殊待遇");
		System.out.println();

		System.out.println("==================== 3. 事故复现：没有那个 BPP，@PostConstruct 就是死注解（静默失效）====================");
		DefaultListableBeanFactory bareFactory = new DefaultListableBeanFactory();   // 故意不注册 CommonAnnotationBPP
		bareFactory.registerBeanDefinition("inv", new RootBeanDefinition(InventorySnapshotBean.class));
		InventorySnapshotBean fromBare = bareFactory.getBean("inv", InventorySnapshotBean.class);
		System.out.println("[裸 BeanFactory 取出] ready=" + fromBare.ready
				+ "  ← @PostConstruct 没人触发，库存快照未加载，下单全判无货且无任何报错");
	}

	/** 库存快照预热 Bean：靠 @PostConstruct 加载快照、@PreDestroy 回写增量。 */
	static class InventorySnapshotBean {

		boolean ready = false;

		@PostConstruct
		void loadSnapshot() {
			this.ready = true;
			System.out.println("    [@PostConstruct] 门店库存快照加载完成 ready=true");
		}

		@PreDestroy
		void flush() {
			System.out.println("    [@PreDestroy] 回写库存增量、释放连接");
		}
	}

	/** 用自定义注解证明「init/destroy 注解类型可插拔」：CommonAnnotationBPP 只是把它预设成了 PostConstruct/PreDestroy。 */
	static class CustomAnnotatedBean {

		@WarmUp
		void prime() {
			System.out.println("    [@WarmUp] 自定义 init 注解被同一套机制识别并回调");
		}

		@Evict
		void clear() {
			System.out.println("    [@Evict] 自定义 destroy 注解被同一套机制识别并回调");
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@interface WarmUp {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@interface Evict {
	}
}
