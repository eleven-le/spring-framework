package org.springframework.lab.aabpp;

import java.util.List;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * W30 核心演练 Bean — 一个类中展示 AABPP 的 6 大注入落点 + 5 大常见坑
 *
 * ═══════════════════════════════════════════════
 * 注入落点: AABPP 扫描到的 InjectedElement 分类
 * ═══════════════════════════════════════════════
 *
 * 落点1 — 字段注入 (AutowiredFieldElement)
 *   AABPP.buildAutowiringMetadata 遍历 clazz.getDeclaredFields(),
 *   发现 @Autowired/@Value → new AutowiredFieldElement(field, required)
 *   最终: field.set(bean, resolvedValue)
 *
 * 落点2 — 方法注入 (AutowiredMethodElement)
 *   遍历 clazz.getDeclaredMethods(), 发现 @Autowired →
 *   new AutowiredMethodElement(method, required, pd)
 *   最终: method.invoke(bean, resolvedArgs...)
 *
 * 落点3 — 构造器注入 (determineCandidateConstructors)
 *   AABPP.determineCandidateConstructors 在 createBeanInstance 阶段被调用,
 *   选定 @Autowired 标注的构造器, 参数由 ConstructorResolver 解析
 *
 * 落点4 — @Value 占位符/SpEL (AutowiredFieldElement 子路径)
 *   字段上的 @Value 也走 AABPP → resolveDependency → evaluateBeanDefinitionString
 *   → PropertySourcesPropertyResolver 解析占位符
 *   → StandardBeanExpressionResolver 解析 SpEL
 *
 * ═══════════════════════════════════════════════
 * 常见坑 (对照断点调试)
 * ═══════════════════════════════════════════════
 */
@Component
public class NotificationService {

	// ╔═══════════════════════════════════════╗
	// ║ 落点1: 字段注入 — AutowiredFieldElement ║
	// ╚═══════════════════════════════════════╝

	/** 正常字段注入: @Primary 生效 → PushSender */
	@Autowired
	private MessageSender primarySender;

	/** 落点4: @Value 走 AABPP 同一通道, 但分流到 placeholder 解析 */
	@Value("${app.notification.maxRetry:3}")
	private int maxRetry;

	@Value("#{T(java.lang.Math).random() * 100}")
	private double randomScore;

	// ╔═══════════════════════════════════════╗
	// ║ 坑1: static 字段 — 静默跳过不注入!     ║
	// ╚═══════════════════════════════════════╝
	// AABPP.buildAutowiringMetadata: if (Modifier.isStatic(field.getModifiers())) → 跳过并打 WARN 日志
	// 结果: staticSender 永远是 null, 启动不报错, 运行时 NPE
	@Autowired
	private static MessageSender staticSender;

	// ╔═══════════════════════════════════════╗
	// ║ 坑2: required=false vs @Nullable      ║
	// ╚═══════════════════════════════════════╝
	// required=false: AABPP 传给 resolveDependency 的 DependencyDescriptor.required=false
	// @Nullable: 由 isRequired() 内部 hasNullableAnnotation() 判断, 效果相同
	// 区别: Optional<T> 还会额外做 Optional.ofNullable() 包装
	@Autowired(required = false)
	@Nullable
	private MessageSender optionalSender; // 不存在的类型不会报错

	// ╔═══════════════════════════════════════╗
	// ║ 坑3: @Lazy 代理 — 延迟解析打破循环依赖  ║
	// ╚═══════════════════════════════════════╝
	// AABPP 调 resolveDependency → ContextAnnotationAutowireCandidateResolver
	//   .getLazyResolutionProxyIfNecessary → buildLazyResolutionProxy
	// 注入的是一个 AOP 代理, 第一次调用方法时才真正 getBean
	@Autowired
	@Lazy
	private MessageSender lazySender;

	// ╔═══════════════════════════════════════╗
	// ║ 坑4: 集合注入 — 走 resolveMultipleBeans ║
	// ╚═══════════════════════════════════════╝
	// List<T> 注入不是 "找一个 List bean", 而是收集所有 T 类型 bean
	// 断点: DefaultListableBeanFactory#resolveMultipleBeans
	@Autowired
	private List<MessageSender> allSenders;

	// ╔═══════════════════════════════════════════════╗
	// ║ 坑5: Prototype注入Singleton — stale reference ║
	// ╚═══════════════════════════════════════════════╝
	// requestContext 是 prototype scope, 但注入 singleton 时只创建一次
	// 之后每次调用拿到的都是同一个实例 — 违反 prototype 语义!
	// 正解: 用 ObjectFactory<T> / ObjectProvider<T> / @Lookup 延迟获取
	@Autowired
	private RequestContext staleRequestContext;  // 坑: 永远是同一实例

	@Autowired
	private ObjectFactory<RequestContext> requestContextFactory;  // 正解: 每次拿新实例

	// ╔════════════════════════════════════════════════════╗
	// ║ 落点2: 方法注入 — AutowiredMethodElement            ║
	// ╚════════════════════════════════════════════════════╝
	private MessageSender setterInjected;

	@Autowired
	public void setSetterInjected(MessageSender sender) {
		System.out.println("  [方法注入] setter被调用, sender=" + sender.channel());
		this.setterInjected = sender;
	}

	// ╔════════════════════════════════════════════════╗
	// ║ 落点3: 构造器注入 — determineCandidateConstructors ║
	// ╚════════════════════════════════════════════════╝
	// 单构造器时 Spring 自动选定; 多构造器需 @Autowired 标注
	// 构造器参数不走 postProcessProperties, 走 ConstructorResolver#resolveAutowiredArgument

	// (本类用默认无参构造器, 构造器注入见 CtorInjectionDemo)


	// ═══════════════ 演示方法 ═══════════════

	public void demoPrimaryFieldInjection() {
		System.out.println("[落点1-字段注入] primarySender → " + primarySender.channel());
	}

	public void demoValueInjection() {
		System.out.println("[落点4-@Value] maxRetry=" + maxRetry + ", randomScore=" + String.format("%.2f", randomScore));
	}

	public void demoStaticFieldPitfall() {
		System.out.println("[坑1-static字段] staticSender=" + staticSender + " (永远null, 启动无报错!)");
	}

	public void demoLazyProxy() {
		System.out.println("[坑3-@Lazy] lazySender.class=" + lazySender.getClass().getSimpleName()
				+ " (代理类, 调用时才真正解析)");
		System.out.println("[坑3-@Lazy] lazySender.channel()=" + lazySender.channel());
	}

	public void demoCollectionInjection() {
		System.out.println("[坑4-集合注入] allSenders.size=" + allSenders.size());
		allSenders.forEach(s -> System.out.println("  - " + s.channel()));
	}

	public void demoPrototypeStalePitfall() {
		System.out.println("[坑5-Prototype stale] 直接字段注入(同一实例):");
		System.out.println("  第1次: " + staleRequestContext);
		System.out.println("  第2次: " + staleRequestContext + "  ← 完全相同!");
		System.out.println("[坑5-正解] ObjectFactory 每次新实例:");
		System.out.println("  第1次: " + requestContextFactory.getObject());
		System.out.println("  第2次: " + requestContextFactory.getObject() + "  ← 不同实例!");
	}

	public void demoSetterInjection() {
		System.out.println("[落点2-方法注入] setterInjected → " + setterInjected.channel());
	}
}
