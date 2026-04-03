package org.springframework.lab.naming;

import java.util.Collections;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * #98 - Spring 命名法则：前缀后缀即设计意图
 *
 * <p>本 Demo 用一个「支付渠道」业务域，完整演示 18 种命名范式在业务代码中的落地：
 * <pre>
 *  前缀 5 种：Abstract / Default / Simple / Generic / Configurable
 *  后缀 13 种：Support / Template / Delegate / Registry / Resolver / Processor / Aware / Capable
 *             / Holder / Composite / Decorator / Adapter / Builder / Utils
 *             / Listener / Configurer / Initializer / Interceptor
 * </pre>
 *
 * <p>实验矩阵：
 * <pre>
 *  实验1:  Abstract vs Default vs Simple — 同一接口的三级实现梯度
 *  实验2:  Support — 可复用工具基类，不是独立组件
 *  实验3:  Template — 封装 try/catch/finally 资源操作骨架
 *  实验4:  Aware — 回调契约，容器给你注入能力
 *  实验5:  Resolver — 输入→输出的策略解析
 *  实验6:  Processor — 拦截→判断→增强/跳过
 *  实验7:  Spring 原生命名对照 — 从 Spring 源码映射到业务命名（完整 18 种）
 *  实验8:  Holder — ThreadLocal 上下文持有（线程绑定 + 数据打包）
 *  实验9:  Composite — 多个同类对象组合成一个
 *  实验10: Decorator — 透明增强，包装对象但不改接口
 *  实验11: Adapter — 接口适配桥梁（第三方 SDK → 标准接口）
 *  实验12: Builder — 流式链式构建复杂对象
 *  实验13: Utils — 无状态静态工具方法集
 *  实验14: Listener — 事件驱动的观察者
 *  实验15: Configurer vs Initializer — 配置收集 vs 初始化执行
 *  实验16: Interceptor — 三段式调用拦截（preHandle/postHandle/afterCompletion）
 * </pre>
 *
 * <p>断点抓手：
 * <ol>
 *   <li>AbstractApplicationContext#refresh():554 — 观察 Abstract 前缀的模板方法骨架</li>
 *   <li>ApplicationContextAwareProcessor#postProcessBeforeInitialization — 观察 Aware 回调注入</li>
 *   <li>PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors — 观察 Processor 调用链</li>
 *   <li>RequestContextHolder#setRequestAttributes — 观察 Holder 的 ThreadLocal set/clear 范式</li>
 *   <li>HandlerMethodArgumentResolverComposite#resolveArgument — 观察 Composite 遍历委托模式</li>
 * </ol>
 */
public class NamingMain {

	public static void main(String[] args) {
		// ── 原有 8 种 ──
		exp1_abstractDefaultSimple();
		exp2_support();
		exp3_template();
		exp4_aware();
		exp5_resolver();
		exp6_processor();
		// ── 新增 10 种 ──
		exp8_holder();
		exp9_composite();
		exp10_decorator();
		exp11_adapter();
		exp12_builder();
		exp13_utils();
		exp14_listener();
		exp15_configurerAndInitializer();
		exp16_interceptor();
		// ── 完整对照表（18 种）──
		exp7_springNativeMapping();
	}

	// ─── 实验1: Abstract vs Default vs Simple ───────────────────────────────

