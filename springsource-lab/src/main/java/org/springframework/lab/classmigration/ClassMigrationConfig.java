package org.springframework.lab.classmigration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lab.classmigration.notification.NotificationChannel;
import org.springframework.lab.classmigration.notification.email.EmailChannel;
import org.springframework.lab.classmigration.notification.push.PushChannel;
import org.springframework.lab.classmigration.notification.sms.SmsChannel;
import org.springframework.lab.classmigration.notification.template.NotificationTemplate;
import org.springframework.lab.classmigration.pay.PayChannel;
import org.springframework.lab.classmigration.pay.alipay.AlipayChannel;
import org.springframework.lab.classmigration.pay.resolver.PayChannelResolver;
import org.springframework.lab.classmigration.pay.template.PayTemplate;
import org.springframework.lab.classmigration.pay.wechat.WechatPayChannel;
import org.springframework.lab.classmigration.risk.RiskRule;
import org.springframework.lab.classmigration.risk.amount.AmountLimitRule;
import org.springframework.lab.classmigration.risk.blacklist.BlacklistRule;
import org.springframework.lab.classmigration.risk.engine.RiskEngine;
import org.springframework.lab.classmigration.risk.frequency.FrequencyRule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring 配置类 —— 装配三个业务域
 *
 * <h3>Spring 映射</h3>
 * <ul>
 *   <li>对标 TransactionAutoConfiguration —— 用 @Bean 装配 TM、TI、APC</li>
 *   <li>对标 AopAutoConfiguration —— 用 @Bean 装配 ProxyCreator</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>List&lt;PayChannel&gt; 自动注入 —— Spring 收集所有 PayChannel Bean</li>
 *   <li>阈值外部化 —— 实际场景通过 @ConfigurationProperties 注入</li>
 *   <li>新增渠道/规则只需加 @Bean —— 零修改现有代码，开闭原则</li>
 * </ul>
 */
@Configuration
public class ClassMigrationConfig {

	// ==================== 支付域 ====================

	@Bean
	public AlipayChannel alipayChannel() {
		return new AlipayChannel();
	}

	@Bean
	public WechatPayChannel wechatPayChannel() {
		return new WechatPayChannel();
	}

	/** Resolver 构造器注入 List<PayChannel> —— Spring 自动收集所有 PayChannel Bean */
	@Bean
	public PayChannelResolver payChannelResolver(List<PayChannel> channels) {
		return new PayChannelResolver(channels);
	}

	@Bean
	public PayTemplate payTemplate(PayChannelResolver resolver) {
		return new PayTemplate(resolver);
	}

	// ==================== 风控域 ====================

	@Bean
	public BlacklistRule blacklistRule() {
		return new BlacklistRule();
	}

	@Bean
	public AmountLimitRule amountLimitRule() {
		// 审核阈值 5000，拒绝阈值 50000 —— 实际场景通过 @ConfigurationProperties 配置
		return new AmountLimitRule(
				new BigDecimal("5000"),
				new BigDecimal("50000")
		);
	}

	@Bean
	public FrequencyRule frequencyRule() {
		return new FrequencyRule(3);  // 窗口内最多 3 次
	}

	/** RiskEngine 构造器注入 List<RiskRule> —— 自动收集 + 排序 */
	@Bean
	public RiskEngine riskEngine(List<RiskRule> rules) {
		return new RiskEngine(rules);
	}

	// ==================== 通知域 ====================

	@Bean
	public SmsChannel smsChannel() {
		return new SmsChannel();
	}

	@Bean
	public EmailChannel emailChannel() {
		return new EmailChannel();
	}

	@Bean
	public PushChannel pushChannel() {
		return new PushChannel();
	}

	/** NotificationTemplate 构造器注入 List<NotificationChannel> */
	@Bean
	public NotificationTemplate notificationTemplate(List<NotificationChannel> channels) {
		return new NotificationTemplate(channels);
	}
}
