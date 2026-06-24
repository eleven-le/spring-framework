package com.leilei.lab.laboratory.l02.l02_05;

import java.beans.PropertyEditorSupport;

import com.leilei.lab.laboratory.common.domain.Channel;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * 📖 知识点：[[L02-05-类型转换-ConversionService-PropertyEditor-Formatter#2.3 PropertyEditor 与 ConversionService 两套并存]]（PropertyEditor 老体系 / BeanWrapper / TypeConverter）
 * 🎯 作用：把「PropertyEditor（JavaBeans 老体系）与 ConversionService（新体系）并存、谁优先」坐实成可运行实验——
 *         {@link BeanWrapperImpl} 是统一的 TypeConverter 入口，{@code setPropertyValue} 时其内部
 *         {@code TypeConverterDelegate#convertIfNecessary} 的取值顺序是
 *         「先 {@code findCustomEditor}（PropertyEditor），找不到再用 ConversionService」——
 *         **自定义 PropertyEditor 优先级高于 ConversionService**。这是历史兼容的刻意设计。
 * 🔗 业务场景：产品中心有从 XML/Spring 早期迁来的老模块仍用 {@code PropertyEditor} 解析渠道、规格，
 *         新模块走 ConversionService；同一套 BeanWrapper 绑定下，老 editor 会「悄悄盖过」新转换器，
 *         排查「我注册的 Converter 怎么没生效」时必须知道这条优先级，否则查半天。
 */
public final class L0205_03_PropertyEditorVsConversionServiceDemo {

	private L0205_03_PropertyEditorVsConversionServiceDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 1. 只挂 ConversionService：BeanWrapper 用新体系完成 String→Channel ====================");
		ChannelHolder holder1 = new ChannelHolder();
		BeanWrapperImpl bw1 = new BeanWrapperImpl(holder1);
		DefaultConversionService cs = new DefaultConversionService();
		cs.addConverter(new MarkingChannelConverter());
		bw1.setConversionService(cs);
		bw1.setPropertyValue("channel", "小程序");
		System.out.println("[结果] holder.channel = " + holder1.channel);
		System.out.println();

		System.out.println("==================== 2. 同时挂 PropertyEditor + ConversionService：customEditor 优先，新转换器被悄悄盖过 ====================");
		ChannelHolder holder2 = new ChannelHolder();
		BeanWrapperImpl bw2 = new BeanWrapperImpl(holder2);
		bw2.setConversionService(cs);                                   // 新体系：会打印 [ConversionService] 标记
		bw2.registerCustomEditor(Channel.class, new ChannelEditor());   // 老体系：会打印 [PropertyEditor] 标记
		bw2.setPropertyValue("channel", "美团外卖");
		System.out.println("[结果] holder.channel = " + holder2.channel + "  ← 只见 PropertyEditor 标记 ⇒ customEditor 优先于 ConversionService");
		System.out.println();

		System.out.println("==================== 3. BeanWrapper 即 TypeConverter：同一个入口可直接做类型转换 ====================");
		Channel direct = bw2.convertIfNecessary("POS", Channel.class);
		System.out.println("[convertIfNecessary] \"POS\" → " + direct);
	}

	/** 被绑定的目标对象，仅持有一个渠道字段。 */
	public static final class ChannelHolder {
		private Channel channel;

		public Channel getChannel() {
			return channel;
		}

		public void setChannel(Channel channel) {
			this.channel = channel;
		}
	}

	/** 新体系转换器：解析时打印标记，便于在「两套并存」时观察到底走了谁。 */
	static final class MarkingChannelConverter implements Converter<String, Channel> {
		@Override
		public Channel convert(String source) {
			System.out.println("    [ConversionService] 解析渠道 " + source);
			return resolve(source);
		}
	}

	/**
	 * 老体系 PropertyEditor：解析时打印标记。
	 * ⚠️ {@link PropertyEditorSupport} 是**有状态**的（持有 value），天生非线程安全——
	 * 千万别把单个实例注册成跨 BeanWrapper 共享的 editor，否则高并发绑定会串数据；
	 * 正确做法是经 {@code CustomEditorConfigurer.setCustomEditors(Map&lt;Class, Class&gt;)} 用「类」注册，每次 new 新实例。
	 */
	static final class ChannelEditor extends PropertyEditorSupport {
		@Override
		public void setAsText(String text) {
			System.out.println("    [PropertyEditor] 解析渠道 " + text);
			setValue(resolve(text));
		}
	}

	/** 名或中文 label → Channel。 */
	private static Channel resolve(String text) {
		String t = text.trim();
		for (Channel c : Channel.values()) {
			if (c.name().equalsIgnoreCase(t) || c.getLabel().equals(t)) {
				return c;
			}
		}
		throw new IllegalArgumentException("未知渠道: " + text);
	}

}
