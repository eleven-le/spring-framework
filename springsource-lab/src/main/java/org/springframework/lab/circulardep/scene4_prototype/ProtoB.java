package org.springframework.lab.circulardep.scene4_prototype;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Scene 4: Prototype 循环依赖的另一端。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProtoB {

	@Autowired
	private ProtoA protoA;

	public ProtoB() {
		System.out.println("  [Scene4] ProtoB 构造");
	}
}