	/**
	 * 三级梯度：
	 *  - Abstract：定义骨架（validate→prepare→execute→callback），留 hook
	 *  - Default：覆盖 80% 场景的"够用"实现
	 *  - Simple：最简实现，适合测试 stub
	 */
	static void exp1_abstractDefaultSimple() {
		banner("实验1: Abstract vs Default vs Simple — 三级实现梯度");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(NamingConfig.class);

		// Default —— 覆盖主流场景
		PayChannel defaultChannel = ctx.getBean("defaultPayChannel", PayChannel.class);
		PayResult r1 = defaultChannel.pay("ORD-001", 9900);
		System.out.println("  [Default] " + r1);

		// Simple —— 最简实现，直接成功
		PayChannel simpleChannel = ctx.getBean("simplePayChannel", PayChannel.class);
		PayResult r2 = simpleChannel.pay("ORD-002", 100);
		System.out.println("  [Simple]  " + r2);

		// Alipay —— 具体渠道，继承 Abstract 骨架
		PayChannel alipay = ctx.getBean("alipayChannel", PayChannel.class);
		PayResult r3 = alipay.pay("ORD-003", 5000);
		System.out.println("  [Alipay]  " + r3);

		ctx.close();
	}

	// ─── 实验2: Support ─────────────────────────────────────────────────────

	/**
	 * Support 后缀 = 可复用工具基类，不是独立组件。
	 * 对照 Spring：ApplicationObjectSupport → WebApplicationObjectSupport
	 * 业务映射：PayChannelSupport 抽出通用日志/配置/重试逻辑
	 */
	static void exp2_support() {
		banner("实验2: Support — 可复用工具基类");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(NamingConfig.class);

		// WechatPayService 继承了 PayChannelSupport，获得了通用能力
		WechatPayService wechat = ctx.getBean(WechatPayService.class);
		wechat.executePayment("ORD-004", 3300);

		ctx.close();
	}

	// ─── 实验3: Template ────────────────────────────────────────────────────

	/**
	 * Template 后缀 = 封装 打开→操作→关闭 的资源操作骨架。
	 * 对照 Spring：JdbcTemplate.execute(ConnectionCallback)
	 * 业务映射：PayTemplate 封装 openChannel→executeCallback→closeChannel
	 */
	static void exp3_template() {
		banner("实验3: Template — 资源操作骨架 (try/catch/finally)");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(NamingConfig.class);

		PayTemplate payTemplate = ctx.getBean(PayTemplate.class);

		// 使用回调模式——用户只关心核心逻辑，资源管理交给 Template
		String result = payTemplate.execute("ALIPAY", connection -> {
			System.out.println("    [Callback] 使用连接执行支付: " + connection);
			return "PAY-TX-" + System.currentTimeMillis();
		});
		System.out.println("  [Template] 支付结果: " + result);

		ctx.close();
	}

	// ─── 实验4: Aware ───────────────────────────────────────────────────────

	/**
	 * Aware 后缀 = 回调契约，容器给你注入能力。
	 * Spring 原生：ApplicationContextAware → setBeanFactory() 回调
	 * 业务自定义：PayContextAware → 容器启动时注入 PayContext
	 *
	 * 断点：ApplicationContextAwareProcessor#postProcessBeforeInitialization
	 *       观察所有 Aware 回调的注入时机
	 */
	static void exp4_aware() {
		banner("实验4: Aware — 回调契约 (容器注入能力)");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(NamingConfig.class);

		// PayContextAwareService 实现了 ApplicationContextAware + 自定义 PayContextAware
		PayContextAwareService service = ctx.getBean(PayContextAwareService.class);
		service.showInjectedCapabilities();

		ctx.close();
	}

	// ─── 实验5: Resolver ────────────────────────────────────────────────────

	/**
	 * Resolver 后缀 = 输入→输出的策略解析。
	 * Spring 原生：HandlerExceptionResolver（异常→视图）, ViewResolver（名称→视图）
	 * 业务映射：PayChannelResolver（支付请求→具体渠道）
	 */
	static void exp5_resolver() {
		banner("实验5: Resolver — 策略解析 (输入→输出转换)");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(NamingConfig.class);

		PayChannelResolver resolver = ctx.getBean(PayChannelResolver.class);

		// 根据不同的支付方式解析到不同渠道
		PayChannel ch1 = resolver.resolve("ALIPAY");
		PayChannel ch2 = resolver.resolve("WECHAT");
		PayChannel ch3 = resolver.resolve("UNKNOWN");

		System.out.println("  ALIPAY  → " + ch1.getClass().getSimpleName());
		System.out.println("  WECHAT  → " + ch2.getClass().getSimpleName());
		System.out.println("  UNKNOWN → " + ch3.getClass().getSimpleName() + " (降级到默认)");

		ctx.close();
	}

