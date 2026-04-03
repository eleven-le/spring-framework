package org.springframework.lab.classmigration;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.lab.classmigration.notification.NotificationMessage;
import org.springframework.lab.classmigration.notification.NotificationResult;
import org.springframework.lab.classmigration.notification.template.NotificationTemplate;
import org.springframework.lab.classmigration.pay.PayOrder;
import org.springframework.lab.classmigration.pay.PayResult;
import org.springframework.lab.classmigration.pay.template.PayTemplate;
import org.springframework.lab.classmigration.risk.RiskContext;
import org.springframework.lab.classmigration.risk.RiskResult;
import org.springframework.lab.classmigration.risk.engine.RiskEngine;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W100 - 类图迁移实战：从 Spring 类设计哲学到 C 端业务建模
 *
 * <h2>核心命题</h2>
 * <p>Spring 源码里反复出现的三层设计（接口→骨架→实现）+ 四种模式（模板方法/策略/工厂/门面），
 * 如何迁移到真实的 C 端业务？本 demo 用三个完整场景演示迁移方法论。
 *
 * <h2>三个场景的 Spring 映射</h2>
 * <pre>
 * ┌──────────────┬─────────────────────────┬─────────────────────────────────────┐
 * │  业务场景     │  类图结构                │  Spring 原型                         │
 * ├──────────────┼─────────────────────────┼─────────────────────────────────────┤
 * │  支付渠道     │  PayChannel             │  PlatformTransactionManager         │
 * │              │  AbstractPayChannel     │  AbstractPlatformTransactionManager │
 * │              │  AlipayChannel          │  DataSourceTransactionManager       │
 * │              │  PayTemplate            │  TransactionTemplate / JdbcTemplate │
 * │              │  PayChannelResolver     │  TransactionInterceptor#determineTM │
 * ├──────────────┼─────────────────────────┼─────────────────────────────────────┤
 * │  风控引擎     │  RiskRule               │  BeanPostProcessor                  │
 * │              │  AbstractRiskRule       │  (无直接骨架，但设计同构)              │
 * │              │  BlacklistRule          │  PriorityOrdered 的 BPP             │
 * │              │  RiskEngine             │  PostProcessorRegistrationDelegate  │
 * │              │                         │  + DefaultAdvisorChainFactory       │
 * ├──────────────┼─────────────────────────┼─────────────────────────────────────┤
 * │  通知系统     │  NotificationChannel    │  Resource / MessageSource           │
 * │              │  AbstractNotifChannel   │  AbstractPlatformTxMgr (骨架同构)    │
 * │              │  SmsChannel             │  ClassPathResource (具体实现)        │
 * │              │  NotificationTemplate   │  JdbcTemplate / RestTemplate        │
 * └──────────────┴─────────────────────────┴─────────────────────────────────────┘
 * </pre>
 *
 * <h2>迁移方法论 5 步</h2>
 * <ol>
 *   <li>识别变化点 vs 不变点 —— 变化点下沉为子类钩子，不变点上提为骨架</li>
 *   <li>定义契约接口 —— 3 个方法以内，只宣告能力，不暴露实现</li>
 *   <li>抽取骨架 Abstract* —— 封装 60%+ 公共逻辑，用 final 锁流程</li>
 *   <li>实现落地层 —— 每个实现只关心自己的差异点</li>
 *   <li>包装门面 Template —— 业务代码只调门面，不碰内部结构</li>
 * </ol>
 *
 * <h2>断点建议</h2>
 * <ol>
 *   <li>AbstractPayChannel#prepay() —— 观察模板方法如何调度 doSign()+doExecutePrepay()</li>
 *   <li>AbstractRiskRule#evaluate() —— 观察骨架如何做开关+适用检查+异常兜底</li>
 *   <li>RiskEngine#evaluate() —— 观察责任链如何排序+短路</li>
 * </ol>
 */
public class ClassMigrationMain {

