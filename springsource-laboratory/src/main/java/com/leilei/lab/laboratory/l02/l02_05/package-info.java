/**
 * 📖 对应章节：[[L02-05-类型转换-ConversionService-PropertyEditor-Formatter]]
 * 🎯 本包主题：L02-05 类型转换——用「配置/后台字符串 → C 端领域类型」三条线索，把
 *    ConversionService 三层扩展点、Formatter 与注解格式化、PropertyEditor 老体系与新体系并存优先级，
 *    坐实成可运行实验。BeanWrapper / TypeConverter 本章讲概念（统一转换入口），其完整源码见 L12-03。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_05.L0205_01_ConversionServiceTierDemo}
 *       —— ConversionService 三层扩展点：Converter（元→分）/ ConverterFactory（String→任意枚举，名或中文 label）/
 *          ConditionalGenericConverter（规格串→有序 Map），及 canConvert 与 ConverterNotFoundException。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_05.L0205_02_FormatterAndAnnotationDemo}
 *       —— Formatter = Printer+Parser（双向 + Locale 感知）；@DateTimeFormat / @NumberFormat 注解格式化由
 *          DefaultFormattingConversionService 提供，经 BeanWrapper 绑定营销活动表单字符串。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_05.L0205_03_PropertyEditorVsConversionServiceDemo}
 *       —— PropertyEditor（老体系）与 ConversionService（新体系）并存：BeanWrapper 作为 TypeConverter 入口，
 *          customEditor 优先于 ConversionService；附 PropertyEditor 有状态非线程安全避坑。</li>
 * </ul>
 *
 * <p>依赖章节 [[L02-04-Environment与属性绑定]]（@Value 注入期会调用类型转换把字符串转成字段类型）。🚫 禁止 hello-world。
 */
package com.leilei.lab.laboratory.l02.l02_05;
