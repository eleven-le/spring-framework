package org.springframework.lab.circulardep.scene4_prototype;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Scene 4: Prototype 作用域循环依赖 — 必死, 三级缓存只对 singleton 生效。
 *
 * 原因: doGetBean 在进入创建前先检查 isPrototypeCurrentlyInCreation,
 * prototype 没有缓存机制, 一旦检测到当前线程正在创建同名 prototype 就直接抛异常。
 *
 * 代码位置: AbstractBeanFactory#doGetBean 行 275
 *   if (isPrototypeCurrentlyInCreation(beanName)) {
 *       throw new BeanCurrentlyInCreationException(beanName);
 *   }
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProtoA {

	@Autowired
	private ProtoB protoB;

	public ProtoA() {
		System.out.println("  [Scene4] ProtoA 构造");
	}
}