	// ─── 实验6: Processor ───────────────────────────────────────────────────

	/**
	 * Processor 后缀 = 拦截→判断→增强/跳过。
	 * Spring 原生：BeanPostProcessor（每个 Bean 创建后拦截）
	 * 业务映射：PayRequestProcessor（支付请求前做风控/限流/日志增强）
	 */
	static void exp6_processor() {
		banner("实验6: Processor — 拦截→判断→增强/跳过");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(NamingConfig.class);

		PayRequestProcessor processor = ctx.getBean(PayRequestProcessor.class);

		// 正常请求 → 增强通过
		PayRequest req1 = new PayRequest("ORD-005", 500, "ALIPAY");
		PayRequest processed1 = processor.process(req1);
		System.out.println("  [正常] " + processed1);

		// 高额请求 → 风控标记
		PayRequest req2 = new PayRequest("ORD-006", 100000, "ALIPAY");
		PayRequest processed2 = processor.process(req2);
		System.out.println("  [高额] " + processed2);

		ctx.close();
	}

	// ─── 实验8: Holder ──────────────────────────────────────────────────────

	/**
	 * Holder 后缀 = ThreadLocal 上下文持有者。
	 *
	 * Spring 中 Holder 两种经典用法：
	 * (1) ThreadLocal 持有者：RequestContextHolder / LocaleContextHolder / SecurityContextHolder
	 * (2) 数据打包持有者：BeanDefinitionHolder = BD + name + aliases
	 *
	 * 关键范式：入口 set → 业务代码 get → finally 中 clear（防泄漏）
	 * 断点：FrameworkServlet#processRequest → RequestContextHolder.setRequestAttributes
	 */
	static void exp8_holder() {
		banner("实验8: Holder — ThreadLocal 上下文持有");

		PayContext ctx = new PayContext("MCH-10086", "PRODUCTION");

		// ── 模拟一次请求的 Holder 使用范式 ──
		// 对照 DispatcherServlet 的 processRequest:
		//   RequestContextHolder.setRequestAttributes(...)  ← 入口设置
		//   try { doService(request, response); }           ← 业务代码随时 get
		//   finally { RequestContextHolder.resetRequestAttributes(); } ← 清理

		PayContextHolder.set(ctx);   // ① 入口绑定（对照 Filter/Interceptor 的 preHandle）
		try {
			// ② 任意层级代码都能拿到上下文，不需要层层传参
			PayContext current = PayContextHolder.require();
			System.out.println("  [Holder] 当前线程上下文: merchantId=" + current.getMerchantId()
					+ ", env=" + current.getEnvironment());

			// 模拟深层调用也能拿到
			deepBusinessMethod();
		}
		finally {
			PayContextHolder.clear(); // ③ 必须清理！（对照 finally 中的 resetRequestAttributes）
			System.out.println("  [Holder] ThreadLocal 已清理, get=" + PayContextHolder.get());
		}
	}

	/** 模拟深层业务方法——不需要传参，直接从 Holder 拿上下文 */
	private static void deepBusinessMethod() {
		PayContext ctx = PayContextHolder.require();
		System.out.println("  [Holder] 深层业务方法拿到上下文: " + ctx.getMerchantId()
				+ " (无需参数透传！)");
	}

	// ─── 实验9: Composite ───────────────────────────────────────────────────

