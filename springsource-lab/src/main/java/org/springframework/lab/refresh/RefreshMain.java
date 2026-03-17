package org.springframework.lab.refresh;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * W03 - refresh() 12 大步骤调试入口
 *
 * <p>断点位置：AbstractApplicationContext#refresh() 第 554 行
 * <p>调试方式：F8 逐步走完 12 步，观察每步做了什么
 *
 * <pre>
 * 12 步速记口诀：
 *   准备 → 拿工厂 → 配工厂 → 子类扩展 →
 *   调 BFPP → 注册 BPP → 消息源 → 多播器 →
 *   子类 onRefresh → 注册监听器 → 实例化单例 → 收尾发事件
 * </pre>
 */
public class RefreshMain {

	public static void main(String[] args) {
		// 断点打在 AnnotationConfigApplicationContext 构造器内部的 refresh() 调用
		// 或直接打在 AbstractApplicationContext#refresh() 第一行（第 554 行）
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AppConfig.class);
		ctx.close();
	}
}
