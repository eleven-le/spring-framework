package org.springframework.lab.circulardep.scene2_constructor;

import org.springframework.stereotype.Component;

/**
 * Scene 2: 构造器注入循环依赖的另一端。
 */
@Component
public class CtorServiceB {

	private final CtorServiceA a;

	public CtorServiceB(CtorServiceA a) {
		System.out.println("  [Scene2] CtorServiceB 构造 — 需要 A");
		this.a = a;
	}
}