	/**
	 * Composite 后缀 = 把多个同类对象组合成一个，对外表现为单个对象。
	 *
	 * Spring 原生：
	 *   HandlerMethodArgumentResolverComposite — 组合多个参数解析器
	 *   WebMvcConfigurerComposite — 组合多个 WebMvcConfigurer
	 *   CompositeCacheManager — 组合多个 CacheManager
	 *
	 * 设计意图：新增校验规则只需 addValidator，不改调用方代码（开闭原则）
	 */
	static void exp9_composite() {
		banner("实验9: Composite — 多个同类对象组合成一个");

		CompositePayValidator composite = new CompositePayValidator();

		// 叶子节点1：金额校验
		composite.addValidator(new PayValidator() {
			@Override
			public boolean validate(PayRequest request) {
				return request.getAmountInCents() > 0 && request.getAmountInCents() <= 10000000;
			}

			@Override
			public String name() {
				return "AmountRangeValidator";
			}
		});

		// 叶子节点2：渠道校验
		composite.addValidator(new PayValidator() {
			@Override
			public boolean validate(PayRequest request) {
				return request.getChannelCode() != null && !request.getChannelCode().isEmpty();
			}

			@Override
			public String name() {
				return "ChannelRequiredValidator";
			}
		});

		// 叶子节点3：订单号格式校验
		composite.addValidator(new PayValidator() {
			@Override
			public boolean validate(PayRequest request) {
				return request.getOrderId() != null && request.getOrderId().startsWith("ORD-");
			}

			@Override
			public String name() {
				return "OrderIdFormatValidator";
			}
		});

		// ── 调用方只调一个 Composite，内部遍历所有校验器 ──
		PayRequest goodReq = new PayRequest("ORD-007", 5000, "ALIPAY");
		System.out.println("  [正常请求] 校验结果=" + composite.validate(goodReq));

		System.out.println();

		PayRequest badReq = new PayRequest("BAD-008", 5000, "ALIPAY");
		System.out.println("  [异常请求] 校验结果=" + composite.validate(badReq));
	}

	// ─── 实验10: Decorator ──────────────────────────────────────────────────

	/**
	 * Decorator 后缀 = 透明增强，包装对象但不改变接口。
	 *
	 * Spring 原生：TransactionAwareCacheDecorator 给 Cache 加事务感知
	 * 设计意图：给已有对象"套一层"行为，可以任意叠加
	 *
	 * 关键区分：
	 *   Decorator 包装对象（长期持有，接口不变）
	 *   Processor 处理对象（一次性，对象被增强后交回）
	 *   Adapter 转换接口（异构→统一，接口变了）
	 */
	static void exp10_decorator() {
		banner("实验10: Decorator — 透明增强 (不改接口)");

		// 原始实现
		PayChannel simple = new SimplePayChannel();
		System.out.println("  [原始调用]");
		simple.pay("ORD-009", 1000);

		System.out.println();

		// 套上 Logging Decorator——接口完全一样，但增加了日志能力
		PayChannel decorated = new LoggingPayChannelDecorator(simple);
		System.out.println("  [装饰后调用]");
		decorated.pay("ORD-010", 2000);

		// 可以继续叠加 Decorator（Decorator 套 Decorator）
		// PayChannel doubleDecorated = new RateLimitDecorator(new LoggingPayChannelDecorator(simple));
	}

	// ─── 实验11: Adapter ────────────────────────────────────────────────────

	/**
	 * Adapter 后缀 = 将不兼容的接口转换为目标接口。
	 *
	 * Spring 原生：HandlerAdapter 把各种 Handler 适配为统一调用接口
	 * 业务映射：第三方支付 SDK 接口和我们的 PayChannel 完全不同，用 Adapter 桥接
	 *
	 * 第三方 SDK: sendPayment(orderNo, amountInYuan:double, currency:String) → String
	 * 我们的接口: pay(orderId, amountInCents:long) → PayResult
	 * 差异：参数类型不同 / 多了币种参数 / 返回类型不同
	 */
	static void exp11_adapter() {
		banner("实验11: Adapter — 接口适配桥梁");

		// 第三方 SDK（接口和 PayChannel 完全不兼容）
		ThirdPartyPayAdapter.ThirdPartySdk sdk = new ThirdPartyPayAdapter.ThirdPartySdk();

		// Adapter 把第三方接口适配为我们的标准接口
		PayChannel adapted = new ThirdPartyPayAdapter(sdk);

		// 调用方按标准接口调用，完全无感
		PayResult result = adapted.pay("ORD-011", 9900);
		System.out.println("  [Adapter] " + result);
		System.out.println("  [Adapter] channelCode=" + adapted.channelCode());
	}

