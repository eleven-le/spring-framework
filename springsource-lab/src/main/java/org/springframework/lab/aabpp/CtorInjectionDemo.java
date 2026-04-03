package org.springframework.lab.aabpp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 落点3: 构造器注入演示
 *
 * 构造器注入走的路径:
 *   createBeanInstance → determineConstructorsFromBeanPostProcessors
 *     → AABPP#determineCandidateConstructors
 *       → 扫描所有构造器, 找 @Autowired 标注的
 *       → 如果只有一个构造器且非无参 → 自动选定(不需要 @Autowired)
 *       → 如果有多个 @Autowired(required=false) → 全部作为候选
 *       → 如果有一个 @Autowired(required=true) → 必须唯一, 否则报错
 *
 * 关键区别: 构造器参数由 ConstructorResolver#resolveAutowiredArgument 解析
 *           不走 postProcessProperties, 而是走 autowireConstructor
 *
 * 断点: AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors
 */
@Component
public class CtorInjectionDemo {

	private final MessageSender primarySender;
	private final List<MessageSender> allSenders;

	/**
	 * 唯一构造器 + @Autowired (Spring 4.3+ 单构造器可省略 @Autowired)
	 * 这里显式写上是为了让 AABPP 打断点时更容易命中
	 */
	@Autowired
	public CtorInjectionDemo(MessageSender primarySender, List<MessageSender> allSenders) {
		this.primarySender = primarySender;
		this.allSenders = allSenders;
		System.out.println("  [构造器注入] CtorInjectionDemo 构造完成, primarySender="
				+ primarySender.channel() + ", allSenders.size=" + allSenders.size());
	}

	public void demo() {
		System.out.println("[落点3-构造器注入] primary=" + primarySender.channel()
				+ ", all=" + allSenders.size() + "个渠道");
	}
}