	public static void main(String[] args) {

		System.out.println("╔══════════════════════════════════════════════════════════════════╗");
		System.out.println("║  W100 类图迁移实战：Spring 设计哲学 → C 端业务建模                  ║");
		System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(ClassMigrationConfig.class);

		PayTemplate payTemplate = ctx.getBean(PayTemplate.class);
		RiskEngine riskEngine = ctx.getBean(RiskEngine.class);
		NotificationTemplate notifyTemplate = ctx.getBean(NotificationTemplate.class);

		// ==================================================================
		// 场景一：多支付渠道 —— 模板方法 + 策略 + 门面
		// ==================================================================
		System.out.println("\n" + "======================================================================");
		System.out.println("【场景一】多支付渠道 —— 模板方法 + 策略 + 门面");
		System.out.println("  Spring 原型: PlatformTransactionManager → APTM → DSTM");
		System.out.println("  迁移产物: PayChannel → AbstractPayChannel → AlipayChannel");
		System.out.println("======================================================================");

		// 1a. 支付宝下单
		System.out.println("\n--- 支付宝下单 ---");
		PayResult alipayResult = payTemplate.pay(
				new PayOrder("ORD_001", "alipay", new BigDecimal("199.90"), "iPhone 手机壳"));
		System.out.println("  最终结果: " + alipayResult);

		// 1b. 微信支付下单
		System.out.println("\n--- 微信支付下单 ---");
		PayResult wechatResult = payTemplate.pay(
				new PayOrder("ORD_002", "wechat", new BigDecimal("59.90"), "蓝牙耳机"));
		System.out.println("  最终结果: " + wechatResult);

		// 1c. 不存在的渠道 —— 测试 Resolver 的异常处理
		System.out.println("\n--- 不存在的渠道（PayPal 还没接入）---");
		PayResult unknownResult = payTemplate.pay(
				new PayOrder("ORD_003", "paypal", new BigDecimal("299.00"), "AirPods"));
		System.out.println("  最终结果: " + unknownResult);

		// 1d. 扩展性演示：新增渠道只需做什么？
		System.out.println("\n  ★ 新增 PayPal 渠道只需 3 步:");
		System.out.println("    1. 创建 PayPalChannel extends AbstractPayChannel");
		System.out.println("    2. 实现 doSign()/doExecutePrepay()/doExecuteQuery()");
		System.out.println("    3. 在 Config 加一个 @Bean → Resolver 零修改！");

		// ==================================================================
		// 场景二：风控规则引擎 —— 责任链 + 策略 + 排序
		// ==================================================================
		System.out.println("\n" + "======================================================================");
		System.out.println("【场景二】风控规则引擎 —— 责任链 + 策略 + 排序");
		System.out.println("  Spring 原型: BPP/Advisor 注册+排序+链式执行");
		System.out.println("  迁移产物: RiskRule → AbstractRiskRule → BlacklistRule");
		System.out.println("  RiskEngine ≈ PostProcessorRegistrationDelegate + AdvisorChainFactory");
		System.out.println("======================================================================");

		// 2a. 正常订单 —— 所有规则通过
		System.out.println("\n--- 正常订单（全部通过）---");
		RiskResult r1 = riskEngine.evaluate(
				new RiskContext("USER_NORMAL", "ORD_101", new BigDecimal("100"), "FP_001", "10.0.0.1"));
		System.out.println("  最终结果: " + r1);

		// 2b. 黑名单用户 —— 第一条规则就短路
		System.out.println("\n--- 黑名单用户（首条规则短路）---");
		RiskResult r2 = riskEngine.evaluate(
				new RiskContext("USER_BANNED_001", "ORD_102", new BigDecimal("50"), "FP_002", "10.0.0.2"));
		System.out.println("  最终结果: " + r2);
		System.out.println("  ★ 短路优化：黑名单 O(1) 查表命中后，后续金额+频率规则不执行！");

		// 2c. 大额订单 —— 金额超过审核阈值
		System.out.println("\n--- 大额订单（人工审核）---");
		RiskResult r3 = riskEngine.evaluate(
				new RiskContext("USER_VIP", "ORD_103", new BigDecimal("8000"), "FP_003", "10.0.0.3"));
		System.out.println("  最终结果: " + r3);

		// 2d. 超大额订单 —— 金额超过拒绝阈值
		System.out.println("\n--- 超大额订单（直接拒绝）---");
		RiskResult r4 = riskEngine.evaluate(
				new RiskContext("USER_WHALE", "ORD_104", new BigDecimal("80000"), "FP_004", "10.0.0.4"));
		System.out.println("  最终结果: " + r4);

		// 2e. 频率攻击 —— 同一用户连续下单超限
		System.out.println("\n--- 频率攻击（同一用户连续 5 次）---");
		for (int i = 1; i <= 5; i++) {
			RiskResult r = riskEngine.evaluate(
					new RiskContext("USER_ATTACKER", "ORD_20" + i, new BigDecimal("10"), "FP_005", "10.0.0.5"));
			System.out.println("  第" + i + "次结果: " + r);
		}

		// ==================================================================
		// 场景三：多渠道通知 —— 模板方法 + 门面 + 幂等
		// ==================================================================
		System.out.println("\n" + "======================================================================");
		System.out.println("【场景三】多渠道通知 —— 模板方法 + 门面 + 幂等");
		System.out.println("  Spring 原型: Resource/MessageSource 多实现 + JdbcTemplate 门面");
		System.out.println("  迁移产物: NotificationChannel → AbstractNotificationChannel → SmsChannel");
		System.out.println("  骨架核心：幂等检查 + 重试机制（对标事务的重复提交保护 + RetryTemplate）");
		System.out.println("======================================================================");

		// 3a. 短信通知
		System.out.println("\n--- 短信通知 ---");
		NotificationResult smsResult = notifyTemplate.send(
				new NotificationMessage("MSG_001", "sms", "13800138000", "PAY_SUCCESS", "您的订单已支付成功"));
		System.out.println("  最终结果: " + smsResult);

		// 3b. 幂等拦截 —— 同一 messageId 重复发送
		System.out.println("\n--- 幂等拦截（同一 messageId 再次发送）---");
		NotificationResult smsResult2 = notifyTemplate.send(
				new NotificationMessage("MSG_001", "sms", "13800138000", "PAY_SUCCESS", "您的订单已支付成功"));
		System.out.println("  最终结果: " + smsResult2);
		System.out.println("  ★ 幂等保护：骨架自动拦截，子类零感知！");

		// 3c. 邮件通知
		System.out.println("\n--- 邮件通知 ---");
		NotificationResult emailResult = notifyTemplate.send(
				new NotificationMessage("MSG_002", "email", "user@example.com", "ORDER_SHIPPED", "您的包裹已发出"));
		System.out.println("  最终结果: " + emailResult);

		// 3d. 多渠道广播 —— 同一事件触发多渠道
		System.out.println("\n--- 多渠道广播（支付成功 → 短信 + 邮件 + Push）---");
		Map<String, String> channelToRecipient = new LinkedHashMap<>();
		channelToRecipient.put("sms", "13800138000");
		channelToRecipient.put("email", "user@example.com");
		channelToRecipient.put("push", "DEVICE_TOKEN_XYZ");
		notifyTemplate.broadcast("BROADCAST_001", "您的订单 ORD_001 已支付成功", channelToRecipient);

		// ==================================================================
		// 场景四（额外发挥）：完整下单链路串联 —— 三域协作
		// ==================================================================
		System.out.println("\n" + "======================================================================");
		System.out.println("【场景四】完整下单链路 —— 风控 → 支付 → 通知 三域串联");
		System.out.println("  这就是实际业务中的调用方式：Controller → Service → 三域门面");
		System.out.println("  业务代码只调三个 Template/Engine，完全不碰内部类图");
		System.out.println("======================================================================");

		simulateOrderFlow(riskEngine, payTemplate, notifyTemplate,
				"USER_NORMAL_2", "ORD_FINAL_001", new BigDecimal("299.00"), "alipay");

		System.out.println();
		simulateOrderFlow(riskEngine, payTemplate, notifyTemplate,
				"USER_BANNED_002", "ORD_FINAL_002", new BigDecimal("100.00"), "wechat");

		// ==================================================================
		// 设计洞察总结
		// ==================================================================
		System.out.println("\n" + "======================================================================");
		System.out.println("【设计洞察】三个领域的同构性");
		System.out.println("======================================================================");
		System.out.println();
		System.out.println("  ┌─────────────┬─────────────────────┬───────────────────┬──────────────────────┐");
		System.out.println("  │  角色         │  支付域              │  风控域             │  通知域               │");
		System.out.println("  ├─────────────┼─────────────────────┼───────────────────┼──────────────────────┤");
		System.out.println("  │  接口(契约)   │  PayChannel         │  RiskRule          │  NotificationChannel │");
		System.out.println("  │  骨架(流程)   │  AbstractPayChannel │  AbstractRiskRule  │  AbstractNotifCh     │");
		System.out.println("  │  实现(落地)   │  AlipayChannel      │  BlacklistRule     │  SmsChannel          │");
		System.out.println("  │  门面(入口)   │  PayTemplate        │  RiskEngine        │  NotificationTemplate│");
		System.out.println("  │  路由(策略)   │  PayChannelResolver │  order 排序        │  channelType 路由     │");
		System.out.println("  ├─────────────┼─────────────────────┼───────────────────┼──────────────────────┤");
		System.out.println("  │  骨架封装     │  签名+日志+异常翻译  │  开关+适用+异常兜底 │  幂等+重试+日志        │");
		System.out.println("  │  子类职责     │  HTTP调用+响应解析   │  规则逻辑           │  渠道发送              │");
		System.out.println("  │  Spring 原型  │  APTM→DSTM          │  BPP+AdvisorChain  │  Resource+JdbcTpl    │");
		System.out.println("  └─────────────┴─────────────────────┴───────────────────┴──────────────────────┘");
		System.out.println();
		System.out.println("  核心洞察：");
		System.out.println("  1. 接口层 = 能力宣告（3个方法以内），面向调用方，不暴露实现细节");
		System.out.println("  2. 骨架层 = 流程锁定（final方法），封装60%+公共逻辑（日志/校验/异常/重试）");
		System.out.println("  3. 实现层 = 差异下沉（abstract钩子），只写各自不同的40%");
		System.out.println("  4. 门面层 = 业务入口（Template），屏蔽路由/异常/编排，业务代码一行搞定");
		System.out.println("  5. 迁移公式：找到 变化点/不变点 → 不变点上提为骨架 → 变化点下沉为钩子 → 门面收口");

		ctx.close();
	}