	// ─── 实验12: Builder ────────────────────────────────────────────────────

	/**
	 * Builder 后缀 = 流式链式构建复杂对象。
	 *
	 * Spring 原生：BeanDefinitionBuilder / UriComponentsBuilder
	 * 设计意图：参数多时避免构造器爆炸，build() 前校验，build() 后不可变
	 */
	static void exp12_builder() {
		banner("实验12: Builder — 流式链式构建");

		// 链式构建——可读性远超多参数构造器
		PayRequest request = PayRequestBuilder.create()
				.orderId("ORD-012")
				.amount(8800)
				.channel("ALIPAY")
				.traceId(PayUtils.generateTraceId())
				.riskTag("NORMAL")
				.build();

		System.out.println("  [Builder] " + request);

		// 对比：new PayRequest("ORD-012", 8800, "ALIPAY") + 多个 setter
		// Builder 的优势：一眼看清所有参数，且 build() 时做校验
	}

	// ─── 实验13: Utils ──────────────────────────────────────────────────────

	/**
	 * Utils 后缀 = 无状态静态方法集合。
	 *
	 * Spring 原生：BeanUtils / StringUtils / ClassUtils / ReflectionUtils
	 * Spring 风格：abstract class + private 构造器，彻底禁止实例化
	 */
	static void exp13_utils() {
		banner("实验13: Utils — 无状态静态工具");

		System.out.println("  formatAmount(9900)  = " + PayUtils.formatAmount(9900) + "元");
		System.out.println("  maskOrderId(ORD-20260327-001) = " + PayUtils.maskOrderId("ORD-20260327-001"));
		System.out.println("  isHighValue(50000)  = " + PayUtils.isHighValue(50000));
		System.out.println("  isHighValue(100)    = " + PayUtils.isHighValue(100));
		System.out.println("  generateTraceId()   = " + PayUtils.generateTraceId());
	}

	// ─── 实验14: Listener ───────────────────────────────────────────────────

	/**
	 * Listener 后缀 = 事件驱动的观察者，被动响应事件。
	 *
	 * Spring 原生：ApplicationListener<ContextRefreshedEvent> — 容器启动后做初始化
	 * 业务映射：PaySuccessListener 监听支付成功事件，触发通知/积分/MQ
	 *
	 * vs Processor：Processor 主动拦截每个对象，Listener 被动等事件推送
	 * vs Interceptor：Interceptor 能改结果/短路，Listener 只能旁路通知不能改结果
	 */
	static void exp14_listener() {
		banner("实验14: Listener — 事件驱动观察者");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(NamingConfig.class);

		// 发布事件 → PaySuccessListener 自动收到
		System.out.println("  [Publisher] 发布支付成功事件...");
		ctx.publishEvent(new PaySuccessEvent(ctx, "ORD-013", 19900));

		ctx.close();
	}

	// ─── 实验15: Configurer vs Initializer ──────────────────────────────────

