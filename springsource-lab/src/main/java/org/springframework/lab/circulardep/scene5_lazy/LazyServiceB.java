package org.springframework.lab.circulardep.scene5_lazy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Scene 5: LazyServiceB 构造器依赖 A (非 @Lazy), 但因为 A 的 B 参数标了 @Lazy,
 * 所以 A 构造时不会真正触发 B 的创建, 打破了循环。
 */
@Component
public class LazyServiceB {

	private final LazyServiceA a;

	@Autowired
	public LazyServiceB(LazyServiceA a) {
		System.out.println("  [Scene5] LazyServiceB 构造 — 注入的 a 是 " + a.getClass().getSimpleName());
		this.a = a;
	}

	public String call() {
		return "LazyB";
	}
}