	/**
	 * 模拟完整下单链路：风控 → 支付 → 通知
	 *
	 * <p>这就是业务 Service 的写法 —— 只调三个门面，不碰内部类图
	 */
	private static void simulateOrderFlow(RiskEngine riskEngine, PayTemplate payTemplate,
			NotificationTemplate notifyTemplate,
			String userId, String orderId, BigDecimal amount, String payChannel) {

		System.out.println("\n  >>> 下单链路开始: user=" + userId + ", order=" + orderId
				+ ", amount=" + amount + ", channel=" + payChannel);

		// Step 1: 风控评估
		System.out.println("  [Step 1] 风控评估");
		RiskResult riskResult = riskEngine.evaluate(
				new RiskContext(userId, orderId, amount, "FP_AUTO", "10.0.0.1"));
		if (riskResult.isBlocked()) {
			System.out.println("  >>> 下单链路终止: 风控拦截 — " + riskResult);
			return;
		}

		// Step 2: 支付
		System.out.println("  [Step 2] 发起支付");
		PayResult payResult = payTemplate.pay(new PayOrder(orderId, payChannel, amount, "商品"));
		if (!payResult.isSuccess()) {
			System.out.println("  >>> 下单链路终止: 支付失败 — " + payResult);
			return;
		}

		// Step 3: 通知（支付成功后）
		System.out.println("  [Step 3] 发送支付成功通知");
		notifyTemplate.send(new NotificationMessage(
				"NOTIFY_" + orderId, "sms", "13800138000", "PAY_SUCCESS",
				"订单 " + orderId + " 支付成功，金额 " + amount));

		System.out.println("  >>> 下单链路完成 ✓");
	}
}