	/**
	 * Configurer 后缀 = 回调式配置（声明式收集参数）
	 * Initializer 后缀 = 一次性初始化执行（命令式执行动作）
	 *
	 * Spring 原生：
	 *   WebMvcConfigurer#addInterceptors() — 框架回调你收集配置
	 *   ApplicationContextInitializer — refresh 之前执行一次性设置
	 *
	 * 关键区分：
	 *   Configurer 声明"要什么配置"（参数收集），Initializer 执行"做什么事"（动作执行）
	 */
	static void exp15_configurerAndInitializer() {
		banner("实验15: Configurer vs Initializer");

		// ── Configurer：声明式收集配置 ──
		PayChannelConfigurer configurer = new PayChannelConfigurer() {
			@Override
			public long getTimeoutMs() {
				return 5000;  // 自定义超时
			}

			@Override
			public boolean enableRiskControl() {
				return true;  // 开启风控
			}
		};

		System.out.println("  [Configurer] timeout=" + configurer.getTimeoutMs() + "ms");
		System.out.println("  [Configurer] riskControl=" + configurer.enableRiskControl());

		// ── Initializer：命令式执行初始化 ──
		PayChannelInitializer initializer = new PayChannelInitializer() {
			@Override
			public void initialize() {
				System.out.println("  [Initializer] 预热支付渠道连接池...");
				System.out.println("  [Initializer] 校验渠道证书有效性...");
				System.out.println("  [Initializer] 加载路由规则...");
			}

			@Override
			public int getOrder() {
				return 1;
			}
		};

		System.out.println("  [Initializer] order=" + initializer.getOrder());
		initializer.initialize();
	}

	// ─── 实验16: Interceptor ────────────────────────────────────────────────

	/**
	 * Interceptor 后缀 = 三段式调用拦截，能修改结果甚至短路。
	 *
	 * Spring 原生：
	 *   HandlerInterceptor — preHandle(可短路) / postHandle(可改结果) / afterCompletion(必执行)
	 *   TransactionInterceptor — invoke 环绕拦截（开启事务→调用→提交/回滚）
	 *   ClientHttpRequestInterceptor — RestTemplate 出站拦截
	 *
	 * 关键区分：
	 *   vs Listener：Listener 不能改结果，Interceptor 能改结果甚至短路
	 *   vs Decorator：Decorator 包装对象长期持有，Interceptor 拦截调用每次触发
	 *   vs Processor：Processor 对"对象"做增强，Interceptor 对"调用"做拦截
	 */
	static void exp16_interceptor() {
		banner("实验16: Interceptor — 三段式调用拦截");

		PayLogInterceptor interceptor = new PayLogInterceptor();
		PayChannel channel = new SimplePayChannel();

		PayRequest request = new PayRequest("ORD-014", 6600, "ALIPAY");
		PayResult result = null;
		Exception ex = null;

		// ── 模拟 HandlerInterceptor 的三段式调用 ──
		try {
			// preHandle → 可以短路
			boolean proceed = interceptor.preHandle(request);
			if (!proceed) {
				System.out.println("  [Interceptor] 被短路，不执行!");
				return;
			}

			// 执行目标
			result = channel.pay(request.getOrderId(), request.getAmountInCents());

			// postHandle → 可以修改结果
			interceptor.postHandle(request, result);
		}
		catch (Exception e) {
			ex = e;
		}
		finally {
			// afterCompletion → 无论成功失败都执行
			interceptor.afterCompletion(request, result, ex);
		}
	}

	// ─── 实验7: Spring 原生命名对照（完整 18 种）─────────────────────────────

