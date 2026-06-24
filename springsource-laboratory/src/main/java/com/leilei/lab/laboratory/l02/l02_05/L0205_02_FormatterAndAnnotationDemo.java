package com.leilei.lab.laboratory.l02.l02_05;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import com.leilei.lab.laboratory.common.domain.Channel;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.Formatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.support.DefaultFormattingConversionService;

/**
 * 📖 知识点：[[L02-05-类型转换-ConversionService-PropertyEditor-Formatter#2.2 Formatter 与注解格式化]]（Formatter / @DateTimeFormat / @NumberFormat）
 * 🎯 作用：把 Formatter 体系坐实成可运行实验——{@link Formatter} = {@code Printer}(对象→展示串) + {@code Parser}(展示串→对象)，
 *         是 {@link org.springframework.core.convert.converter.Converter} 的「双向 + Locale 感知」超集；
 *         {@code @DateTimeFormat}/{@code @NumberFormat} 的注解格式化能力由
 *         {@link DefaultFormattingConversionService}（经 {@code addFormatterForFieldAnnotation} 注册的
 *         AnnotationFormatterFactory）提供——纯 ConversionService 不认这两个注解（见 §3 事故二）。
 *         {@link BeanWrapperImpl} 作为 TypeConverter 入口，{@code setPropertyValue} 时按字段上的注解走对应格式化器。
 * 🔗 业务场景：营销活动后台运营手填的都是「人类可读串」——活动开始时间「2026-06-24 10:00:00」、
 *         折扣率「15%」、客单价封顶「1,200.00」——要双向绑定到活动配置对象；
 *         自定义 {@link Formatter} 让渠道枚举既能解析中文也能渲染中文给前端展示。
 */
public final class L0205_02_FormatterAndAnnotationDemo {

	private L0205_02_FormatterAndAnnotationDemo() {
	}

	public static void main(String[] args) throws Exception {
		// 货币 / 百分比格式化按当前 Locale 走，固定为中国以保证输出确定
		Locale previous = LocaleContextHolder.getLocale();
		LocaleContextHolder.setLocale(Locale.CHINA);
		try {
			DefaultFormattingConversionService fcs = new DefaultFormattingConversionService();
			fcs.addFormatter(new ChannelFormatter()); // 自定义 Formatter：双向 + Locale 感知

			System.out.println("==================== 1. @DateTimeFormat / @NumberFormat：营销后台字符串 → 活动配置对象（注解驱动格式化） ====================");
			BeanWrapper bw = new BeanWrapperImpl(new PromotionForm());
			bw.setConversionService(fcs); // 关键：用 FormattingConversionService，注解才生效（普通 ConversionService 不认注解）
			bw.setPropertyValue("startTime", "2026-06-24 10:00:00"); // @DateTimeFormat(pattern)
			bw.setPropertyValue("endDate", "2026-07-01");            // @DateTimeFormat(iso=DATE)
			bw.setPropertyValue("discountRate", "15%");              // @NumberFormat(style=PERCENT) → 0.15
			bw.setPropertyValue("capAmount", "1,200.00");            // @NumberFormat(pattern)
			PromotionForm form = (PromotionForm) bw.getWrappedInstance();
			System.out.println("[startTime]    \"2026-06-24 10:00:00\" → " + form.startTime + "  (" + form.startTime.getClass().getSimpleName() + ")");
			System.out.println("[endDate]      \"2026-07-01\"          → " + form.endDate);
			System.out.println("[discountRate] \"15%\"                 → " + form.discountRate + "  (PERCENT 解析成小数)");
			System.out.println("[capAmount]    \"1,200.00\"            → " + form.capAmount);
			System.out.println();

			System.out.println("==================== 2. Formatter = 双向：parse(串→对象) + print(对象→串)，对照单向 Converter ====================");
			Channel parsed = fcs.convert("美团外卖", Channel.class);          // parse：中文 → 枚举
			String printed = fcs.convert(Channel.MINI_PROGRAM, String.class); // print：枚举 → 中文
			System.out.println("[parse] \"美团外卖\"          → " + parsed);
			System.out.println("[print] Channel.MINI_PROGRAM → \"" + printed + "\"  ← 同一个 Formatter 反向渲染回展示串");
		}
		finally {
			LocaleContextHolder.setLocale(previous);
		}
	}

	/** 营销活动配置表单：字段上的格式化注解决定「人类可读串」如何双向绑定。 */
	public static final class PromotionForm {
		@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private LocalDateTime startTime;
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		private LocalDate endDate;
		@NumberFormat(style = NumberFormat.Style.PERCENT)
		private BigDecimal discountRate;
		@NumberFormat(pattern = "#,##0.00")
		private BigDecimal capAmount;

		public LocalDateTime getStartTime() {
			return startTime;
		}

		public void setStartTime(LocalDateTime startTime) {
			this.startTime = startTime;
		}

		public LocalDate getEndDate() {
			return endDate;
		}

		public void setEndDate(LocalDate endDate) {
			this.endDate = endDate;
		}

		public BigDecimal getDiscountRate() {
			return discountRate;
		}

		public void setDiscountRate(BigDecimal discountRate) {
			this.discountRate = discountRate;
		}

		public BigDecimal getCapAmount() {
			return capAmount;
		}

		public void setCapAmount(BigDecimal capAmount) {
			this.capAmount = capAmount;
		}
	}

	/** 渠道枚举的双向 Formatter：parse 认中文 label，print 渲染回中文——展示 Formatter 比 Converter 多出的「反向」能力。 */
	static final class ChannelFormatter implements Formatter<Channel> {
		@Override
		public Channel parse(String text, Locale locale) throws ParseException {
			for (Channel c : Channel.values()) {
				if (c.getLabel().equals(text.trim()) || c.name().equalsIgnoreCase(text.trim())) {
					return c;
				}
			}
			throw new ParseException("未知渠道: " + text, 0);
		}

		@Override
		public String print(Channel object, Locale locale) {
			return object.getLabel();
		}
	}

}
