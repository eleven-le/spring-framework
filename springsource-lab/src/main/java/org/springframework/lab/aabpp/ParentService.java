package org.springframework.lab.aabpp;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 坑6 演示: 父类 private 字段/方法注入
 *
 * AABPP.buildAutowiringMetadata 递归扫描父类:
 *   do { scan fields/methods of targetClass } while ((targetClass = targetClass.getSuperclass()) != null)
 *
 * 所以父类的 @Autowired private 字段也会被注入!
 * 但要注意: 如果子类重写了父类的 @Autowired setter, AABPP 会去重(BridgeMethodResolver)
 */
public abstract class ParentService {

	@Autowired
	private MessageSender parentInjected;  // private! 但仍会被 AABPP 通过反射注入

	public String getParentInjectedChannel() {
		return parentInjected != null ? parentInjected.channel() : "NULL";
	}
}