	/**
	 * 从 Spring 源码的命名范式，直接映射到你的业务命名。
	 * 每个 Spring 类名都是一个"设计意图声明"。
	 */
	static void exp7_springNativeMapping() {
		banner("实验7: Spring 原生命名完整对照表（18 种范式）");

		System.out.println("  ┌── 前缀：声明\"我是谁\"─────────────────────────────────────────────────┐");
		System.out.println("  │ 范式         │ Spring 典型类                     │ 业务映射             │");
		System.out.println("  │ Abstract     │ AbstractApplicationContext        │ AbstractPayChannel   │");
		System.out.println("  │ Default      │ DefaultListableBeanFactory        │ DefaultPayChannel    │");
		System.out.println("  │ Simple       │ SimpleApplicationEventMulticaster │ SimplePayChannel     │");
		System.out.println("  │ Generic      │ GenericApplicationContext         │ GenericPayRequest    │");
		System.out.println("  │ Configurable │ ConfigurableApplicationContext    │ ConfigurableRetryPolicy│");
		System.out.println("  ├── 后缀：声明\"我干什么\"───────────────────────────────────────────────┤");
		System.out.println("  │ Support      │ ApplicationObjectSupport          │ PayChannelSupport    │");
		System.out.println("  │ Template     │ JdbcTemplate                      │ PayTemplate          │");
		System.out.println("  │ Delegate     │ PostProcessorRegistrationDelegate │ PayRoutingDelegate   │");
		System.out.println("  │ Registry     │ BeanDefinitionRegistry            │ PayChannelRegistry   │");
		System.out.println("  │ Resolver     │ HandlerExceptionResolver          │ PayChannelResolver   │");
		System.out.println("  │ Processor    │ BeanPostProcessor                 │ PayRequestProcessor  │");
		System.out.println("  │ Aware        │ ApplicationContextAware           │ PayContextAware      │");
		System.out.println("  │ Capable      │ HierarchicalBeanFactory           │ RefundCapable        │");
		System.out.println("  │ ★ Holder    │ RequestContextHolder              │ PayContextHolder     │");
		System.out.println("  │ ★ Composite │ ArgumentResolverComposite         │ CompositePayValidator│");
		System.out.println("  │ ★ Decorator │ TransactionAwareCacheDecorator    │ LoggingPayDecorator  │");
		System.out.println("  │ ★ Adapter   │ HandlerAdapter                    │ ThirdPartyPayAdapter │");
		System.out.println("  │ ★ Builder   │ BeanDefinitionBuilder             │ PayRequestBuilder    │");
		System.out.println("  │ ★ Utils     │ BeanUtils / StringUtils           │ PayUtils             │");
		System.out.println("  │ ★ Listener  │ ApplicationListener               │ PaySuccessListener   │");
		System.out.println("  │ ★ Configurer│ WebMvcConfigurer                  │ PayChannelConfigurer │");
		System.out.println("  │ ★ Initializer│ ApplicationContextInitializer    │ PayChannelInitializer│");
		System.out.println("  │ ★ Interceptor│ HandlerInterceptor / TxInterceptor│ PayLogInterceptor   │");
		System.out.println("  └────────────────────────────────────────────────────────────────────────┘");

		System.out.println();
		System.out.println("  === 关键区分（必须记住 7 组）===");
		System.out.println("  ① Default vs Simple    : Default 功能完整(生产用), Simple 功能最简(测试stub)");
		System.out.println("  ② Support vs Template  : Support 工具基类(被继承), Template 算法骨架(被调用)");
		System.out.println("  ③ Resolver vs Processor: Resolver 做转换(A→B类型变), Processor 做增强(A→A'类型不变)");
		System.out.println("  ④ Aware vs Capable     : Aware \"我需要什么\"(容器注入), Capable \"我能提供什么\"(能力声明)");
		System.out.println("  ⑤ Decorator vs Adapter : Decorator 接口不变加行为, Adapter 接口变了做桥接");
		System.out.println("  ⑥ Interceptor vs Listener: Interceptor 能改结果/短路, Listener 只旁路通知");
		System.out.println("  ⑦ Configurer vs Initializer: Configurer 声明式收集参数, Initializer 命令式执行动作");
	}

	// ─── 工具方法 ───────────────────────────────────────────────────────────

	private static void banner(String title) {
		String line = String.join("", Collections.nCopies(80, "="));
		System.out.println("\n" + line);
		System.out.println("  " + title);
		System.out.println(line);
	}
}
