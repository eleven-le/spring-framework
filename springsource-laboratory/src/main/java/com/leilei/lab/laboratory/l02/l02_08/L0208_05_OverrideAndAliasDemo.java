package com.leilei.lab.laboratory.l02.l02_08;

import java.util.Arrays;

import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedRuntimeException;

/**
 * 📖 知识点：[[L02-08-歧义裁决与容器语义-Primary-Qualifier-DependsOn-Bean覆盖#2.5 Bean 覆盖语义与别名]]
 *         （allowBeanDefinitionOverriding / 别名机制）
 * 🎯 作用：演示同名 Bean 定义覆盖的两种容器语义与别名机制：
 *         ① 默认 {@code allowBeanDefinitionOverriding=true}——后注册的同名定义<b>静默覆盖</b>先前的（后来者胜），
 *            这正是「两份配置撞名、灰度切流注错实现」事故的温床；
 *         ② {@code setAllowBeanDefinitionOverriding(false)}——撞名直接抛 {@code BeanDefinitionOverrideException} fail-fast；
 *         ③ 别名（一个 Bean 多名）：{@code @Bean(name={主名, 别名...})} 注册后，按主名 / 任一别名 {@code getBean} 拿到同一单例。
 * 🔗 业务场景：古茗支付网关从「单一支付宝」演进到「灰度切微信支付」时，两份配置类各定义一个 paymentGateway——
 *         默认覆盖语义下哪份生效取决于装配顺序，极易在灰度时悄悄注错网关；线上应关闭覆盖以启动期暴露撞名。
 */
public final class L0208_05_OverrideAndAliasDemo {

	private L0208_05_OverrideAndAliasDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 默认允许覆盖：后注册的同名定义静默胜出 ====================");
		try (AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(BaseGatewayConfig.class, GrayGatewayConfig.class)) {
			PaymentGateway gateway = ctx.getBean("paymentGateway", PaymentGateway.class);
			System.out.println("撞名两份 paymentGateway，默认覆盖后实际生效 = " + gateway.channel()
					+ "（后注册的 GrayGatewayConfig 胜出，无任何报错）");
		}

		System.out.println();
		System.out.println("==================== 2. 关闭覆盖：撞名启动期 fail-fast ====================");
		AnnotationConfigApplicationContext strict = new AnnotationConfigApplicationContext();
		strict.setAllowBeanDefinitionOverriding(false);
		strict.register(BaseGatewayConfig.class, GrayGatewayConfig.class);
		try {
			strict.refresh();
			System.out.println("不该走到这里：" + strict);
		}
		catch (NestedRuntimeException ex) {
			System.out.println("关闭覆盖后撞名被拦下 = " + ex.contains(BeanDefinitionOverrideException.class));
			System.out.println("  根因：" + rootMessage(ex));
		}
		finally {
			strict.close();
		}

		System.out.println();
		System.out.println("==================== 3. 别名：一个 Bean 多名，按任一名拿到同一单例 ====================");
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AliasGatewayConfig.class)) {
			PaymentGateway byMain = ctx.getBean("alipayGateway", PaymentGateway.class);
			PaymentGateway byAlias1 = ctx.getBean("paymentGateway", PaymentGateway.class);
			PaymentGateway byAlias2 = ctx.getBean("defaultGateway", PaymentGateway.class);
			System.out.println("主名 alipayGateway 与别名 paymentGateway / defaultGateway 同一实例 = "
					+ (byMain == byAlias1 && byMain == byAlias2));
			System.out.println("alipayGateway 的别名清单 = " + Arrays.toString(ctx.getAliases("alipayGateway")));
		}
	}

	private static String rootMessage(Throwable ex) {
		Throwable cur = ex;
		while (cur.getCause() != null) {
			cur = cur.getCause();
		}
		String msg = cur.getMessage();
		if (msg != null && msg.length() > 160) {
			msg = msg.substring(0, 160) + "...";
		}
		return cur.getClass().getSimpleName() + ": " + msg;
	}

	// ===================== 支付网关 =====================

	interface PaymentGateway {

		String channel();
	}

	static class AlipayGateway implements PaymentGateway {

		@Override
		public String channel() {
			return "支付宝";
		}
	}

	static class WechatGateway implements PaymentGateway {

		@Override
		public String channel() {
			return "微信支付";
		}
	}

	/** 基线配置：paymentGateway = 支付宝。 */
	@Configuration
	static class BaseGatewayConfig {

		@Bean
		PaymentGateway paymentGateway() {
			return new AlipayGateway();
		}
	}

	/** 灰度配置：同名 paymentGateway = 微信支付（与基线撞名）。 */
	@Configuration
	static class GrayGatewayConfig {

		@Bean
		PaymentGateway paymentGateway() {
			return new WechatGateway();
		}
	}

	/** 别名配置：主名 alipayGateway + 两个别名。 */
	@Configuration
	static class AliasGatewayConfig {

		@Bean(name = {"alipayGateway", "paymentGateway", "defaultGateway"})
		PaymentGateway alipayGateway() {
			return new AlipayGateway();
		}
	}

}
