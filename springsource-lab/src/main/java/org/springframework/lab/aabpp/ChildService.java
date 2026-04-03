package org.springframework.lab.aabpp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 坑6 演示: 子类继承父类的注入点
 *
 * 注入顺序: 父类字段 → 子类字段 → 父类方法 → 子类方法
 * (buildAutowiringMetadata 收集时从子类向上遍历, 但 elements 列表是父类在前)
 *
 * 断点: InjectionMetadata#inject → 观察 checkedElements 的顺序
 */
@Component
public class ChildService extends ParentService {

	@Autowired
	private MessageSender childInjected;

	public void demo() {
		System.out.println("[坑6-父类private注入] parentInjected → " + getParentInjectedChannel());
		System.out.println("[坑6-子类字段注入]     childInjected  → " + childInjected.channel());
	}
}
