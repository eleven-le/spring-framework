package com.leilei.lab.laboratory.l02.l02_05;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.leilei.lab.laboratory.common.domain.Channel;

import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * 📖 知识点：[[L02-05-类型转换-ConversionService-PropertyEditor-Formatter#2.1 ConversionService 三层扩展点]]（ConversionService 体系）
 * 🎯 作用：把 ConversionService 的三个扩展点坐实成可运行实验——
 *         {@link Converter}（一对一，最常用）、{@link ConverterFactory}（一对一族，String→任意枚举）、
 *         {@link org.springframework.core.convert.converter.GenericConverter} / {@link ConditionalGenericConverter}
 *         （多对多 + 按 {@link TypeDescriptor} 条件匹配，能力最强）。
 *         三者都 {@code addConverter*} 进同一个 {@link DefaultConversionService}，
 *         取值时 {@code GenericConversionService#getConverter} 按类型对查表、命中即转、查不到抛
 *         {@link ConverterNotFoundException}。
 * 🔗 业务场景：配置中心 / 营销后台 / 上游 RPC 传进来的一律是字符串——
 *         价格「元」字符串要落成「分」(long) 杜绝 double 精度资损（Converter）；
 *         渠道既可能传英文枚举名 {@code MINI_PROGRAM} 也可能传中文「小程序」(ConverterFactory 兼容两种)；
 *         SKU 规格串「杯型=大杯;糖度=五分糖;温度=少冰」要拆成有序 Map（GenericConverter）。
 */
public final class L0205_01_ConversionServiceTierDemo {

	private L0205_01_ConversionServiceTierDemo() {
	}

	public static void main(String[] args) {
		// DefaultConversionService 已内置 String→Number/Enum 等一票默认转换器，我们在它之上叠加领域转换器
		DefaultConversionService cs = new DefaultConversionService();
		cs.addConverter(new YuanToFenConverter());                 // 一对一：String → Long(分)
		cs.addConverterFactory(new StringToLabeledEnumConverterFactory()); // 一对一族：String → 任意枚举(名或中文label)
		cs.addConverter(new SpecStringToMapConverter());           // 多对多 + 条件：String → Map<String,String>

		System.out.println("==================== 1. Converter：价格「元」字符串 → 「分」(long)，杜绝 double 精度资损 ====================");
		System.out.println("[元→分] \"12.50\" → " + cs.convert("12.50", Long.class) + " 分");
		System.out.println("[元→分] \"0.01\"  → " + cs.convert("0.01", Long.class) + " 分");
		System.out.println();

		System.out.println("==================== 2. ConverterFactory：一个工厂吃下「String → 任意枚举」，名与中文 label 都认 ====================");
		System.out.println("[英文名] \"MINI_PROGRAM\" → " + cs.convert("MINI_PROGRAM", Channel.class));
		System.out.println("[中文label] \"小程序\"      → " + cs.convert("小程序", Channel.class));
		System.out.println("[中文label] \"美团外卖\"    → " + cs.convert("美团外卖", Channel.class));
		System.out.println();

		System.out.println("==================== 3. GenericConverter(Conditional)：SKU 规格串 → 有序 Map，仅 String→Map 时才匹配 ====================");
		@SuppressWarnings("unchecked")
		Map<String, String> specs = (Map<String, String>) cs.convert(
				"杯型=大杯;糖度=五分糖;温度=少冰",
				TypeDescriptor.valueOf(String.class),
				TypeDescriptor.map(LinkedHashMap.class, TypeDescriptor.valueOf(String.class), TypeDescriptor.valueOf(String.class)));
		System.out.println("[规格串→Map] " + specs);
		System.out.println();

		System.out.println("==================== 4. canConvert / ConverterNotFoundException：缺转换器是会抛异常的硬失败 ====================");
		System.out.println("[canConvert String→Channel] " + cs.canConvert(String.class, Channel.class));
		System.out.println("[canConvert String→Sku]     " + cs.canConvert(String.class, com.leilei.lab.laboratory.common.domain.Sku.class) + "  ← 没注册，false");
		try {
			cs.convert("随便什么", com.leilei.lab.laboratory.common.domain.Sku.class);
		}
		catch (ConverterNotFoundException ex) {
			System.out.println("[convert String→Sku] 抛 ConverterNotFoundException：" + ex.getMessage());
		}
	}

	/** 一对一转换器：价格「元」字符串 → 「分」(long)。定价金额一律用分(整数)存储，从源头杜绝 double/float 精度资损。 */
	static final class YuanToFenConverter implements Converter<String, Long> {
		@Override
		public Long convert(String source) {
			// 用 BigDecimal 精确换算，再放大 100 倍取整成分
			return new java.math.BigDecimal(source.trim()).movePointRight(2).longValueExact();
		}
	}

	/**
	 * 一对一族转换器：String → 任意 Enum。先按 {@code name()} 匹配（与 DefaultConversionService 默认枚举转换器同口径），
	 * 匹配不到再尝试枚举上的 {@code getLabel()} 中文标签——这正是「中文渠道名绑不上枚举」事故的修复点。
	 */
	static final class StringToLabeledEnumConverterFactory implements ConverterFactory<String, Enum> {
		@Override
		@SuppressWarnings({"rawtypes", "unchecked"})
		public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
			return new StringToLabeledEnum<>(targetType);
		}

		private static final class StringToLabeledEnum<T extends Enum> implements Converter<String, T> {
			private final Class<T> enumType;

			StringToLabeledEnum(Class<T> enumType) {
				this.enumType = enumType;
			}

			@Override
			@SuppressWarnings("unchecked")
			public T convert(String source) {
				String text = source.trim();
				for (T constant : this.enumType.getEnumConstants()) {
					if (constant.name().equalsIgnoreCase(text)) {
						return constant;
					}
				}
				// 名匹配失败 → 尝试中文 label（若该枚举提供 getLabel()）
				for (T constant : this.enumType.getEnumConstants()) {
					try {
						Object label = this.enumType.getMethod("getLabel").invoke(constant);
						if (text.equals(label)) {
							return constant;
						}
					}
					catch (ReflectiveOperationException ignored) {
						// 该枚举没有 getLabel()，放弃 label 匹配
					}
				}
				throw new IllegalArgumentException("无法识别的枚举值[" + source + "] for " + this.enumType.getSimpleName());
			}
		}
	}

	/**
	 * 多对多 + 条件转换器：「杯型=大杯;糖度=五分糖;温度=少冰」→ 有序 {@link LinkedHashMap}。
	 * {@link ConditionalGenericConverter#matches} 让它只在「源是 String、目标是 Map」时才介入，不污染其它转换。
	 */
	static final class SpecStringToMapConverter implements ConditionalGenericConverter {
		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(new ConvertiblePair(String.class, Map.class));
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 仅当目标是 Map（含其子类型）时才认领，避免误吃其它 String→X 转换
			return Map.class.isAssignableFrom(targetType.getType());
		}

		@Override
		public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			Map<String, String> specs = new LinkedHashMap<>();
			if (source == null) {
				return specs;
			}
			for (String pair : ((String) source).split(";")) {
				int eq = pair.indexOf('=');
				if (eq > 0) {
					specs.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
				}
			}
			return specs;
		}
	}

}
