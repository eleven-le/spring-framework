package com.leilei.lab.laboratory.l02.l02_09;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 📖 知识点：[[L02-09-容器基础设施-Resource-SpEL-MessageSource-父子容器#2.3 MessageSource：国际化与文案外置]]
 * 🎯 作用：演示 MessageSource 的四个要点——
 *         ① {@code ResourceBundleMessageSource} 按 basename + {@code Locale} 解析文案，{@code {0}} 占位用 {@code MessageFormat} 填参；
 *         ② Bean <b>必须命名为 "messageSource"</b>，{@code ApplicationContext} 才会接管它，从而 {@code ctx.getMessage(...)} 直接可用（命名错→静默退化为返回 code 本身/抛 NoSuchMessageException）；
 *         ③ {@code defaultMessage} 兜底缺失 code，避免线上抛 {@code NoSuchMessageException}；
 *         ④ {@code MessageSourceAccessor} 绑定一个默认 Locale，省去每次传 Locale。
 * 🔗 业务场景：古茗 C 端下单错误码 / 营销文案外置——国内门店走中文、海外（新加坡）门店按 {@code Locale.ENGLISH} 出英文，
 *         文案改动不发版、不动代码，运营改 properties 即可。
 */
public final class L0209_03_MessageSourceI18nDemo {

	public static void main(String[] args) {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(I18nConfig.class)) {

			System.out.println("==================== 1. 同一 code 按 Locale 出不同语言（{0} 占位填参） ====================");
			// ctx 自身就是 MessageSource：因为容器接管了名为 "messageSource" 的 Bean
			String zh = ctx.getMessage("order.fail.stock", new Object[] {3}, Locale.SIMPLIFIED_CHINESE);
			String en = ctx.getMessage("order.fail.stock", new Object[] {3}, Locale.ENGLISH);
			System.out.println("zh-CN = " + zh);
			System.out.println("en    = " + en);
			System.out.println("营销文案 zh-CN = " + ctx.getMessage("promo.welcome", new Object[] {"小明"}, Locale.SIMPLIFIED_CHINESE));
			System.out.println("营销文案 en    = " + ctx.getMessage("promo.welcome", new Object[] {"Tom"}, Locale.ENGLISH));

			System.out.println();
			System.out.println("==================== 2. defaultMessage 兜底缺失 code（避免线上抛 NoSuchMessageException） ====================");
			String withDefault = ctx.getMessage("order.fail.unknown_code", null, "未知下单错误，请稍后重试", Locale.SIMPLIFIED_CHINESE);
			System.out.println("缺失 code + defaultMessage = " + withDefault);
			try {
				ctx.getMessage("order.fail.unknown_code", null, Locale.SIMPLIFIED_CHINESE);
				System.out.println("不该走到这里");
			}
			catch (NoSuchMessageException ex) {
				System.out.println("缺失 code 且无 defaultMessage → 抛 = " + ex.getClass().getSimpleName());
			}

			System.out.println();
			System.out.println("==================== 3. MessageSourceAccessor 绑定默认 Locale，调用免传 Locale ====================");
			MessageSource messageSource = ctx.getBean("messageSource", MessageSource.class);
			MessageSourceAccessor enAccessor = new MessageSourceAccessor(messageSource, Locale.ENGLISH);
			System.out.println("accessor(en).getMessage(order.success) = "
					+ enAccessor.getMessage("order.success", new Object[] {5}));
		}
	}

	@Configuration
	static class I18nConfig {

		/**
		 * ⚠️ Bean 名必须正好是 "messageSource"——AbstractApplicationContext#initMessageSource 只认这个名字。
		 * 用 @Bean 方法名声明即可（方法名即 Bean 名）。
		 */
		@Bean
		MessageSource messageSource() {
			ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
			ms.setBasename("i18n/order-messages");
			ms.setDefaultEncoding("UTF-8");
			// 找不到对应 Locale 时回退到默认（不带后缀的）messages 文件
			ms.setFallbackToSystemLocale(false);
			return ms;
		}
	}

}
